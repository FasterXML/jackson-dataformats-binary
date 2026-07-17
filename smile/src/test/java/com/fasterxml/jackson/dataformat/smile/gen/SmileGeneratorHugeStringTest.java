package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SmileGeneratorHugeStringTest extends BaseTestForSmile
{
    // 3*len + 2 overflows a signed int for this length
    private final static int OVERFLOW_LEN = 715_827_882;

    // Require a comfortable margin of heap before even attempting allocation
    private final static long REQUIRED_HEAP = 3L * 1024 * 1024 * 1024; // 3 GB

    // Discards output but counts bytes, so we do not also retain ~700 MB of encoded data
    private static final class CountingOutputStream extends OutputStream {
        long count;

        @Override
        public void write(int b) { count++; }

        @Override
        public void write(byte[] b, int off, int len) { count += len; }
    }

    @Test
    public void testHugeAsciiStringDoesNotOverflowBuffer() throws IOException
    {
        assumeTrue(Runtime.getRuntime().maxMemory() >= REQUIRED_HEAP,
                "Requires large heap (>= 3 GB) to allocate a ~700M char String");

        char[] chars = new char[OVERFLOW_LEN];
        Arrays.fill(chars, 'a'); // all-ASCII to hit the fast in-buffer encoding loop

        // (1) String value path: writeString(String) -> _writeNonSharedString
        String big = new String(chars);
        CountingOutputStream out = new CountingOutputStream();
        try (JsonGenerator gen = smileGenerator(out, true)) {
            gen.writeString(big);
        }
        // type byte + payload + end marker: must have written the whole thing
        assertTrue(out.count > OVERFLOW_LEN,
                "Expected > " + OVERFLOW_LEN + " bytes, got " + out.count);
        big = null;

        // (2) char[] value path: writeString(char[], off, len)
        out = new CountingOutputStream();
        try (JsonGenerator gen = smileGenerator(out, true)) {
            gen.writeString(chars, 0, chars.length);
        }
        assertTrue(out.count > OVERFLOW_LEN,
                "Expected > " + OVERFLOW_LEN + " bytes, got " + out.count);
    }
}
