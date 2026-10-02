package tools.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.dataformat.smile.*;
import tools.jackson.dataformat.smile.async.AsyncReaderWrapper;
import tools.jackson.dataformat.smile.async.AsyncTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#834]: `BigDecimal` scales outside of range that can be
// encoded (zigzag value must fit in 31 bits) must fail, not be silently truncated
public class GeneratorBigDecimalScaleTest
    extends AsyncTestBase
{
    private final static int MIN_SCALE = -(1 << 30);
    private final static int MAX_SCALE = (1 << 30) - 1;

    private final SmileMapper F = new SmileMapper();

    @Test
    public void testScalesWithinRange() throws Exception
    {
        for (int scale : new int[] { 0, 1, -1, 1000, -1000,
                MAX_SCALE - 1, MAX_SCALE, MIN_SCALE + 1, MIN_SCALE }) {
            final BigDecimal value = new BigDecimal(BigInteger.ONE, scale);
            final byte[] doc = _write(value);
            assertEquals(value, _readBlocking(doc), "Blocking, scale "+scale);
            // Non-blocking: both all content at once and byte-by-byte (split scale VInt)
            for (int bytesPerFeed : new int[] { 1, 2, 3, doc.length }) {
                assertEquals(value, _readNonBlocking(doc, bytesPerFeed),
                        "Non-blocking ("+bytesPerFeed+" bytes per feed), scale "+scale);
            }
        }
    }

    @Test
    public void testScalesOutOfRange() throws Exception
    {
        for (int scale : new int[] { MAX_SCALE + 1, MIN_SCALE - 1,
                Integer.MAX_VALUE, Integer.MIN_VALUE }) {
            final BigDecimal value = new BigDecimal(BigInteger.ONE, scale);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (SmileGenerator g = _smileGenerator(out, true)) {
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
        try (SmileGenerator g = _smileGenerator(out, true)) {
            g.writeNumber("1E-1073741824");
            fail("Should not pass");
        } catch (StreamWriteException e) {
            verifyException(e, "Cannot write `BigDecimal` with scale 1073741824");
        }
    }

    private byte[] _write(BigDecimal value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SmileGenerator g = _smileGenerator(out, true)) {
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

    private BigDecimal _readNonBlocking(byte[] doc, int bytesPerFeed) throws Exception
    {
        AsyncReaderWrapper r = asyncForBytes(F, bytesPerFeed, doc, 0);
        try {
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, r.nextToken());
            BigDecimal result = r.getBigDecimalValue();
            assertNull(r.nextToken());
            return result;
        } finally {
            r.close();
        }
    }
}
