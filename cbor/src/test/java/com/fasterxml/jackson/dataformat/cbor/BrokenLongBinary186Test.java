package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;

import com.fasterxml.jackson.databind.ObjectMapper;

// Mostly for [dataformats-binary#186]: corrupt encoding indicating humongous payload
public class BrokenLongBinary186Test extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    /*
    /**********************************************************************
    /* First regular, read-it-all access, from non-chunked
    /**********************************************************************
     */

    // [dataformats-binary#186]
    public void testCorruptVeryLongBinary() throws Exception {
        // Let's do about 2 GB to likely trigger failure
        _testCorruptLong(1_999_999_999, 95000);
    }

    // [dataformats-binary#186]
    public void testCorruptQuiteLongBinary() throws Exception {
        // Value below limit for chunked handling
        _testCorruptLong(CBORParser.LONGEST_NON_CHUNKED_BINARY >> 1, 37);
    }

    private void _testCorruptLong(int allegedLength, int actualIncluded) throws Exception
    {
        JsonParser p = MAPPER.createParser(_createBrokenDoc(allegedLength, actualIncluded));
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        try {
            p.getBinaryValue();
            fail("Should fail");
        } catch (JsonProcessingException e) {
            verifyException(e, "Unexpected end-of-input for Binary value");
            verifyException(e, "expected "+allegedLength+" bytes, only found "+actualIncluded);
        }
    }

    /*
    /**********************************************************************
    /* And then "streaming" access
    /**********************************************************************
     */

    // [dataformats-binary#186]
    public void testQuiteLongStreaming() throws Exception
    {
        // Can try bit shorter here, like 500 megs
        final int allegedLength = 500_000_000;
        
        JsonParser p = MAPPER.createParser(_createBrokenDoc(allegedLength, 72000));
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            p.readBinaryValue(bytes);
            fail("Should fail");
        } catch (JsonProcessingException e) {
            verifyException(e, "Unexpected end-of-input for Binary value");
            verifyException(e, "expected "+allegedLength+" bytes, only found 72000");
        }
    }

    private byte[] _createBrokenDoc(int allegedLength, int actualIncluded) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        if (allegedLength > 0xFFFF) {
            bytes.write((byte) (CBORConstants.PREFIX_TYPE_BYTES | CBORConstants.SUFFIX_UINT32_ELEMENTS));
            bytes.write((byte) (allegedLength >> 24));
            bytes.write((byte) (allegedLength >> 16));
            bytes.write((byte) (allegedLength >> 8));
            bytes.write((byte) allegedLength);
        } else { // assume shorter
            bytes.write((byte) (CBORConstants.PREFIX_TYPE_BYTES | CBORConstants.SUFFIX_UINT16_ELEMENTS));
            bytes.write((byte) (allegedLength >> 8));
            bytes.write((byte) allegedLength);
        }
        // but only include couple of bytes
        for (int i = 0; i < actualIncluded; ++i) {
            bytes.write((byte) i);
        }
        return bytes.toByteArray();
    }

}
