package tools.jackson.dataformat.protobuf;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Utility class that provides {@link VarHandle} instances for efficient
 * multi-byte primitive reads and writes on byte arrays.
 *<p>
 * NOTE: handles here are LITTLE-endian, unlike the big-endian ones CBOR needs:
 * protobuf encodes its {@code fixed32}/{@code fixed64} types (and hence
 * {@code float}/{@code double}) as little-endian.
 *<p>
 * Handles are resolved once at class initialization. On runtimes where
 * {@code MethodHandles.byteArrayViewVarHandle()} is unsupported (for example
 * some Android runtimes) they are left {@code null} and {@link #isAvailable()}
 * returns {@code false}. Callers MUST check {@link #isAvailable()} first: the
 * {@code getXxx()}/{@code setXxx()} methods dereference the handles
 * unconditionally, and the byte-shifting fallback lives in the caller, not here.
 *<p>
 * Callers must also invoke {@link #isAvailable()} from within a
 * {@code try}/{@code catch (Throwable)} block: this class names {@link VarHandle}
 * in its field and method signatures, so on a runtime lacking
 * {@code java.lang.invoke.VarHandle} entirely it fails to <i>link</i>, raising
 * {@link LinkageError} before any code here can run.
 *
 * @since 3.3
 */
final class ProtobufVarHandleUtil
{
    /**
     * VarHandle for reading/writing an {@code int} as 4 little-endian bytes.
     * {@code null} if VarHandles are unavailable.
     */
    static final VarHandle INT_LE;

    /**
     * VarHandle for reading/writing a {@code long} as 8 little-endian bytes.
     * {@code null} if VarHandles are unavailable.
     */
    static final VarHandle LONG_LE;

    static {
        VarHandle intLe = null;
        VarHandle longLe = null;
        try {
            intLe = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
            longLe = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
        } catch (Throwable t) {
            // VarHandles not available (e.g., Android): fall back to manual byte shifting
        }
        INT_LE = intLe;
        // assigned last: non-null implies every handle above resolved too
        LONG_LE = longLe;
    }

    private ProtobufVarHandleUtil() { }

    static boolean isAvailable() {
        return LONG_LE != null;
    }

    // Helper methods that read/write primitives via the class's own VarHandle
    // fields. Only called when the corresponding field is non-null, which implies
    // VarHandle is available on this runtime. Caller MUST also have verified that
    // the full 4/8 bytes are within bounds of the given array.

    static int getInt(byte[] array, int offset) {
        return (int) INT_LE.get(array, offset);
    }

    static long getLong(byte[] array, int offset) {
        return (long) LONG_LE.get(array, offset);
    }

    static void setInt(byte[] array, int offset, int value) {
        INT_LE.set(array, offset, value);
    }

    static void setLong(byte[] array, int offset, long value) {
        LONG_LE.set(array, offset, value);
    }
}
