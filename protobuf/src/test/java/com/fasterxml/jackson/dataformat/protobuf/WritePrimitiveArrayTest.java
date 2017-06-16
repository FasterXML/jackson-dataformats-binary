package com.fasterxml.jackson.dataformat.protobuf;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Assert;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParser.NumberType;
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

    final protected static String PROTOC_STRING_ARRAY_SPARSE = "message Strings {\n"
            +" repeated string values = 1;\n"
            +"}\n";

    final protected static String PROTOC_STRING_ARRAY_PACKED = "message Strings {\n"
            +" repeated string values = 1 [packed=true];\n"
            +"}\n";

    final protected static String PROTOC_FLOAT_ARRAY_SPARSE = "message Floats {\n"
            +" repeated float values = 1;\n"
            +"}\n";

    final protected static String PROTOC_FLOAT_ARRAY_PACKED = "message Floats {\n"
            +" repeated float values = 1 [packed=true];\n"
            +"}\n";
    
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

    static class FloatArray {
        public float[] values;

        protected FloatArray() { }
        public FloatArray(float... v) {
            values = v;
        }
    }
    
    static class StringArray {
        public String[] values;

        protected StringArray() { }
        public StringArray(String... v) {
            values = v;
        }
    }

    final ObjectMapper MAPPER = new ProtobufMapper();

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
        assertEquals(0x0A, bytes[0]); // packed (2) value, field 1
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
        IntArray input = new IntArray(3, -1, -2225, 1235909);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(36, bytes.length);

        IntArray result = MAPPER.readerFor(IntArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
    }

    public void testIntAsLongArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_INT64_ARRAY_PACKED);
        final ObjectWriter w = MAPPER.writer(schema);
        IntArray input = new IntArray(3, -1, -2225, 1235909);
        byte[] bytes = w.writeValueAsBytes(input);
        // 1 byte for typed tag, 1 byte for length, 3 x 8 byte per value -> 26
        assertEquals(34, bytes.length);

        IntArray result = MAPPER.readerFor(IntArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
    }

    // // // But then as regular longs

    public void testLongArraySparse() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_INT64_ARRAY_SPARSE);
        final ObjectWriter w = MAPPER.writer(schema);
        LongArray input = new LongArray(Integer.MAX_VALUE, -1, 3L + Integer.MAX_VALUE, Long.MIN_VALUE);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(36, bytes.length);

        LongArray result = MAPPER.readerFor(LongArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);

        _verifyLongArray(bytes, schema, input.values);
    }

    public void testLongArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_INT64_ARRAY_PACKED);
        final ObjectWriter w = MAPPER.writer(schema);
        LongArray input = new LongArray(Integer.MIN_VALUE, 7L + Integer.MAX_VALUE, -1, Long.MAX_VALUE);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(34, bytes.length);

        LongArray result = MAPPER.readerFor(LongArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
 
        _verifyLongArray(bytes, schema, input.values);
    }

    private void _verifyLongArray(byte[] doc, ProtobufSchema schema,
            long[] inputValues)
        throws Exception
    {
        // also via streaming API
        JsonParser p = MAPPER.getFactory().createParser(doc);
        p.setSchema(schema);

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("values", p.getCurrentName());

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals(Long.valueOf(inputValues[0]), p.getNumberValue());
        assertFalse(p.isNaN());
        assertEquals(BigInteger.valueOf(inputValues[0]), p.getBigIntegerValue());

        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        // skip value
        assertNull(p.nextFieldName()); // just for funsies

        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(inputValues[3], p.getLongValue());
        assertEquals((double) inputValues[3], p.getDoubleValue());
        assertEquals((float) inputValues[3], p.getFloatValue());

        assertToken(JsonToken.END_ARRAY, p.nextToken());
        
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        
        p.close();
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
        DoubleArray input = new DoubleArray(0.25, -2.5, 1000.125, 1234567891234567890.5);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(36, bytes.length);

        DoubleArray result = MAPPER.readerFor(DoubleArray.class).with(schema)
                .readValue(bytes);
        _assertEquals(input.values, result.values);

        _verifyDoubleArray(bytes, schema, input.values);
    }

    public void testDoubleArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_DOUBLE_ARRAY_PACKED);
        final ObjectWriter w = MAPPER.writer(schema);
        DoubleArray input = new DoubleArray(-0.5, 89245.25, 0.625, 1234567891234567890.5);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(34, bytes.length);

        DoubleArray result = MAPPER.readerFor(DoubleArray.class).with(schema)
                .readValue(bytes);
        _assertEquals(input.values, result.values);

        _verifyDoubleArray(bytes, schema, input.values);
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

    private void _verifyDoubleArray(byte[] doc, ProtobufSchema schema,
            double[] inputValues)
        throws Exception
    {
        // also via streaming API
        JsonParser p = MAPPER.getFactory().createParser(doc);
        p.setSchema(schema);

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("values", p.getCurrentName());

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.DOUBLE, p.getNumberType());
        assertEquals(Double.valueOf(inputValues[0]), p.getNumberValue());
        assertFalse(p.isNaN());
        assertEquals(new BigDecimal(inputValues[0]), p.getDecimalValue());

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertNull(p.nextFieldName()); // just for funsies

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(Double.valueOf(inputValues[3]), p.getDoubleValue());
        assertEquals((long) inputValues[3], p.getLongValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        
        p.close();
    }
    
    public void testFloatArraySparse() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_FLOAT_ARRAY_SPARSE);
        final ObjectWriter w = MAPPER.writer(schema);
        FloatArray input = new FloatArray(0.25f, -2.5f, 55555555.5f);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(15, bytes.length);

        FloatArray result = MAPPER.readerFor(FloatArray.class).with(schema)
                .readValue(bytes);
        _assertEquals(input.values, result.values);

        _verifyFloatArray(bytes, schema, input.values);
    }

    public void testFloatArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_FLOAT_ARRAY_PACKED);
        final ObjectWriter w = MAPPER.writer(schema);
        FloatArray input = new FloatArray(-0.5f, 89245.25f, 55555555.5f);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(14, bytes.length);

        FloatArray result = MAPPER.readerFor(FloatArray.class).with(schema)
                .readValue(bytes);
        _assertEquals(input.values, result.values);

        _verifyFloatArray(bytes, schema, input.values);
    }

    private void _verifyFloatArray(byte[] doc, ProtobufSchema schema,
            float[] inputValues)
        throws Exception
    {
        // also via streaming API
        JsonParser p = MAPPER.getFactory().createParser(doc);
        p.setSchema(schema);

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("values", p.getCurrentName());

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.FLOAT, p.getNumberType());
        assertEquals(Float.valueOf(inputValues[0]), p.getNumberValue());
        assertFalse(p.isNaN());

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());

        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(Float.valueOf(inputValues[2]), p.getFloatValue());
        assertEquals((int) inputValues[2], p.getIntValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        
        p.close();
    }
    
    private void _assertEquals(float[] exp, float[] act)
    {
        assertEquals(exp.length, act.length);
        for (int i = 0; i < exp.length; ++i) {
            // note: caller ensures it only uses values that reliably round-trip
            if (exp[i] != act[i]) {
                fail("Entry #"+i+" wrong: expected "+exp[i]+", got "+act[i]);
            }
        }
    }

    /*
    /**********************************************************
    /* Test methods, String arrays
    /**********************************************************
     */

    public void testStringArraySparse() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STRING_ARRAY_SPARSE);
        final ObjectWriter w = MAPPER.writer(schema);
        final StringArray input = new StringArray("foo", "foobar", "", "x");
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(18, bytes.length);

        StringArray result = MAPPER.readerFor(StringArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
    }

    public void testStringArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STRING_ARRAY_PACKED);
        final ObjectWriter w = MAPPER.writer(schema);
        final StringArray input = new StringArray("foobar", "", "x", "foo");
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(16, bytes.length);

        StringArray result = MAPPER.readerFor(StringArray.class).with(schema)
                .readValue(bytes);
        Assert.assertArrayEquals(input.values, result.values);
    }
}
