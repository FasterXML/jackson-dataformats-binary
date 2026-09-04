package com.fasterxml.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    public void testLongNameDoesNotCollideWithNulPrefixedLonger() throws Exception
    {
        // Names over 64 bytes use the "long" (marker-terminated) encoding, decoded by
        // NonBlockingByteArrayParser._finishLongFieldName(): 68-byte name ends with
        // NUL,NUL,NUL,'a' (17 full quads), 65-byte one with a partial quad holding 'a',
        // so without padding both end in quad 0x00000061 and collide.
        final String prefix = _repeat('x', 64);
        final String n1 = prefix + new String(new char[] { 0, 0, 0, 'a' });
        final String n2 = prefix + "a";

        byte[] doc1 = MAPPER.writeValueAsBytes(_mapOf(n1, 1));
        byte[] doc2 = MAPPER.writeValueAsBytes(_mapOf(n2, 2));

        // Long names are only added to the symbol table by the blocking parser, so seed
        // the factory-shared table with the longer name first...
        assertEquals(listOf(n1), _readNamesBlocking(doc1));
        // ... and then verify async parser does not match it for the shorter name
        assertEquals(listOf(n2), _readNamesAsync(doc2, Integer.MAX_VALUE));
        assertEquals(listOf(n2), _readNamesAsync(doc2, 7));
    }

    // [dataformats-binary#761]: async parser must also add "long" (marker-terminated)
    // names to symbol table, the way blocking parser does; otherwise every occurrence
    // gets decoded anew
    @Test
    public void testLongNameAddedToSymbolTable() throws Exception
    {
        final String name = _repeat('z', 70);
        byte[] doc = _writeRepeatedNameWithoutSharing(name, 2);

        List<String> names = _readNamesAsync(doc, Integer.MAX_VALUE);
        assertEquals(listOf(name, name), names);
        assertSame(names.get(0), names.get(1),
                "second occurrence of long name should be found from symbol table");

        // and symbol table entry must also be visible to later parsers of same factory
        List<String> namesLater = _readNamesAsync(doc, 5);
        assertSame(names.get(0), namesLater.get(0),
                "long name should have been added to factory-shared symbol table");
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

    // Writes {@code count} objects, each with the same property name, with shared-name
    // back-references disabled so that every occurrence is encoded in full
    private byte[] _writeRepeatedNameWithoutSharing(String name, int count) throws Exception
    {
        SmileFactory f = SmileFactory.builder()
                .disable(SmileGenerator.Feature.CHECK_SHARED_NAMES)
                .build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = f.createGenerator(bytes)) {
            g.writeStartArray();
            for (int i = 0; i < count; ++i) {
                g.writeStartObject();
                g.writeNumberField(name, i);
                g.writeEndObject();
            }
            g.writeEndArray();
        }
        return bytes.toByteArray();
    }

    private static String _repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; ++i) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static Map<String, Object> _mapOf(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
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
