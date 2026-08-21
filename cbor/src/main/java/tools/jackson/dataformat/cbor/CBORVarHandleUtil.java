package tools.jackson.dataformat.cbor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utility class that provides {@link VarHandle} instances for efficient
 * multi-byte primitive writes to byte arrays. Falls back gracefully
 * if VarHandles are not available (e.g., on some Android runtimes).
 *
 * @since 3.3
 */
final class CBORVarHandleUtil
{
    /**
     * VarHandle for writing a {@code float} as 4 big-endian bytes.
     * {@code null} if VarHandles are unavailable.
     */
    static final VarHandle FLOAT_BE;

    /**
     * VarHandle for writing a {@code double} as 8 big-endian bytes.
     * {@code null} if VarHandles are unavailable.
     */
    static final VarHandle DOUBLE_BE;

    /**
     * VarHandle for writing an {@code int} as 4 big-endian bytes.
     * {@code null} if VarHandles are unavailable.
     */
    static final VarHandle INT_BE;

    /**
     * VarHandle for writing a {@code long} as 8 big-endian bytes.
     * {@code null} if VarHandles are unavailable.
     */
    static final VarHandle LONG_BE;

    static {
        VarHandle floatBe = null;
        VarHandle doubleBe = null;
        VarHandle intBe = null;
        VarHandle longBe = null;
        try {
            floatBe = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.BIG_ENDIAN);
            doubleBe = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.BIG_ENDIAN);
            intBe = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
            longBe = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
        } catch (Throwable t) {
            // VarHandles not available (e.g., Android) — fall back to manual byte shifting
        }
        FLOAT_BE = floatBe;
        DOUBLE_BE = doubleBe;
        INT_BE = intBe;
        LONG_BE = longBe;
    }

    private CBORVarHandleUtil() { }

    static boolean isAvailable() {
        return LONG_BE != null;
    }

    // Helper methods that write primitives via the class's own VarHandle fields.
    // Only called when the corresponding field is non-null, which implies
    // VarHandle is available on this runtime.

    static void setInt(byte[] array, int offset, int value) {
        INT_BE.set(array, offset, value);
    }

    static void setLong(byte[] array, int offset, long value) {
        LONG_BE.set(array, offset, value);
    }

    static void setFloat(byte[] array, int offset, float value) {
        FLOAT_BE.set(array, offset, value);
    }

    static void setDouble(byte[] array, int offset, double value) {
        DOUBLE_BE.set(array, offset, value);
    }
}
