package tools.jackson.dataformat.cbor.gen;

import java.io.ByteArrayOutputStream;

import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.dataformat.cbor.*;

public class LenientUnicodeCBORGenerationTest extends CBORTestBase
{
    /**
     * Test that encoding a String containing invalid surrogates fail with an exception
     */
    public void testFailForInvalidSurrogate() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);

        assertEquals(0, gen.streamWriteOutputBuffered());

        // Invalid first surrogate character
        try {
            gen.writeString("x\ud83d");
            fail("Should not pass");
        } catch (StreamWriteException e) {
            verifyException(e, "Unmatched surrogate pair");
            verifyException(e, "0xD83D");
            verifyException(e, "without low surrogate");
        }
        assertEquals(0, gen.streamWriteOutputBuffered());

        // Missing second surrogate character
        try {
            gen.writeString("x\ude01");
            fail("Should not pass");
        } catch (StreamWriteException e) {
            verifyException(e, "Invalid surrogate pair");
            verifyException(e, "0xDE01");
            verifyException(e, "invalid high surrogate");
        }
        assertEquals(0, gen.streamWriteOutputBuffered());

        // Invalid second surrogate character (1)
        try {
            gen.writeString("x\ud801\ud802");
            fail("Should not pass");
        } catch (StreamWriteException e) {
            verifyException(e, "Invalid surrogate pair");
            verifyException(e, "0xD801");
            verifyException(e, "0xD802");
            verifyException(e, "valid high surrogate");
            verifyException(e, "invalid low surrogate");
        }
        assertEquals(0, gen.streamWriteOutputBuffered());

        // Invalid second surrogate character (2)
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
        assertEquals(0, gen.streamWriteOutputBuffered());
    }

    /**
     * Test that when the lenient unicode feature is enabled, the replacement character is used to fix invalid sequences
     */
    public void testRecoverInvalidSurrogate1() throws Exception
    {
        ByteArrayOutputStream out;
        CBORGenerator gen;
        byte[] b;

        out = new ByteArrayOutputStream();
        gen = lenientUnicodeCborGenerator(out);
        assertEquals(0, gen.streamWriteOutputBuffered());

        // Unmatched first surrogate character
        gen.writeString("x\ud83d");
        gen.close();
        b = "x\ufffd".getBytes("utf-8");
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + b.length), b);

        out = new ByteArrayOutputStream();
        gen = lenientUnicodeCborGenerator(out);
        assertEquals(0, gen.streamWriteOutputBuffered());

        // Unmatched second surrogate character
        gen.writeString("x\ude01");
        gen.close();
        b = "x\ufffd".getBytes("utf-8");
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + b.length), b);

        out = new ByteArrayOutputStream();
        gen = lenientUnicodeCborGenerator(out);
        assertEquals(0, gen.streamWriteOutputBuffered());

        // Unmatched second surrogate character (2)
        gen.writeString("x\ude01x");
        gen.close();
        b = "x\ufffdx".getBytes("utf-8");
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + b.length), b);
    }

    public void testRecoverInvalidSurrogate2() throws Exception
    {
        ByteArrayOutputStream out;
        CBORGenerator gen;
        byte[] b;

        out = new ByteArrayOutputStream();
        gen = lenientUnicodeCborGenerator(out);
        assertEquals(0, gen.streamWriteOutputBuffered());

        // Broken surrogate pair
        gen.writeString("X\ud83dY");
        gen.close();
        b = "X\ufffdY".getBytes("utf-8");
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + b.length), b);
    }
}
