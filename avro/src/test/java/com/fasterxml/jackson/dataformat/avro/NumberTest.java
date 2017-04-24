package com.fasterxml.jackson.dataformat.avro;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.dataformat.avro.testsupport.LimitingInputStream;

public class NumberTest extends AvroTestBase
{
    static class NumberWrapper {
        public Number value;

        public NumberWrapper() { }
        public NumberWrapper(Number v) { value = v; }
    }

    @JsonPropertyOrder({ "i", "l", "f", "d" })
    static class Numbers {
        public int i;
        public long l;

        public float f;
        public double d;
        
        public Numbers() { }
        public Numbers(int i0, long l0,
                float f0, double d0) {
            i = i0;
            l = l0;
            f = f0;
            d = d0;
        }
    }

    private final AvroMapper MAPPER = new AvroMapper();
    
    // for [dataformat-avro#41]
    public void testNumberType() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFor(NumberWrapper.class);

        byte[] bytes = MAPPER.writer(schema)
                .writeValueAsBytes(new NumberWrapper(Integer.valueOf(17)));
        NumberWrapper result = MAPPER.readerFor(NumberWrapper.class)
                .with(schema)
                .readValue(bytes);
        assertNotNull(result);
        assertEquals(Integer.valueOf(17), result.value);
    }

    public void testNumberCoercions() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFor(Numbers.class);
        Numbers input = new Numbers(Integer.MIN_VALUE,
                Long.MAX_VALUE,
                0.125f, -3.75);
        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        JsonParser p = MAPPER.getFactory()
                .createParser(LimitingInputStream.wrap(bytes, 42));
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertTrue(p.nextFieldName(new SerializedString("i")));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertFalse(p.isNaN());
        assertEquals(input.i, p.getIntValue());
        assertEquals(Integer.valueOf(input.i), p.getNumberValue());
        assertEquals((float) input.i, p.getFloatValue());
        assertEquals((double) input.i, p.getDoubleValue());
        assertEquals(BigInteger.valueOf(input.i), p.getBigIntegerValue());
        assertEquals(BigDecimal.valueOf(input.i), p.getDecimalValue());

        assertTrue(p.nextFieldName(new SerializedString("l")));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertFalse(p.isNaN());
        assertEquals(input.l, p.getLongValue());
        assertEquals(Long.valueOf(input.l), p.getNumberValue());
        assertEquals(BigDecimal.valueOf(input.l), p.getDecimalValue());
        assertEquals((float) input.l, p.getFloatValue());
        assertEquals((double) input.l, p.getDoubleValue());
        assertEquals(BigInteger.valueOf(input.l), p.getBigIntegerValue());

        assertTrue(p.nextFieldName(new SerializedString("f")));
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.FLOAT, p.getNumberType());
        assertFalse(p.isNaN());
        assertEquals(input.f, p.getFloatValue());
        // NOTE: order of execution important here; access as Double would
        // result in type seemingly changing
        assertEquals(Float.valueOf(input.f), p.getNumberValue());
        
        assertEquals((double) input.f, p.getDoubleValue());
        assertEquals((int) input.f, p.getIntValue());
        assertEquals((long) input.f, p.getLongValue());

        assertTrue(p.nextFieldName(new SerializedString("d")));
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.DOUBLE, p.getNumberType());
        assertFalse(p.isNaN());
        assertEquals((float) input.d, p.getFloatValue());
        assertEquals(input.d, p.getDoubleValue());
        assertEquals((int) input.d, p.getIntValue());
        assertEquals((long) input.d, p.getLongValue());
        assertEquals(Double.valueOf(input.d), p.getNumberValue());

        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }
}
