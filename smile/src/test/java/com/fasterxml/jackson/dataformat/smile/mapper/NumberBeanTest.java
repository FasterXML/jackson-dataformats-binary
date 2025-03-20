package com.fasterxml.jackson.dataformat.smile.mapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;

import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NumberBeanTest extends BaseTestForSmile
{
    static class DoublesWrapper {
        public double[][] values;

        protected DoublesWrapper() { }
        public DoublesWrapper(double[][] v) { values = v; }
    }

    static class NumberWrapper {
        public Number nr;
    }

    // // Copied form "JDKNumberDeserTest", related to BigDecimal precision
    // // retaining through buffering

    // [databind#2784]
    static class BigDecimalHolder2784 {
        public BigDecimal value;
    }

    static class NestedBigDecimalHolder2784 {
        @JsonUnwrapped
        public BigDecimalHolder2784 holder;
    }

    // for [databind#4917]
    static class DeserializationIssue4917 {
        public DecimalHolder4917 decimalHolder;
        public double number;
    }

    static class DecimalHolder4917 {
        @JsonValue
        BigDecimal value;

        private DecimalHolder4917(BigDecimal value) {
            this.value = value;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static DecimalHolder4917 of(BigDecimal value) {
            return new DecimalHolder4917(value);
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = smileMapper();

    // for [dataformats-binary#31]
    @Test
    public void testDoubleArrayRoundTrip() throws Exception
    {
        double[][] inputArray = new double[][]{ { 0.25, -1.5 } };
        byte[] cbor = MAPPER.writeValueAsBytes(new DoublesWrapper(inputArray));
        DoublesWrapper result = MAPPER.readValue(cbor, DoublesWrapper.class);
        assertNotNull(result);
        assertNotNull(result.values);
        assertEquals(1, result.values.length);
        assertEquals(2, result.values[0].length);
        assertEquals(inputArray[0][0], result.values[0][0]);
        assertEquals(inputArray[0][1], result.values[0][1]);
    }

    @Test
    public void testNumberTypeRetainingInt() throws Exception
    {
        NumberWrapper result;
        ByteArrayOutputStream bytes;

        bytes = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(bytes, true)) {
            g.writeStartObject();
            g.writeNumberField("nr", 123);
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(Integer.valueOf(123), result.nr);

        bytes = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(bytes, true)) {
            g.writeStartObject();
            g.writeNumberField("nr", Long.MAX_VALUE);
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(Long.valueOf(Long.MAX_VALUE), result.nr);

        bytes = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(bytes, true)) {
            g.writeStartObject();
            g.writeNumberField("nr", BigInteger.valueOf(-42L));
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(BigInteger.valueOf(-42L), result.nr);
    }

    @Test
    public void testNumberTypeRetainingFP() throws Exception
    {
        NumberWrapper result;
        ByteArrayOutputStream bytes;

        bytes = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(bytes, true)) {
            g.writeStartObject();
            g.writeNumberField("nr", 0.25f);
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(Float.valueOf(0.25f), result.nr);

        bytes = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(bytes, true)) {
            g.writeStartObject();
            g.writeNumberField("nr", 0.5);
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(Double.valueOf(0.5), result.nr);

        bytes = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(bytes, true)) {
            g.writeStartObject();
            g.writeNumberField("nr", new BigDecimal("0.100"));
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(new BigDecimal("0.100"), result.nr);
    }

    @Test
    public void testNumberTypeRetainingBuffering() throws Exception
    {
        ByteArrayOutputStream bytes;
        final BigDecimal EXP_BIG_DEC = new BigDecimal("0.0100");

        bytes = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(bytes, true)) {
            g.writeStartArray();
            g.writeNumber(101);
            g.writeNumber(0.25);
            g.writeNumber(Long.MAX_VALUE);
            g.writeNumber(0.5f);
            g.writeNumber(BigInteger.valueOf(1972));
            g.writeNumber(EXP_BIG_DEC);
            g.writeEndArray();
        }

        try (SmileParser p = _smileParser(bytes.toByteArray(), true)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());

            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(NumberType.INT, p.getNumberType());
            assertEquals(Integer.valueOf(101), p.getNumberValue());
            assertEquals(Integer.valueOf(101), p.getNumberValueExact());

            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(NumberType.DOUBLE, p.getNumberType());
            assertEquals(Double.valueOf(0.25), p.getNumberValue());
            assertEquals(Double.valueOf(0.25), p.getNumberValueExact());

            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(NumberType.LONG, p.getNumberType());
            assertEquals(Long.MAX_VALUE, p.getNumberValue());
            assertEquals(Long.MAX_VALUE, p.getNumberValueExact());

            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(NumberType.FLOAT, p.getNumberType());
            assertEquals(Float.valueOf(0.5f), p.getNumberValue());
            assertEquals(Float.valueOf(0.5f), p.getNumberValueExact());

            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(NumberType.BIG_INTEGER, p.getNumberType());
            assertEquals(BigInteger.valueOf(1972), p.getNumberValue());
            assertEquals(BigInteger.valueOf(1972), p.getNumberValueExact());

            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(NumberType.BIG_DECIMAL, p.getNumberType());
            assertEquals(EXP_BIG_DEC, p.getNumberValue());
            assertEquals(EXP_BIG_DEC, p.getNumberValueExact());

            assertToken(JsonToken.END_ARRAY, p.nextToken());
        }
    }

    // [databind#2784]
    @Test
    public void testBigDecimalWithBuffering() throws Exception
    {
        final BigDecimal VALUE = new BigDecimal("5.00");
        // Need to generate by hand since JSON would not indicate desire for BigDecimal
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(bytes, true)) {
            g.writeStartObject();
            g.writeNumberField("value", VALUE);
            g.writeEndObject();
        }

        NestedBigDecimalHolder2784 result = MAPPER.readValue(bytes.toByteArray(),
                NestedBigDecimalHolder2784.class);
        assertEquals(VALUE, result.holder.value);
    }

    // [databind#4917]
    @Test
    public void testIssue4917() throws Exception {
        final String bd = "100.00";
        final double d = 50.0;
        final DeserializationIssue4917 value = new DeserializationIssue4917();
        value.decimalHolder = DecimalHolder4917.of(new BigDecimal(bd));
        value.number = d;
        final byte[] data = MAPPER.writeValueAsBytes(value);

        final DeserializationIssue4917 result = MAPPER.readValue(
                data, DeserializationIssue4917.class);
        assertEquals(value.decimalHolder.value, result.decimalHolder.value);
        assertEquals(value.number, result.number);
    }
}
