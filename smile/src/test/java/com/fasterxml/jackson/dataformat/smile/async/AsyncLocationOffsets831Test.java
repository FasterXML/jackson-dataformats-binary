package com.fasterxml.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [dataformats-binary#831]: non-blocking parser reported wrong byte offsets
// for both current location and current token location
public class AsyncLocationOffsets831Test extends AsyncTestBase
{
    private final SmileFactory F = new SmileFactory();

    @Test
    public void testSimpleArray() throws Exception
    {
        byte[] doc = _smileDoc("[1, 2]");
        // header (4 bytes), then START_ARRAY, 2 ints, END_ARRAY
        for (int padding : new int[] { 0, 1, 100 }) {
            AsyncReaderWrapper r = asyncForBytes(F, 1000, doc, padding);
            assertToken(JsonToken.START_ARRAY, r.nextToken());
            _assertOffsets(r.parser(), 4L, 5L);
            assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
            _assertOffsets(r.parser(), 5L, 6L);
            assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
            _assertOffsets(r.parser(), 6L, 7L);
            assertToken(JsonToken.END_ARRAY, r.nextToken());
            _assertOffsets(r.parser(), 7L, 8L);
            r.close();
        }
    }

    // Compare against blocking parser, with tokens that span chunks
    @Test
    public void testTokenOffsetsMatchBlocking() throws Exception
    {
        byte[] doc = _smileDoc(a2q("{'a':1,'name':'some longer String value here','arr':[true,null,-12345678,"
                +"0.25,12345678901234567890,'"+UNICODE_SEGMENT+"'],'nested':{'x':'y','a':[]}}"));
        List<Long> exp = _blockingTokenOffsets(doc);
        for (int chunk : new int[] { 1, 2, 3, 5, 7, 1000 }) {
            for (int padding : new int[] { 0, 1, 17 }) {
                AsyncReaderWrapper r = asyncForBytes(F, chunk, doc, padding);
                List<Long> act = new ArrayList<>();
                JsonToken t;
                do {
                    t = r.nextToken();
                    act.add(r.parser().currentTokenLocation().getByteOffset());
                } while (t != null);
                assertEquals(exp, act, "chunk="+chunk+", padding="+padding);
                // and at the end, should have consumed all content
                assertEquals((long) doc.length, r.parser().currentLocation().getByteOffset(),
                        "chunk="+chunk+", padding="+padding);
                r.close();
            }
        }
    }

    // Header ending exactly at chunk boundary: token location should not point
    // to the header while waiting for more content
    @Test
    public void testHeaderAtChunkBoundary() throws Exception
    {
        byte[] doc = _smileDoc("[1, 2]");
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
            byte[] buf = new byte[10 + doc.length];
            System.arraycopy(doc, 0, buf, 10, doc.length);
            feeder.feedInput(buf, 10, 14); // just the header
            assertToken(JsonToken.NOT_AVAILABLE, p.nextToken());
            _assertOffsets(p, 4L, 4L);
            feeder.feedInput(buf, 14, buf.length);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            _assertOffsets(p, 4L, 5L);
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            _assertOffsets(p, 5L, 6L);
        }
    }

    // Multiple documents, separated by in-line headers (and with end marker
    // at the end): compare against blocking parser
    @Test
    public void testMultipleDocsMatchBlocking() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(_smileDoc(a2q("{'a':'value','b':[1,2.5,'xyz']}")));
        bytes.write(_smileDoc(a2q("['some longer String value here',-123456789012345]")));
        bytes.write(_smileDoc(a2q("'"+UNICODE_SEGMENT+"'")));
        bytes.write(_smileDoc("[true,false]"));
        bytes.write(_smileDoc(a2q("{'x':{'y':null}}")));
        // NOTE: end marker only at the end: blocking and non-blocking parsers differ
        // in whether end marker followed by in-line header produces one or two
        // `null` tokens
        bytes.write(SmileConstants.BYTE_MARKER_END_OF_CONTENT);
        byte[] doc = bytes.toByteArray();

        List<String> exp = new ArrayList<>();
        try (JsonParser p = _smileParser(doc)) {
            _collectTokens(p, exp);
        }
        // sanity check: ends at end of content
        assertEquals("null@"+doc.length, exp.get(exp.size()-1));
        // and has a `null` token after each document (plus one for end marker)
        assertEquals(6L, exp.stream().filter(str -> str.startsWith("null@")).count(),
                "Should have `null` token after each document, got: "+exp);

        for (int chunk : new int[] { 1, 2, 3, 5, 7, 1000 }) {
            for (int padding : new int[] { 0, 1, 17 }) {
                AsyncReaderWrapper r = asyncForBytes(F, chunk, doc, padding);
                List<String> act = new ArrayList<>();
                // `AsyncReaderWrapper` handles NOT_AVAILABLE (feeding more)
                JsonToken t;
                do {
                    t = r.nextToken();
                    act.add(t+"@"+r.parser().currentTokenLocation().getByteOffset());
                } while ((t != null) || !r.parser().isClosed());
                assertEquals(exp, act, "chunk="+chunk+", padding="+padding);
                r.close();
            }
        }
    }

    private void _collectTokens(JsonParser p, List<String> tokens) throws Exception
    {
        JsonToken t;
        do {
            t = p.nextToken();
            tokens.add(t+"@"+p.currentTokenLocation().getByteOffset());
        } while ((t != null) || !p.isClosed());
    }

    private List<Long> _blockingTokenOffsets(byte[] doc) throws Exception
    {
        List<Long> offsets = new ArrayList<>();
        try (JsonParser p = _smileParser(doc)) {
            JsonToken t;
            do {
                t = p.nextToken();
                offsets.add(p.currentTokenLocation().getByteOffset());
            } while (t != null);
        }
        // sanity check: end-of-input "token" located at the end of content
        assertEquals(Long.valueOf(doc.length), offsets.get(offsets.size()-1));
        return offsets;
    }

    private void _assertOffsets(JsonParser p, long expTokenOffset, long expCurrOffset)
    {
        assertEquals(expTokenOffset, p.currentTokenLocation().getByteOffset());
        assertEquals(expCurrOffset, p.currentLocation().getByteOffset());
    }
}
