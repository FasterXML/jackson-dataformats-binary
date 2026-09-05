package tools.jackson.dataformat.cbor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utility class that provides {@link VarHandle} instances for efficient
 * multi-byte primitive reads and writes on byte arrays.
 *<p>
 * Handles are resolved once at class initialization. On runtimes where
 * {@code MethodHandles.byteArrayViewVarHandle()} is unsupported (for example
 * some Android runtimes) they are left {@code null} and {@link #isAvailable()}
 * returns {@code false}. Callers MUST check {@link #isAvailable()} first: the
 * {@code getXxx()} and {@code setXxx()} methods dereference the handles
 * unconditionally, and the byte-shifting fallback lives in the caller, not here.
 *
 * @since 3.3
 */
final class CBORVarHandleUtil
{
    /**
     * VarHandle for reading/writing an {@code int} as 4 big-endian bytes.
     * {@code null} if VarHandles are unavailable.
     */
    static final VarHandle INT_BE;

    /**
     * VarHandle for reading/writing a {@code long} as 8 big-endian bytes.
     * {@code null} if VarHandles are unavailable.
     */
    static final VarHandle LONG_BE;

    static {
        VarHandle intBe = null;
        VarHandle longBe = null;
        try {
            intBe = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
            longBe = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
        } catch (Throwable t) {
            // VarHandles not available (e.g., Android): fall back to manual byte shifting
        }
        INT_BE = intBe;
        // assigned last: non-null implies every handle above resolved too
        LONG_BE = longBe;
    }

    private CBORVarHandleUtil() { }

    static boolean isAvailable() {
        return LONG_BE != null;
    }

    // Helper methods that read/write primitives via the class's own VarHandle
    // fields. Only called when the corresponding field is non-null, which implies
    // VarHandle is available on this runtime.

    static void setIntBE(byte[] array, int offset, int value) {
        INT_BE.set(array, offset, value);
    }

    static void setLongBE(byte[] array, int offset, long value) {
        LONG_BE.set(array, offset, value);
    }

    /**
     * Reads 4 bytes at given offset as a big-endian {@code int}; caller MUST
     * have verified that {@code offset+4} is within bounds of given array.
     *
     * @since 3.3
     */
    static int getIntBE(byte[] array, int offset) {
        return (int) INT_BE.get(array, offset);
    }

    /**
     * Reads 8 bytes at given offset as a big-endian {@code long}; caller MUST
     * have verified that {@code offset+8} is within bounds of given array.
     *
     * @since 3.3
     */
    static long getLongBE(byte[] array, int offset) {
        return (long) LONG_BE.get(array, offset);
    }
}
