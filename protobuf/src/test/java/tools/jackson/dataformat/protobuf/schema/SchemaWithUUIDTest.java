package tools.jackson.dataformat.protobuf.schema;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.Assert;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.dataformat.protobuf.ProtobufMapper;
import tools.jackson.dataformat.protobuf.ProtobufTestBase;

public class SchemaWithUUIDTest extends ProtobufTestBase
{
    static class UUIDBean
    {
//        @JsonProperty(required = true, index = 1)
        public UUID messageId;
    }

    static class ShortBean
    {
        @JsonProperty(index = 1)
        public short version;
    }

    static class BinaryBean
    {
        @JsonProperty(index = 2)
        public byte[] data;

        @JsonProperty(index = 3)
        public ByteBuffer extraData;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = new ProtobufMapper();

    // [dataformats-binary#68]
    public void testWithUUID() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(UUIDBean.class);
        assertNotNull(schema);

        UUIDBean input = new UUIDBean();
        input.messageId = UUID.nameUUIDFromBytes("abc".getBytes(StandardCharsets.UTF_8));

        byte[] proto = MAPPER.writer().with(schema)
                .writeValueAsBytes(input);
        UUIDBean result = MAPPER.readerFor(UUIDBean.class)
                .with(schema)
                .readValue(proto);
        assertNotNull(result.messageId);
        assertEquals(input.messageId, result.messageId);
    }

    // [dataformats-binary#68]
    public void testWithShort() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(ShortBean.class);
        assertNotNull(schema);
    }

    public void testWithBinary() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(BinaryBean.class);
        assertNotNull(schema);

        // But let's try round-tripping too
        BinaryBean input = new BinaryBean();
        input.data = new byte[] { 1, 2, -1 };

        byte[] proto = MAPPER.writer().with(schema)
                .writeValueAsBytes(input);
        BinaryBean result = MAPPER.readerFor(BinaryBean.class)
                .with(schema)
                .readValue(proto);
        assertNotNull(result.data);
        Assert.assertArrayEquals(input.data, result.data);
    }
}
