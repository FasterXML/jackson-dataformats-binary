package com.fasterxml.jackson.dataformat.ion.dos;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Verifies that {@link StreamReadConstraints#getMaxNumberLength()} is enforced
 * by the Ion parser when decoding {@code BigInteger} and {@code BigDecimal}
 * values, matching the behavior of CBOR / Smile / Avro codecs which guard
 * equivalent native big-number tokens. See [dataformats-binary#696].
 */
public class IonBigNumberLengthLimitTest
{
    private final IonObjectMapper DEFAULT_MAPPER = IonObjectMapper
            .builderForBinaryWriters().build();

    // Strict limit: 20 bytes of unscaled magnitude (~48 decimal digits)
    private final IonObjectMapper STRICT_MAPPER;
    {
        IonFactory f = IonFactory.builderForBinaryWriters().build();
        f.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNumberLength(20).build());
        STRICT_MAPPER = IonObjectMapper.builder(f).build();
    }

    @Test
    public void testBigIntegerLargeTripsLimit() throws Exception
    {
        // ~62 decimal digits → ~26 bytes, well above limit of 20
        BigInteger big = new BigInteger(
                "12345678901234567890123456789012345678901234567890123456789012");
        byte[] doc = DEFAULT_MAPPER.writeValueAsBytes(big);

        try (JsonParser p = STRICT_MAPPER.createParser(doc)) {
            try {
                p.nextToken();
                p.getBigIntegerValue();
                fail("Should not pass: number length should exceed limit");
            } catch (StreamConstraintsException e) {
                _verifyException(e, "Number value length");
            }
        }
    }

    @Test
    public void testBigDecimalLargeTripsLimit() throws Exception
    {
        // unscaled magnitude ~62 digits → ~26 bytes, above limit of 20
        BigDecimal big = new BigDecimal(
                "123456789012345678901234567890123456789012345678901234567890.12");
        byte[] doc = DEFAULT_MAPPER.writeValueAsBytes(big);

        try (JsonParser p = STRICT_MAPPER.createParser(doc)) {
            try {
                p.nextToken();
                p.getDecimalValue();
                fail("Should not pass: number length should exceed limit");
            } catch (StreamConstraintsException e) {
                _verifyException(e, "Number value length");
            }
        }
    }

    @Test
    public void testBigIntegerSmallPasses() throws Exception
    {
        BigInteger small = new BigInteger("42");
        byte[] doc = DEFAULT_MAPPER.writeValueAsBytes(small);

        try (JsonParser p = STRICT_MAPPER.createParser(doc)) {
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertNotNull(p.getBigIntegerValue());
        }
    }

    @Test
    public void testBigDecimalSmallPasses() throws Exception
    {
        BigDecimal small = new BigDecimal("42.20");
        byte[] doc = DEFAULT_MAPPER.writeValueAsBytes(small);

        try (JsonParser p = STRICT_MAPPER.createParser(doc)) {
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(small, p.getDecimalValue());
        }
    }

    @Test
    public void testDefaultLimitAllowsTypicalDecimals() throws Exception
    {
        BigDecimal v = new BigDecimal("12345678901234567890.42");
        byte[] doc = DEFAULT_MAPPER.writeValueAsBytes(v);

        try (JsonParser p = DEFAULT_MAPPER.createParser(doc)) {
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(v, p.getDecimalValue());
        }
    }

    private void _verifyException(Throwable e, String match) {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        if (!lmsg.contains(match.toLowerCase())) {
            fail("Expected exception with substring (" + match + "); got: \"" + msg + "\"");
        }
    }
}
