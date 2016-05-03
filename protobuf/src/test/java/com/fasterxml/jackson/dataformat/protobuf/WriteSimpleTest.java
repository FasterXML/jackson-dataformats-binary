package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class WriteSimpleTest extends ProtobufTestBase
{
    static class Point3D extends Point {
        public int z;
        
        public Point3D(int x, int y, int z) {
            super(x, y);
            this.z = z;
        }
    }

    final protected static String PROTOC_ID_POINTS =
            "message OptionalPoint {\n"
            +" required int32 id = 1;\n"
            +" repeated Point points = 2;\n"
            +"}\n"
            +PROTOC_POINT;
    ;

    @JsonPropertyOrder({ "id", "point" })
    static class IdPoints {
        public int id;
        public List<Point> points;

        protected IdPoints() { }
        
        public IdPoints(int id, int x, int y) {
            this.id = id;
            points = Arrays.asList(new Point(x, y));
        }

        @Override
        public String toString() {
            return String.format("[id=%d, points=%d]", id, points);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o.getClass() != getClass()) return false;
            IdPoints other = (IdPoints) o;
            return (other.id == id) && points.equals(other.points);
        }
    }
    
    private final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testWritePointInt() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Point");
        final ObjectWriter w = MAPPER.writerFor(Point.class)
                .with(schema);
        Point input = new Point(7, 2);
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);

        // 4 bytes: 1 byte tags, 1 byte values
        assertEquals(4, bytes.length);
        assertEquals(8, bytes[0]); // wire type 0 (3 LSB), id of 1 (-> 0x8)
        assertEquals(7, bytes[1]); // VInt 7, no zig-zag
        assertEquals(0x10, bytes[2]); // wire type 0 (3 LSB), id of 2 (-> 0x10)
        assertEquals(4, bytes[3]); // VInt 2, but with zig-zag

        // Plus read back using mapper as well
        Point result = MAPPER.readerFor(Point.class)
                .with(schema)
                .readValue(bytes);
        assertEquals(input, result);
    }

    public void testWritePointLongFixed() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT_FL);
        final ObjectWriter w = MAPPER.writerFor(PointL.class)
                .with(schema);
        PointL input = new PointL(1, -1);
        byte[] bytes = w.writeValueAsBytes(input);

        assertNotNull(bytes);
        assertEquals(18, bytes.length);

        // read back using Mapper as well
        PointL result = MAPPER.readerFor(PointL.class)
                .with(schema)
                .readValue(bytes);
        assertEquals(input, result);
    }

    public void testWritePointDouble() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT_D);
        final ObjectWriter w = MAPPER.writerFor(PointD.class)
                .with(schema);
        PointD input = new PointD(-100.75, 0.375);
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);

        // read back using Mapper as well
        PointD result = MAPPER.readerFor(PointD.class)
                .with(schema)
                .readValue(bytes);
        assertEquals(input, result);
    }
    
    public void testWriteNameManual() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_NAME);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = MAPPER.getFactory().createGenerator(bytes);
        g.setSchema(schema);

        g.writeStartObject();
        g.writeFieldName("last");
        char[] ch = "Ford".toCharArray();
        g.writeString(ch, 0, ch.length);
        // call flush() for fun, at root level, to verify that works
        g.flush();
        g.writeFieldName(new SerializedString("last"));
        byte[] b = "Bob".getBytes("UTF-8");
        g.writeRawUTF8String(b, 0, b.length);
        g.writeEndObject();
        g.close();
        
        b = bytes.toByteArray();
        assertNotNull(bytes);

        // 11 bytes: 2 tags, 2 length markers, Strings of 3 and 4 bytes
        assertEquals(11, b.length);
    }

    public void testWritePointWithLongsManual() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT_L);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = MAPPER.getFactory().createGenerator(bytes);
        g.setSchema(schema);

        g.writeStartObject();
        g.writeFieldName("x");
        g.writeNumber(Long.MAX_VALUE);
        g.writeFieldName("y");
        g.writeNumber(Long.MAX_VALUE);
        g.writeEndObject();
        g.close();
        
        byte[] b = bytes.toByteArray();
        assertNotNull(bytes);

        // 22 bytes: 1 byte tags, 10 byte values
        assertEquals(21, b.length);
        assertEquals(8, b[0]); // wire type 0 (3 LSB), id of 1 (-> 0x8)

        // 7 x 0xFF, last 0x7F -> 0x7F....FF, NOT using zig-zag
        assertEquals(0xFF, b[1] & 0xFF);
        assertEquals(0xFF, b[2] & 0xFF);
        assertEquals(0xFF, b[3] & 0xFF);
        assertEquals(0xFF, b[4] & 0xFF);
        assertEquals(0xFF, b[5] & 0xFF);
        assertEquals(0xFF, b[6] & 0xFF);
        assertEquals(0xFF, b[7] & 0xFF);
        assertEquals(0xFF, b[8] & 0xFF);
        assertEquals(0x7F, b[9] & 0x7F);

        assertEquals(0x10, b[10]); // wire type 0 (3 LSB), id of 2 (-> 0x10)

        // but 'y' will be using zig-zag
        assertEquals(0xFE, b[11] & 0xFF);
        assertEquals(0xFF, b[12] & 0xFF);
        assertEquals(0xFF, b[13] & 0xFF);
        assertEquals(0xFF, b[14] & 0xFF);
        assertEquals(0xFF, b[15] & 0xFF);
        assertEquals(0xFF, b[16] & 0xFF);
        assertEquals(0xFF, b[17] & 0xFF);
        assertEquals(0xFF, b[18] & 0xFF);
        assertEquals(0xFF, b[19] & 0xFF);
        assertEquals(0x01, b[20] & 0x01);
    }

    public void testBooleanAndNull() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_OPTIONAL_VALUE);
        ObjectWriter w = MAPPER.writerFor(OptionalValue.class)
                .with(schema);
        OptionalValue input = new OptionalValue(false, null);
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);
        OptionalValue result = MAPPER.readerFor(OptionalValue.class)
                .with(schema)
                .readValue(bytes);
        assertEquals(input, result);

        // and another one with true
        input = new OptionalValue(true, "abc");
        bytes = w.writeValueAsBytes(input);
        result = MAPPER.readerFor(OptionalValue.class)
                .with(schema)
                .readValue(bytes);
        assertEquals(input, result);
    }

    public void testIdPoint() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_ID_POINTS);
        ObjectWriter w = MAPPER.writerFor(IdPoints.class)
                .with(schema);
        IdPoints input = new IdPoints(1, 100, -200);
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);
        IdPoints result = MAPPER.readerFor(IdPoints.class)
                .with(schema)
                .readValue(bytes);
        assertEquals(input, result);
    }
    
    public void testWriteCoord() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Box");
        schema = schema.withRootType("Box");
        final ObjectWriter w = MAPPER.writerFor(Box.class)
                .with(schema);
        byte[] bytes = w.writeValueAsBytes(new Box(0x3F, 0x11, 0x18, 0xF));
        assertNotNull(bytes);
        
        // 11 bytes for 2 Points; 4 single-byte ids, 3 x 2-byte values, 1 x 1-byte value
        // but then 2 x 2 bytes for tag, length

        // Root-level has no length-prefix; so we have sequence of Box fields (topLeft, bottomRight)
        // with ids of 3 and 5, respectively.
        // As child messages, they have typed-tag, then VInt-encoded length; lengths are 
        // 4 byte each (typed tag, 1-byte ints)
        // It all adds up to 12 bytes as follows:

        /*
            "message Point {\n"
            +" required int32 x = 1;\n"
            +" required sint32 y = 2;\n"
            +"}\n"            
            +"message Box {\n"
            +" required Point topLeft = 3;\n"
            +" required Point bottomRight = 5;\n"
            +"}\n"
         */

        assertEquals(12, bytes.length);
        
        assertEquals(0x1A, bytes[0]); // wire type 2 (length-prefix), tag id 3
        assertEquals(0x4, bytes[1]); // length, 4 bytes
        assertEquals(0x8, bytes[2]); // wire type 0 (vint), tag id 1
        assertEquals(0x3F, bytes[3]); // vint value, 0x3F remains as is
        assertEquals(0x10, bytes[4]); // wire type 0 (vint), tag id 2
        assertEquals(0x22, bytes[5]); // zig-zagged vint value, 0x11 becomes 0x22

        assertEquals(0x2A, bytes[6]); // wire type 2 (length-prefix), tag id 5
        assertEquals(0x4, bytes[7]); // length, 4 bytes
        assertEquals(0x8, bytes[8]); // wire type 0 (vint), tag id 1
        assertEquals(0x18, bytes[9]); // vint value, 0x18 remains as is
        assertEquals(0x10, bytes[10]); // wire type 0 (vint), tag id 2
        assertEquals(0x1E, bytes[11]); // zig-zagged vint value, 0xF becomes 0x1E
    }
}
