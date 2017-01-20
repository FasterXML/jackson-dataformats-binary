package com.fasterxml.jackson.dataformat.avro;

public class ScalarTest extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

    public void testRootString() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(quote("string"));
        byte[] avro = MAPPER.writer(schema)
                .writeValueAsBytes("foobar");
        String str = MAPPER.readerFor(String.class)
                .with(schema)
                .readValue(avro);
        assertEquals("foobar", str);
    }

    public void testRootInt() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(quote("int"));
        Integer input = Integer.valueOf(123456);
        byte[] avro = MAPPER.writer(schema)
                .writeValueAsBytes(input);
        Integer result = MAPPER.readerFor(Integer.class)
                .with(schema)
                .readValue(avro);
        assertEquals(input, result);
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
