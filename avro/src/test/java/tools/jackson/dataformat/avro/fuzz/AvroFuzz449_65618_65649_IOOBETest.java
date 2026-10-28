package tools.jackson.dataformat.avro.fuzz;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectReader;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#449]
public class AvroFuzz449_65618_65649_IOOBETest extends AvroTestBase
{
    @JsonPropertyOrder({ "name", "value" })
    static class RootType {
        public String name;
        public int value;
    }

    final private AvroFactory factory = AvroFactory.builderWithNativeDecoder().build();
    final private AvroMapper mapper = new AvroMapper(factory);

    private void testFuzzIOOBE(byte[] input, String msg) throws Exception {
        final AvroSchema schema = mapper.schemaFor(RootType.class);
        ObjectReader r = mapper.reader()
                .with(schema);
        try (JsonParser p = r.createParser(input)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("name", p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.nextToken();
            p.nextToken();
            fail("Should not pass (invalid content)");
        } catch (StreamReadException e) {
            verifyException(e, msg);
        }
    }

    @Test
    public void testFuzz65618_IOOBE() throws Exception {
        final byte[] doc = {
            (byte) 2, (byte) 22, (byte) 36, (byte) 2, (byte) 0,
            (byte) 0, (byte) 8, (byte) 3, (byte) 3, (byte) 3,
            (byte) 122, (byte) 3, (byte) -24
        };
        // [dataformats-binary#862]: 0xE8 is lead byte of 3-byte sequence
        testFuzzIOOBE(doc, "Invalid UTF-8: truncated 3-byte sequence");
    }

    @Test
    public void testFuzz65649_IOOBE() throws Exception {
        final byte[] doc = AvroFuzzTestUtil.readResource("/data/fuzz-65649.avro");
        // [dataformats-binary#862]: same message for short and long text
        testFuzzIOOBE(doc, "Invalid UTF-8: unexpected byte 0x80");
    }
}
