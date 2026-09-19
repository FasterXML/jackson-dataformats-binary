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
 * what {@code IonReader} pulls from them. See [dataformats-binary#358].
 */
public class DocumentLengthIonReadTest
{
    // Small enough to be tripped by fixed-buffer input, which is checked exactly
    private final static int SMALL_LIMIT = 100;

    // Streaming checks allow the reader's buffer as slack, so a stream test needs
    // a document comfortably past limit + slack
    private final static int STREAM_LIMIT = 50_000;

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
        byte[] doc = BINARY_MAPPER.writeValueAsBytes(_text(200_000));
        assertTrue(doc.length > STREAM_LIMIT);
        _verifyTripsLimit(STREAM_LIMIT,
                () -> _mapper(STREAM_LIMIT).createParser(new ByteArrayInputStream(doc)));
    }

    @Test
    public void testReaderTripsLimit() throws Exception {
        String doc = VANILLA_MAPPER.writeValueAsString(_text(200_000));
        assertTrue(doc.length() > STREAM_LIMIT);
        _verifyTripsLimit(STREAM_LIMIT,
                () -> _mapper(STREAM_LIMIT).createParser(new StringReader(doc)));
    }

    // Documents within the limit must still read, from every source type
    @Test
    public void testWithinLimitPasses() throws Exception {
        byte[] binary = BINARY_MAPPER.writeValueAsBytes(_text(1_000));
        String textual = VANILLA_MAPPER.writeValueAsString(_text(1_000));
        final int limit = 10 * Math.max(binary.length, textual.length());

        _readAll(_mapper(limit).createParser(binary));
        _readAll(_mapper(limit).createParser(new ByteArrayInputStream(binary)));
        _readAll(_mapper(limit).createParser(textual.toCharArray(), 0, textual.length()));
        _readAll(_mapper(limit).createParser(new StringReader(textual)));
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
