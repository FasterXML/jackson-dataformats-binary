package com.fasterxml.jackson.dataformat.cbor.parse;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

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
}
