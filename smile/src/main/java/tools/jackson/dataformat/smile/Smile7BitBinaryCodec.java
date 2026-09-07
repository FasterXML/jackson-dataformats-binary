package tools.jackson.dataformat.smile;

/**
 * Helper for Smile's "7-bit safe" binary encoding, which packs 7 payload bytes
 * into 8 output bytes of 7 significant bits each (see
 * {@link SmileWriteFeature#ENCODE_BINARY_AS_7BIT}, enabled by default).
 *<p>
 * Both directions are done with SWAR bit manipulation over a single 8-byte load
 * and a single 8-byte store, replacing the byte-at-a-time shifting the callers
 * would otherwise do. The load and store go through {@link SmileVarHandleUtil};
 * where that is unusable there is nothing to gain (the byte-shifting fallback
 * would be doing exactly the per-byte work we are trying to avoid), so both
 * methods simply report failure and the caller runs its own loop.
 *<p>
 * Note that the SWAR forms mask each input byte to 7 bits. For well-formed Smile
 * content, where the high bit is always clear, that is a no-op. For corrupt
 * content the two paths can differ -- but so does the value they decode to,
 * which is meaningless either way.
 *
 * @since 3.3
 */
final class Smile7BitBinaryCodec
{
    /**
     * Whether {@code VarHandle}-based array access is usable on this runtime;
     * probed once at class load, using the same pattern as
     * {@code SmileParserBase._decodeQuad()}.
     */
    private final static boolean VARHANDLE_AVAILABLE = _checkVarHandleAvailable();

    private static boolean _checkVarHandleAvailable() {
        // NOTE: this call is what first loads `SmileVarHandleUtil`, and that class
        // names `VarHandle` in its field/method signatures. On a runtime without
        // `java.lang.invoke.VarHandle` (some Android builds) loading it raises
        // `NoClassDefFoundError` -- an Error, not an Exception -- so `Throwable`
        // is what has to be caught here.
        try {
            return SmileVarHandleUtil.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private Smile7BitBinaryCodec() { }

    /**
     * Decodes 8 encoded bytes at {@code inPtr} into the 7 payload bytes they
     * represent, writing them at {@code outPtr}.
     *<p>
     * The store writes a full 8 bytes: the 7 wanted ones plus a trailing zero.
     * That byte is either overwritten by the next chunk or lies past the end of
     * the decoded content, but it still has to be inside {@code out} -- hence
     * the bounds check, which also rejects a final chunk that ends flush with
     * the output array.
     *
     * @return {@code true} if the 7 bytes were written; {@code false} if the
     *    caller must decode this chunk itself
     */
    static boolean decodeChunk(byte[] in, int inPtr, byte[] out, int outPtr)
    {
        if (!VARHANDLE_AVAILABLE
                || ((inPtr + 8) > in.length) || ((outPtr + 8) > out.length)) {
            return false;
        }
        long v = SmileVarHandleUtil.getLongBE(in, inPtr);
        // Compact 8 x 7 bits down to 56, doubling the field width each round
        v = ((v & 0x7F007F007F007F00L) >>> 1) | (v & 0x007F007F007F007FL);
        v = ((v & 0x3FFF00003FFF0000L) >>> 2) | (v & 0x00003FFF00003FFFL);
        v = ((v & 0x0FFFFFFF00000000L) >>> 4) | (v &         0x0FFFFFFFL);
        // Left-align the 56 bits so they land in the first 7 bytes written
        SmileVarHandleUtil.setLongBE(out, outPtr, v << 8);
        return true;
    }

    /**
     * Encodes the 7 payload bytes at {@code inPtr} as the 8 bytes of 7
     * significant bits they become, writing them at {@code outPtr}.
     *<p>
     * The load reads a full 8 bytes and discards the last, so -- as in
     * {@link #decodeChunk} -- the extra byte has to be inside {@code in}.
     *
     * @return {@code true} if the 8 bytes were written; {@code false} if the
     *    caller must encode this chunk itself
     */
    static boolean encodeChunk(byte[] in, int inPtr, byte[] out, int outPtr)
    {
        if (!VARHANDLE_AVAILABLE
                || ((inPtr + 8) > in.length) || ((outPtr + 8) > out.length)) {
            return false;
        }
        // Drop the 8th byte: we want the 56 bits of the 7 payload bytes
        long v = SmileVarHandleUtil.getLongBE(in, inPtr) >>> 8;
        // Spread 56 bits back out into 8 x 7, halving the field width each round
        v = ((v & (0x0FFFFFFFL << 28)) << 4) | (v & 0x0FFFFFFFL);
        v = ((v & 0x0FFFC0000FFFC000L) << 2) | (v & 0x00003FFF00003FFFL);
        v = ((v & 0x3F803F803F803F80L) << 1) | (v & 0x007F007F007F007FL);
        SmileVarHandleUtil.setLongBE(out, outPtr, v);
        return true;
    }
}
