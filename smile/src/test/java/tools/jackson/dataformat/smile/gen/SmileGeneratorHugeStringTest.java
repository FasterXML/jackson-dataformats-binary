package tools.jackson.dataformat.smile.gen;

import java.io.OutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SmileGeneratorHugeStringTest extends BaseTestForSmile
{
    // Length chosen so that BOTH `3*len` (long field-name guard) and `3*len + 2`
    // (String/char[]/raw-UTF8 guards) overflow a signed int to a negative value:
    //   3 * 715_827_883      = 2_147_483_649 -> overflows (Integer.MAX_VALUE is 2_147_483_647)
    //   3 * 715_827_883 + 2  = 2_147_483_651 -> overflows
    private final static int OVERFLOW_LEN = 715_827_883;

    // Require a comfortable margin of heap before even attempting allocation. Peak on JDK 8
    // (no compact strings) is char[len] (~1.4 GB) + a copied String char[] (~1.4 GB) ~= 2.9 GB,
    // so gate well above that to skip cleanly rather than OOM on mid-sized heaps.
    private final static long REQUIRED_HEAP = 4L * 1024 * 1024 * 1024; // 4 GB

    // Discards output but counts bytes, so we do not also retain ~700 MB of encoded data
    private static final class CountingOutputStream extends OutputStream {
        long count;

        @Override
        public void write(int b) { count++; }

        @Override
        public void write(byte[] b, int off, int len) { count += len; }
    }

    private static char[] newAsciiChars() {
        char[] chars = new char[OVERFLOW_LEN];
        Arrays.fill(chars, 'a'); // all-ASCII to hit the fast in-buffer encoding loop
        return chars;
    }

    // (1) String value path: writeString(String) -> _writeNonSharedString
    @Test
    public void testHugeStringValueDoesNotOverflowBuffer() throws Exception
    {
        assumeTrue(Runtime.getRuntime().maxMemory() >= REQUIRED_HEAP,
                "Requires large heap (>= 4 GB) to allocate a ~700M char String");

        char[] chars = newAsciiChars();
        String big = new String(chars);
        chars = null; // release the source array before encoding

        CountingOutputStream out = new CountingOutputStream();
        try (JsonGenerator gen = _smileGenerator(out, true)) {
            gen.writeString(big);
        }
        // type byte + payload + end marker: must have written the whole thing
        assertTrue(out.count > OVERFLOW_LEN,
                "Expected > " + OVERFLOW_LEN + " bytes, got " + out.count);
    }

    // (2) char[] value path: writeString(char[], off, len)
    @Test
    public void testHugeCharArrayValueDoesNotOverflowBuffer() throws Exception
    {
        assumeTrue(Runtime.getRuntime().maxMemory() >= REQUIRED_HEAP,
                "Requires large heap (>= 4 GB) to allocate a ~700M char[]");

        char[] chars = newAsciiChars();
        CountingOutputStream out = new CountingOutputStream();
        try (JsonGenerator gen = _smileGenerator(out, true)) {
            gen.writeString(chars, 0, chars.length);
        }
        assertTrue(out.count > OVERFLOW_LEN,
                "Expected > " + OVERFLOW_LEN + " bytes, got " + out.count);
    }

    // (3) long field-name path: writeName(String) -> _writeNonShortFieldName
    @Test
    public void testHugeFieldNameDoesNotOverflowBuffer() throws Exception
    {
        assumeTrue(Runtime.getRuntime().maxMemory() >= REQUIRED_HEAP,
                "Requires large heap (>= 4 GB) to allocate a ~700M char field name");

        char[] chars = newAsciiChars();
        String bigName = new String(chars);
        chars = null; // release the source array before encoding

        CountingOutputStream out = new CountingOutputStream();
        try (JsonGenerator gen = _smileGenerator(out, true)) {
            gen.writeStartObject();
            gen.writeName(bigName);
            gen.writeNull();
            gen.writeEndObject();
        }
        assertTrue(out.count > OVERFLOW_LEN,
                "Expected > " + OVERFLOW_LEN + " bytes, got " + out.count);
    }

    // (4) raw UTF-8 value path: writeRawUTF8String(byte[], off, len)
    @Test
    public void testHugeRawUTF8StringDoesNotOverflowBuffer() throws Exception
    {
        assumeTrue(Runtime.getRuntime().maxMemory() >= REQUIRED_HEAP,
                "Requires large heap (>= 4 GB) to allocate a ~700M byte[]");

        byte[] bytes = new byte[OVERFLOW_LEN];
        Arrays.fill(bytes, (byte) 'a'); // all-ASCII so byteLen == len

        CountingOutputStream out = new CountingOutputStream();
        try (JsonGenerator gen = _smileGenerator(out, true)) {
            gen.writeRawUTF8String(bytes, 0, bytes.length);
        }
        assertTrue(out.count > OVERFLOW_LEN,
                "Expected > " + OVERFLOW_LEN + " bytes, got " + out.count);
    }
}
