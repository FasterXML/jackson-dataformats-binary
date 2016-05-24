package com.fasterxml.jackson.dataformat.protobuf;

import org.junit.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class WritePrimitiveArrayTest extends ProtobufTestBase
{
    final protected static String PROTOC_INT_ARRAY_SPARSE = "message Ints {\n"
            +" repeated sint32 values = 1;\n"
            +"}\n"
    ;

    final protected static String PROTOC_INT_ARRAY_PACKED = "message Ints {\n"
            +" repeated sint32 values = 1 [packed=true];\n"
            +"}\n"
    ;

    final protected static String PROTOC_INT32_ARRAY_SPARSE = "message Ints {\n"
            +" repeated fixed32 values = 1;\n"
            +"}\n"
    ;

    final protected static String PROTOC_INT32_ARRAY_PACKED = "message Ints {\n"
            +" repeated fixed32 values = 1 [packed=true];\n"
            +"}\n"
    ;

    final protected static String PROTOC_INT64_ARRAY_SPARSE = "message Longs {\n"
            +" repeated fixed64 values = 1;\n"
            +"}\n"
    ;

    final protected static String PROTOC_INT64_ARRAY_PACKED = "message Longs {\n"
            +" repeated fixed64 values = 1 [packed=true];\n"
            +"}\n"
    ;

    final protected static String PROTOC_DOUBLE_ARRAY_SPARSE = "message Doubles {\n"
            +" repeated double values = 1;\n"
            +"}\n"
    ;

    final protected static String PROTOC_DOUBLE_ARRAY_PACKED = "message Doubles {\n"
            +" repeated double values = 1 [packed=true];\n"
            +"}\n"
    ;
    
    static class IntArray {
        public int[] values;

        protected IntArray() { }
        public IntArray(int... v) {
            values = v;
        }
    }

    static class LongArray {
        public long[] values;

        protected LongArray() { }
        public LongArray(long... v) {
            values = v;
        }
    }

    static class DoubleArray {
        public double[] values;

        protected DoubleArray() { }
        public DoubleArray(double... v) {
            values = v;
        }
    }
    
    final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    public WritePrimitiveArrayTest() throws Exception { }

    /*
    /**********************************************************
    /* Test methods, int arrays
    /**********************************************************
     */

    public void testVIntArraySparse() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(ProtobufSchemaLoader.std.parse(PROTOC_INT_ARRAY_SPARSE));
        byte[] bytes = w.writeValueAsBytes(new IntArray(3, -1, 2));
        // 3 x 2 bytes per value (typed tag, value) -> 6
        assertEquals(6, bytes.length);
        assertEquals(0x8, bytes[0]); // zig-zagged vint (0) value, field 1
        assertEquals(0x6, bytes[1]); // zig-zagged value for 3
        assertEquals(0x8, bytes[2]);
        assertEquals(0x1, bytes[3]); // zig-zagged value for -1
        assertEquals(0x8, bytes[4]);
        assertEquals(0x4, bytes[5]); // zig-zagged value for 2
    }

    public void testVIntArrayPacked() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(ProtobufSchemaLoader.std.parse(PROTOC_INT_ARRAY_PACKED));
        byte[] bytes = w.writeValueAsBytes(new IntArray(3, -1, 2));
        // 1 byte for typed tag, 1 byte for length, 3 x 1 byte per value -> 5
        assertEquals(5, bytes.length);
        assertEquals(0x8, bytes[0]); // zig-zagged vint (0) value, field 1
        assertEquals(0x3, bytes[1]); // length for array, 3 bytes
        assertEquals(0x6, bytes[2]); // zig-zagged value for 3
        assertEquals(0x1, bytes[3]); // zig-zagged value for -1
        assertEquals(0x4, bytes[4]); // zig-zagged value for 2
    }

    public void testInt32ArraySparse() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(ProtobufSchemaLoader.std.parse
                (PROTOC_INT32_ARRAY_SPARSE));
        byte[] bytes = w.writeValueAsBytes(new IntArray(3, -1, 2));
        // 3 x 5 bytes per value (typed tag, value) -> 18
        assertEquals(15, bytes.length);
    }

    public void testInt32ArrayPacked() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(ProtobufSchemaLoader.std.parse
                (PROTOC_INT32_ARRAY_PACKED));
        byte[] bytes = w.writeValueAsBytes(new IntArray(3, -1, 2));
        // 1 byte for typed tag, 1 byte for length, 3 x 4 byte per value -> 14
        assertEquals(14, bytes.length);
    }

    /*
    /**********************************************************
    /* Test methods, long arrays
    /**********************************************************
     */

    // // // First as ints:

    public void testIntAsLongArraySparse() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_INT64_ARRAY_SPARSE);
        final ObjectWriter w = MAPPER.writer(schema);
        IntArray input = new IntArray(3, -1, -2);
        byte[] bytes = w.writeValueAsBytes(input);
        // 3 x 9 bytes per value (typed tag, value) -> 30
        assertEquals(27, bytes.length);

        IntArray result = MAPPER.readerFor(IntArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
    }

    public void testIntAsLongArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_INT64_ARRAY_PACKED);
        final ObjectWriter w = MAPPER.writer(schema);
        IntArray input = new IntArray(3, -1, -2);
        byte[] bytes = w.writeValueAsBytes(input);
        // 1 byte for typed tag, 1 byte for length, 3 x 8 byte per value -> 26
        assertEquals(26, bytes.length);

        IntArray result = MAPPER.readerFor(IntArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
    }

    // // // But then as regular longs

    public void testLongArraySparse() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_INT64_ARRAY_SPARSE);
        final ObjectWriter w = MAPPER.writer(schema);
        LongArray input = new LongArray(Integer.MAX_VALUE, -1, Long.MIN_VALUE);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(27, bytes.length);

        LongArray result = MAPPER.readerFor(LongArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
    }

    public void testLongArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_INT64_ARRAY_PACKED);
        final ObjectWriter w = MAPPER.writer(schema);
        LongArray input = new LongArray(Integer.MIN_VALUE, -1, Long.MAX_VALUE);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(26, bytes.length);

        LongArray result = MAPPER.readerFor(LongArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
    }

    /*
    /**********************************************************
    /* Test methods, floating-point arrays
    /**********************************************************
     */

    public void testDoubleArraySparse() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_DOUBLE_ARRAY_SPARSE);
        final ObjectWriter w = MAPPER.writer(schema);
        DoubleArray input = new DoubleArray(0.25, -2.5, 1000.125);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(27, bytes.length);

        DoubleArray result = MAPPER.readerFor(DoubleArray.class).with(schema)
                .readValue(bytes);
        _assertEquals(input.values, result.values);
    }

    public void testDoubleArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_DOUBLE_ARRAY_PACKED);
        final ObjectWriter w = MAPPER.writer(schema);
        DoubleArray input = new DoubleArray(-0.5, 89245.25, 0.625);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(26, bytes.length);

        DoubleArray result = MAPPER.readerFor(DoubleArray.class).with(schema)
                .readValue(bytes);
        _assertEquals(input.values, result.values);
    }

    private void _assertEquals(double[] exp, double[] act)
    {
        assertEquals(exp.length, act.length);
        for (int i = 0; i < exp.length; ++i) {
            // note: caller ensures it only uses values that reliably round-trip
            if (exp[i] != act[i]) {
                fail("Entry #"+i+" wrong: expected "+exp[i]+", got "+act[i]);
            }
        }
    }
}
