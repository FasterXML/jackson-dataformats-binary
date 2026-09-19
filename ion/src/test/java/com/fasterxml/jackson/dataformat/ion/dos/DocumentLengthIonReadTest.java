package com.fasterxml.jackson.dataformat.ion.dos;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that {@link StreamReadConstraints#getMaxDocumentLength()} is enforced
 * by the Ion parser, for fixed buffers up front and for streaming sources by counting
 * what {@code IonReader} pulls from them. See [dataformats-binary#805].
 */
public class DocumentLengthIonReadTest
{
    // Deliberately far below ion-java's read buffer size (32kB): all source types
    // must honor limits smaller than that, not just the fixed-buffer ones
    private final static int SMALL_LIMIT = 100;

    private final IonObjectMapper VANILLA_MAPPER = IonObjectMapper.builderForTextualWriters().build();
    private final IonObjectMapper BINARY_MAPPER = IonObjectMapper.builderForBinaryWriters().build();

    @Test
    public void testByteArrayTripsLimit() throws Exception {
        byte[] doc = BINARY_MAPPER.writeValueAsBytes(_text(500));
        assertTrue(doc.length > SMALL_LIMIT);
        _verifyTripsLimit(SMALL_LIMIT, () -> _mapper(SMALL_LIMIT).createParser(doc));
    }

    @Test
    public void testCharArrayTripsLimit() throws Exception {
        char[] doc = VANILLA_MAPPER.writeValueAsString(_text(500)).toCharArray();
        assertTrue(doc.length > SMALL_LIMIT);
        _verifyTripsLimit(SMALL_LIMIT, () -> _mapper(SMALL_LIMIT).createParser(doc, 0, doc.length));
    }

    @Test
    public void testInputStreamTripsLimit() throws Exception {
        byte[] doc = BINARY_MAPPER.writeValueAsBytes(_text(500));
        assertTrue(doc.length > SMALL_LIMIT);
        _verifyTripsLimit(SMALL_LIMIT,
                () -> _mapper(SMALL_LIMIT).createParser(new ByteArrayInputStream(doc)));
    }

    @Test
    public void testReaderTripsLimit() throws Exception {
        String doc = VANILLA_MAPPER.writeValueAsString(_text(500));
        assertTrue(doc.length() > SMALL_LIMIT);
        _verifyTripsLimit(SMALL_LIMIT,
                () -> _mapper(SMALL_LIMIT).createParser(new StringReader(doc)));
    }

    // `IonFactory.canUseCharArrays()` is `false`, so `createParser(String)` -- and hence
    // `readValue(String)` / `readTree(String)`, the common textual entry point -- goes
    // through `Reader`, not the exactly-checked `char[]` path
    @Test
    public void testStringSourceTripsLimit() throws Exception {
        final String doc = VANILLA_MAPPER.writeValueAsString(_text(500));
        assertTrue(doc.length() > SMALL_LIMIT);
        _verifyTripsLimit(SMALL_LIMIT, () -> _mapper(SMALL_LIMIT).createParser(doc));

        try {
            _mapper(SMALL_LIMIT).readTree(doc);
            fail("Should not pass: document length should exceed limit");
        } catch (StreamConstraintsException e) {
            assertTrue("Unexpected message: "+e.getMessage(),
                    e.getMessage().contains("Document length"));
        }
    }

    // Same content must be accepted or rejected the same way whatever source type
    // it is fed through
    @Test
    public void testAllSourceTypesAgree() throws Exception {
        byte[] binary = BINARY_MAPPER.writeValueAsBytes(_text(500));
        String textual = VANILLA_MAPPER.writeValueAsString(_text(500));

        // Over the limit: every source type rejects
        _verifyTripsLimit(SMALL_LIMIT, () -> _mapper(SMALL_LIMIT).createParser(binary));
        _verifyTripsLimit(SMALL_LIMIT,
                () -> _mapper(SMALL_LIMIT).createParser(new ByteArrayInputStream(binary)));
        _verifyTripsLimit(SMALL_LIMIT,
                () -> _mapper(SMALL_LIMIT).createParser(textual.toCharArray(), 0, textual.length()));
        _verifyTripsLimit(SMALL_LIMIT,
                () -> _mapper(SMALL_LIMIT).createParser(new StringReader(textual)));

        // Under it: every source type accepts
        final int ample = 10 * Math.max(binary.length, textual.length());
        _readAll(_mapper(ample).createParser(binary));
        _readAll(_mapper(ample).createParser(new ByteArrayInputStream(binary)));
        _readAll(_mapper(ample).createParser(textual.toCharArray(), 0, textual.length()));
        _readAll(_mapper(ample).createParser(new StringReader(textual)));
    }

    // Reported length must be a real count, not internal bookkeeping: at least the
    // limit that was breached, never more than the document actually holds
    @Test
    public void testReportedLengthIsPlausible() throws Exception {
        final byte[] doc = BINARY_MAPPER.writeValueAsBytes(_text(50_000));
        final int limit = 1_000;
        try {
            _readAll(_mapper(limit).createParser(new ByteArrayInputStream(doc)));
            fail("Should not pass: document length should exceed limit");
        } catch (StreamConstraintsException e) {
            long reported = _reportedLength(e.getMessage());
            assertTrue("Reported length "+reported+" should exceed limit "+limit,
                    reported > limit);
            assertTrue("Reported length "+reported+" should not exceed document size "+doc.length,
                    reported <= doc.length);
        }
    }

    private long _reportedLength(String msg) {
        int start = msg.indexOf('(');
        int end = msg.indexOf(')', start);
        assertTrue("Unexpected message: "+msg, start > 0 && end > start);
        return Long.parseLong(msg.substring(start+1, end));
    }

    // Documents within the limit must still read, from every source type
    @Test
    public void testWithinLimitPasses() throws Exception {
        String textual = VANILLA_MAPPER.writeValueAsString(_text(1_000));
        final int limit = 10 * textual.length();
        assertNotNull(_mapper(limit).readTree(textual));
    }

    // Without a configured limit nothing should be checked (and no wrapper applied)
    @Test
    public void testNoLimitConfigured() throws Exception {
        byte[] doc = BINARY_MAPPER.writeValueAsBytes(_text(200_000));
        _readAll(VANILLA_MAPPER.createParser(new ByteArrayInputStream(doc)));
    }

    private IonObjectMapper _mapper(int maxDocLength) {
        IonFactory f = IonFactory.builderForTextualWriters().build();
        f.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxDocumentLength(maxDocLength).build());
        return IonObjectMapper.builder(f).build();
    }

    private interface ParserSupplier {
        JsonParser get() throws Exception;
    }

    private void _verifyTripsLimit(int limit, ParserSupplier supplier) throws Exception {
        JsonParser p = null;
        try {
            p = supplier.get();
            _readAll(p);
            fail("Should not pass: document length should exceed limit");
        } catch (StreamConstraintsException e) {
            final String msg = e.getMessage();
            assertTrue("Unexpected message: "+msg, msg.contains("Document length"));
            assertTrue("Unexpected message: "+msg,
                    msg.contains("exceeds the maximum allowed ("+limit
                            +", from `StreamReadConstraints.getMaxDocumentLength()`)"));
        } finally {
            if (p != null) {
                p.close();
            }
        }
    }

    private void _readAll(JsonParser p) throws Exception {
        try {
            while (p.nextToken() != null) { }
        } finally {
            p.close();
        }
    }

    // Single Ion struct with one long text value, of approximately given length
    private Object _text(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; ++i) {
            sb.append((char) ('a' + (i % 26)));
        }
        return java.util.Collections.singletonMap("value", sb.toString());
    }
}
