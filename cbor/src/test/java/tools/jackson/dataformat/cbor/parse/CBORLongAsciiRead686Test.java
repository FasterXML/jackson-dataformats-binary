package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.cbor.CBORMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// For [dataformats-binary#686]
public class CBORLongAsciiRead686Test
{
    // TextBuffer segment sizes grow 1.5× per flip, starting at 200 chars (MIN=500):
    //     S0=200, S1=500, S2=750, S3=1125, S4=1687, S5=2530, S6=3795, S7=5692, S8=8538
    // After 8 flips the segment is 8538 chars — the first one larger than the 8000-byte I/O buffer.
    // Total chars consumed reaching S8: 200+500+750+1125+1687+2530+3795+5692 = 16279.
    private static final int CHARS_TO_REACH_S8 = 16279;
    private static final int IO_BUFFER_SIZE = 8000;

    /**
     * Parses a definite-length CBOR map {a: <16279 'a' bytes>, b: <8000 'a' bytes + 0xB7>, n: "x"}
     * via InputStream and expects all tokens to be read without error.
     * <p>
     * Actual result on affected versions: JsonParseException "Unsupported major type (5)"
     */
    @Test
    public void testFinishLongTextAsciiDoesNotLeaveNonAsciiByte()
    {
        byte[] strA = new byte[CHARS_TO_REACH_S8];
        Arrays.fill(strA, (byte) 'a');

        // 0xC2 0xB7 = U+00B7 "·" (middle dot): a valid 2-byte UTF-8 sequence.
        // 0xC2 is non-ASCII so _finishLongTextAscii exits, but it must leave len non-negative
        // so that _finishLongText's while loop can still decode the sequence.
        byte[] strB = new byte[IO_BUFFER_SIZE + 2];
        Arrays.fill(strB, (byte) 'a');
        strB[IO_BUFFER_SIZE]         = (byte) 0xC2;
        strB[IO_BUFFER_SIZE + 1] = (byte) 0xB7;

        assertDoesNotThrow(() ->
                new CBORMapper()
                        .readValue(new ByteArrayInputStream(buildMap(strA, strB)), Object.class));
    }

    /**
     * definite-length map(3): {a: strA, b: strB, n: "x"}
     */
    private static byte[] buildMap(byte[] strA, byte[] strB) throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0xa3);
        writeText(bos, new byte[]{'a'});
        writeText(bos, strA);
        writeText(bos, new byte[]{'b'});
        writeText(bos, strB);
        writeText(bos, new byte[]{'n'});
        writeText(bos, new byte[]{'x'});
        return bos.toByteArray();
    }

    private static void writeText(ByteArrayOutputStream bos, byte[] bytes) throws Exception
    {
        int n = bytes.length;
        if (n <= 23) {
            bos.write(0x60 | n);
        } else if (n <= 0xFF) {
            bos.write(0x78);
            bos.write(n);
        } else if (n <= 0xFFFF) {
            bos.write(0x79);
            bos.write((n >> 8) & 0xFF);
            bos.write(n & 0xFF);
        }
        bos.write(bytes);
    }
}
