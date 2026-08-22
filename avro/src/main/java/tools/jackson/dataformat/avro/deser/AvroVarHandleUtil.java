package tools.jackson.dataformat.avro.deser;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utility class that provides {@link VarHandle}-based access for reading
 * multi-byte primitives out of byte arrays.
 *<p>
 * NOTE: handles here are LITTLE-endian, unlike the big-endian ones other
 * Jackson binary backends need: Avro encodes {@code float} and {@code double}
 * as little-endian IEEE-754.
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
 * {@code JacksonAvroParserImpl} does both; see it for the pattern.
 *
 * @since 3.3
 */
final class AvroVarHandleUtil
{
    /**
     * VarHandle for reading 4 little-endian bytes as an {@code int}.
     * {@code null} if byte-array views are unsupported.
     */
    private static final VarHandle INT_LE;

    /**
     * VarHandle for reading 8 little-endian bytes as a {@code long}.
     * {@code null} if byte-array views are unsupported.
     */
    private static final VarHandle LONG_LE;

    static {
        VarHandle intLe = null;
        VarHandle longLe = null;
        try {
            intLe = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
            longLe = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
        } catch (Throwable t) {
            // Byte-array views not supported: caller falls back to byte shifting
        }
        INT_LE = intLe;
        // assigned last: non-null implies the handle above resolved too
        LONG_LE = longLe;
    }

    private AvroVarHandleUtil() { }

    /**
     * @return {@code true} if the {@code getXxx()} methods may be called; if
     *    {@code false}, caller MUST use its own byte-shifting fallback
     */
    static boolean isAvailable() {
        return LONG_LE != null;
    }

    // Helper methods that read primitives via the class's own VarHandle fields.
    // Only called when {@link #isAvailable()} returned true; the handles are
    // dereferenced unconditionally and the fallback lives in the caller.

    /**
     * Reads 4 bytes at given offset as a little-endian {@code int}; caller MUST
     * have verified that {@code offset+4} is within bounds of given array.
     */
    static int getIntLE(byte[] array, int offset) {
        return (int) INT_LE.get(array, offset);
    }

    /**
     * Reads 8 bytes at given offset as a little-endian {@code long}; caller MUST
     * have verified that {@code offset+8} is within bounds of given array.
     */
    static long getLongLE(byte[] array, int offset) {
        return (long) LONG_LE.get(array, offset);
    }
}
