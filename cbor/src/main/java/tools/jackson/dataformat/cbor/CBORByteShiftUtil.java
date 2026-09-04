package tools.jackson.dataformat.cbor;

/**
 * Byte-shifting fallback for reading and writing multi-byte primitives on byte
 * arrays, used on runtimes where {@link CBORVarHandleUtil} is unusable.
 *<p>
 * IMPORTANT: this class must NOT reference {@code java.lang.invoke.VarHandle},
 * directly or indirectly: it is the fallback for runtimes that lack that type,
 * and naming it here would make this class fail to link on exactly those
 * runtimes. Keeping the two implementations in separate classes is what makes
 * the fallback path safe; see {@link CBORVarHandleUtil} for the full pattern.
 *
 * @since 3.3
 */
final class CBORByteShiftUtil
{
    private CBORByteShiftUtil() { }

    /**
     * Reads 4 bytes starting at given offset as a big-endian {@code int}.
     * Caller MUST have verified that {@code offset+4} is within bounds of
     * given array.
     */
    static int getIntBE(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFF) << 24)
                | ((buffer[offset+1] & 0xFF) << 16)
                | ((buffer[offset+2] & 0xFF) << 8)
                | (buffer[offset+3] & 0xFF);
    }

    /**
     * Reads 8 bytes starting at given offset as a big-endian {@code long}.
     * Caller MUST have verified that {@code offset+8} is within bounds of
     * given array.
     */
    static long getLongBE(byte[] buffer, int offset) {
        // the two 32-bit halves combine to exactly a big-endian 8-byte read
        final int i1 = getIntBE(buffer, offset);
        final int i2 = getIntBE(buffer, offset+4);
        return (((long) i1) << 32) | (((long) i2) & 0xFFFFFFFFL);
    }

    /**
     * Writes given {@code int} as 4 big-endian bytes at given offset; caller
     * MUST have verified that {@code offset+4} is within bounds of given array.
     */
    static void setIntBE(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >> 24);
        buffer[offset+1] = (byte) (value >> 16);
        buffer[offset+2] = (byte) (value >> 8);
        buffer[offset+3] = (byte) value;
    }

    /**
     * Writes given {@code long} as 8 big-endian bytes at given offset; caller
     * MUST have verified that {@code offset+8} is within bounds of given array.
     */
    static void setLongBE(byte[] buffer, int offset, long value) {
        setIntBE(buffer, offset, (int) (value >> 32));
        setIntBE(buffer, offset+4, (int) value);
    }
}
