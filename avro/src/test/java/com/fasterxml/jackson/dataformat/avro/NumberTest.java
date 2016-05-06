package com.fasterxml.jackson.dataformat.avro;

public class NumberTest extends AvroTestBase
{
    static class NumberWrapper {
        public Number value;

        public NumberWrapper() { }
        public NumberWrapper(Number v) { value = v; }
    }

    // for [dataformat-avro#41]
    public void testNumberType() throws Exception
    {
        AvroMapper mapper = new AvroMapper();
        AvroSchema schema = mapper.schemaFor(NumberWrapper.class);

        byte[] bytes = mapper.writer(schema)
                .writeValueAsBytes(new NumberWrapper(Integer.valueOf(17)));
        NumberWrapper result = mapper.readerFor(NumberWrapper.class)
                .with(schema)
                .readValue(bytes);
        assertNotNull(result);
        assertEquals(Integer.valueOf(17), result.value);
    }
}
