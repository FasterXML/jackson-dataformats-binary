package tools.jackson.dataformat.protobuf;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerDeserLongTest extends ProtobufTestBase
{
    @JsonPropertyOrder({ "long1", "long2" })
    public static class BigNumPair {
        public static final String protobuf_str =
                "message BigNumPair {\n"
                        + " required int64 long1 = 1;\n"
                        + " required int64 long2 = 2;\n"
                        + "}\n";

        public long long1;
        public long long2;
    }

    @Test
    public void testWeirdLongSerDeser() throws Exception {
        ObjectMapper mapper = newObjectMapper();
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(BigNumPair.protobuf_str);

        BigNumPair bnp = new BigNumPair();
        bnp.long1 = 72057594037927935L;
        bnp.long2 = 0;

        byte[] encoded = mapper.writer(schema).writeValueAsBytes(bnp);

        BigNumPair parsed = mapper.readerFor(BigNumPair.class).with(schema).readValue(encoded);

        assertEquals(bnp.long1, parsed.long1);
        assertEquals(bnp.long2, parsed.long2);
    }
}
