package tools.jackson.dataformat.protobuf;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;

import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EnumHandlingTest extends ProtobufTestBase
{
    public enum TinyEnum {
        X;
    }

    public enum BigEnum {
        A, B, C, D, E,
        F, G, H, I, J;
    }

    public static class TinyEnumWrapper {
        public TinyEnum value;

        public TinyEnumWrapper() { }
        public TinyEnumWrapper(TinyEnum v) { value = v; }
    }

    public static class BigEnumWrapper {
        public BigEnum value;

        public BigEnumWrapper() { }
        public BigEnumWrapper(BigEnum v) { value = v; }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = newObjectMapper();

    @Test
    public void testBigEnum() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(BigEnumWrapper.class);
        final ObjectWriter w = MAPPER.writer(schema);
        BigEnumWrapper input = new BigEnumWrapper(BigEnum.H);

        byte[] bytes = w.writeValueAsBytes(input);

        assertNotNull(bytes);
        // type + short id == 2 bytes
        assertEquals(2, bytes.length);

        ObjectReader r =  MAPPER.readerFor(new TypeReference<BigEnumWrapper> () {}).with(schema);
        BigEnumWrapper result = r.readValue(bytes);
        assertEquals(input.value, result.value);
    }

    @Test
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
