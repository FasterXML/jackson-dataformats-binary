package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by miz on 8/10/16.
 */
public class SerDeserLongTest {
    @Test
    public void testWeirdLongSerDeser() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new ProtobufFactory());
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(BigNumPair.protobuf_str);

        BigNumPair bnp = new BigNumPair();
        bnp.long1 = 72057594037927935L;
        bnp.long2 = 0;

        byte[] encoded = mapper.writer(schema).writeValueAsBytes(bnp);

        BigNumPair parsed = mapper.readerFor(BigNumPair.class).with(schema).readValue(encoded);

        assert parsed.long1 == bnp.long1;
        assert parsed.long2 == bnp.long2;
    }
}
