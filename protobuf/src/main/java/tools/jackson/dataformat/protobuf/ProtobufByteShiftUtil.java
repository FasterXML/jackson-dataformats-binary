package tools.jackson.dataformat.protobuf;

/**
 * Byte-shifting fallback for reading and writing multi-byte primitives on byte
 * arrays, used on runtimes where {@link ProtobufVarHandleUtil} is unusable.
 *<p>
 * NOTE: accessors here are LITTLE-endian, unlike the big-endian ones CBOR needs:
 * protobuf encodes its {@code fixed32}/{@code fixed64} types (and hence
 * {@code float}/{@code double}) as little-endian.
 *<p>
 * IMPORTANT: this class must NOT reference {@code java.lang.invoke.VarHandle},
 * directly or indirectly: it is the fallback for runtimes that lack that type,
 * and naming it here would make this class fail to link on exactly those
 * runtimes. Keeping the two implementations in separate classes is what makes
 * the fallback path safe; see {@link ProtobufVarHandleUtil} for the full pattern.
 *
 * @since 3.3
 */
final class ProtobufByteShiftUtil
{
    private ProtobufByteShiftUtil() { }

    /**
     * Reads 4 bytes starting at given offset as a little-endian {@code int}.
     * Caller MUST have verified that {@code offset+4} is within bounds of
     * given array.
     */
    static int getIntLE(byte[] buffer, int offset) {
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
    static long getLongLE(byte[] buffer, int offset) {
        // the two 32-bit halves combine to exactly a little-endian 8-byte read
        final int i1 = getIntLE(buffer, offset);
        final int i2 = getIntLE(buffer, offset+4);
        return (((long) i1) & 0xFFFFFFFFL) | (((long) i2) << 32);
    }

    /**
     * Writes given {@code int} as 4 little-endian bytes at given offset; caller
     * MUST have verified that {@code offset+4} is within bounds of given array.
     */
    static void setIntLE(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) value;
        buffer[offset+1] = (byte) (value >> 8);
        buffer[offset+2] = (byte) (value >> 16);
        buffer[offset+3] = (byte) (value >> 24);
    }

    /**
     * Writes given {@code long} as 8 little-endian bytes at given offset; caller
     * MUST have verified that {@code offset+8} is within bounds of given array.
     */
    static void setLongLE(byte[] buffer, int offset, long value) {
        setIntLE(buffer, offset, (int) value);
        setIntLE(buffer, offset+4, (int) (value >> 32));
    }
}
