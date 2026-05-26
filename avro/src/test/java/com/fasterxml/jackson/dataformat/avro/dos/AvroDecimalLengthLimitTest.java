package com.fasterxml.jackson.dataformat.avro.dos;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link StreamReadConstraints#getMaxNumberLength()} is enforced
 * by the Avro parser when decoding the {@code decimal} logical type
 * (both {@code bytes}- and {@code fixed}-backed forms), matching the behavior
 * of CBOR / Smile codecs which guard equivalent native big-number tokens.
 */
public class AvroDecimalLengthLimitTest extends AvroTestBase
{
    private final AvroMapper DEFAULT_MAPPER = new AvroMapper();

    // Strict limit: 20 bytes of unscaled magnitude (≈ 48 decimal digits)
    private final AvroMapper STRICT_MAPPER;
    {
        AvroFactory f = AvroFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNumberLength(20).build())
                .build();
        STRICT_MAPPER = new AvroMapper(f);
    }

    private final AvroSchema BYTES_DECIMAL_SCHEMA;
    private final AvroSchema FIXED_DECIMAL_SCHEMA;
    {
        try {
            BYTES_DECIMAL_SCHEMA = DEFAULT_MAPPER.schemaFrom(
                    "{\"type\":\"record\",\"name\":\"D\",\"fields\":[{"
                    + "\"name\":\"v\",\"type\":{"
                    + "\"type\":\"bytes\",\"logicalType\":\"decimal\","
                    + "\"precision\":100,\"scale\":2}}]}");
            FIXED_DECIMAL_SCHEMA = DEFAULT_MAPPER.schemaFrom(
                    "{\"type\":\"record\",\"name\":\"D\",\"fields\":[{"
                    + "\"name\":\"v\",\"type\":{"
                    + "\"type\":\"fixed\",\"name\":\"F\",\"size\":24,"
                    + "\"logicalType\":\"decimal\","
                    + "\"precision\":56,\"scale\":2}}]}");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testBytesDecimalLargePayloadTripsLimit() throws Exception
    {
        // Unscaled value with ~62 digits -> ~26 bytes, above limit of 20
        BigDecimal big = new BigDecimal(
                "123456789012345678901234567890123456789012345678901234567890.12");
        byte[] doc = encodeBytesDecimal(big);

        try (JsonParser p = STRICT_MAPPER.reader().with(BYTES_DECIMAL_SCHEMA).createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.FIELD_NAME, p.nextToken());
            StreamConstraintsException e = assertThrows(StreamConstraintsException.class,
                    () -> p.nextToken());
            verifyException(e, "Number value length");
        }
    }

    @Test
    public void testFixedDecimalLargePayloadTripsLimit() throws Exception
    {
        // fixed size 24 bytes -> always 24 bytes of payload, exceeds limit of 20
        BigDecimal big = new BigDecimal("100.00");
        byte[] doc = encodeFixedDecimal(big);

        try (JsonParser p = STRICT_MAPPER.reader().with(FIXED_DECIMAL_SCHEMA).createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.FIELD_NAME, p.nextToken());
            StreamConstraintsException e = assertThrows(StreamConstraintsException.class,
                    () -> p.nextToken());
            verifyException(e, "Number value length");
        }
    }

    @Test
    public void testBytesDecimalSmallPayloadPasses() throws Exception
    {
        // Unscaled 4220 fits in 2 bytes -> well below limit of 20
        BigDecimal small = new BigDecimal("42.20");
        byte[] doc = encodeBytesDecimal(small);

        try (JsonParser p = STRICT_MAPPER.reader().with(BYTES_DECIMAL_SCHEMA).createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(small, p.getDecimalValue());
        }
    }

    @Test
    public void testDefaultLimitAllowsTypicalDecimals() throws Exception
    {
        BigDecimal v = new BigDecimal("12345678901234567890.42");
        byte[] doc = encodeBytesDecimal(v);

        try (JsonParser p = DEFAULT_MAPPER.reader().with(BYTES_DECIMAL_SCHEMA).createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(v, p.getDecimalValue());
        }
    }

    private byte[] encodeBytesDecimal(BigDecimal v) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("v", v);
        return DEFAULT_MAPPER.writer(BYTES_DECIMAL_SCHEMA).writeValueAsBytes(m);
    }

    private byte[] encodeFixedDecimal(BigDecimal v) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("v", v);
        return DEFAULT_MAPPER.writer(FIXED_DECIMAL_SCHEMA).writeValueAsBytes(m);
    }
}
