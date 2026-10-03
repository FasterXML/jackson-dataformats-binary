package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import static com.fasterxml.jackson.core.JsonToken.END_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.START_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT;
import static com.fasterxml.jackson.core.JsonToken.VALUE_STRING;

// [dataformats-binary#838]: non-blocking parser required header after
// a root-level scalar value (rejecting end marker, header-less root values)
public class AsyncRootScalar838Test extends AsyncTestBase
{
    private final static JsonToken INT = VALUE_NUMBER_INT;
    private final static JsonToken STRING = VALUE_STRING;

    private final SmileFactory F_REQ_HEADER = smileFactory(true, false, false);

    private final SmileFactory F_NO_REQ_HEADER = smileFactory(false, false, false);

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

    // int, end marker, header, int
    private final static byte[] DOC_INT_END_MARKER_HEADER_INT = _bytes(0x3A, 0x29, 0x0A, 0x01,
            0xC2, 0xFF, 0x3A, 0x29, 0x0A, 0x01, 0xC4);

    // [], end marker, header, []
    private final static byte[] DOC_ARRAY_END_MARKER_HEADER_ARRAY = _bytes(0x3A, 0x29, 0x0A, 0x01,
            0xF8, 0xF9, 0xFF, 0x3A, 0x29, 0x0A, 0x01, 0xF8, 0xF9);

    // int, header, int (no end marker)
    private final static byte[] DOC_INT_HEADER_INT = _bytes(0x3A, 0x29, 0x0A, 0x00,
            0xC2, 0x3A, 0x29, 0x0A, 0x00, 0xC4);

    // int, end marker, 3 headers, int
    private final static byte[] DOC_INT_END_MARKER_HEADERS_INT = _bytes(0x3A, 0x29, 0x0A, 0x01,
            0xC2, 0xFF, 0x3A, 0x29, 0x0A, 0x01, 0x3A, 0x29, 0x0A, 0x01, 0x3A, 0x29, 0x0A, 0x01,
            0xC4);

    // 2 initial headers, int
    private final static byte[] DOC_HEADERS_INT = _bytes(0x3A, 0x29, 0x0A, 0x00,
            0x3A, 0x29, 0x0A, 0x00, 0xC2);

    // NOTE: trailing `null` token (end marker at the end of content) is not
    // included in expected tokens

    @Test
    public void testRootScalarWithEndMarker() throws Exception {
        _verify(DOC_INT_END_MARKER, INT);
        _verify(DOC_INT_STRING_END_MARKER, INT, STRING);
    }

    // End marker followed by header should only produce a single `null` token
    @Test
    public void testEndMarkerFollowedByHeader() throws Exception {
        _verify(DOC_INT_END_MARKER_HEADER_INT, INT, null, INT);
        _verify(DOC_ARRAY_END_MARKER_HEADER_ARRAY,
                START_ARRAY, END_ARRAY, null, START_ARRAY, END_ARRAY);
        // repeated headers: only collapsed if not followed by another header
        // (to limit recursion), same as with blocking parser
        _verify(DOC_INT_END_MARKER_HEADERS_INT, INT, null, null, null, INT);
    }

    @Test
    public void testRootScalarFollowedByHeader() throws Exception {
        _verify(DOC_INT_HEADER_INT, INT, null, INT);
        _verify(DOC_HEADERS_INT, INT);
    }

    @Test
    public void testRootArrayWithEndMarker() throws Exception {
        _verify(DOC_ARRAY_END_MARKER, START_ARRAY, INT, END_ARRAY);
    }

    @Test
    public void testRootScalarFollowedByValues() throws Exception {
        _verify(DOC_TWO_INTS, INT, INT);
        _verify(DOC_INT_ARRAY, INT, START_ARRAY, END_ARRAY);
        _verify(DOC_ARRAY_INT, START_ARRAY, END_ARRAY, INT);
    }

    // Also verify header-less content when header is not required
    @Test
    public void testRootScalarsWithoutHeader() throws Exception {
        byte[] doc = _bytes(0xC2, 0xC4, 0xF8, 0xF9, 0xC2, 0xFF);
        _verify(F_NO_REQ_HEADER, doc, INT, INT, START_ARRAY, END_ARRAY, INT);
    }

    private void _verify(byte[] doc, JsonToken... expTokens) throws Exception
    {
        _verify(F_REQ_HEADER, doc, expTokens);
        _verify(F_NO_REQ_HEADER, doc, expTokens);
    }

    // Verify that both blocking and non-blocking parsers produce expected tokens
    private void _verify(SmileFactory f, byte[] doc, JsonToken... expTokens) throws Exception
    {
        List<JsonToken> exp = Arrays.asList(expTokens);
        assertEquals(exp, _blockingTokens(f, doc), "blocking");
        for (int chunk : new int[] { 1, 2, 3, 1000 }) {
            for (int padding : new int[] { 0, 1 }) {
                assertEquals(exp, _asyncTokens(f, doc, chunk, padding),
                        "non-blocking, chunk="+chunk+", padding="+padding);
            }
        }
    }

    private List<JsonToken> _blockingTokens(SmileFactory f, byte[] doc) throws Exception
    {
        List<JsonToken> tokens = new ArrayList<>();
        try (JsonParser p = f.createParser(doc)) {
            _collectTokens(p, doc, tokens, p::nextToken);
        }
        return tokens;
    }

    private List<JsonToken> _asyncTokens(SmileFactory f, byte[] doc, int chunk, int padding)
        throws Exception
    {
        List<JsonToken> tokens = new ArrayList<>();
        AsyncReaderWrapper r = asyncForBytes(f, chunk, doc, padding);
        _collectTokens(r.parser(), doc, tokens, r::nextToken);
        r.close();
        return tokens;
    }

    interface TokenSource {
        JsonToken nextToken() throws IOException;
    }

    // Collects all tokens, including `null` tokens from end markers / in-line
    // headers: stops only when all content has been consumed
    private void _collectTokens(JsonParser p, byte[] doc, List<JsonToken> tokens,
            TokenSource src) throws IOException
    {
        while (true) {
            JsonToken t = src.nextToken();
            if (t == null && p.currentLocation().getByteOffset() >= doc.length) {
                break;
            }
            tokens.add(t);
            if (tokens.size() > 100) {
                fail("Too many tokens: "+tokens);
            }
        }
    }

    private static byte[] _bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; ++i) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
