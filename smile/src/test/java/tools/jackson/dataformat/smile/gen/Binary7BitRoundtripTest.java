package tools.jackson.dataformat.smile.gen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trip coverage for Smile's "7-bit safe" binary encoding, which packs 7
 * payload bytes into 8 encoded bytes. Exercises every combination of full and
 * partial trailing chunk against each of the four code paths (byte[] vs
 * InputStream on write, in-memory vs streaming on read), since those paths take
 * a SWAR fast path for whole chunks and fall back to byte-at-a-time for chunks
 * that would over-read or over-write the buffer.
 */
public class Binary7BitRoundtripTest extends BaseTestForSmile
{
    // Every length across the first few chunk boundaries, then sizes that push
    // past the parser's internal buffers and the "long binary" threshold
    private final static int[] LENGTHS = _lengths();

    private static int[] _lengths() {
        int[] big = { 500, 999, 1000, 1001, 2000, 4096, 7 * 128, (7 * 128) + 1,
                50_000, 260_000 };
        int[] all = new int[64 + big.length];
        for (int i = 0; i < 64; ++i) {
            all[i] = i;
        }
        System.arraycopy(big, 0, all, 64, big.length);
        return all;
    }

    @Test
    public void testFromByteArrayReadInMemory() throws Exception {
        for (int len : LENGTHS) {
            byte[] data = _data(len);
            assertArrayEquals(data, _readInMemory(_writeFromArray(data)), "length "+len);
        }
    }

    @Test
    public void testFromByteArrayReadStreaming() throws Exception {
        for (int len : LENGTHS) {
            byte[] data = _data(len);
            assertArrayEquals(data, _readStreaming(_writeFromArray(data)), "length "+len);
        }
    }

    @Test
    public void testFromInputStreamReadInMemory() throws Exception {
        for (int len : LENGTHS) {
            byte[] data = _data(len);
            assertArrayEquals(data, _readInMemory(_writeFromStream(data)), "length "+len);
        }
    }

    @Test
    public void testFromInputStreamReadStreaming() throws Exception {
        for (int len : LENGTHS) {
            byte[] data = _data(len);
            assertArrayEquals(data, _readStreaming(_writeFromStream(data)), "length "+len);
        }
    }

    /**
     * The encoded form itself must not change: the SWAR and byte-shifting paths
     * have to agree byte for byte, and both have to agree with what previous
     * versions wrote.
     */
    @Test
    public void testEncodedFormIsStable() throws Exception {
        // 7 payload bytes with bits set across every 7-bit group boundary
        byte[] data = new byte[] { (byte) 0xFF, 0x00, (byte) 0xAA, 0x55,
                (byte) 0x80, 0x7F, (byte) 0xC3 };
        byte[] encoded = _writeFromArray(data);
        // trailing 8 bytes are the single encoded chunk
        byte[] chunk = new byte[8];
        System.arraycopy(encoded, encoded.length-8, chunk, 0, 8);

        // Reference: accumulate all 56 bits, then split into 7-bit groups
        long bits = 0L;
        for (byte b : data) {
            bits = (bits << 8) | (b & 0xFF);
        }
        for (int i = 0; i < 8; ++i) {
            assertEquals((int) ((bits >>> (7 * (7-i))) & 0x7F), chunk[i] & 0xFF,
                    "encoded byte #"+i);
        }
        assertArrayEquals(data, _readInMemory(encoded));
    }

    private byte[] _data(int len) {
        // Deterministic, and full-range so the high bit of every payload byte
        // gets exercised (encoded bytes must still stay 7-bit)
        byte[] data = new byte[len];
        new Random(len).nextBytes(data);
        return data;
    }

    private byte[] _writeFromArray(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator g = _smileGenerator(out, true)) {
            g.writeBinary(data);
        }
        return out.toByteArray();
    }

    private byte[] _writeFromStream(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator g = _smileGenerator(out, true)) {
            g.writeBinary(new ByteArrayInputStream(data), data.length);
        }
        return out.toByteArray();
    }

    private byte[] _readInMemory(byte[] doc) throws Exception {
        try (JsonParser p = _smileParser(doc)) {
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            byte[] result = p.getBinaryValue();
            assertEquals(null, p.nextToken());
            return result;
        }
    }

    private byte[] _readStreaming(byte[] doc) throws Exception {
        try (JsonParser p = _smileParser(doc)) {
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int count = p.readBinaryValue(out);
            assertEquals(out.size(), count);
            assertEquals(null, p.nextToken());
            return out.toByteArray();
        }
    }
}
