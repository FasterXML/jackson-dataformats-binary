package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.smile.*;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;

public class LenientUnicodeSmileGenerationTest extends BaseTestForSmile
{
    private final SmileMapper MAPPER = smileMapper();

    private final ObjectWriter LENIENT_WRITER = MAPPER.writer()
            .with(SmileGenerator.Feature.LENIENT_UTF_ENCODING);

    /**
     * Test that encoding a String containing invalid surrogates fail with an exception
     */
    public void testFailForInvalidSurrogate() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (JsonGenerator gen = MAPPER.createGenerator(out)) {
            assertEquals(0, gen.getOutputBuffered());
            try {
                // Invalid first surrogate character
                gen.writeString("x\ud83d");
                fail("Should not pass");
            } catch (StreamWriteException e) {
                verifyException(e, "Unmatched surrogate pair");
                verifyException(e, "0xD83D");
                verifyException(e, "without low surrogate");
            }
            assertEquals(1, gen.getOutputBuffered());
        }

        try (JsonGenerator gen = MAPPER.createGenerator(out)) {
            try {
                // Missing second surrogate character
                gen.writeString("x\ude01");
                fail("Should not pass");
            } catch (StreamWriteException e) {
                verifyException(e, "Invalid surrogate pair");
                verifyException(e, "0xDE01");
                verifyException(e, "invalid high surrogate");
            }
            assertEquals(1, gen.getOutputBuffered());
        }

        try (JsonGenerator gen = MAPPER.createGenerator(out)) {
            try {
                // Invalid second surrogate character (1)
                gen.writeString("x\ud801\ud802");
                fail("Should not pass");
            } catch (StreamWriteException e) {
                verifyException(e, "Invalid surrogate pair");
                verifyException(e, "0xD801");
                verifyException(e, "0xD802");
                verifyException(e, "valid high surrogate");
                verifyException(e, "invalid low surrogate");
            }
            assertEquals(1, gen.getOutputBuffered());
        }

        try (JsonGenerator gen = MAPPER.createGenerator(out)) {
            try {
                gen.writeString("x\ud83dx");
                fail("Should not pass");
            } catch (StreamWriteException e) {
                verifyException(e, "Invalid surrogate pair");
                verifyException(e, "0xD83D");
                verifyException(e, "0x0078");
                verifyException(e, "valid high surrogate");
                verifyException(e, "invalid low surrogate");
            }
            assertEquals(1, gen.getOutputBuffered());
        }
    }

    /**
     * Test that when the lenient unicode feature is enabled, the replacement character is used to fix invalid sequences
     */
    public void testRecoverInvalidSurrogate1() throws Exception
    {
        // Unmatched first surrogate character
        _writeAndVerifyLenientString("x\ud83d", "x\ufffd");

        // Unmatched second surrogate character
        _writeAndVerifyLenientString("x\ude01", "x\ufffd");

        // Unmatched second surrogate character (2)
        _writeAndVerifyLenientString("x\ude01x", "x\ufffdx");
    }

    public void testRecoverInvalidSurrogate2() throws Exception
    {
        _writeAndVerifyLenientString("X\ud83dY", "X\ufffdY");
    }

    private void _writeAndVerifyLenientString(String inputText, String expText) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = LENIENT_WRITER.createGenerator(bytes)) {
            g.writeString(inputText);
        }
        try (JsonParser p = MAPPER.createParser(bytes.toByteArray())) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(expText, p.getText());
            assertNull(p.nextToken());
        }
    }
}
