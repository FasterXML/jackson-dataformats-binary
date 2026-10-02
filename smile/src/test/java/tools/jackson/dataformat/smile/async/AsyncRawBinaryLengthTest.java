package tools.jackson.dataformat.smile.async;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.dataformat.smile.SmileConstants;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;

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

    // 5-byte VInts that do not fit in 32 bits: one wraps to -1, the other
    // to 2^32 + 3 truncated to 3
    private final static byte[] OVERFLOW_NEGATIVE = { 0x7F, 0x7F, 0x7F, 0x7F, (byte) 0xBF };
    private final static byte[] OVERFLOW_POSITIVE = { 0x20, 0x00, 0x00, 0x00, (byte) 0x83 };
    // 5-byte VInt that does not fit in 31 bits, but does in 32 (2^31)
    private final static byte[] OVERFLOW_31_BITS = { 0x10, 0x00, 0x00, 0x00, (byte) 0x80 };
    // 5th byte does not have end marker
    private final static byte[] UNTERMINATED = { 0x0F, 0x7F, 0x7F, 0x7F, 0x7F };

    private final SmileMapper F = new SmileMapper();

    /*
    /**********************************************************************
    /* Test methods, declared length handling
    /**********************************************************************
     */

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

    // Length and content fed separately (content possibly split too)
    @Test
    public void testLengthAndContentFedSeparately() throws Exception
    {
        final byte[] content = new byte[1000];
        for (int i = 0; i < content.length; ++i) {
            content[i] = (byte) i;
        }
        // 1000 as VInt: 15 x 64 + 40
        final byte[] prefix = concat(HEADER, RAW_BINARY, new byte[] { 15, (byte) (0x80 | 40) });
        final byte[] doc = concat(prefix, content);
        for (int split : new int[] { 0, 1, 999 }) {
            try (JsonParser p = F.createNonBlockingByteArrayParser()) {
                ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
                feeder.feedInput(doc, 0, prefix.length);
                assertEquals(JsonToken.NOT_AVAILABLE, p.nextToken());
                if (split > 0) {
                    feeder.feedInput(doc, prefix.length, prefix.length + split);
                    assertEquals(JsonToken.NOT_AVAILABLE, p.nextToken());
                }
                feeder.feedInput(doc, prefix.length + split, doc.length);
                assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
                assertArrayEquals(content, p.getBinaryValue());
            }
        }
    }

    // Should not be able to feed more content after close (which releases buffers)
    @Test
    public void testFeedAfterClose() throws Exception
    {
        byte[] doc = concat(HEADER, RAW_BINARY, MAX_LENGTH, new byte[100]);
        JsonParser p = F.createNonBlockingByteArrayParser();
        ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
        feeder.feedInput(doc, 0, 50);
        assertEquals(JsonToken.NOT_AVAILABLE, p.nextToken());
        p.close();
        assertFalse(feeder.needMoreInput());
        try {
            feeder.feedInput(doc, 50, doc.length);
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Parser closed, can not feed more input");
        }
    }

    /*
    /**********************************************************************
    /* Test methods, invalid lengths
    /**********************************************************************
     */

    @Test
    public void testInvalidRawBinaryLength() throws Exception
    {
        _verifyInvalidLength(RAW_BINARY, JsonToken.VALUE_EMBEDDED_OBJECT);
    }

    @Test
    public void testInvalid7BitBinaryLength() throws Exception
    {
        _verifyInvalidLength(BINARY_7BIT, JsonToken.VALUE_EMBEDDED_OBJECT);
    }

    @Test
    public void testInvalidBigIntegerLength() throws Exception
    {
        _verifyInvalidLength(BIG_INTEGER, JsonToken.VALUE_NUMBER_INT);
    }

    @Test
    public void testInvalidBigDecimalLength() throws Exception
    {
        // valid scale (0), invalid length
        _verifyInvalidLength(concat(BIG_DECIMAL, new byte[] { (byte) 0x80 }),
                JsonToken.VALUE_NUMBER_FLOAT);
    }

    /*
    /**********************************************************************
    /* Test methods, BigDecimal scale (zigzag-encoded, 31-bit as per blocking parser)
    /**********************************************************************
     */

    @Test
    public void testInvalidBigDecimalScale() throws Exception
    {
        _verifyInvalidLength(BIG_DECIMAL, JsonToken.VALUE_NUMBER_FLOAT);
    }

    @Test
    public void testExtremeBigDecimalScales() throws Exception
    {
        // zigzag-encoded 2^31-1 -> -2^30
        _verifyBigDecimalScale(MAX_LENGTH, -(1 << 30));
        // and 2^31-2 -> 2^30-1
        _verifyBigDecimalScale(new byte[] { 0x0F, 0x7F, 0x7F, 0x7F, (byte) 0xBE },
                (1 << 30) - 1);
        // but 2^31 not accepted by blocking parser either
        final byte[] doc = concat(HEADER, BIG_DECIMAL, OVERFLOW_31_BITS,
                new byte[] { (byte) 0x81, 0x00, 0x01 });
        try (JsonParser p = F.createParser(doc)) {
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            p.getDecimalValue();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Overflow in VInt");
            verifyException(e, "1st byte (0x10)");
        }
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private void _verifyInvalidLength(byte[] prefix, JsonToken valueType) throws Exception
    {
        _verifyInvalidVInt(prefix, OVERFLOW_NEGATIVE, valueType, "1st byte (0x7F)");
        _verifyInvalidVInt(prefix, OVERFLOW_POSITIVE, valueType, "1st byte (0x20)");
        _verifyInvalidVInt(prefix, OVERFLOW_31_BITS, valueType, "1st byte (0x10)");
        _verifyInvalidVInt(prefix, UNTERMINATED, valueType, "5th byte (0x7F)");
    }

    // Verifies that failure is the same whether fed all at once or byte-by-byte
    private void _verifyInvalidVInt(byte[] prefix, byte[] vint, JsonToken valueType,
            String expMsg) throws Exception
    {
        final byte[] doc = concat(HEADER, prefix, vint);
        for (int chunk : new int[] { doc.length, 1 }) {
            try (JsonParser p = F.createNonBlockingByteArrayParser()) {
                _feedAll(p, doc, chunk);
                fail("Should not pass");
            } catch (StreamReadException e) {
                verifyException(e, "Overflow in VInt (current token "+valueType+")");
                verifyException(e, expMsg);
            }
        }
    }

    private void _verifyNotAvailable(byte[] doc, int chunk) throws Exception
    {
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            _feedAll(p, doc, chunk);
        }
    }

    // Feeds all of content, verifying no token gets completed
    private void _feedAll(JsonParser p, byte[] doc, int chunk) throws Exception
    {
        ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
        for (int offset = 0; offset < doc.length; offset += chunk) {
            feeder.feedInput(doc, offset, Math.min(doc.length, offset + chunk));
            assertEquals(JsonToken.NOT_AVAILABLE, p.nextToken());
        }
    }

    private void _verifyBigDecimalScale(byte[] scaleVInt, int expScale) throws Exception
    {
        // unscaled value 1: length 1, 7-bit encoded as 2 bytes
        final byte[] doc = concat(HEADER, BIG_DECIMAL, scaleVInt,
                new byte[] { (byte) 0x81, 0x00, 0x01 });
        final BigDecimal exp = new BigDecimal(BigInteger.ONE, expScale);
        for (int chunk : new int[] { doc.length, 1 }) {
            AsyncReaderWrapper r = asyncForBytes(F, chunk, doc, 0);
            try {
                assertToken(JsonToken.VALUE_NUMBER_FLOAT, r.nextToken());
                assertEquals(exp, r.getBigDecimalValue());
            } finally {
                r.close();
            }
        }
        // and same with blocking parser
        try (JsonParser p = F.createParser(doc)) {
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals(exp, p.getDecimalValue());
        }
    }
}
