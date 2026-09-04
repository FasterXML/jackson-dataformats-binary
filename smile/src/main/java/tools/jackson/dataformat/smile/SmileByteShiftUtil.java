package tools.jackson.dataformat.smile;

/**
 * Byte-shifting fallback for reading multi-byte primitives out of byte arrays,
 * used on runtimes where {@link SmileVarHandleUtil} is unusable.
 *<p>
 * IMPORTANT: this class must NOT reference {@code java.lang.invoke.VarHandle},
 * directly or indirectly: it is the fallback for runtimes that lack that type,
 * and naming it here would make this class fail to link on exactly those
 * runtimes. Keeping the two implementations in separate classes is what makes
 * the fallback path safe; see {@link SmileVarHandleUtil} for the full pattern.
 *
 * @since 3.3
 */
final class SmileByteShiftUtil
{
    private SmileByteShiftUtil() { }

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
}
