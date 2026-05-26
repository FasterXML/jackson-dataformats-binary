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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link StreamReadConstraints#getMaxNumberLength()} is enforced
 * by the Avro parser when decoding the {@code decimal} logical type
 * (both {@code bytes}- and {@code fixed}-backed forms), matching the behavior
 * of CBOR / Smile codecs which guard equivalent native big-number tokens.
 */
public class AvroDecimalLengthLimitTest extends AvroTestBase
{
    private final AvroMapper DEFAULT_MAPPER = new AvroMapper();

    // Strict limit: 4 bytes of unscaled magnitude (≈ 9-10 decimal digits)
    private final AvroMapper STRICT_MAPPER;
    {
        AvroFactory f = AvroFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNumberLength(4).build())
                .build();
        STRICT_MAPPER = new AvroMapper(f);
    }

    private final String BYTES_DECIMAL_SCHEMA =
            "{\"type\":\"record\",\"name\":\"D\",\"fields\":[{"
            + "\"name\":\"v\",\"type\":{"
            + "\"type\":\"bytes\",\"logicalType\":\"decimal\","
            + "\"precision\":40,\"scale\":2}}]}";

    private final String FIXED_DECIMAL_SCHEMA =
            "{\"type\":\"record\",\"name\":\"D\",\"fields\":[{"
            + "\"name\":\"v\",\"type\":{"
            + "\"type\":\"fixed\",\"name\":\"F\",\"size\":16,"
            + "\"logicalType\":\"decimal\","
            + "\"precision\":38,\"scale\":2}}]}";

    @Test
    public void testBytesDecimalLargePayloadTripsLimit() throws Exception
    {
        // Unscaled value with ~32 digits -> 14 bytes, well above limit of 4
        BigDecimal big = new BigDecimal("12345678901234567890123456789012.34");
        byte[] doc = encodeBytesDecimal(big);
        AvroSchema schema = DEFAULT_MAPPER.schemaFrom(BYTES_DECIMAL_SCHEMA);

        try (JsonParser p = STRICT_MAPPER.reader().with(schema).createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.FIELD_NAME, p.nextToken());
            StreamConstraintsException e = assertThrows(StreamConstraintsException.class,
                    () -> p.nextToken());
            assertTrue(e.getMessage().contains("Number value length"),
                    "unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testFixedDecimalLargePayloadTripsLimit() throws Exception
    {
        // fixed size 16 bytes -> always 16 bytes of payload, exceeds limit of 4
        BigDecimal big = new BigDecimal("100.00");
        byte[] doc = encodeFixedDecimal(big);
        AvroSchema schema = DEFAULT_MAPPER.schemaFrom(FIXED_DECIMAL_SCHEMA);

        try (JsonParser p = STRICT_MAPPER.reader().with(schema).createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.FIELD_NAME, p.nextToken());
            StreamConstraintsException e = assertThrows(StreamConstraintsException.class,
                    () -> p.nextToken());
            assertTrue(e.getMessage().contains("Number value length"),
                    "unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testBytesDecimalSmallPayloadPasses() throws Exception
    {
        // Unscaled 4220 fits in 2 bytes -> below limit of 4
        BigDecimal small = new BigDecimal("42.20");
        byte[] doc = encodeBytesDecimal(small);
        AvroSchema schema = DEFAULT_MAPPER.schemaFrom(BYTES_DECIMAL_SCHEMA);

        try (JsonParser p = STRICT_MAPPER.reader().with(schema).createParser(doc)) {
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
        AvroSchema schema = DEFAULT_MAPPER.schemaFrom(BYTES_DECIMAL_SCHEMA);

        try (JsonParser p = DEFAULT_MAPPER.reader().with(schema).createParser(doc)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(v, p.getDecimalValue());
        }
    }

    private byte[] encodeBytesDecimal(BigDecimal v) throws Exception {
        AvroSchema schema = DEFAULT_MAPPER.schemaFrom(BYTES_DECIMAL_SCHEMA);
        Map<String, Object> m = new HashMap<>();
        m.put("v", v);
        return DEFAULT_MAPPER.writer(schema).writeValueAsBytes(m);
    }

    private byte[] encodeFixedDecimal(BigDecimal v) throws Exception {
        AvroSchema schema = DEFAULT_MAPPER.schemaFrom(FIXED_DECIMAL_SCHEMA);
        Map<String, Object> m = new HashMap<>();
        m.put("v", v);
        return DEFAULT_MAPPER.writer(schema).writeValueAsBytes(m);
    }
}
