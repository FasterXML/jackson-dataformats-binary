package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.avro.testsupport.ThrottledInputStream;

public class ScalarTest extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

    public void testRootString() throws Exception
    {
        final String INPUT = "Something or other";
        AvroSchema schema = MAPPER.schemaFrom(quote("string"));
        byte[] avro = MAPPER.writerFor(String.class)
                .with(schema)
                .writeValueAsBytes(INPUT);
        ObjectReader r = MAPPER.readerFor(String.class)
                .with(schema);
        _testRootString(100, r, avro, INPUT);
        _testRootString(3, r, avro, INPUT);
        _testRootString(1, r, avro, INPUT);
    }
    
    public void _testRootString(int chunkSize, ObjectReader r, byte[] encoded,
            String inputValue) throws Exception
    {
        ThrottledInputStream in = new ThrottledInputStream(encoded, chunkSize);
        String actual = r.readValue(in);
        assertEquals(inputValue, actual);
        in.close();
    }

    public void testRootInt() throws Exception
    {
        Integer inputValue = Integer.valueOf(0xE134567);
        AvroSchema schema = MAPPER.schemaFrom(quote("int"));
        byte[] avro = MAPPER.writer(schema)
                .writeValueAsBytes(inputValue);
        ObjectReader r = MAPPER.readerFor(Integer.class)
                .with(schema);
        _testRootInt(100, r, avro, inputValue);
        _testRootInt(3, r, avro, inputValue);
        _testRootInt(1, r, avro, inputValue);
    }
        
    public void _testRootInt(int chunkSize, ObjectReader r, byte[] encoded,
            Integer inputValue) throws Exception
    {
        ThrottledInputStream in = new ThrottledInputStream(encoded, chunkSize);
        Integer actual = r.readValue(in);
        assertEquals(inputValue,actual);
        in.close();
    }

    public void testRootLong() throws Exception
    {
        for (long l : new long[] {
                0L, 1L, -1L,
                // [dataformats-binary#113]
                -5580797459299185431L,
                0x1234567890abcdefL,
                Long.MAX_VALUE,
                Long.MIN_VALUE,
        }) {
            Long inputValue = Long.valueOf(l);
            AvroSchema schema = MAPPER.schemaFor(Long.class);
            byte[] avro = MAPPER.writer(schema)
                    .writeValueAsBytes(inputValue);
            ObjectReader r = MAPPER.readerFor(Long.class)
                    .with(schema);
            _testRootLong(100, r, avro, inputValue);
            _testRootLong(3, r, avro, inputValue);
            _testRootLong(1, r, avro, inputValue);
        }
    }

    public void _testRootLong(int chunkSize, ObjectReader r, byte[] encoded,
            Long inputValue) throws Exception
    {
        ThrottledInputStream in = new ThrottledInputStream(encoded, chunkSize);
        Long actual = r.readValue(in);
        in.close();
        if (!inputValue.equals(actual)) {
            fail(String.format("Expected 0x%08x got 0x%08x",
                    inputValue.longValue(), actual.longValue()));
        }
    }

    public void testRootDouble() throws Exception
    {
        for (double d : new double[] {
                0.0, 1.0, -1.0,
                0.25,
                0.1,
                -9125436547457.903576
        }) {
            Double inputValue = Double.valueOf(d);
            AvroSchema schema = MAPPER.schemaFor(Double.class);
            byte[] avro = MAPPER.writer(schema)
                    .writeValueAsBytes(inputValue);
            ObjectReader r = MAPPER.readerFor(Double.class)
                    .with(schema);
            _testRootDouble(100, r, avro, inputValue);
            _testRootDouble(3, r, avro, inputValue);
            _testRootDouble(1, r, avro, inputValue);
        }
    }

    public void _testRootDouble(int chunkSize, ObjectReader r, byte[] encoded,
            Double inputValue) throws Exception
    {
        ThrottledInputStream in = new ThrottledInputStream(encoded, chunkSize);
        Double actual = r.readValue(in);
        in.close();
        if (inputValue.doubleValue() != actual.doubleValue()) {
            fail(String.format("Expected %f got %f",
                    inputValue.doubleValue(), actual.doubleValue()));
        }
    }

    public void testRootFloat() throws Exception
    {
        for (float f : new float[] {
                0.0f, 1.0f, -1.0f,
                0.25f,
                0.1f,
                -125657.306347f
        }) {
            Float inputValue = Float.valueOf(f);
            AvroSchema schema = MAPPER.schemaFor(Float.class);
            byte[] avro = MAPPER.writer(schema)
                    .writeValueAsBytes(inputValue);
            ObjectReader r = MAPPER.readerFor(Float.class)
                    .with(schema);
            _testRootFloat(100, r, avro, inputValue);
            _testRootFloat(3, r, avro, inputValue);
            _testRootFloat(1, r, avro, inputValue);
        }
    }

    public void _testRootFloat(int chunkSize, ObjectReader r, byte[] encoded,
            Float inputValue) throws Exception
    {
        ThrottledInputStream in = new ThrottledInputStream(encoded, chunkSize);
        Float actual = r.readValue(in);
        in.close();
        if (inputValue.floatValue() != actual.floatValue()) {
            fail(String.format("Expected %f got %f",
                    inputValue.doubleValue(), actual.doubleValue()));
        }
    }
    
    public void testRootBoolean() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(quote("boolean"));
        byte[] avro = MAPPER.writer(schema)
                .writeValueAsBytes(Boolean.TRUE);
        Boolean result = MAPPER.readerFor(Boolean.class)
                .with(schema)
                .readValue(avro);
        assertEquals(Boolean.TRUE, result);
    }
}
