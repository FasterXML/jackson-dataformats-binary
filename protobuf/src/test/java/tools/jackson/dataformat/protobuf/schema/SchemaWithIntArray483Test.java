package tools.jackson.dataformat.protobuf.schema;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Ignore;

import tools.jackson.dataformat.protobuf.ProtobufMapper;
import tools.jackson.dataformat.protobuf.ProtobufTestBase;

public class SchemaWithIntArray483Test extends ProtobufTestBase
{
    static class IntArrayBean
    {
//        @JsonProperty(required = true, index = 1)
        public int[] value;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ProtobufMapper MAPPER = new ProtobufMapper();

    // [dataformats-binary#483]: int arrays supported as nested (property) values
    public void testWithWrappedIntArray() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(IntArrayBean.class);
        assertNotNull(schema);

        IntArrayBean input = new IntArrayBean();
        input.value = new int[] { 1, 2, 3 };

        byte[] proto = MAPPER.writer().with(schema)
                .writeValueAsBytes(input);
        IntArrayBean result = MAPPER.readerFor(IntArrayBean.class)
                .with(schema)
                .readValue(proto);
        assertNotNull(result.value);
        assertArrayEquals(input.value, result.value);
    }

    // [dataformats-binary#483]: cannot support root-level arrays, unfortunately
    @Ignore("Can't be supported with Protobuf")
    public void dontTestWithRootIntArray() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(int[].class);
        assertNotNull(schema);

        int[] input = new int[] { 1, 2, 3 };

        byte[] proto = MAPPER.writer().with(schema)
                .writeValueAsBytes(input);
        int[] result = MAPPER.readerFor(int[].class)
                .with(schema)
                .readValue(proto);
        assertNotNull(result);
        assertArrayEquals(input, result);
    }
}
