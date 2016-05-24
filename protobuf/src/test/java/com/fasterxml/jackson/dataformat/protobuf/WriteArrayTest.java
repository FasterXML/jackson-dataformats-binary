package com.fasterxml.jackson.dataformat.protobuf;

import org.junit.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class WriteArrayTest extends ProtobufTestBase
{
    final protected static String PROTOC_STRING_ARRAY_SPARSE = "message Strings {\n"
            +" repeated string values = 1;\n"
            +"}\n"
    ;

    final protected static String PROTOC_STRING_ARRAY_PACKED = "message Strings {\n"
            +" repeated string values = 1 [packed=true];\n"
            +"}\n"
    ;
    
    final protected static String PROTOC_POINT_ARRAY_SPARSE = "message Points {\n"
            +" repeated Point points = 1;\n"
            +"}\n"
            +PROTOC_POINT;
    ;

    final protected static String PROTOC_POINT_ARRAY_PACKED = "message Points {\n"
          +" repeated Point points = 1 [packed=true];\n"
          +"}\n"
          +PROTOC_POINT;
    ;

    static class StringArray {
        public String[] values;

        public StringArray(String... v) {
            values = v;
        }
    }
    
    static class PointArray {
        public Point[] points;

        public PointArray(Point... p) {
            points = p;
        }
    }

    final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    final ProtobufSchema SPARSE_STRING_SCHEMA;
    final ProtobufSchema PACKED_STRING_SCHEMA;

    public WriteArrayTest() throws Exception {
        SPARSE_STRING_SCHEMA = ProtobufSchemaLoader.std.parse(PROTOC_STRING_ARRAY_SPARSE);
        PACKED_STRING_SCHEMA = ProtobufSchemaLoader.std.parse(PROTOC_STRING_ARRAY_PACKED);
    }

    /*
    /**********************************************************
    /* Test methods, String arrays
    /**********************************************************
     */

    public void testStringArraySparse() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(SPARSE_STRING_SCHEMA);
        byte[] bytes = w.writeValueAsBytes(new StringArray("Foo", "Bar"));
        assertEquals(10, bytes.length);
        Assert.assertArrayEquals(new byte[] {
                0xA, 3, 'F', 'o', 'o',
                0xA, 3, 'B', 'a', 'r',
        }, bytes);
    }

    public void testStringArrayPacked() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(PACKED_STRING_SCHEMA);
        byte[] bytes = w.writeValueAsBytes(new StringArray("A", "B", "C"));
        assertEquals(8, bytes.length);
        Assert.assertArrayEquals(new byte[] {
                0xA, 6,
                1, 'A',
                1, 'B',
                1, 'C',
        }, bytes);
    }

    /*
    /**********************************************************
    /* Test methods, POJO arrays
    /**********************************************************
     */

    public void testPointArraySparse() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(ProtobufSchemaLoader.std.parse(PROTOC_POINT_ARRAY_SPARSE));
        byte[] bytes = w.writeValueAsBytes(new PointArray(new Point(1, 2), new Point(3, 4)));
        // sequence of 2 embedded messages, each with 1 byte typed tag, 1 byte length
        // and 2 fields of typed-tag and single-byte value
        assertEquals(12, bytes.length);

        assertEquals(0xA, bytes[0]); // wire type 2 (length prefix), id of 1 (-> 0x8)
        assertEquals(4, bytes[1]); // length
        assertEquals(8, bytes[2]); // wire type 0 (3 LSB), id of 1 (-> 0x8)
        assertEquals(1, bytes[3]); // VInt 1, no zig-zag
        assertEquals(0x10, bytes[4]); // wire type 0 (3 LSB), id of 2 (-> 0x10)
        assertEquals(4, bytes[5]); // VInt 2, but with zig-zag

        assertEquals(0xA, bytes[6]); // similar to above
        assertEquals(4, bytes[7]); 
        assertEquals(8, bytes[8]);
        assertEquals(3, bytes[9]); // Point(3, )
        assertEquals(0x10, bytes[10]);
        assertEquals(8, bytes[11]); // Point (, 4)
    }

    public void testPointArrayPacked() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(ProtobufSchemaLoader.std.parse(PROTOC_POINT_ARRAY_PACKED));
        byte[] bytes = w.writeValueAsBytes(new PointArray(new Point(1, 2), new Point(3, 4)));
        // should have 1 byte typed-tag, 1 byte length (for array contents);
        // followed by 2 embedded messages of 5 bytes length

        assertEquals(12, bytes.length);
        assertEquals(0xA, bytes[0]); // length-prefixed (2) value, field 1
        assertEquals(10, bytes[1]); // length of entries in array

        assertEquals(4, bytes[2]); // length of first entry
        assertEquals(8, bytes[3]); // wire type 0 (3 LSB), id of 1 (-> 0x8)
        assertEquals(1, bytes[4]); // VInt 1, no zig-zag
        assertEquals(0x10, bytes[5]); // wire type 0 (3 LSB), id of 2 (-> 0x10)
        assertEquals(4, bytes[6]); // VInt 2, but with zig-zag

        assertEquals(4, bytes[7]); // length of second entry
        assertEquals(8, bytes[8]);
        assertEquals(3, bytes[9]); // Point(3, )
        assertEquals(0x10, bytes[10]);
        assertEquals(8, bytes[11]); // Point (, 4)
    }    
}
