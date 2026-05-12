package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.testutil.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for missing setCurrentLength on the early-return path in _finishLongTextAscii
 * when _tryToLoadToHaveAtLeast returns false (stream ends prematurely).
 *
 * The bug exists in the code (outPtr > 0 but setCurrentLength not called), but in
 * practice it's masked because the caller _finishLongText then tries to read the
 * remaining bytes via _nextByte() which throws on EOF before the corrupted TextBuffer
 * is ever converted to a String.
 *
 * These tests document the behavior and verify correctness on both truncated and
 * valid long ASCII input via throttled streams.
 */
public class TruncatedLongAsciiTextTest extends CBORTestBase
{
    /**
     * Verifies that truncated long ASCII text results in a clean exception.
     */
    @Test
    public void testTruncatedAsciiThrowsCleanly() throws Exception
    {
        // Grow TextBuffer past I/O buffer size, then truncate
        final int actualPayload = 16279 + 8000;
        final int declaredLen = actualPayload + 5000;

        CBORFactory f = cborFactory();
        byte[] cbor = buildTruncatedCborText(declaredLen, actualPayload);

        try (CBORParser p = cborParser(f, new ThrottledInputStream(cbor, 2000))) {
            assertEquals(JsonToken.VALUE_STRING, p.nextToken());
            assertThrows(StreamReadException.class, () -> p.getText());
        }
    }

    /**
     * Verifies that a long ASCII text that fits exactly in the delivered bytes
     * is decoded correctly even when the TextBuffer segment has grown past the
     * I/O buffer size (exercises the partial-fill normal exit path).
     */
    @Test
    public void testLongAsciiExactLengthDecodedCorrectly() throws Exception
    {
        final int len = 25000;

        CBORFactory f = cborFactory();
        byte[] cbor = buildCborText(len);

        try (CBORParser p = cborParser(f, new ThrottledInputStream(cbor, 2000))) {
            assertEquals(JsonToken.VALUE_STRING, p.nextToken());
            String text = p.getText();
            assertEquals(len, text.length());
            String expected = new String(new char[len]).replace('\0', 'A');
            assertEquals(expected, text);
        }
    }

    private static byte[] buildTruncatedCborText(int declaredLen, int actualPayload) throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x7A);
        bos.write((declaredLen >> 24) & 0xFF);
        bos.write((declaredLen >> 16) & 0xFF);
        bos.write((declaredLen >> 8) & 0xFF);
        bos.write(declaredLen & 0xFF);
        byte[] payload = new byte[actualPayload];
        Arrays.fill(payload, (byte) 'A');
        bos.write(payload);
        return bos.toByteArray();
    }

    private static byte[] buildCborText(int len) throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x7A);
        bos.write((len >> 24) & 0xFF);
        bos.write((len >> 16) & 0xFF);
        bos.write((len >> 8) & 0xFF);
        bos.write(len & 0xFF);
        byte[] payload = new byte[len];
        Arrays.fill(payload, (byte) 'A');
        bos.write(payload);
        return bos.toByteArray();
    }
}
