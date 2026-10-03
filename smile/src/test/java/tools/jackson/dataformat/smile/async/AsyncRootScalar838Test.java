package tools.jackson.dataformat.smile.async;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.smile.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import static tools.jackson.core.JsonToken.END_ARRAY;
import static tools.jackson.core.JsonToken.START_ARRAY;
import static tools.jackson.core.JsonToken.VALUE_NUMBER_INT;
import static tools.jackson.core.JsonToken.VALUE_STRING;

// [dataformats-binary#838]: non-blocking parser required header after
// a root-level scalar value (rejecting end marker, header-less root values)
public class AsyncRootScalar838Test extends AsyncTestBase
{
    private final static JsonToken INT = VALUE_NUMBER_INT;
    private final static JsonToken STRING = VALUE_STRING;

    private final SmileMapper MAPPER_REQ_HEADER = smileMapper(true, false, false);

    private final SmileMapper MAPPER_NO_REQ_HEADER = smileMapper(false, false, false);

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

    // NOTE: expected tokens include `null` for end-of-content, as well as
    // preceding `null` for end marker (if any)

    @Test
    public void testRootScalarWithEndMarker() throws Exception {
        _verify(DOC_INT_END_MARKER, INT, null, null);
        _verify(DOC_INT_STRING_END_MARKER, INT, STRING, null, null);
    }

    // End marker followed by header should only produce a single `null` token
    @Test
    public void testEndMarkerFollowedByHeader() throws Exception {
        _verify(DOC_INT_END_MARKER_HEADER_INT, INT, null, INT, null);
        _verify(DOC_ARRAY_END_MARKER_HEADER_ARRAY,
                START_ARRAY, END_ARRAY, null, START_ARRAY, END_ARRAY, null);
        // repeated headers: only collapsed if not followed by another header
        // (to limit recursion), same as with blocking parser
        _verify(DOC_INT_END_MARKER_HEADERS_INT, INT, null, null, null, INT, null);
    }

    @Test
    public void testRootScalarFollowedByHeader() throws Exception {
        _verify(DOC_INT_HEADER_INT, INT, null, INT, null);
        _verify(DOC_HEADERS_INT, INT, null);
    }

    @Test
    public void testRootArrayWithEndMarker() throws Exception {
        _verify(DOC_ARRAY_END_MARKER, START_ARRAY, INT, END_ARRAY, null, null);
    }

    @Test
    public void testRootScalarFollowedByValues() throws Exception {
        _verify(DOC_TWO_INTS, INT, INT, null);
        _verify(DOC_INT_ARRAY, INT, START_ARRAY, END_ARRAY, null);
        _verify(DOC_ARRAY_INT, START_ARRAY, END_ARRAY, INT, null);
    }

    // Also verify header-less content when header is not required
    @Test
    public void testRootScalarsWithoutHeader() throws Exception {
        byte[] doc = _bytes(0xC2, 0xC4, 0xF8, 0xF9, 0xC2, 0xFF);
        _verify(MAPPER_NO_REQ_HEADER, doc, INT, INT, START_ARRAY, END_ARRAY, INT, null, null);
    }

    private void _verify(byte[] doc, JsonToken... expTokens) throws Exception
    {
        _verify(MAPPER_REQ_HEADER, doc, expTokens);
        _verify(MAPPER_NO_REQ_HEADER, doc, expTokens);
    }

    // Verify that both blocking and non-blocking parsers produce expected tokens
    private void _verify(SmileMapper mapper, byte[] doc, JsonToken... expTokens) throws Exception
    {
        List<JsonToken> exp = Arrays.asList(expTokens);
        assertEquals(exp, _blockingTokens(mapper, doc), "blocking");
        for (int chunk : new int[] { 1, 2, 3, 1000 }) {
            for (int padding : new int[] { 0, 1 }) {
                assertEquals(exp, _asyncTokens(mapper, doc, chunk, padding),
                        "non-blocking, chunk="+chunk+", padding="+padding);
            }
        }
    }

    private List<JsonToken> _blockingTokens(SmileMapper mapper, byte[] doc) throws Exception
    {
        List<JsonToken> tokens = new ArrayList<>();
        try (JsonParser p = mapper.createParser(doc)) {
            _collectTokens(p, tokens, p::nextToken);
        }
        return tokens;
    }

    private List<JsonToken> _asyncTokens(SmileMapper mapper, byte[] doc, int chunk, int padding)
        throws Exception
    {
        List<JsonToken> tokens = new ArrayList<>();
        AsyncReaderWrapper r = asyncForBytes(mapper, chunk, doc, padding);
        _collectTokens(r.parser(), tokens, r::nextToken);
        r.close();
        return tokens;
    }

    interface TokenSource {
        JsonToken nextToken();
    }

    // Collects all tokens, including `null` tokens from end markers / in-line
    // headers: stops only when parser is closed (end of content)
    private void _collectTokens(JsonParser p, List<JsonToken> tokens,
            TokenSource src)
    {
        JsonToken t;
        do {
            t = src.nextToken();
            tokens.add(t);
            if (tokens.size() > 100) {
                fail("Too many tokens: "+tokens);
            }
        } while ((t != null) || !p.isClosed());
    }

    private static byte[] _bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; ++i) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
