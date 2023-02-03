package com.fasterxml.jackson.dataformat.smile.parse;

import java.lang.reflect.Field;
import java.util.Random;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;

import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParserBase;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;

public class SymbolTableTest extends BaseTestForSmile
{
    static class Point {
        public int x, y;
    }

    private final SmileMapper NO_CAN_MAPPER = SmileMapper.builder(SmileFactory.builder()
            .disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES)
            .build())
            .build();

    public void testSimpleDefault() throws Exception
    {
        final SmileMapper vanillaMapper = smileMapper();
        final byte[] doc = _smileDoc("{\"a\":1,\"b\":2}");

        // First: should have empty symbol table
        try (JsonParser p = vanillaMapper.createParser(doc)) {
            ByteQuadsCanonicalizer syms = _findSymbols(p);
            assertTrue(syms.isCanonicalizing()); // added in 2.13

            assertEquals(0, syms.size());
            assertEquals(0, _findParent(syms).size());

            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("a", p.currentName());
            assertEquals(1, syms.size());
            // not yet synced to parent
            assertEquals(0, _findParent(syms).size());

            while (p.nextToken() != null) { ; }
            assertEquals(2, syms.size());
            // but after closing, should sync
            assertEquals(2, _findParent(syms).size());
        }

        // by default, should canonicalize etc:
        try (JsonParser p = vanillaMapper.createParser(doc)) {
            ByteQuadsCanonicalizer syms = _findSymbols(p);
            assertEquals(2, syms.size());
            // also check that parent (root) has it all?
            assertEquals(2, _findParent(syms).size());

            // but no additions second time around
            while (p.nextToken() != null) { ; }
            assertEquals(2, syms.size());
        }

        // yet may get more added
        final byte[] doc2 = _smileDoc("{\"a\":1,\"foo\":2}");
        try (JsonParser p = vanillaMapper.createParser(doc2)) {
            ByteQuadsCanonicalizer syms = _findSymbols(p);
            assertEquals(2, syms.size());
            vanillaMapper.readValue(p, Object.class);
            syms = _findSymbols(p);
            assertEquals(3, syms.size());
        }

        // and verify it gets reflected too
        try (JsonParser p = vanillaMapper.createParser(doc)) {
            ByteQuadsCanonicalizer syms = _findSymbols(p);
            assertEquals(3, syms.size());
        }
    }

    // [dataformats-binary#252]: should be able to prevent canonicalization
    // Assumption: there is still non-null symbol table, but has "no canonicalization"
    public void testNoCanonicalizeWithMapper() throws Exception
    {
        final byte[] doc = _smileDoc(a2q("{ 'x':13, 'y':-999}"));
        try (JsonParser p = NO_CAN_MAPPER.createParser(doc)) {
            Point point = NO_CAN_MAPPER.readValue(p, Point.class);
            assertEquals(13, point.x);
            assertEquals(-999, point.y);
        }
    }

    // [dataformats-binary#252]: should be able to prevent canonicalization
    public void testSimpleNoCanonicalize() throws Exception
    {
        final String[] fieldNames = new String[] {
            // Ascii, various lengths
            "abc", "abcd123", "abcdefghi123940963", "",
            // Unicode, also (2-byte ones ought to be ok)
            "F\u00F6\u00F6", "F\u00F6\u00F6bar", "Longer F\u00F6\u00F6bar",

            // and then couple of longer names; total needs to exceed 64k
            generateName(77),
            generateName(2000),
            generateName(17000),
            generateName(23000),
            generateName(33033),
            "end", // just simple end marker
        };
        final byte[] doc = _smileDoc(jsonFrom(fieldNames));

        try (JsonParser p = NO_CAN_MAPPER.createParser(doc)) {
            ByteQuadsCanonicalizer syms = _findSymbols(p);
            assertFalse(syms.isCanonicalizing()); // added in 2.13
            assertEquals(-1, syms.size());
            // also, should not have parent:
            assertNull(_findParent(syms));

            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals(fieldNames[0], p.currentName());
            // should NOT add
            assertEquals(-1, syms.size());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(0, p.getIntValue());

            // and from thereon...
            for (int i = 1; i < fieldNames.length; ++i) {
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals(fieldNames[i], p.currentName());
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(i, p.getIntValue());
            }
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());

            assertEquals(-1, syms.size());
        }

        // But let's also try other accessors: first, nextName()
        try (JsonParser p = NO_CAN_MAPPER.createParser(doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(fieldNames[0], p.nextFieldName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(0, p.getIntValue());

            // and from thereon...
            for (int i = 1; i < fieldNames.length; ++i) {
                assertEquals(fieldNames[i], p.nextFieldName());
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(i, p.getIntValue());
            }
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());

            assertEquals(-1, _findSymbols(p).size());
        }

        // and then nextName(match)
        try (JsonParser p = NO_CAN_MAPPER.createParser(doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertTrue(p.nextFieldName(new SerializedString(fieldNames[0])));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(0, p.getIntValue());

            // and then negative match
            assertFalse(p.nextFieldName(new SerializedString("bogus")));
            assertEquals(fieldNames[1], p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());

            // and from thereon...
            for (int i = 2; i < fieldNames.length; ++i) {
                assertEquals(fieldNames[i], p.nextFieldName());
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(i, p.getIntValue());
            }
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());

            assertEquals(-1, _findSymbols(p).size());
        }
    }

    private String jsonFrom(String... fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        for (int i = 0, len = fields.length; i < len; ++i) {
            if (i > 0) {
                sb.append(",\n");
            }
            sb.append('"').append(fields[i]).append('"')
                .append(" : ").append(i);
        }
        sb.append("}");
        return sb.toString();
    }

    private String generateName(int minLen)
    {
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random(123);
        while (sb.length() < minLen) {
          int ch = rnd.nextInt(96);
          if (ch < 32) { // ascii (single byte)
               sb.append((char) (48 + ch));
          } else if (ch < 64) { // 2 byte
               sb.append((char) (128 + ch));
          } else { // 3 byte
               sb.append((char) (4000 + ch));
          }
        }
        return sb.toString();
    }

    // Helper method to dig up symbol table reference for tests, without
    // exposing it to user code
    private ByteQuadsCanonicalizer _findSymbols(JsonParser p) throws Exception
    {
        Field f = SmileParserBase.class.getDeclaredField("_symbols");
        f.setAccessible(true);
        return (ByteQuadsCanonicalizer) f.get(p);
    }

    private ByteQuadsCanonicalizer _findParent(ByteQuadsCanonicalizer sym) throws Exception
    {
        Field f = ByteQuadsCanonicalizer.class.getDeclaredField("_parent");
        f.setAccessible(true);
        return (ByteQuadsCanonicalizer) f.get(sym);
    }
}
