package tools.jackson.dataformat.smile;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utility class that provides {@link VarHandle}-based access for reading
 * multi-byte primitives out of byte arrays.
 *<p>
 * IMPORTANT: this class references {@link VarHandle} in field and method
 * signatures, so on a runtime that does not provide {@code java.lang.invoke.VarHandle}
 * at all it will fail to <i>link</i>, before any code here gets a chance to run.
 * Callers MUST therefore both:
 *<ol>
 * <li>load this class from within a {@code try}/{@code catch (Throwable)} block,
 *   so that {@link LinkageError} is caught, and</li>
 * <li>keep the byte-shifting fallback in a class that does not reference
 *   {@link VarHandle}, so the fallback path never resolves this class.</li>
 *</ol>
 * {@code SmileParserBase._decodeQuad()} does both; see it for the pattern.
 *
 * @since 3.3
 */
final class SmileVarHandleUtil
{
    /**
     * VarHandle for reading 4 big-endian bytes as an {@code int}.
     * {@code null} if {@code byteArrayViewVarHandle()} is unsupported.
     */
    private static final VarHandle INT_BE;

    static {
        VarHandle intBe = null;
        try {
            intBe = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
        } catch (Throwable t) {
            // Byte-array views not supported: caller falls back to byte shifting
        }
        INT_BE = intBe;
    }

    private SmileVarHandleUtil() { }

    /**
     * @return {@code true} if {@link #getIntBE} may be called; if {@code false},
     *    caller MUST use its own byte-shifting fallback
     */
    static boolean isAvailable() {
        return INT_BE != null;
    }

    /**
     * Reads 4 bytes starting at given offset as a big-endian {@code int}.
     *<p>
     * Only to be called if {@link #isAvailable()} returned {@code true}: the
     * handle is dereferenced unconditionally, and the fallback lives in the
     * caller, not here.
     * Caller MUST also have verified that {@code offset+4} is within bounds of
     * given array.
     */
    static int getIntBE(byte[] buffer, int offset) {
        return (int) INT_BE.get(buffer, offset);
    }
}
