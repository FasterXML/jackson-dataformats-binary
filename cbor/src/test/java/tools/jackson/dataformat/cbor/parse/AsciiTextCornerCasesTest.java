package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORGenerator;
import tools.jackson.dataformat.cbor.CBORParser;
import tools.jackson.dataformat.cbor.CBORTestBase;
import tools.jackson.dataformat.cbor.testutil.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parameterized tests covering all ASCII-optimization exit paths in CBORParser:
 *
 * 1. _finishShortText: text fits in I/O buffer, ASCII fast-path + fallback to multi-byte
 * 2. _finishLongTextAscii: text exceeds I/O buffer, iterates with buffer growth
 *    - Exit (B): non-ASCII byte found mid-iteration
 *    - Exit (C): all bytes consumed normally
 *    - Exercises TextBuffer segment growth (500→750→1125→...→8538→...)
 * 3. _finishChunkedTextAscii: chunked text, ASCII fast-path per chunk
 *
 * Each test generates a string with a controlled ASCII prefix followed by unicode,
 * encodes it to CBOR, then decodes via both byte[] and throttled InputStream to
 * exercise both _finishShortText and _finishLongTextAscii/_finishChunkedTextAscii paths.
 *
 * Sizes are chosen to hit TextBuffer segment boundaries and I/O buffer boundaries:
 * - Small (< I/O buffer): exercises _finishShortText
 * - Medium (> I/O buffer but < first segment regrowth): exercises first iterations
 * - Large (forces multiple segment growths): exercises finishCurrentSegment transitions
 * - XL (segment grows past I/O buffer size): exercises partial-fill iterations
 *
 * NOTE: Each test creates a fresh CBORFactory via cborFactory() to ensure the
 * BufferRecycler does not carry over grown char[] buffers from previous tests.
 * The recycler retains the largest buffer returned to it, which would change the
 * initial TextBuffer segment size and alter which code paths are exercised.
 */
public class AsciiTextCornerCasesTest extends CBORTestBase
{
    // TextBuffer segment sizes: 500, 750, 1125, 1687, 2530, 3795, 5692, 8538, 12807...
    // I/O buffer size: 8000
    // Key boundaries to test around:
    private static final int IO_BUF = 8000;
    private static final int SEG_0 = 500;   // initial segment
    private static final int SEG_1 = 750;
    private static final int SEG_2 = 1125;
    private static final int SEG_7 = 8538;  // first segment > I/O buffer
    private static final int CUMULATIVE_TO_SEG_7 = 16279; // total chars to fill segments 0-6

    static Stream<Arguments> asciiPrefixSizes() {
        List<Arguments> args = new ArrayList<>();
        int[] sizes = {
                // _finishShortText path (fits in I/O buffer)
                1, 10, 100, SEG_0 - 1, SEG_0, SEG_0 + 1,
                SEG_1, SEG_2, 2000, 4000, 7000, IO_BUF - 1,

                // _finishLongTextAscii path (exceeds I/O buffer)
                IO_BUF, IO_BUF + 1, IO_BUF + 500,
                // Forces multiple segment growths
                IO_BUF + SEG_0 + SEG_1,
                CUMULATIVE_TO_SEG_7 - 1,
                CUMULATIVE_TO_SEG_7,
                CUMULATIVE_TO_SEG_7 + 1,
                // Segment larger than I/O buffer (partial fill, no finishCurrentSegment)
                CUMULATIVE_TO_SEG_7 + IO_BUF - 1,
                CUMULATIVE_TO_SEG_7 + IO_BUF,
                CUMULATIVE_TO_SEG_7 + IO_BUF + 1,
                // Even larger: forces segment to grow again after partial fill
                CUMULATIVE_TO_SEG_7 + SEG_7,
                CUMULATIVE_TO_SEG_7 + SEG_7 + IO_BUF,
                // 50K+ to force many regrowths
                50000
        };
        for (int asciiPrefix : sizes) {
            args.add(Arguments.of(asciiPrefix, "pure_ascii"));
            // With unicode suffix (exercises non-ASCII exit path B)
            args.add(Arguments.of(asciiPrefix, "ascii_then_unicode"));
        }
        return args.stream();
    }

