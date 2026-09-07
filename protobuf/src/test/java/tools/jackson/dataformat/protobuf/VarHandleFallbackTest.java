package tools.jackson.dataformat.protobuf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProtobufByteShiftUtil}, the byte-shifting fallback used on
 * runtimes where {@link ProtobufVarHandleUtil} is unusable.
 *<p>
 * Needed because {@code ProtobufParser._VARHANDLE_AVAILABLE} (and the generator's
 * equivalent) is {@code static final} and true on every JDK we test on: the
 * fallback branches are constant-folded away, so parsing and generation tests
 * alone never execute them. Here the fallback is called directly and checked
 * against {@link ByteBuffer} as an independent oracle, plus against the VarHandle
 * implementation it stands in for.
 */
public class VarHandleFallbackTest extends ProtobufTestBase
{
    private final static int OFFSET = 3; // deliberately unaligned

    @Test
    public void testGetIntLE() throws Exception
    {
        for (byte[] input : _inputs(4)) {
            final int exp = ByteBuffer.wrap(input, OFFSET, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt();
            assertEquals(exp, ProtobufByteShiftUtil.getIntLE(input, OFFSET));
            if (ProtobufVarHandleUtil.isAvailable()) {
                assertEquals(exp, ProtobufVarHandleUtil.getIntLE(input, OFFSET));
            }
        }
    }

    @Test
    public void testGetLongLE() throws Exception
    {
        for (byte[] input : _inputs(8)) {
            final long exp = ByteBuffer.wrap(input, OFFSET, 8)
                    .order(ByteOrder.LITTLE_ENDIAN).getLong();
            assertEquals(exp, ProtobufByteShiftUtil.getLongLE(input, OFFSET));
            if (ProtobufVarHandleUtil.isAvailable()) {
                assertEquals(exp, ProtobufVarHandleUtil.getLongLE(input, OFFSET));
            }
        }
    }

    @Test
    public void testSetIntLE() throws Exception
    {
        for (int value : _intValues()) {
            byte[] exp = new byte[OFFSET+4];
            ByteBuffer.wrap(exp, OFFSET, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);

            byte[] act = new byte[OFFSET+4];
            ProtobufByteShiftUtil.setIntLE(act, OFFSET, value);
            assertArrayEquals(exp, act, "for value "+value);

            if (ProtobufVarHandleUtil.isAvailable()) {
                byte[] viaHandle = new byte[OFFSET+4];
                ProtobufVarHandleUtil.setIntLE(viaHandle, OFFSET, value);
                assertArrayEquals(exp, viaHandle, "for value "+value);
            }
        }
    }

    @Test
    public void testSetLongLE() throws Exception
    {
        for (long value : _longValues()) {
            byte[] exp = new byte[OFFSET+8];
            ByteBuffer.wrap(exp, OFFSET, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(value);

            byte[] act = new byte[OFFSET+8];
            ProtobufByteShiftUtil.setLongLE(act, OFFSET, value);
            assertArrayEquals(exp, act, "for value "+value);

            if (ProtobufVarHandleUtil.isAvailable()) {
                byte[] viaHandle = new byte[OFFSET+8];
                ProtobufVarHandleUtil.setLongLE(viaHandle, OFFSET, value);
                assertArrayEquals(exp, viaHandle, "for value "+value);
            }
        }
    }

    // // // Helper methods for building inputs: sign-bit and all-bits-set cases
    // // // first, then pseudo-random ones (fixed seed, for reproducibility)

    private byte[][] _inputs(int length) {
        final int size = OFFSET + length;
        byte[][] result = new byte[3+100][];
        result[0] = new byte[size];
        result[1] = new byte[size];
        Arrays.fill(result[1], (byte) 0xFF);
        result[2] = new byte[size];
        result[2][size-1] = (byte) 0x80; // high bit of most-significant (last) byte only
        Random rnd = new Random(1234);
        for (int i = 3; i < result.length; ++i) {
            byte[] b = new byte[size];
            rnd.nextBytes(b);
            result[i] = b;
        }
        return result;
    }

    private int[] _intValues() {
        int[] result = new int[4+100];
        result[0] = 0;
        result[1] = -1;
        result[2] = Integer.MIN_VALUE;
        result[3] = Integer.MAX_VALUE;
        Random rnd = new Random(5678);
        for (int i = 4; i < result.length; ++i) {
            result[i] = rnd.nextInt();
        }
        return result;
    }

    private long[] _longValues() {
        long[] result = new long[4+100];
        result[0] = 0L;
        result[1] = -1L;
        result[2] = Long.MIN_VALUE;
        result[3] = Long.MAX_VALUE;
        Random rnd = new Random(9012);
        for (int i = 4; i < result.length; ++i) {
            result[i] = rnd.nextLong();
        }
        return result;
    }
}
