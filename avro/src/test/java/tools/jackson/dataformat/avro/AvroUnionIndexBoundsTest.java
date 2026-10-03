package tools.jackson.dataformat.avro;

import org.junit.jupiter.api.Test;

import tools.jackson.core.exc.StreamReadException;
import tools.jackson.dataformat.avro.deser.AvroParserImpl;

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
    private final static byte INDEX_5 = 10;
    private final static byte INDEX_MINUS_1 = 1;

    private final AvroMapper NATIVE_MAPPER = new AvroMapper(AvroFactory.builderWithNativeDecoder().build());
    private final AvroMapper APACHE_MAPPER = new AvroMapper(AvroFactory.builderWithApacheDecoder().build());

    @Test
    public void testInvalidStructUnionIndex() throws Exception {
        _testInvalidIndex(STRUCT_UNION_TYPE, "Invalid Union index", "union only has 2 types");
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

    // Test value of given type directly as record field, as well as array element
    // and map value (which use different code paths from record fields)
    private void _testInvalidIndex(String valueType, String msgPrefix, String msgSuffix)
        throws Exception
    {
        _testInvalidIndex(valueType,
                new byte[0], msgPrefix, msgSuffix);
        // array with one element: block count 1 (zig-zag 2), element, end-of-array (0)
        _testInvalidIndex("{'type':'array','items':" + valueType + "}",
                new byte[] { 2 }, msgPrefix, msgSuffix);
        // map with one entry: block count 1 (zig-zag 2), key "k", value, end-of-map (0)
        _testInvalidIndex("{'type':'map','values':" + valueType + "}",
                new byte[] { 2, 2, 'k' }, msgPrefix, msgSuffix);
    }

    private void _testInvalidIndex(String valueType, byte[] docPrefix,
            String msgPrefix, String msgSuffix)
        throws Exception
    {
        for (AvroMapper mapper : new AvroMapper[] { NATIVE_MAPPER, APACHE_MAPPER }) {
            final AvroSchema writerSchema = mapper.schemaFrom(_writerSchemaJson(valueType));
            final AvroSchema skippingSchema = writerSchema.withReaderSchema(
                    mapper.schemaFrom(SKIPPING_READER_SCHEMA_JSON));
            for (AvroSchema schema : new AvroSchema[] { writerSchema, skippingSchema }) {
                _verifyFail(mapper, schema, _doc(docPrefix, INDEX_5),
                        msgPrefix + " (5)", msgSuffix);
                _verifyFail(mapper, schema, _doc(docPrefix, INDEX_MINUS_1),
                        msgPrefix + " (-1)", msgSuffix);
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

    // Document: prefix, then the invalid index, followed by zero bytes as filler
    // (end-of-array/map marker and/or "after" field value)
    private static byte[] _doc(byte[] prefix, byte index) {
        byte[] doc = new byte[prefix.length + 3];
        System.arraycopy(prefix, 0, doc, 0, prefix.length);
        doc[prefix.length] = index;
        return doc;
    }

    private void _verifyFail(AvroMapper mapper, AvroSchema schema, byte[] doc,
            String msg, String msgSuffix)
        throws Exception
    {
        try (AvroParserImpl p = (AvroParserImpl) mapper.reader().with(schema).createParser(doc)) {
            try {
                while (p.nextToken() != null) { }
                fail("Should not pass (invalid index)");
            } catch (StreamReadException e) {
                verifyException(e, msg);
                verifyException(e, msgSuffix);
                // Must refer to the parser, also when skipping
                assertSame(p, e.processor());
                // Invalid index must not be left as the current branch/enum index
                assertEquals(-1, p.branchIndex());
                assertEquals(-1, p.enumIndex());
            }
        }
    }
}
