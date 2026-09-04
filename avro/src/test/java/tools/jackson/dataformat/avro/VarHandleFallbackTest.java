package tools.jackson.dataformat.avro;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AvroByteShiftUtil}, the byte-shifting fallback used on
 * runtimes where {@link AvroVarHandleUtil} is unusable.
 *<p>
 * Needed because {@code JacksonAvroParserImpl._VARHANDLE_AVAILABLE} is
 * {@code static final} and true on every JDK we test on: the fallback branches
 * in {@code decodeFloat()} / {@code decodeDouble()} are constant-folded away, so
 * reading tests alone never execute them. Here the fallback is called directly
 * and checked against {@link ByteBuffer} as an independent oracle, plus against
 * the VarHandle implementation it stands in for.
 */
public class VarHandleFallbackTest
{
    private final static int OFFSET = 3; // deliberately unaligned

    @Test
    public void testGetIntLE() throws Exception
    {
        for (byte[] input : _inputs(4)) {
            final int exp = ByteBuffer.wrap(input, OFFSET, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt();
            assertEquals(exp, AvroByteShiftUtil.getIntLE(input, OFFSET));
            if (AvroVarHandleUtil.isAvailable()) {
                assertEquals(exp, AvroVarHandleUtil.getIntLE(input, OFFSET));
            }
        }
    }

    @Test
    public void testGetLongLE() throws Exception
    {
        for (byte[] input : _inputs(8)) {
            final long exp = ByteBuffer.wrap(input, OFFSET, 8)
                    .order(ByteOrder.LITTLE_ENDIAN).getLong();
            assertEquals(exp, AvroByteShiftUtil.getLongLE(input, OFFSET));
            if (AvroVarHandleUtil.isAvailable()) {
                assertEquals(exp, AvroVarHandleUtil.getLongLE(input, OFFSET));
            }
        }
    }

    // Also verify the values the parser actually produces from these bytes
    @Test
    public void testFloatAndDoubleBits() throws Exception
    {
        for (byte[] input : _inputs(4)) {
            assertEquals(ByteBuffer.wrap(input, OFFSET, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).getFloat(),
                    Float.intBitsToFloat(AvroByteShiftUtil.getIntLE(input, OFFSET)),
                    0f);
        }
        for (byte[] input : _inputs(8)) {
            assertEquals(ByteBuffer.wrap(input, OFFSET, 8)
                        .order(ByteOrder.LITTLE_ENDIAN).getDouble(),
                    Double.longBitsToDouble(AvroByteShiftUtil.getLongLE(input, OFFSET)),
                    0d);
        }
    }

    // Sign-bit and all-bits-set cases first, then pseudo-random ones
    // (fixed seed, for reproducibility)
    private byte[][] _inputs(int length) {
        final int size = OFFSET + length;
        byte[][] result = new byte[3+100][];
        result[0] = new byte[size];
        result[1] = new byte[size];
        Arrays.fill(result[1], (byte) 0xFF);
        result[2] = new byte[size];
        result[2][size-1] = (byte) 0x80; // high bit of most-significant (last) byte
        Random rnd = new Random(1234);
        for (int i = 3; i < result.length; ++i) {
            byte[] b = new byte[size];
            rnd.nextBytes(b);
            result[i] = b;
        }
        return result;
    }
}
