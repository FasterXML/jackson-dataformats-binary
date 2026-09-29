package com.fasterxml.jackson.dataformat.avro.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.fail;

// Union index out of bounds must fail with StreamReadException, not AIOOBE
public class AvroUnionIndexBoundsTest extends AvroTestBase
{
    // Union with a non-scalar member, so `UnionReader` (not `ScalarDecoder`) is used
    private final static String SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':[{'name':'value','type':"
            + "['null',{'type':'record','name':'Inner','fields':[{'name':'x','type':'int'}]}]"
            + "}]}");

    @Test
    public void testInvalidUnionIndexNative() throws Exception {
        _testInvalidUnionIndex(new AvroMapper(AvroFactory.builderWithNativeDecoder().build()));
    }

    @Test
    public void testInvalidUnionIndexApache() throws Exception {
        _testInvalidUnionIndex(new AvroMapper(AvroFactory.builderWithApacheDecoder().build()));
    }

    private void _testInvalidUnionIndex(AvroMapper mapper) throws Exception {
        final AvroSchema schema = mapper.schemaFrom(SCHEMA_JSON);
        // zig-zag varints: 10 -> index 5; 1 -> index -1
        _verifyFail(mapper, schema, new byte[] { 10, 0 }, "Invalid index (5)");
        _verifyFail(mapper, schema, new byte[] { 1, 0 }, "Invalid index (-1)");
    }

    private void _verifyFail(AvroMapper mapper, AvroSchema schema, byte[] doc, String msg)
        throws Exception
    {
        try (AvroParser p = (AvroParser) mapper.createParser(doc)) {
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            p.nextToken();
            fail("Should not pass (invalid union index)");
        } catch (StreamReadException e) {
            verifyException(e, msg);
        }
    }
}
