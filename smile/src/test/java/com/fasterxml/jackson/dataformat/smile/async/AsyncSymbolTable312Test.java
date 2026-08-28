package com.fasterxml.jackson.dataformat.smile.async;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Async counterpart of {@code SymbolTable312Test}: the blocking Smile parser
 * pads unused high bytes of a partial quad so that a short name cannot collide
 * with a longer NUL-prefixed one. The non-blocking parser missed that padding
 * ([dataformats-binary#761], follow-up to #312).
 */
public class AsyncSymbolTable312Test extends AsyncTestBase
{
    private final SmileMapper MAPPER = smileMapper();

    @Test
    public void testShortNameDoesNotCollideWithNulPrefixedLonger() throws Exception
    {
        // Issue #761 repro: 1-byte "a" and 4-byte "\0\0\0a" both hashed as 0x00000061
        // without padding, so the async parser reports the first name twice.
        final String n1 = new String(new char[] { 0, 0, 0, 'a' });
        final String n2 = "a";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put(n1, 1);
        m.put(n2, 2);
        byte[] doc = MAPPER.writeValueAsBytes(m);

        assertEquals(listOf(n1, n2), _readNamesBlocking(doc));
        assertEquals(listOf(n1, n2), _readNamesAsync(doc, Integer.MAX_VALUE));
        assertEquals(listOf(n1, n2), _readNamesAsync(doc, 1));
    }

    @Test
    public void testNullHandling1Quad() throws Exception
    {
        _testNullHandling(1);
        _testNullHandling(2);
    }

    @Test
    public void testNullHandling2Quads() throws Exception
    {
        _testNullHandling(5);
        _testNullHandling(6);
    }

    @Test
    public void testNullHandling3Quads() throws Exception
    {
        _testNullHandling(9);
        _testNullHandling(10);
    }

    @Test
    public void testNullHandlingNQuads() throws Exception
    {
        _testNullHandling(13);
        _testNullHandling(14);
        _testNullHandling(17);
        _testNullHandling(18);
        _testNullHandling(21);
    }

    private void _testNullHandling(int minNulls) throws Exception
    {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String name = _nulls(minNulls + i);
            expected.add(name);
            m.put(name, String.valueOf((char) ('a' + i)));
        }
        byte[] doc = MAPPER.writeValueAsBytes(m);

        assertEquals(expected, _readNamesBlocking(doc),
                "blocking parser should distinguish NUL-only names of length "
                        + minNulls + ".." + (minNulls + 4));
        assertEquals(expected, _readNamesAsync(doc, Integer.MAX_VALUE),
                "async parser should match blocking parser for NUL-only names of length "
                        + minNulls + ".." + (minNulls + 4));
        assertEquals(expected, _readNamesAsync(doc, 3),
                "async parser (chunked) should match blocking parser for NUL-only names of length "
                        + minNulls + ".." + (minNulls + 4));
    }

    private List<String> _readNamesBlocking(byte[] doc) throws Exception
    {
        List<String> names = new ArrayList<>();
        try (JsonParser p = MAPPER.createParser(doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                names.add(p.currentName());
                p.nextToken();
            }
            assertToken(JsonToken.END_OBJECT, p.currentToken());
        }
        return names;
    }

    private List<String> _readNamesAsync(byte[] doc, int bytesPerRead) throws Exception
    {
        List<String> names = new ArrayList<>();
        AsyncReaderWrapper p = asyncForBytes((SmileFactory) MAPPER.getFactory(), bytesPerRead, doc, 0);
        try {
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                if (t == JsonToken.FIELD_NAME) {
                    names.add(p.currentName());
                }
            }
        } finally {
            p.close();
        }
        return names;
    }

    private String _nulls(int len) {
        return new String(new byte[len], StandardCharsets.US_ASCII);
    }

    private static List<String> listOf(String... values) {
        List<String> list = new ArrayList<>(values.length);
        for (String v : values) {
            list.add(v);
        }
        return list;
    }
}
