package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.concurrent.TimeUnit;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Resolving the Java class name of a record, enum or fixed schema must not
// initialize the class named by the (possibly untrusted) schema
public class SchemaNameClassInitTest extends AvroTestBase
{
    static class InitTracker {
        static volatile boolean initialized;
    }

    // Never referenced by test code: only named by the schema below
    static class NotToBeInitialized {
        static {
            InitTracker.initialized = true;
        }
    }

    static class Inner {
        public String x;
    }

    static class Outer {
        public Inner value;
    }

    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testReadDoesNotInitializeClassNamedBySchema() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom("{\"type\":\"record\",\"name\":\"Outer\",\"fields\":["
                +"{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"NotToBeInitialized\","
                +"\"namespace\":\"" + SchemaNameClassInitTest.class.getName() + "\","
                +"\"fields\":[{\"name\":\"x\",\"type\":\"string\"}]}}]}");
        Outer result = MAPPER.readerFor(Outer.class)
                .with(schema)
                .readValue(new byte[] { 2, 'a' });
        assertEquals("a", result.value.x);

        // Name is still resolved to the nested class...
        assertEquals(SchemaNameClassInitTest.class.getName() + "$NotToBeInitialized",
                AvroSchemaHelper.getFullName(schema.getAvroSchema().getField("value").schema()));
        // ... but without running its static initializer
        assertFalse(InitTracker.initialized);
    }

    // Namespace with more dots than any real class name must not be probed
    // for nested classes (one class loading attempt per dot)
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testNoClassProbingForExcessivelyDottedNamespace() throws Exception
    {
        StringBuilder sb = new StringBuilder("a");
        for (int i = 0; i < 20_000; ++i) {
            sb.append(".a");
        }
        final String namespace = sb.toString();
        Schema schema = Schema.createRecord("Rec", null, namespace, false);
        assertEquals(namespace + ".Rec", AvroSchemaHelper.getFullName(schema));
    }

    // But nesting within limit is still resolved
    @Test
    public void testNestedClassStillResolvedWithinDotLimit() throws Exception
    {
        Schema schema = Schema.createRecord("Inner", null,
                SchemaNameClassInitTest.class.getName(), false);
        assertEquals(SchemaNameClassInitTest.class.getName() + "$Inner",
                AvroSchemaHelper.getFullName(schema));
    }
}
