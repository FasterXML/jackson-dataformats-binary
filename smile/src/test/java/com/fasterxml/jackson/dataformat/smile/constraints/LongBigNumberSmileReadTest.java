package com.fasterxml.jackson.dataformat.smile.constraints;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.async.AsyncReaderWrapper;
import com.fasterxml.jackson.dataformat.smile.async.AsyncTestBase;

import static org.junit.jupiter.api.Assertions.fail;

// Declared length of BigInteger/BigDecimal values should be validated against
// `maxNumberLength` before content is read (and buffered), not after
public class LongBigNumberSmileReadTest extends AsyncTestBase
{
    private final static byte[] HEADER = new byte[] {
            SmileConstants.HEADER_BYTE_1, SmileConstants.HEADER_BYTE_2,
            SmileConstants.HEADER_BYTE_3, 0
    };

    // 8_000_000 as unsigned VInt: way above default `maxNumberLength` (1000)
    private final static byte[] LONG_LENGTH = new byte[] { 0x07, 0x50, 0x48, (byte) 0x80 };

    // Just a bit of content: nowhere near declared length
    private final static byte[] SHORT_CONTENT = new byte[20];

    private final SmileFactory F = new SmileFactory();

    @Test
    public void testLongBigInteger() throws Exception
    {
        _verifyEarlyFailure(concat(HEADER,
                new byte[] { SmileConstants.TOKEN_PREFIX_INTEGER + 2 },
                LONG_LENGTH, SHORT_CONTENT), JsonToken.VALUE_NUMBER_INT);
    }

    @Test
    public void testLongBigDecimal() throws Exception
    {
        _verifyEarlyFailure(concat(HEADER,
                new byte[] { SmileConstants.TOKEN_PREFIX_FP + 2, (byte) 0x80 }, // scale 0
                LONG_LENGTH, SHORT_CONTENT), JsonToken.VALUE_NUMBER_FLOAT);
    }

    private void _verifyEarlyFailure(byte[] doc, JsonToken expToken) throws Exception
    {
        // Blocking: decoded lazily, on access
        try (JsonParser p = F.createParser(doc)) {
            assertToken(expToken, p.nextToken());
            p.getNumberValue();
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Number value length (8000000) exceeds the maximum allowed (1000");
        }
        // Non-blocking: both all at once and byte-by-byte
        for (int bytesPerRead : new int[] { doc.length, 1 }) {
            AsyncReaderWrapper r = asyncForBytes(F, bytesPerRead, doc, 0);
            try {
                r.nextToken();
                fail("Should not pass");
            } catch (StreamConstraintsException e) {
                verifyException(e, "Number value length (8000000) exceeds the maximum allowed (1000");
            } finally {
                r.close();
            }
        }
    }
}
