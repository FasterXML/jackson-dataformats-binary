package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for [dataformats-binary#714]: a "packed" repeated field whose contents span
 * an input buffer reload used to fail with a spurious "corrupt content" error, since
 * the array context was created with an end offset of 0 (instead of the actual one)
 * whenever the context was freshly allocated rather than recycled.
 */
public class ReadPackedArrayAcrossReload714Test extends ProtobufTestBase
{
    // proto3 defaults repeated scalars to packed encoding
    private final static String SCHEMA_STR =
            "syntax = \"proto3\";\n"
            + "message Ints {\n"
            + "  repeated int32 values = 1;\n"
            + "}\n";

    static class Ints {
        public int[] values;

        protected Ints() { }
        public Ints(int[] v) { values = v; }
    }

    // Enough values that the encoded packed block is much bigger than the input
    // buffer (8000 bytes), forcing at least one reload while inside the array
    private final static int COUNT = 6000;

    @Test
    public void testPackedArrayFromInputStream() throws Exception
    {
        final ProtobufMapper mapper = newObjectMapper();
        final ProtobufSchema schema = mapper.schemaLoader().parse(SCHEMA_STR);

        final int[] values = new int[COUNT];
        for (int i = 0; i < COUNT; ++i) {
            values[i] = 100_000 + i; // 3-byte varints; ~18kB payload
        }
        final byte[] doc = mapper.writer(schema).writeValueAsBytes(new Ints(values));

        // Baseline: byte[] input keeps the whole document in one buffer, so no reload
        _verify(mapper, schema, mapper.getFactory().createParser(doc), values);

        // The actual regression: reading from a stream forces loadMore() mid-array
        try (InputStream in = new ByteArrayInputStream(doc)) {
            _verify(mapper, schema, mapper.getFactory().createParser(in), values);
        }
    }

    private void _verify(ProtobufMapper mapper, ProtobufSchema schema,
            JsonParser p, int[] expected) throws Exception
    {
        try {
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("values", p.currentName());
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            for (int i = 0; i < expected.length; ++i) {
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(expected[i], p.getIntValue(), "Mismatch at index "+i);
            }
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
        } finally {
            p.close();
        }
    }
}
