package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.*;

// 32-bit (additional info 26) lengths are unsigned: values above
// `Integer.MAX_VALUE` must be rejected (as with 64-bit lengths), not
// wrap to negative and get handled as indefinite length.
// But skipping such lengths is fine. Tag values above `Integer.MAX_VALUE` are
// returned as negative ints, except for 0xFFFFFFFF that would be -1 ("no tag").
public class Explicit32BitLengthTest extends CBORTestBase
{
    private final static long LEN_2_TO_31 = 0x80000000L;

    private final CBORFactory F = cborFactory();

    @Test
    public void testValid32BitLengths() throws Exception
    {
        // [1, 2], "abc", and map {"k": 1} with 32-bit length for both map and name
        final byte[] doc = bytes(0x83,
                0x9A, 0, 0, 0, 2, 0x01, 0x02,
                0x7A, 0, 0, 0, 3, 'a', 'b', 'c',
                0xBA, 0, 0, 0, 1, 0x7A, 0, 0, 0, 1, 'k', 0x01);
        try (CBORParser p = cborParser(F, doc)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertEquals("abc", p.nextTextValue());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("k", p.nextFieldName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
        }
    }

    @Test
    public void testArrayLengthTooBig() throws Exception
    {
        // Was read as indefinite-length array [1, 2]
        _verifyFails(bytes(0x9A, 0xFF, 0xFF, 0xFF, 0xFF, 0x01, 0x02, 0xFF),
                "CBOR Array: 4294967295", p -> p.nextToken());
    }

    @Test
    public void testMapLengthTooBig() throws Exception
    {
        _verifyFails(bytes(0xBA, 0x80, 0, 0, 0, 0x61, 'k', 0x01, 0xFF),
                "CBOR Map: 2147483648", p -> p.nextToken());
    }

    @Test
    public void testTextLengthTooBig() throws Exception
    {
        // Was read as chunked text "abc"
        final byte[] doc = bytes(0x7A, 0xFF, 0xFF, 0xFF, 0xFF, 0x63, 'a', 'b', 'c', 0xFF);
        _verifyFails(doc, "CBOR Text String: 4294967295", p -> {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getText();
        });
        _verifyFails(doc, "CBOR Text String: 4294967295", p -> p.nextTextValue());
    }

    @Test
    public void testBinaryLengthTooBig() throws Exception
    {
        _verifyFails(bytes(0x5A, 0x80, 0, 0, 0, 0x43, 'x', 'y', 'z', 0xFF),
                "CBOR Byte String: 2147483648", p -> {
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            p.getBinaryValue();
        });
    }

    @Test
    public void testNameLengthTooBig() throws Exception
    {
        final byte[] textName = bytes(0xA1, 0x7A, 0xFF, 0xFF, 0xFF, 0xFF, 0x61, 'n', 0xFF, 0x01);
        _verifyFails(textName, "CBOR Text String: 4294967295", p -> {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            p.nextToken();
        });
        _verifyFails(textName, "CBOR Text String: 4294967295", p -> {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            p.nextFieldName();
        });
        _verifyFails(bytes(0xA1, 0x5A, 0xFF, 0xFF, 0xFF, 0xFF, 0x41, 'n', 0xFF, 0x01),
                "CBOR Byte String: 4294967295", p -> {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            p.nextToken();
        });
    }

    @Test
    public void testValid32BitTags() throws Exception
    {
        _verifyTag(bytes(0xDA, 0x7F, 0xFF, 0xFF, 0xFF, 0x01), Integer.MAX_VALUE);
        // Above `Integer.MAX_VALUE`: returned as negative
        _verifyTag(bytes(0xDA, 0x80, 0, 0, 0, 0x01), Integer.MIN_VALUE);
        _verifyTag(bytes(0xDA, 0xFF, 0xFF, 0xFF, 0xFE, 0x01), -2);
    }

    @Test
    public void testInvalidTags() throws Exception
    {
        // Invalid Tags 2^32-1 and 2^64-1: were read as -1 ("no tag")
        _verifyTagFails(bytes(0xDA, 0xFF, 0xFF, 0xFF, 0xFF, 0x01), "4294967295");
        _verifyTagFails(bytes(0xDB, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01),
                "18446744073709551615");
        // 64-bit Tags not fitting in `int` were (and are) rejected
        _verifyTagFails(bytes(0xDB, 0, 0, 0, 0, 0x80, 0, 0, 0, 0x01), "2147483648");
    }

    // Skipping does not need lengths to fit in `int`
    @Test
    public void testSkipTextWith32BitLength() throws Exception
    {
        // [<2^31 byte String>, 1]
        _verifySkip(bytes(0x82, 0x7A, 0x80, 0, 0, 0), bytes(0x01), JsonToken.VALUE_STRING);
    }

    @Test
    public void testSkipBinaryWith32BitLength() throws Exception
    {
        _verifySkip(bytes(0x82, 0x5A, 0x80, 0, 0, 0), bytes(0x01), JsonToken.VALUE_EMBEDDED_OBJECT);
    }

    @Test
    public void testSkipChunkWith32BitLength() throws Exception
    {
        // [<chunked String with one 2^31 byte chunk>, 1]
        _verifySkip(bytes(0x82, 0x7F, 0x7A, 0x80, 0, 0, 0), bytes(0xFF, 0x01), JsonToken.VALUE_STRING);
    }

    @Test
    public void testSkipChunkWithInvalidLength() throws Exception
    {
        // [<chunked String with chunk with reserved length indicator 28>, 1]:
        // error must report the chunk byte, not the outer one (0x7F)
        try (CBORParser p = cborParser(F, bytes(0x82, 0x7F, 0x7C, 'a', 0xFF, 0x01))) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.nextToken();
            fail("Should not pass: token "+p.currentToken());
        } catch (StreamReadException e) {
            verifyException(e, "Invalid CBOR value token (first byte): 0x7c");
        }
    }