    @ParameterizedTest(name = "asciiPrefix={0}, flavor={1}")
    @MethodSource("asciiPrefixSizes")
    public void testNonChunkedViaByteArray(int asciiPrefix, String flavor) throws Exception
    {
        String input = buildTestString(asciiPrefix, flavor);
        CBORFactory f = cborFactory();
        byte[] cbor = encodeCbor(f, input);

        // byte[] path: exercises _finishShortText for small, _finishLongText for large
        try (CBORParser p = cborParser(f, cbor)) {
            assertEquals(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(input, p.getString());
        }
    }

    @ParameterizedTest(name = "asciiPrefix={0}, flavor={1}")
    @MethodSource("asciiPrefixSizes")
    public void testNonChunkedViaStream(int asciiPrefix, String flavor) throws Exception
    {
        String input = buildTestString(asciiPrefix, flavor);
        CBORFactory f = cborFactory();
        byte[] cbor = encodeCbor(f, input);

        // InputStream path with throttling: forces _finishLongTextAscii for texts > I/O buffer
        try (CBORParser p = (CBORParser) f.createParser(ObjectReadContext.empty(),
                new ThrottledInputStream(cbor, 997))) {
            assertEquals(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(input, p.getString());
        }
    }

    @ParameterizedTest(name = "asciiPrefix={0}, flavor={1}")
    @MethodSource("asciiPrefixSizes")
    public void testChunkedViaStream(int asciiPrefix, String flavor) throws Exception
    {
        String input = buildTestString(asciiPrefix, flavor);
        CBORFactory f = cborFactory();
        byte[] cbor = encodeChunkedCbor(input);

        // Chunked encoding exercises _finishChunkedTextAscii
        try (CBORParser p = (CBORParser) f.createParser(ObjectReadContext.empty(),
                new ThrottledInputStream(cbor, 997))) {
            assertEquals(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(input, p.getString());
        }
    }

    /**
     * Tests that re-using the same parser across multiple strings exercises
     * TextBuffer regrowth correctly (segments grow and are reused across getText calls).
     */
    @ParameterizedTest(name = "asciiPrefix={0}, flavor={1}")
    @MethodSource("asciiPrefixSizes")
    public void testRepeatedParsesWithBufferGrowth(int asciiPrefix, String flavor) throws Exception
    {
        String input = buildTestString(asciiPrefix, flavor);
        CBORFactory f = cborFactory();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CBORGenerator gen = (CBORGenerator) f.createGenerator(ObjectWriteContext.empty(), bos)) {
            gen.writeStartArray();
            gen.writeString(input);
            gen.writeString(input);
            gen.writeString(input);
            gen.writeEndArray();
        }
        byte[] cbor = bos.toByteArray();

        try (CBORParser p = (CBORParser) f.createParser(ObjectReadContext.empty(),
                new ThrottledInputStream(cbor, 997))) {
            assertEquals(JsonToken.START_ARRAY, p.nextToken());
            for (int i = 0; i < 3; i++) {
                assertEquals(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(input, p.getString(),
                        "Mismatch on iteration " + i + " (buffer regrowth issue?)");
            }
            assertEquals(JsonToken.END_ARRAY, p.nextToken());
        }
    }

    /**
     * Targeted test for the top-of-loop exit in _finishChunkedTextAscii:
     * the final ASCII byte of the chunk sits exactly at the I/O buffer
     * boundary so that, on the iteration after the chunk's bytes are fully
     * consumed, both _inputPtr >= _chunkEnd and _chunkLeft == 0 hold
     * simultaneously and the method must return without peeking past the
     * window (the break byte 0xFF lives in the next buffer fill).
     */
    @Test
    public void testChunkedTextEndsAtIOBufferBoundary() throws Exception
    {
        // Default CBORParser I/O buffer size is 8000 bytes.
        // Layout (total = 8001 bytes):
        //   [0]       0x7F            indefinite-length text
        //   [1..3]    0x79 HH LL      chunk header (2-byte length)
        //   [4..7999] payload         exactly fills the first I/O read
        //   [8000]    0xFF            break byte (next I/O read)
        final int payloadLen = IO_BUF - 4;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x7F);
        bos.write(0x79);
        bos.write((payloadLen >> 8) & 0xFF);
        bos.write(payloadLen & 0xFF);
        byte[] payload = new byte[payloadLen];
        Arrays.fill(payload, (byte) 'A');
        bos.write(payload);
        bos.write(0xFF);
        byte[] cbor = bos.toByteArray();
        assertEquals(IO_BUF + 1, cbor.length);

        CBORFactory f = cborFactory();
        // Plain ByteArrayInputStream: first read fills exactly IO_BUF bytes,
        // setting _chunkEnd == _inputEnd == IO_BUF and _chunkLeft == 0.
        try (CBORParser p = (CBORParser) f.createParser(ObjectReadContext.empty(),
                new ByteArrayInputStream(cbor))) {
            assertEquals(JsonToken.VALUE_STRING, p.nextToken());
            String text = p.getString();
            assertEquals(payloadLen, text.length());
            for (int i = 0; i < payloadLen; i++) {
                assertEquals('A', text.charAt(i),
                        "mismatch at index " + i);
            }
        }
    }

    private String buildTestString(int asciiPrefix, String flavor) {
        if ("pure_ascii".equals(flavor)) {
            return generateLongAsciiString(asciiPrefix);
        }
        // ascii_then_unicode: ASCII prefix + unicode suffix
        return generateUnicodeStringWithAsciiPrefix(asciiPrefix, asciiPrefix + 200);
    }

    private byte[] encodeCbor(CBORFactory f, String text) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CBORGenerator gen = (CBORGenerator) f.createGenerator(ObjectWriteContext.empty(), bos)) {
            gen.writeString(text);
        }
        return bos.toByteArray();
    }

    private byte[] encodeChunkedCbor(String text) throws IOException {
        byte[] utf8 = text.getBytes("UTF-8");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x7F); // indefinite-length text
        int chunkSize = 997;
        int offset = 0;
        while (offset < utf8.length) {
            int end = Math.min(offset + chunkSize, utf8.length);
            // Don't split multi-byte UTF-8 sequences
            while (end < utf8.length && (utf8[end] & 0xC0) == 0x80) {
                end--;
            }
            int len = end - offset;
            writeChunkHeader(bos, len);
            bos.write(utf8, offset, len);
            offset = end;
        }
        bos.write(0xFF); // break
        return bos.toByteArray();
    }

    private static void writeChunkHeader(ByteArrayOutputStream bos, int len) {
        if (len <= 23) {
            bos.write(0x60 | len);
        } else if (len <= 0xFF) {
            bos.write(0x78);
            bos.write(len);
        } else if (len <= 0xFFFF) {
            bos.write(0x79);
            bos.write((len >> 8) & 0xFF);
            bos.write(len & 0xFF);
        } else {
            bos.write(0x7A);
            bos.write((len >> 24) & 0xFF);
            bos.write((len >> 16) & 0xFF);
            bos.write((len >> 8) & 0xFF);
            bos.write(len & 0xFF);
        }
    }
}
