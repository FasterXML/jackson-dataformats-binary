package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#834]: `BigDecimal` scales outside of range that can be
// encoded (zigzag value must fit in 31 bits) must fail, not be silently truncated
public class GeneratorBigDecimalScaleTest
    extends BaseTestForSmile
{
    private final static int MIN_SCALE = -(1 << 30);
    private final static int MAX_SCALE = (1 << 30) - 1;

    private final SmileFactory F = new SmileFactory();

    @Test
    public void testScalesWithinRange() throws Exception
    {
        for (int scale : new int[] { 0, 1, -1, 1000, -1000,
                MAX_SCALE - 1, MAX_SCALE, MIN_SCALE + 1, MIN_SCALE }) {
            final BigDecimal value = new BigDecimal(BigInteger.ONE, scale);
            final byte[] doc = _write(value);
            assertEquals(value, _readBlocking(doc), "Blocking, scale "+scale);
            assertEquals(value, _readNonBlocking(doc), "Non-blocking, scale "+scale);
        }
    }

    @Test
    public void testScalesOutOfRange() throws Exception
    {
        for (int scale : new int[] { MAX_SCALE + 1, MIN_SCALE - 1,
                Integer.MAX_VALUE, Integer.MIN_VALUE }) {
            final BigDecimal value = new BigDecimal(BigInteger.ONE, scale);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (SmileGenerator g = smileGenerator(F, out, true)) {
                g.writeNumber(value);
                fail("Should not pass, scale "+scale);
            } catch (StreamWriteException e) {
                verifyException(e, "Cannot write `BigDecimal` with scale "+scale);
            }
        }
    }

    // Also via `writeNumber(String)` which delegates to `writeNumber(BigDecimal)`
    @Test
    public void testScaleOutOfRangeFromString() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(F, out, true)) {
            g.writeNumber("1E-1073741824");
            fail("Should not pass");
        } catch (StreamWriteException e) {
            verifyException(e, "Cannot write `BigDecimal` with scale 1073741824");
        }
    }

    private byte[] _write(BigDecimal value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SmileGenerator g = smileGenerator(F, out, true)) {
            g.writeNumber(value);
        }
        return out.toByteArray();
    }

    private BigDecimal _readBlocking(byte[] doc) throws Exception
    {
        try (JsonParser p = _smileParser(doc)) {
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            BigDecimal result = p.getDecimalValue();
            assertNull(p.nextToken());
            return result;
        }
    }

    private BigDecimal _readNonBlocking(byte[] doc) throws Exception
    {
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
            feeder.feedInput(doc, 0, doc.length);
            feeder.endOfInput();
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            BigDecimal result = p.getDecimalValue();
            assertNull(p.nextToken());
            return result;
        }
    }
}
