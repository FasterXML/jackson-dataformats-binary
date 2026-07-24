package com.fasterxml.jackson.dataformat.smile.constraints;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.async.AsyncReaderWrapper;
import com.fasterxml.jackson.dataformat.smile.async.AsyncTestBase;

// [dataformats-binary#726]: `maxNameLength` was not enforced by Smile parsers
public class LongNameSmileReadTest extends AsyncTestBase
{
    private final static int MAX_NAME_LEN = 1000;

    private final SmileFactory F_VANILLA = new SmileFactory();

    private final SmileFactory F_CONSTRAINED = SmileFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNameLength(MAX_NAME_LEN)
                    .build())
            .build();

    // Names of 64 bytes or less use the "short name" encodings, which are
    // length-bounded by the format itself; anything longer uses the "long
    // name" encoding, which was unbounded before the fix.

    public void testLongNameBlocking() throws Exception
    {
        for (boolean stream : new boolean[] { true, false }) {
            _verifyFails(_nameDoc(MAX_NAME_LEN + 100), stream);
            // and one much longer than the limit, to check we fail before
            // buffering it all
            _verifyFails(_nameDoc(400_000), stream);
        }
    }

    public void testLongNameAsync() throws Exception
    {
        // vary feed sizes to exercise both the single-chunk and the
        // split-across-feeds paths
        for (int bytesPerFeed : new int[] { 1, 7, 1000, 100_000 }) {
            _verifyFailsAsync(_nameDoc(MAX_NAME_LEN + 100), bytesPerFeed);
            _verifyFailsAsync(_nameDoc(400_000), bytesPerFeed);
        }
    }

    // Names at or below the limit must still be accepted
    public void testNameWithinLimitBlocking() throws Exception
    {
        for (boolean stream : new boolean[] { true, false }) {
            for (int len : new int[] { 100, MAX_NAME_LEN }) {
                _verifyPasses(_nameDoc(len), _name(len), stream);
            }
        }
    }

    public void testNameWithinLimitAsync() throws Exception
    {
        for (int bytesPerFeed : new int[] { 1, 7, 1000, 100_000 }) {
            for (int len : new int[] { 100, MAX_NAME_LEN }) {
                _verifyPassesAsync(_nameDoc(len), _name(len), bytesPerFeed);
            }
        }
    }

    // Symbol table caches decoded names; a hit must not bypass the check.
    // First parse (which populates the table) has a lax limit, second one
    // does not -- and both use the same underlying symbol table root.
    public void testLongNameNotBypassedViaSymbolTable() throws Exception
    {
        final byte[] doc = _nameDoc(400_000);

        SmileFactory lax = SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNameLength(1_000_000)
                        .maxStringLength(1_000_000)
                        .build())
                .build();
        try (JsonParser p = lax.createParser(doc)) {
            while (p.nextToken() != null) { }
        }
        // now the same name is in `lax`'s symbol table; a constrained factory
        // must still reject it (and so must `lax` itself, were it constrained)
        _verifyFails(doc, true);
        _verifyFails(doc, false);
        _verifyFailsAsync(doc, 100_000);
    }

    private void _verifyFails(byte[] doc, boolean stream) throws Exception
    {
        try (JsonParser p = stream
                ? F_CONSTRAINED.createParser(new ByteArrayInputStream(doc))
                : F_CONSTRAINED.createParser(doc, 0, doc.length)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            _verifyNameLengthException(e);
        }
    }

    private void _verifyFailsAsync(byte[] doc, int bytesPerFeed) throws Exception
    {
        AsyncReaderWrapper p = asyncForBytes(F_CONSTRAINED, bytesPerFeed, doc, 0);
        try {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException (bytesPerFeed: "+bytesPerFeed+")");
        } catch (StreamConstraintsException e) {
            _verifyNameLengthException(e);
        }
    }

    private void _verifyNameLengthException(StreamConstraintsException e)
    {
        final String msg = e.getMessage();
        assertTrue("Unexpected message: "+msg, msg.contains("Name length ("));
        assertTrue("Unexpected message: "+msg,
                msg.contains("exceeds the maximum allowed ("+MAX_NAME_LEN));
    }

    private void _verifyPasses(byte[] doc, String expName, boolean stream) throws Exception
    {
        try (JsonParser p = stream
                ? F_CONSTRAINED.createParser(new ByteArrayInputStream(doc))
                : F_CONSTRAINED.createParser(doc, 0, doc.length)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals(expName, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    private void _verifyPassesAsync(byte[] doc, String expName, int bytesPerFeed) throws Exception
    {
        AsyncReaderWrapper p = asyncForBytes(F_CONSTRAINED, bytesPerFeed, doc, 0);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(expName, p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    private byte[] _nameDoc(int nameLen) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(nameLen + 100);
        try (JsonGenerator g = F_VANILLA.createGenerator(bytes)) {
            g.writeStartObject();
            g.writeFieldName(_name(nameLen));
            g.writeString("v");
            g.writeEndObject();
        }
        return bytes.toByteArray();
    }

    // ASCII name, so byte length == character length
    private String _name(int len)
    {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }
}
