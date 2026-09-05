package tools.jackson.dataformat.smile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SmileByteShiftUtil}, the byte-shifting fallback used on
 * runtimes where {@link SmileVarHandleUtil} is unusable.
 *<p>
 * Needed because {@code SmileParserBase._VARHANDLE_AVAILABLE} is
 * {@code static final} and true on every JDK we test on: the fallback branch is
 * constant-folded away, so parsing tests alone never execute it. Here the
 * fallback is called directly and checked against {@link ByteBuffer} as an
 * independent oracle, plus against the VarHandle implementation it stands in for.
 */
public class VarHandleFallbackTest extends BaseTestForSmile
{
    private final static int OFFSET = 3; // deliberately unaligned

    @Test
    public void testGetIntBE() throws Exception
    {
        for (byte[] input : _inputs()) {
            final int exp = ByteBuffer.wrap(input, OFFSET, 4)
                    .order(ByteOrder.BIG_ENDIAN).getInt();
            assertEquals(exp, SmileByteShiftUtil.getIntBE(input, OFFSET));
            if (SmileVarHandleUtil.isAvailable()) {
                assertEquals(exp, SmileVarHandleUtil.getIntBE(input, OFFSET));
            }
        }
    }

    // Sign-bit and all-bits-set cases first, then pseudo-random ones
    // (fixed seed, for reproducibility)
    private byte[][] _inputs() {
        final int size = OFFSET + 4;
        byte[][] result = new byte[3+100][];
        result[0] = new byte[size];
        result[1] = new byte[size];
        Arrays.fill(result[1], (byte) 0xFF);
        result[2] = new byte[size];
        result[2][OFFSET] = (byte) 0x80; // high bit of first byte only
        Random rnd = new Random(1234);
        for (int i = 3; i < result.length; ++i) {
            byte[] b = new byte[size];
            rnd.nextBytes(b);
            result[i] = b;
        }
        return result;
    }
}
