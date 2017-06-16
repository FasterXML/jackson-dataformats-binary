package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class WriteLongString94Test extends ProtobufTestBase
{
    @JsonPropertyOrder({ "a", "b" })
    public static class TwoStrings {
        public String a;
        public String b;
    }    

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = new ProtobufMapper();

    // [dataformats-binary#94]
    public void testLongerStrings() throws Exception {
        TwoStrings p = new TwoStrings();
        // near 8000, so index out of bounds at 8000
        p.a  = makeString(7995);
        p.b = makeString(7995);

        ProtobufSchema schema = MAPPER.generateSchemaFor(p.getClass());
        byte[] proto = MAPPER.writer(schema)
                .writeValueAsBytes(p);
        assertEquals(2 * (7995 + 3), proto.length);

        TwoStrings result = MAPPER.readerFor(p.getClass())
                .with(schema)
                .readValue(proto);
        assertEquals(p.a, result.a);
        assertEquals(p.b, result.b);
    }

    private String makeString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + (i & 15)));
        }
        return sb.toString();
    }
}
