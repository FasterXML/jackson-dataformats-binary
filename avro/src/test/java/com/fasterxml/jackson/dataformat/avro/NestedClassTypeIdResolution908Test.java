package com.fasterxml.jackson.dataformat.avro;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#908]: resolution of type ids of nested classes, for which
// schemas use dot as separator between enclosing and nested class; for cases
// other than ClassLoader handling (see `NestedClassTypeId908Test` for that)
public class NestedClassTypeIdResolution908Test extends AvroTestBase
{
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    static abstract class Base { }

    static class ImplA extends Base { public String a; }
    static class ImplB extends Base { public int b; }

    static class Bean { public Base value; }

    static class ObjectBean { public Object value; }

    static class Mid {
        static class Leaf { public int b; }
    }

    static class InitTracker {
        static volatile boolean initialized;
    }

    // Never referenced by test code other than by name
    static class NotToBeInitialized {
        public int b;

        static {
            InitTracker.initialized = true;
        }
    }

    private final static String NS = NestedClassTypeIdResolution908Test.class.getName();

    // Schema with union of 2 records, with given namespaces (if any) and names
    private static String _schemaJson(String name1, String name2) {
        return "{\"type\":\"record\",\"name\":\"Bean\",\"namespace\":\"" + NS + "\","
                +"\"fields\":[{\"name\":\"value\",\"type\":["
                +"{\"type\":\"record\",\"name\":\"" + name1 + "\",\"fields\":[{\"name\":\"a\",\"type\":\"string\"}]},"
                +"{\"type\":\"record\",\"name\":\"" + name2 + "\",\"fields\":[{\"name\":\"b\",\"type\":\"int\"}]}"
                +"]}]}";
    }

    // union index 1 (zigzag: 2), then int 42 (zigzag: 84)
    private final static byte[] AVRO_SECOND_42 = new byte[] { 2, 84 };

    private final AvroMapper MAPPER = newMapper();

    // Type id exposed by parser is name of the actual (nested) class; found
    // without initializing the class (name comes from possibly untrusted schema)
    @Test
    public void testStreamingTypeIdForNestedClass() throws Exception
    {
        final AvroSchema schema = MAPPER.schemaFrom(_schemaJson("ImplA", "NotToBeInitialized"));
        try (JsonParser p = MAPPER.getFactory().createParser(AVRO_SECOND_42)) {
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(NS + "$NotToBeInitialized", p.getTypeId());
            // and same if asked again
            assertEquals(NS + "$NotToBeInitialized", p.getTypeId());
        }
        assertFalse(InitTracker.initialized);
    }

    // No class to find: name as schema has it
    @Test
    public void testStreamingTypeIdForUnknownClass() throws Exception
    {
        final AvroSchema schema = MAPPER.schemaFrom(_schemaJson("ImplA", "NoSuchClass"));
        try (JsonParser p = MAPPER.getFactory().createParser(AVRO_SECOND_42)) {
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(NS + ".NoSuchClass", p.getTypeId());
        }
    }

    // Standard (non-Avro) type id resolver must be able to use native type ids too
    @Test
    public void testClassNameTypeInfoWithNestedClasses() throws Exception
    {
        final AvroSchema schema = MAPPER.schemaFrom(_schemaJson("ImplA", "ImplB"));
        // (writing of type ids via `@JsonTypeInfo` not supported, so hand-encoded)
        Bean result = MAPPER.readerFor(Bean.class).with(schema).readValue(AVRO_SECOND_42);
        assertNotNull(result.value);
        assertEquals(ImplB.class, result.value.getClass());
        assertEquals(42, ((ImplB) result.value).b);
    }

    @Test
    public void testUntypedWithNestedClasses() throws Exception
    {
        final AvroSchema schema = MAPPER.schemaFrom(_schemaJson("ImplA", "ImplB"));
        ObjectBean result = _roundTripUntyped(schema, _implB(42));
        assertEquals(ImplB.class, result.value.getClass());
        assertEquals(42, ((ImplB) result.value).b);
    }

    // Check for registered subtypes must apply to nested classes too
    @Test
    public void testUnregisteredSubtypeNotAllowed() throws Exception
    {
        final AvroSchema schema = MAPPER.schemaFrom(_schemaJson("ImplA", "ImplB"));
        final byte[] avro = MAPPER.writer(schema).writeValueAsBytes(_objectBean(_implB(42)));
        ObjectBean result = MAPPER.readerFor(ObjectBean.class)
                .with(schema)
                .with(DeserializationFeature.FAIL_ON_SUBTYPE_CLASS_NOT_REGISTERED)
                .readValue(avro);
        // not resolved into class: bound as "untyped"
        assertTrue(result.value instanceof Map<?,?>,
                "Expected Map, got: "+result.value.getClass().getName());
        assertEquals(42, ((Map<?,?>) result.value).get("b"));
    }

    // Names that use both dollar sign and dot as separators: either explicit
    // namespace, or ones by Avro before 1.9 for deeper nesting
    @Test
    public void testMixedSeparatorsInName() throws Exception
    {
        // Namespace from `Bean`, `NS`; so: "NS.ImplA", "NS$Mid.Leaf"
        final AvroSchema schema = MAPPER.schemaFrom(_schemaJson("ImplA", NS + "$Mid.Leaf"));
        Mid.Leaf leaf = new Mid.Leaf();
        leaf.b = 42;
        ObjectBean result = _roundTripUntyped(schema, leaf);
        assertEquals(Mid.Leaf.class, result.value.getClass());
        assertEquals(42, ((Mid.Leaf) result.value).b);
    }

    // Legacy (Avro before 1.9) namespace with trailing dollar sign
    @Test
    public void testLegacyDollarNamespace() throws Exception
    {
        final AvroSchema schema = MAPPER.schemaFrom(_schemaJson(NS + "$.ImplA", NS + "$.ImplB"));
        ObjectBean result = _roundTripUntyped(schema, _implB(42));
        assertEquals(ImplB.class, result.value.getClass());
        assertEquals(42, ((ImplB) result.value).b);
    }

    private ObjectBean _roundTripUntyped(AvroSchema schema, Object value) throws Exception
    {
        final byte[] avro = MAPPER.writer(schema).writeValueAsBytes(_objectBean(value));
        assertArrayEquals(AVRO_SECOND_42, avro);
        return MAPPER.readerFor(ObjectBean.class).with(schema).readValue(avro);
    }

    private static ObjectBean _objectBean(Object value) {
        ObjectBean bean = new ObjectBean();
        bean.value = value;
        return bean;
    }

    private static ImplB _implB(int b) {
        ImplB impl = new ImplB();
        impl.b = b;
        return impl;
    }
}
