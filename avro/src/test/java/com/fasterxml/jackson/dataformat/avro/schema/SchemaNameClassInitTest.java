package com.fasterxml.jackson.dataformat.avro.schema;

import org.junit.jupiter.api.Test;

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
}
