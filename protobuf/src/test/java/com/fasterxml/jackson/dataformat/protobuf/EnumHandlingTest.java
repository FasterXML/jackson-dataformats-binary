package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class EnumHandlingTest extends ProtobufTestBase
{
    enum TinyEnum {
        X;
    }

    enum BigEnum {
        A, B, C, D, E,
        F, G, H, I, J;
    }

    static class TinyEnumWrapper {
        public TinyEnum value;

        TinyEnumWrapper() { }
        public TinyEnumWrapper(TinyEnum v) { value = v; }
    }

    static class BigEnumWrapper {
        public BigEnum value;

        BigEnumWrapper() { }
        public BigEnumWrapper(BigEnum v) { value = v; }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = new ProtobufMapper();

    public void testBigEnum() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(BigEnumWrapper.class);
        final ObjectWriter w = MAPPER.writer(schema);
        BigEnumWrapper input = new BigEnumWrapper(BigEnum.H);

        byte[] bytes = w.writeValueAsBytes(input);

        assertNotNull(bytes);
        // type + short id == 2 bytes
        assertEquals(2, bytes.length);

        ObjectReader r =  MAPPER.readerFor(BigEnumWrapper.class).with(schema);
        BigEnumWrapper result = r.readValue(bytes);
        assertEquals(input.value, result.value);
    }

    public void testTinyEnum() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(TinyEnumWrapper.class);
        final ObjectWriter w = MAPPER.writer(schema);
        TinyEnumWrapper input = new TinyEnumWrapper(TinyEnum.X);

        byte[] bytes = w.writeValueAsBytes(input);

        assertNotNull(bytes);
        // type + short id == 2 bytes
        assertEquals(2, bytes.length);

        ObjectReader r =  MAPPER.readerFor(TinyEnumWrapper.class).with(schema);
        TinyEnumWrapper result = r.readValue(bytes);
        assertEquals(input.value, result.value);
    }
}
