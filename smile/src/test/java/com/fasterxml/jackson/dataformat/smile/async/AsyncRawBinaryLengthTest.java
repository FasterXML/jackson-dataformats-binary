package com.fasterxml.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

// Checks handling of declared length of raw binary values by the
// non-blocking parser: validated, and not used for allocation up front
public class AsyncRawBinaryLengthTest extends AsyncTestBase
{
    private final SmileFactory F = new SmileFactory();

    // Declared length (~2 GB) far exceeds content fed so far: should just
    // wait for more content
    public void testLongDeclaredLengthWithShortContent() throws Exception
    {
        // 2_000_000_000 as unsigned VInt: 4 x 7 bits, then last 6 bits
        final int len = 2_000_000_000;
        byte[] doc = _rawBinaryDoc(new byte[] {
                (byte) ((len >>> 27) & 0x7F),
                (byte) ((len >>> 20) & 0x7F),
                (byte) ((len >>> 13) & 0x7F),
                (byte) ((len >>> 6) & 0x7F),
                (byte) (0x80 | (len & 0x3F))
        }, 100);
        // both with all content at once, and byte-by-byte (split length)
        _verifyNotAvailable(doc, doc.length);
        _verifyNotAvailable(doc, 1);
    }

    // 5-byte VInt whose value does not fit in 31 bits
    public void testInvalidDeclaredLength() throws Exception
    {
        byte[] doc = _rawBinaryDoc(new byte[] {
                0x7F, 0x7F, 0x7F, 0x7F, (byte) 0xBF
        }, 10);
        _verifyInvalidLength(doc, doc.length);
        _verifyInvalidLength(doc, 1);
    }

    private void _verifyNotAvailable(byte[] doc, int chunk) throws Exception
    {
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            assertEquals(JsonToken.NOT_AVAILABLE, _feedAll(p, doc, chunk));
        }
    }

    private void _verifyInvalidLength(byte[] doc, int chunk) throws Exception
    {
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            _feedAll(p, doc, chunk);
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "invalid length for raw binary value");
        }
    }

    // Feeds all of content, returns last token returned
    private JsonToken _feedAll(JsonParser p, byte[] doc, int chunk) throws Exception
    {
        ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
        JsonToken t = null;
        for (int offset = 0; offset < doc.length; offset += chunk) {
            feeder.feedInput(doc, offset, Math.min(doc.length, offset + chunk));
            while ((t = p.nextToken()) != JsonToken.NOT_AVAILABLE) {
                assertFalse("Should not get complete value", t == JsonToken.VALUE_EMBEDDED_OBJECT);
            }
        }
        return t;
    }

    private byte[] _rawBinaryDoc(byte[] vint, int contentLen)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(SmileConstants.HEADER_BYTE_1);
        bytes.write(SmileConstants.HEADER_BYTE_2);
        bytes.write(SmileConstants.HEADER_BYTE_3);
        bytes.write(SmileConstants.HEADER_BIT_HAS_RAW_BINARY);
        bytes.write(SmileConstants.TOKEN_MISC_BINARY_RAW);
        bytes.write(vint, 0, vint.length);
        bytes.write(new byte[contentLen], 0, contentLen);
        return bytes.toByteArray();
    }
}
