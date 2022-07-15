package tools.jackson.dataformat.cbor.parse;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.dataformat.cbor.CBORParser;
import tools.jackson.dataformat.cbor.CBORTestBase;

public class ParseInvalidUTF8String236Test extends CBORTestBase
{
    // [dataformats-binary#236]: Original version; broken UTF-8 all around.
    // but gets hit by end-of-input only (since content not validated)
    public void testShortString236Original() throws Exception
    {
        final byte[] input = {0x66, (byte) 0xef, 0x7d, 0x7d, 0xa, 0x2d, (byte) 0xda};
        try (CBORParser p = cborParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                String str = p.getText();
                fail("Should have failed, did not, String = '"+str+"'");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid UTF-8 middle byte 0x7d");
            }
        }
    }

    // Variant where the length would be valid, but the last byte is partial UTF-8
    // code point
    public void testShortString236EndsWithPartialUTF8() throws Exception
    {
        final byte[] input = {0x63, 0x41, 0x2d, (byte) 0xda};
        try (CBORParser p = cborParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                String str = p.getText();
                fail("Should have failed, did not, String = '"+str+"'");
            } catch (StreamReadException e) {
                verifyException(e, "Malformed UTF-8 character at the end of");
            }
        }
    }

    // Variant where the length itself exceeds buffer
    public void testShortString236TruncatedString() throws Exception
    {
        // String with length of 6 bytes claimed; only 5 provided
        final byte[] input = {0x63, 0x41, 0x2d };
        try (CBORParser p = cborParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                String str = p.getText();
                fail("Should have failed, did not, String = '"+str+"'");
            } catch (StreamReadException e) {
                verifyException(e, "Unexpected end-of-input in VALUE_STRING");
            }
        }
    }

    public void testShortString237InvalidTextValue() throws Exception
    {
        // String with length of 2 bytes, but a few null bytes as fillers to
        // avoid buffer boundary
        // (2nd byte implies 2-byte sequence but 3rd byte does not have high-bit set)
        byte[] input2 = {0x62, (byte) 0xCF, 0x2d,
                0, 0, 0, 0, 0, 0};
        try (CBORParser p = cborParser(input2)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                String str = p.getText();
                fail("Should have failed, did not, String = '"+str+"'");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid UTF-8 middle byte 0x2d");
            }
        }

        // but let's also validate 3-byte variant as well
        byte[] input3 = {0x63, (byte) 0xEF, (byte) 0x8e, 0x2d,
                0, 0, 0, 0, 0, 0};
        try (CBORParser p = cborParser(input3)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            try {
                String str = p.getText();
                fail("Should have failed, did not, String = '"+str+"'");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid UTF-8 middle byte 0x2d");
            }
        }
    }

    public void testShortString237InvalidName() throws Exception
    {
        // Object with 2-byte invalid name
        byte[] input2 = { (byte) 0xBF, // Object, indefinite length
                0x62, (byte) 0xCF, 0x2e, // 2-byte name but invalid second byte
                0x21, // int value of 33
                (byte) 0xFF, // Object END marker
                0, 0, 0, 0 // padding
        };
        try (CBORParser p = cborParser(input2)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            try {
                p.nextToken();
                String str = p.getText();
                fail("Should have failed, did not, String = '"+str+"'");
            } catch (StreamReadException e) {
                verifyException(e, "Invalid UTF-8 middle byte 0x2e");
            }
        }

        // but let's also validate 3-byte variant as well
        byte[] input3 = { (byte) 0xBF, // Object, indefinite length
                0x62, (byte) 0xEF, (byte) 0x8e, 0x2f, // 3-byte name but invalid third byte
                0x22, // int value of 34
                (byte) 0xFF, // Object END marker
                0, 0, 0, 0 // padding
        };
        try (CBORParser p = cborParser(input3)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            try {
                p.nextToken();
                String str = p.getText();
                fail("Should have failed, did not, String = '"+str+"'");
            } catch (StreamReadException e) {
                verifyException(e, "Truncated UTF-8 character in Map key (2 bytes)");
            }
        }
    }
}
