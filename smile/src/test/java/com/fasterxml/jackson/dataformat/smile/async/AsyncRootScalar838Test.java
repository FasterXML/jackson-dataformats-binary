package com.fasterxml.jackson.dataformat.smile.async;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [dataformats-binary#838]: non-blocking parser required header after
// a root-level scalar value (rejecting end marker, header-less root values)
public class AsyncRootScalar838Test extends AsyncTestBase
{
    private final SmileFactory F_REQ_HEADER = SmileFactory.builder()
            .enable(SmileParser.Feature.REQUIRE_HEADER)
            .build();

    private final SmileFactory F_NO_REQ_HEADER = SmileFactory.builder()
            .disable(SmileParser.Feature.REQUIRE_HEADER)
            .build();

    // int 1, end marker
    private final static byte[] DOC_INT_END_MARKER = _bytes(0x3A, 0x29, 0x0A, 0x01, 0xC2, 0xFF);

    // [1], end marker
    private final static byte[] DOC_ARRAY_END_MARKER = _bytes(0x3A, 0x29, 0x0A, 0x01, 0xF8, 0xC2, 0xF9, 0xFF);

    // two root-level ints
    private final static byte[] DOC_TWO_INTS = _bytes(0x3A, 0x29, 0x0A, 0x00, 0xC2, 0xC4);

    // int, then []
    private final static byte[] DOC_INT_ARRAY = _bytes(0x3A, 0x29, 0x0A, 0x00, 0xC2, 0xF8, 0xF9);

    // [], then int
    private final static byte[] DOC_ARRAY_INT = _bytes(0x3A, 0x29, 0x0A, 0x00, 0xF8, 0xF9, 0xC2);

    // int, then string "a", then end marker
    private final static byte[] DOC_INT_STRING_END_MARKER = _bytes(0x3A, 0x29, 0x0A, 0x01,
            0xC2, 0x40, 0x61, 0xFF);

    @Test
    public void testRootScalarWithEndMarker() throws Exception {
        _verifyMatchesBlocking(DOC_INT_END_MARKER);
        _verifyMatchesBlocking(DOC_INT_STRING_END_MARKER);
    }

    @Test
    public void testRootArrayWithEndMarker() throws Exception {
        _verifyMatchesBlocking(DOC_ARRAY_END_MARKER);
    }

    @Test
    public void testRootScalarFollowedByValues() throws Exception {
        _verifyMatchesBlocking(DOC_TWO_INTS);
        _verifyMatchesBlocking(DOC_INT_ARRAY);
        _verifyMatchesBlocking(DOC_ARRAY_INT);
    }

    // Also verify header-less content when header is not required
    @Test
    public void testRootScalarsWithoutHeader() throws Exception {
        byte[] doc = _bytes(0xC2, 0xC4, 0xF8, 0xF9, 0xC2, 0xFF);
        assertEquals(_blockingTokens(F_NO_REQ_HEADER, doc), _asyncTokens(F_NO_REQ_HEADER, doc, 1000, 0));
        assertEquals(_blockingTokens(F_NO_REQ_HEADER, doc), _asyncTokens(F_NO_REQ_HEADER, doc, 1, 1));
    }

    private void _verifyMatchesBlocking(byte[] doc) throws Exception
    {
        for (SmileFactory f : new SmileFactory[] { F_REQ_HEADER, F_NO_REQ_HEADER }) {
            List<JsonToken> exp = _blockingTokens(f, doc);
            for (int chunk : new int[] { 1, 2, 3, 1000 }) {
                for (int padding : new int[] { 0, 1 }) {
                    assertEquals(exp, _asyncTokens(f, doc, chunk, padding),
                            "chunk="+chunk+", padding="+padding);
                }
            }
        }
    }

    private List<JsonToken> _blockingTokens(SmileFactory f, byte[] doc) throws Exception
    {
        List<JsonToken> tokens = new ArrayList<>();
        try (JsonParser p = f.createParser(doc)) {
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private List<JsonToken> _asyncTokens(SmileFactory f, byte[] doc, int chunk, int padding)
        throws Exception
    {
        List<JsonToken> tokens = new ArrayList<>();
        AsyncReaderWrapper r = asyncForBytes(f, chunk, doc, padding);
        JsonToken t;
        while ((t = r.nextToken()) != null) {
            tokens.add(t);
        }
        r.close();
        return tokens;
    }

    private static byte[] _bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; ++i) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
