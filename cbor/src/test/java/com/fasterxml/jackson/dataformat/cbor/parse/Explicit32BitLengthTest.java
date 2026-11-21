package com.fasterxml.jackson.dataformat.cbor.parse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.*;

// 32-bit (additional info 26) lengths are unsigned: values above
// `Integer.MAX_VALUE` must be rejected (as with 64-bit lengths), not
// wrap to negative and get handled as indefinite length.
public class Explicit32BitLengthTest extends CBORTestBase
{
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
                "4294967295", p -> p.nextToken());
    }

    @Test
    public void testMapLengthTooBig() throws Exception
    {
        _verifyFails(bytes(0xBA, 0x80, 0, 0, 0, 0x61, 'k', 0x01, 0xFF),
                "2147483648", p -> p.nextToken());
    }

    @Test
    public void testTextLengthTooBig() throws Exception
    {
        // Was read as chunked text "abc"
        final byte[] doc = bytes(0x7A, 0xFF, 0xFF, 0xFF, 0xFF, 0x63, 'a', 'b', 'c', 0xFF);
        _verifyFails(doc, "4294967295", p -> {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getText();
        });
        _verifyFails(doc, "4294967295", p -> p.nextTextValue());
    }

    @Test
    public void testBinaryLengthTooBig() throws Exception
    {
        _verifyFails(bytes(0x5A, 0x80, 0, 0, 0, 0x43, 'x', 'y', 'z', 0xFF),
                "2147483648", p -> {
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            p.getBinaryValue();
        });
    }

    @Test
    public void testNameLengthTooBig() throws Exception
    {
        final byte[] textName = bytes(0xA1, 0x7A, 0xFF, 0xFF, 0xFF, 0xFF, 0x61, 'n', 0xFF, 0x01);
        _verifyFails(textName, "4294967295", p -> {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            p.nextToken();
        });
        _verifyFails(textName, "4294967295", p -> {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            p.nextFieldName();
        });
        _verifyFails(bytes(0xA1, 0x5A, 0xFF, 0xFF, 0xFF, 0xFF, 0x41, 'n', 0xFF, 0x01),
                "4294967295", p -> {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            p.nextToken();
        });
    }

    interface ParserAction {
        void run(CBORParser p) throws Exception;
    }

    private void _verifyFails(byte[] doc, String expLength, ParserAction action) throws Exception
    {
        try (CBORParser p = cborParser(F, doc)) {
            action.run(p);
            fail("Should not pass: token "+p.currentToken());
        } catch (StreamReadException e) {
            verifyException(e, "Illegal length");
            verifyException(e, ": "+expLength);
        }
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; ++i) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
