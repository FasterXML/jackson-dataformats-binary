package tools.jackson.dataformat.avro.schemaev;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import tools.jackson.dataformat.avro.*;
import tools.jackson.dataformat.avro.testsupport.NonSkippingInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Test for [dataformats-binary#785]: skipping of values must work even if
// `InputStream.skip()` never skips anything
public class SkipNonSkippableStreamTest extends AvroTestBase
{
    // NOTE: Avro requires named types to match, hence same record name for both
    static String SCHEMA_WITH_DATA_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'Blob',\n"+
            " 'fields':[\n"+
            "    { 'name':'name', 'type':'string' },\n"+
            "    { 'name':'data', 'type':'bytes' },\n"+
            "    { 'name':'text', 'type':'string' }\n"+
            " ]\n"+
            "}\n");

    // Reader schema without `data`/`text`: both writer values have to be skipped
    static String SCHEMA_NO_DATA_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'Blob',\n"+
            " 'fields':[\n"+
            "    { 'name':'name', 'type':'string' }\n"+
            " ]\n"+
            "}\n");

    private final AvroMapper MAPPER = newMapper();

    // Values big enough not to fit in input buffer, to force skipping straight
    // from the input source
    private final static int BIG_VALUE_LEN = 50_000;

    @Test
    public void testSkipBigValuesWithNonSkippingStream() throws Exception
    {
        AvroSchema writerSchema = MAPPER.schemaFrom(SCHEMA_WITH_DATA_JSON);
        AvroSchema schema = writerSchema.withReaderSchema(MAPPER.schemaFrom(SCHEMA_NO_DATA_JSON));

        byte[] doc = createDoc(writerSchema);

        try (JsonParser p = MAPPER.reader().with(schema)
                .createParser(NonSkippingInputStream.wrap(doc))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("name", p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("blob", p.getText());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    // And also verify that true end-of-input is still reported as such
    @Test
    public void testTruncatedValueWithNonSkippingStream() throws Exception
    {
        AvroSchema writerSchema = MAPPER.schemaFrom(SCHEMA_WITH_DATA_JSON);
        AvroSchema schema = writerSchema.withReaderSchema(MAPPER.schemaFrom(SCHEMA_NO_DATA_JSON));

        byte[] doc = createDoc(writerSchema);
        byte[] truncated = new byte[doc.length - (BIG_VALUE_LEN / 2)];
        System.arraycopy(doc, 0, truncated, 0, truncated.length);

        try (JsonParser p = MAPPER.reader().with(schema)
                .createParser(NonSkippingInputStream.wrap(truncated))) {
            while (p.nextToken() != null) { }
            fail("Should not pass");
        } catch (Exception e) {
            verifyException(e, "end-of-input");
        }
    }

    private byte[] createDoc(AvroSchema writerSchema) throws Exception
    {
        Map<String, Object> blob = new java.util.LinkedHashMap<>();
        blob.put("name", "blob");
        blob.put("data", new byte[BIG_VALUE_LEN]);
        StringBuilder sb = new StringBuilder(BIG_VALUE_LEN);
        for (int i = 0; i < BIG_VALUE_LEN; ++i) {
            sb.append('a');
        }
        blob.put("text", sb.toString());
        byte[] doc = MAPPER.writer(writerSchema).writeValueAsBytes(blob);
        assertTrue(doc.length > (2 * BIG_VALUE_LEN), "doc.length="+doc.length);
        return doc;
    }
}
