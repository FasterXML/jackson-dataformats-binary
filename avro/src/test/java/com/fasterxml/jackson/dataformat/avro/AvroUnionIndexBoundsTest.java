package com.fasterxml.jackson.dataformat.avro;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.avro.deser.AvroParserImpl;

import static org.junit.jupiter.api.Assertions.*;

// Union (and Enum) index out of bounds must fail with StreamReadException,
// not AIOOBE or plain IOException; both when reading and skipping the value
public class AvroUnionIndexBoundsTest extends AvroTestBase
{
    // Union with a non-scalar member, so `UnionReader` (not `ScalarDecoder`) is used
    private final static String STRUCT_UNION_TYPE =
            "['null',{'type':'record','name':'Inner','fields':[{'name':'x','type':'int'}]}]";

    // Union with only scalar members, so `ScalarUnionDecoder` is used
    private final static String SCALAR_UNION_TYPE = "['null','int']";

    private final static String ENUM_TYPE =
            "{'type':'enum','name':'Color','symbols':['RED','GREEN']}";

    // Reader schema without field "value", to force skipping it
    private final static String SKIPPING_READER_SCHEMA_JSON = aposToQuotes("{"
            + "'type':'record','name':'Root','fields':[{'name':'after','type':'int'}]}");

    // zig-zag varints: 10 -> index 5; 1 -> index -1
    private final static byte[] DOC_INDEX_5 = new byte[] { 10, 0 };
    private final static byte[] DOC_INDEX_MINUS_1 = new byte[] { 1, 0 };

    private final AvroMapper NATIVE_MAPPER = new AvroMapper(AvroFactory.builderWithNativeDecoder().build());
    private final AvroMapper APACHE_MAPPER = new AvroMapper(AvroFactory.builderWithApacheDecoder().build());

    @Test
    public void testInvalidStructUnionIndex() throws Exception {
        _testInvalidIndex(STRUCT_UNION_TYPE, "Invalid index", "union only has 2 types");
    }

    @Test
    public void testInvalidScalarUnionIndex() throws Exception {
        _testInvalidIndex(SCALAR_UNION_TYPE, "Invalid Union index", "union only has 2 types");
    }

    @Test
    public void testInvalidEnumIndex() throws Exception {
        // Should name the enum type, not the field
        _testInvalidIndex(ENUM_TYPE, "Invalid Enum index", "enum 'Color' only has 2 types");
    }

    private void _testInvalidIndex(String valueType, String msgPrefix, String msgSuffix)
        throws Exception
    {
        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            final AvroSchema writerSchema = mapper.schemaFrom(_writerSchemaJson(valueType));
            final AvroSchema skippingSchema = writerSchema.withReaderSchema(
                    mapper.schemaFrom(SKIPPING_READER_SCHEMA_JSON));
            for (AvroSchema schema : new AvroSchema[] { writerSchema, skippingSchema }) {
                _verifyFail(mapper, schema, DOC_INDEX_5, msgPrefix + " (5)", msgSuffix);
                _verifyFail(mapper, schema, DOC_INDEX_MINUS_1, msgPrefix + " (-1)", msgSuffix);
            }
        }
    }

    private static String _writerSchemaJson(String valueType) {
        return aposToQuotes("{"
                + "'type':'record','name':'Root','fields':["
                + "{'name':'value','type':" + valueType + "},"
                + "{'name':'after','type':'int'}"
                + "]}");
    }

    private void _verifyFail(AvroMapper mapper, AvroSchema schema, byte[] doc,
            String msg, String msgSuffix)
        throws Exception
    {
        try (AvroParserImpl p = (AvroParserImpl) mapper.createParser(doc)) {
            p.setSchema(schema);
            try {
                while (p.nextToken() != null) { }
                fail("Should not pass (invalid index)");
            } catch (StreamReadException e) {
                verifyException(e, msg);
                verifyException(e, msgSuffix);
                // Must refer to the parser, also when skipping
                assertSame(p, e.getProcessor());
                // Invalid index must not be left as the current branch/enum index
                assertEquals(-1, p.branchIndex());
                assertEquals(-1, p.enumIndex());
            }
        }
    }
}
