package com.fasterxml.jackson.dataformat.smile.async;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.*;

// Checks handling of declared lengths of binary (and big number) values by the
// non-blocking parser: validated, and not used for allocation up front
public class AsyncRawBinaryLengthTest extends AsyncTestBase
{
    private final static byte[] HEADER = new byte[] {
            SmileConstants.HEADER_BYTE_1, SmileConstants.HEADER_BYTE_2,
            SmileConstants.HEADER_BYTE_3, SmileConstants.HEADER_BIT_HAS_RAW_BINARY
    };

    private final static byte[] RAW_BINARY = { SmileConstants.TOKEN_MISC_BINARY_RAW };
    private final static byte[] BINARY_7BIT = { SmileConstants.TOKEN_MISC_BINARY_7BIT };
    private final static byte[] BIG_INTEGER = { SmileConstants.TOKEN_PREFIX_INTEGER + 2 };
    private final static byte[] BIG_DECIMAL = { SmileConstants.TOKEN_PREFIX_FP + 2 };

    // Integer.MAX_VALUE as unsigned VInt: no heap could allocate that up front
    private final static byte[] MAX_LENGTH = { 0x0F, 0x7F, 0x7F, 0x7F, (byte) 0xBF };

    // 5-byte VInts that do not fit in 31 bits: one wraps to -1, the other
    // to 2^32 + 3 truncated to 3
    private final static byte[] OVERFLOW_NEGATIVE = { 0x7F, 0x7F, 0x7F, 0x7F, (byte) 0xBF };
    private final static byte[] OVERFLOW_POSITIVE = { 0x20, 0x00, 0x00, 0x00, (byte) 0x83 };

    private final SmileFactory F = new SmileFactory();

    // Declared length far exceeds content fed so far: should just wait for
    // more content, not try to allocate full length
    @Test
    public void testLongDeclaredLengthWithShortContent() throws Exception
    {
        byte[] doc = concat(HEADER, RAW_BINARY, MAX_LENGTH, new byte[100]);
        // both with all content at once, and byte-by-byte (split length)
        _verifyNotAvailable(doc, doc.length);
        _verifyNotAvailable(doc, 1);
    }

    @Test
    public void testInvalidRawBinaryLength() throws Exception
    {
        _verifyOverflow(RAW_BINARY, "abc".getBytes("UTF-8"));
    }

    @Test
    public void testInvalid7BitBinaryLength() throws Exception
    {
        _verifyOverflow(BINARY_7BIT, new byte[] { 0x01, 0x02 });
    }

    @Test
    public void testInvalidBigIntegerLength() throws Exception
    {
        _verifyOverflow(BIG_INTEGER, new byte[] { 0x01, 0x02 });
    }

    @Test
    public void testInvalidBigDecimalScaleAndLength() throws Exception
    {
        // invalid scale
        _verifyOverflow(BIG_DECIMAL, new byte[] { (byte) 0x81, 0x01 });
        // valid scale (0), invalid length
        _verifyOverflow(concat(BIG_DECIMAL, new byte[] { (byte) 0x80 }),
                new byte[] { 0x01, 0x02 });
    }

    private void _verifyOverflow(byte[] prefix, byte[] suffix) throws Exception
    {
        for (byte[] vint : new byte[][] { OVERFLOW_NEGATIVE, OVERFLOW_POSITIVE }) {
            // add trailing content so that all of VInt is decoded at once
            byte[] doc = concat(HEADER, prefix, vint, suffix, new byte[10]);
            _verifyInvalidLength(doc, doc.length);
            _verifyInvalidLength(doc, 1);
        }
    }

    private void _verifyNotAvailable(byte[] doc, int chunk) throws Exception
    {
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
            for (int offset = 0; offset < doc.length; offset += chunk) {
                feeder.feedInput(doc, offset, Math.min(doc.length, offset + chunk));
                assertEquals(JsonToken.NOT_AVAILABLE, p.nextToken());
            }
        }
    }

    private void _verifyInvalidLength(byte[] doc, int chunk) throws Exception
    {
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
            for (int offset = 0; offset < doc.length; offset += chunk) {
                feeder.feedInput(doc, offset, Math.min(doc.length, offset + chunk));
                JsonToken t;
                while ((t = p.nextToken()) != JsonToken.NOT_AVAILABLE) {
                    if (t == null) {
                        break;
                    }
                }
            }
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Overflow in VInt");
        }
    }
}
