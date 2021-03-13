package com.fasterxml.jackson.dataformat.cbor.parse;

import java.lang.reflect.Field;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonFactory;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

public class SymbolTableTest extends CBORTestBase
{
    public void testSimpleDefault() throws Exception
    {
        final CBORMapper vanillaMapper = cborMapper();
        final byte[] doc = cborDoc("{\"a\":1,\"b\":2}");

        // First: should have empty symbol table
        try (JsonParser p = vanillaMapper.createParser(doc)) {
            ByteQuadsCanonicalizer syms = _findSymbols(p);
            assertEquals(0, syms.size());
            assertEquals(0, _findParent(syms).size());

            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
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
        final byte[] doc2 = cborDoc("{\"a\":1,\"foo\":2}");
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

    // !!! TODO:
    // [dataformats-binary#252]: should be able to prevent canonicalization
    public void testSimpleNoCanonicalize() throws Exception
    {
        final byte[] doc = cborDoc("{\"a\":1,\"b\":2}");
        final CBORMapper mapper = CBORMapper.builder(CBORFactory.builder()
                .disable(JsonFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
                .build())
                .build();

        try (JsonParser p = mapper.createParser(doc)) {
            ByteQuadsCanonicalizer syms = _findSymbols(p);
            assertEquals(0, syms.size());
        }
    }
    
    // Helper method to dig up symbol table reference for tests, without
    // exposing it to user code
    private ByteQuadsCanonicalizer _findSymbols(JsonParser p) throws Exception
    {
        Field f = CBORParser.class.getDeclaredField("_symbols");
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