    interface ParserAction {
        void run(CBORParser p) throws Exception;
    }

    private void _verifyFails(byte[] doc, String expMsg, ParserAction action) throws Exception
    {
        try (CBORParser p = cborParser(F, doc)) {
            action.run(p);
            fail("Should not pass: token "+p.currentToken());
        } catch (StreamReadException e) {
            verifyException(e, "Illegal length for "+expMsg);
        }
    }

    private void _verifyTag(byte[] doc, int expTag) throws Exception
    {
        try (CBORParser p = cborParser(F, doc)) {
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(expTag, p.getCurrentTag());
            assertEquals(1, p.getIntValue());
        }
    }

    private void _verifyTagFails(byte[] doc, String expTag) throws Exception
    {
        try (CBORParser p = cborParser(F, doc)) {
            p.nextToken();
            fail("Should not pass: token "+p.currentToken()+", tag "+p.getCurrentTag());
        } catch (StreamReadException e) {
            verifyException(e, "Illegal Tag value: "+expTag);
        }
    }

    // Array of a value with 2^31 bytes of content (skipped), followed by int 1
    private void _verifySkip(byte[] prefix, byte[] suffix, JsonToken expToken) throws Exception
    {
        try (CBORParser p = F.createParser(new PaddedInputStream(prefix, LEN_2_TO_31, suffix))) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(expToken, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; ++i) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    // Prefix, then given number of padding bytes, then suffix. Padding bytes
    // are not actually written into the buffer, since they are only skipped.
    static class PaddedInputStream extends InputStream
    {
        private final byte[] _prefix, _suffix;
        private int _prefixPtr, _suffixPtr;
        private long _padLeft;

        PaddedInputStream(byte[] prefix, long padding, byte[] suffix) {
            _prefix = prefix;
            _padLeft = padding;
            _suffix = suffix;
        }

        @Override
        public int read() {
            byte[] b = new byte[1];
            return (read(b, 0, 1) < 0) ? -1 : (b[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len == 0) {
                return 0;
            }
            if (_prefixPtr < _prefix.length) {
                int n = Math.min(len, _prefix.length - _prefixPtr);
                System.arraycopy(_prefix, _prefixPtr, b, off, n);
                _prefixPtr += n;
                return n;
            }
            if (_padLeft > 0L) {
                int n = (int) Math.min(len, _padLeft);
                _padLeft -= n;
                return n;
            }
            if (_suffixPtr < _suffix.length) {
                int n = Math.min(len, _suffix.length - _suffixPtr);
                System.arraycopy(_suffix, _suffixPtr, b, off, n);
                _suffixPtr += n;
                return n;
            }
            return -1;
        }
    }
}
