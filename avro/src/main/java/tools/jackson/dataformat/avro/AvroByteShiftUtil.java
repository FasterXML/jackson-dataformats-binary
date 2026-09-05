package tools.jackson.dataformat.avro;

/**
 * Byte-shifting fallback for reading multi-byte primitives out of byte arrays,
 * used on runtimes where {@link AvroVarHandleUtil} is unusable.
 *<p>
 * NOTE: {@code public} only so that the {@code ...avro.deser} package can
 * use it; this class is an internal implementation detail and NOT part of
 * the public API: it may change or be removed without notice.
 *<p>
 * NOTE: reads here are LITTLE-endian, matching Avro's IEEE-754 encoding of
 * {@code float} and {@code double}.
 *<p>
 * IMPORTANT: this class must NOT reference {@code java.lang.invoke.VarHandle},
 * directly or indirectly: it is the fallback for runtimes that lack that type,
 * and naming it here would make this class fail to link on exactly those
 * runtimes. Keeping the two implementations in separate classes is what makes
 * the fallback path safe; see {@link AvroVarHandleUtil} for the full pattern.
 *
 * @since 3.3
 */
public final class AvroByteShiftUtil
{
    private AvroByteShiftUtil() { }

    /**
     * Reads 4 bytes starting at given offset as a little-endian {@code int}.
     * Caller MUST have verified that {@code offset+4} is within bounds of
     * given array.
     */
    public static int getIntLE(byte[] buffer, int offset) {
        return (buffer[offset] & 0xFF)
                | ((buffer[offset+1] & 0xFF) << 8)
                | ((buffer[offset+2] & 0xFF) << 16)
                | ((buffer[offset+3] & 0xFF) << 24);
    }

    /**
     * Reads 8 bytes starting at given offset as a little-endian {@code long}.
     * Caller MUST have verified that {@code offset+8} is within bounds of
     * given array.
     */
    public static long getLongLE(byte[] buffer, int offset) {
        // the two 32-bit halves combine to exactly a little-endian 8-byte read
        final int i1 = getIntLE(buffer, offset);
        final int i2 = getIntLE(buffer, offset+4);
        return (((long) i1) & 0xFFFFFFFFL) | (((long) i2) << 32);
    }
}
