package com.fasterxml.jackson.dataformat.avro;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#908]: nested classes named by schema (with dots, as with
// Avro 1.9+) are resolved when deserializing, by type id resolver (using databind's
// class lookup), instead of by probing when schema is processed
public class NestedClassTypeId908Test extends AvroTestBase
{
    static class Leaf {
        public String name;

        protected Leaf() { }
        public Leaf(String n) { name = n; }

        // Nested class, 2 levels deep
        static class SubLeaf {
            public int id;

            protected SubLeaf() { }
            public SubLeaf(int i) { id = i; }
        }
    }

    static class ObjectWrapper {
        public Object value;

        protected ObjectWrapper() { }
        public ObjectWrapper(Object v) { value = v; }
    }

    private final static String TEST_CLASS_NAME = NestedClassTypeId908Test.class.getName();

    private final AvroMapper MAPPER = newMapper();

    private AvroSchema _wrapperSchema(String namespace, String name, String fieldDef) throws Exception {
        return MAPPER.schemaFrom("{\"type\":\"record\",\"name\":\"ObjectWrapper\",\"fields\":["
                +"{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"" + name + "\","
                +"\"namespace\":\"" + namespace + "\","
                +"\"fields\":[" + fieldDef + "]}}]}");
    }

    @Test
    public void testNestedClassResolvedFromDottedNamespace() throws Exception
    {
        AvroSchema schema = _wrapperSchema(TEST_CLASS_NAME, "Leaf",
                "{\"name\":\"name\",\"type\":\"string\"}");
        byte[] avro = MAPPER.writer(schema).writeValueAsBytes(new ObjectWrapper(new Leaf("abc")));
        // twice, to cover cached resolution too
        for (int i = 0; i < 2; ++i) {
            ObjectWrapper result = MAPPER.readerFor(ObjectWrapper.class)
                    .with(schema)
                    .readValue(avro);
            assertEquals(Leaf.class, result.value.getClass());
            assertEquals("abc", ((Leaf) result.value).name);
        }
    }

    @Test
    public void testDeeperNestedClassResolvedFromDottedNamespace() throws Exception
    {
        AvroSchema schema = _wrapperSchema(TEST_CLASS_NAME + ".Leaf", "SubLeaf",
                "{\"name\":\"id\",\"type\":\"int\"}");
        byte[] avro = MAPPER.writer(schema).writeValueAsBytes(new ObjectWrapper(new Leaf.SubLeaf(42)));
        ObjectWrapper result = MAPPER.readerFor(ObjectWrapper.class)
                .with(schema)
                .readValue(avro);
        assertEquals(Leaf.SubLeaf.class, result.value.getClass());
        assertEquals(42, ((Leaf.SubLeaf) result.value).id);
    }

    // Older style with dollar signs in namespace still works
    @Test
    public void testNestedClassResolvedFromDollarNamespace() throws Exception
    {
        AvroSchema schema = _wrapperSchema(TEST_CLASS_NAME + "$", "Leaf",
                "{\"name\":\"name\",\"type\":\"string\"}");
        byte[] avro = MAPPER.writer(schema).writeValueAsBytes(new ObjectWrapper(new Leaf("abc")));
        ObjectWrapper result = MAPPER.readerFor(ObjectWrapper.class)
                .with(schema)
                .readValue(avro);
        assertEquals(Leaf.class, result.value.getClass());
    }

    // Name that matches no class: "natural" type (Map), as before
    @Test
    public void testUnknownClassNameReadAsMap() throws Exception
    {
        AvroSchema schema = _wrapperSchema(TEST_CLASS_NAME, "NoSuchClass",
                "{\"name\":\"name\",\"type\":\"string\"}");
        for (int i = 0; i < 2; ++i) {
            ObjectWrapper result = MAPPER.readerFor(ObjectWrapper.class)
                    .with(schema)
                    .readValue(new byte[] { 6, 'a', 'b', 'c' });
            assertInstanceOf(Map.class, result.value);
            assertEquals("abc", ((Map<?,?>) result.value).get("name"));
        }
    }

    @Test
    public void testProbeLimits() throws Exception
    {
        // no dots, nothing to probe
        assertFalse(AvroTypeIdResolver._shouldProbe(""));
        assertFalse(AvroTypeIdResolver._shouldProbe("Name"));

        final int maxDots = AvroTypeIdResolver.MAX_PROBE_DOTS;
        assertEquals(32, maxDots);
        assertTrue(AvroTypeIdResolver._shouldProbe("a.B"));
        assertTrue(AvroTypeIdResolver._shouldProbe(_dottedName(maxDots - 1)));
        assertTrue(AvroTypeIdResolver._shouldProbe(_dottedName(maxDots)));
        assertFalse(AvroTypeIdResolver._shouldProbe(_dottedName(maxDots + 1)));
        assertFalse(AvroTypeIdResolver._shouldProbe(_dottedName(maxDots + 100)));

        final int maxLen = AvroTypeIdResolver.MAX_PROBE_LENGTH;
        assertEquals(1024, maxLen);
        assertTrue(AvroTypeIdResolver._shouldProbe("a." + _longName(maxLen - 3)));
        assertTrue(AvroTypeIdResolver._shouldProbe("a." + _longName(maxLen - 2)));
        assertFalse(AvroTypeIdResolver._shouldProbe("a." + _longName(maxLen - 1)));
        assertFalse(AvroTypeIdResolver._shouldProbe("a." + _longName(100_000)));
    }

    // Names beyond limits are not probed, read as "natural" type
    @Test
    public void testExcessivelyDottedNameReadAsMap() throws Exception
    {
        AvroSchema schema = _wrapperSchema(_dottedName(40), "Leaf",
                "{\"name\":\"name\",\"type\":\"string\"}");
        ObjectWrapper result = MAPPER.readerFor(ObjectWrapper.class)
                .with(schema)
                .readValue(new byte[] { 6, 'a', 'b', 'c' });
        assertInstanceOf(Map.class, result.value);
    }

    private static String _dottedName(int dots) {
        StringBuilder sb = new StringBuilder("a");
        for (int i = 0; i < dots; ++i) {
            sb.append(".a");
        }
        return sb.toString();
    }

    private static String _longName(int len) {
        StringBuilder sb = new StringBuilder(len);
        while (sb.length() < len) {
            sb.append('x');
        }
        return sb.toString();
    }
}
