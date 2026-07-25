package com.fasterxml.jackson.dataformat.cbor.parse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.fail;

public class ParseIncompleteArray240Test extends CBORTestBase
{
    private final CBORFactory F = cborFactory();

    // [dataformats-binary#240]
    @Test
    public void testIncompleteFixedSizeArray() throws Exception
    {
        final byte[] input = {  (byte) 0x84 };
        try (CBORParser p = cborParser(F, input)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            try {
                p.nextToken();
                fail("Should NOT pass");
            } catch (StreamReadException e) {
                verifyException(e, "Unexpected end-of-input in Array value: expected 4 more");
            }
        }
    }

    @Test
    public void testIncompleteMarkerBasedArray() throws Exception
    {
        final byte[] input = {  (byte) 0x9F };
        try (CBORParser p = cborParser(F, input)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            try {
                p.nextToken();
                fail("Should NOT pass");
            } catch (StreamReadException e) {
                verifyException(e, "Unexpected end-of-input in Array value: expected an element or ");
            }
        }
    }

    // And might as well do the same for Objects too
    @Test
    public void testIncompleteFixedSizeObject() throws Exception
    {
        final byte[] input = {  (byte) 0xA3 };
        try (CBORParser p = cborParser(F, input)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            try {
                p.nextToken();
                fail("Should NOT pass");
            } catch (StreamReadException e) {
                verifyException(e, "Unexpected end-of-input in Object value: expected 3 more");
            }
        }
    }

    @Test
    public void testIncompleteMarkerBasedObject() throws Exception
    {
        final byte[] input = {  (byte) 0xBF };
        try (CBORParser p = cborParser(F, input)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            try {
                p.nextToken();
                fail("Should NOT pass");
            } catch (StreamReadException e) {
                verifyException(e,
                        "Unexpected end-of-input in Object value: expected a property or close marker");
            }
        }
    }
}
