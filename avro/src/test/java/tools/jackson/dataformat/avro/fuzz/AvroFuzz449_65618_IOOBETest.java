package tools.jackson.dataformat.avro.fuzz;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectReader;

import tools.jackson.dataformat.avro.*;

// [dataformats-binary#449]
public class AvroFuzz449_65618_IOOBETest extends AvroTestBase
{
    @JsonPropertyOrder({ "name", "value" })
    static class RootType {
        public String name;
        public int value;
    }

    @Test
    public void testFuzz65618IOOBE() throws Exception {
        final AvroFactory factory = AvroFactory.builderWithNativeDecoder().build();
        final AvroMapper mapper = new AvroMapper(factory);

        final byte[] doc = {
            (byte) 2, (byte) 22, (byte) 36, (byte) 2, (byte) 0,
            (byte) 0, (byte) 8, (byte) 3, (byte) 3, (byte) 3,
            (byte) 122, (byte) 3, (byte) -24
        };

        final AvroSchema schema = mapper.schemaFor(RootType.class);
        ObjectReader r = mapper.reader()
                .with(schema);
        try (JsonParser p = r.createParser(doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("name", p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            System.err.println("TEXT len = "+p.getText().length());
            fail("Should not pass (invalid content): got "+p.getText());
        } catch (StreamReadException e) {
            verifyException(e, "Malformed 2-byte UTF-8 character at the end of");
        }
    }
}
