package com.fasterxml.jackson.dataformat.smile.async;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
                while (r.nextToken() != null) {
                    act.add(r.parser().currentTokenLocation().getByteOffset());
                }
                assertEquals(exp, act, "chunk="+chunk+", padding="+padding);
                // and at the end, should have consumed all content
                assertEquals((long) doc.length, r.parser().currentLocation().getByteOffset(),
                        "chunk="+chunk+", padding="+padding);
                r.close();
            }
        }
    }

    private List<Long> _blockingTokenOffsets(byte[] doc) throws Exception
    {
        List<Long> offsets = new ArrayList<>();
        try (JsonParser p = _smileParser(doc)) {
            while (p.nextToken() != null) {
                offsets.add(p.currentTokenLocation().getByteOffset());
            }
        }
        return offsets;
    }

    private void _assertOffsets(JsonParser p, long expTokenOffset, long expCurrOffset)
    {
        assertEquals(expTokenOffset, p.currentTokenLocation().getByteOffset());
        assertEquals(expCurrOffset, p.currentLocation().getByteOffset());
    }
}
