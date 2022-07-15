package tools.jackson.dataformat.cbor.mapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import tools.jackson.core.JsonToken;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser.NumberType;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.dataformat.cbor.CBORGenerator;
import tools.jackson.dataformat.cbor.CBORParser;
import tools.jackson.dataformat.cbor.CBORTestBase;

public class NumberBeanTest extends CBORTestBase
{
    static class IntBean {
        public int value;

        public IntBean(int v) { value = v; }
        protected IntBean() { }
    }

    static class LongBean {
        public long value;

        public LongBean(long v) { value = v; }
        protected LongBean() { }
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

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = cborMapper();

    public void testIntRoundTrip() throws Exception
    {
        for (int i : new int[] { 0, 1, -1,
                99, -120,
                5500, -9000,
                Integer.MIN_VALUE, Integer.MAX_VALUE }) {
            IntBean input = new IntBean(i);
            byte[] b = MAPPER.writeValueAsBytes(input);
            IntBean result = MAPPER.readValue(b, IntBean.class);
            assertEquals(input.value, result.value);
        }
    }

    public void testLongRoundTrip() throws Exception
    {
        for (long v : new long[] { 0, 1, -1,
                100L, -200L,
                5000L, -3600L,
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                1L + Integer.MAX_VALUE, -1L + Integer.MIN_VALUE
        }) {
            _testLongRoundTrip(v);
        }

        _testLongRoundTrip(2330462449L); // from [dataformats-binary#30]
        _testLongRoundTrip(0xFFFFFFFFL); // max positive uint32
        _testLongRoundTrip(-0xFFFFFFFFL);
        _testLongRoundTrip(0x100000000L);
        _testLongRoundTrip(-0x100000000L);
        _testLongRoundTrip(0x100000001L);
        _testLongRoundTrip(-0x100000001L);
        _testLongRoundTrip(Long.MIN_VALUE);
        _testLongRoundTrip(Long.MAX_VALUE);
    }

    private void _testLongRoundTrip(long v) throws Exception
    {
        LongBean input = new LongBean(v);
        byte[] b = MAPPER.writeValueAsBytes(input);
        LongBean result = MAPPER.readValue(b, LongBean.class);
        assertEquals(input.value, result.value);
    }

    // for [dataformats-binary#32] coercion of Float into Double
    public void testUntypedWithFloat() throws Exception
    {
        Object[] input = new Object[] { Float.valueOf(0.5f) };
        byte[] b = MAPPER.writeValueAsBytes(input);
        Object[] result = MAPPER.readValue(b, Object[].class);
        assertEquals(1, result.length);
        assertEquals(Float.class, result[0].getClass());
        assertEquals(input[0], result[0]);
    }

    public void testNumberTypeRetainingInt() throws Exception
    {
        NumberWrapper result;
        ByteArrayOutputStream bytes;

        bytes = new ByteArrayOutputStream();
        try (CBORGenerator g = cborGenerator(bytes)) {
            g.writeStartObject();
            g.writeNumberProperty("nr", 123);
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(Integer.valueOf(123), result.nr);

        bytes = new ByteArrayOutputStream();
        try (CBORGenerator g = cborGenerator(bytes)) {
            g.writeStartObject();
            g.writeNumberProperty("nr", Long.MAX_VALUE);
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(Long.valueOf(Long.MAX_VALUE), result.nr);

        bytes = new ByteArrayOutputStream();
        try (CBORGenerator g = cborGenerator(bytes)) {
            g.writeStartObject();
            g.writeNumberProperty("nr", BigInteger.valueOf(-42L));
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(BigInteger.valueOf(-42L), result.nr);
    }

    public void testNumberTypeRetainingFP() throws Exception
    {
        NumberWrapper result;
        ByteArrayOutputStream bytes;

        bytes = new ByteArrayOutputStream();
        try (CBORGenerator g = cborGenerator(bytes)) {
            g.writeStartObject();
            g.writeNumberProperty("nr", 0.25f);
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(Float.valueOf(0.25f), result.nr);

        bytes = new ByteArrayOutputStream();
        try (CBORGenerator g = cborGenerator(bytes)) {
            g.writeStartObject();
            g.writeNumberProperty("nr", 0.5);
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(Double.valueOf(0.5), result.nr);

        bytes = new ByteArrayOutputStream();
        try (CBORGenerator g = cborGenerator(bytes)) {
            g.writeStartObject();
            g.writeNumberProperty("nr", new BigDecimal("0.100"));
            g.writeEndObject();
        }
        result = MAPPER.readValue(bytes.toByteArray(), NumberWrapper.class);
        assertEquals(new BigDecimal("0.100"), result.nr);
    }

    public void testNumberTypeRetainingBuffering() throws Exception
    {
        ByteArrayOutputStream bytes;

        ObjectWriter w = MAPPER.writer()
                .without(CBORGenerator.Feature.WRITE_MINIMAL_INTS);
        final BigDecimal EXP_BIG_DEC = new BigDecimal("0.0100");
        
        bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = w.createGenerator(bytes)) {
            g.writeStartArray();
            g.writeNumber(101);
            g.writeNumber(0.25);
            g.writeNumber(13117L);
            g.writeNumber(0.5f);
            g.writeNumber(BigInteger.valueOf(1972));
            g.writeNumber(EXP_BIG_DEC);
            g.writeEndArray();
        }

        try (CBORParser p = cborParser(bytes.toByteArray())) {
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
            assertEquals(Long.valueOf(13117L), p.getNumberValue());
            assertEquals(Long.valueOf(13117L), p.getNumberValueExact());

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
    public void testBigDecimalWithBuffering() throws Exception
    {
        final BigDecimal VALUE = new BigDecimal("5.00");
        // Need to generate by hand since JSON would not indicate desire for BigDecimal
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CBORGenerator g = cborGenerator(bytes)) {
            g.writeStartObject();
            g.writeNumberProperty("value", VALUE);
            g.writeEndObject();
        }
        
        NestedBigDecimalHolder2784 result = MAPPER.readValue(bytes.toByteArray(),
                NestedBigDecimalHolder2784.class);
        assertEquals(VALUE, result.holder.value);
    }
}
