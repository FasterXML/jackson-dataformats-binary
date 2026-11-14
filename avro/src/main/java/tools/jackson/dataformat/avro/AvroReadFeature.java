package tools.jackson.dataformat.avro;

import tools.jackson.core.FormatFeature;

/**
 * Enumeration that defines all togglable features for Avro parsers.
 *<p>
 * NOTE: in Jackson 2.x this was named {@code AvroParser.Feature}.
 */
public enum AvroReadFeature
    implements FormatFeature
{
    /**
     * Feature that can be disabled to prevent Avro from buffering any more
     * data then absolutely necessary.
     *<p>
     * Enabled by default to preserve the existing behavior.
     */
    AVRO_BUFFERING(true),

    /**
     * Feature that tells parser that each root-level value is in Avro "Single Object
     * Encoding" (as written by {@code BinaryMessageEncoder} of Apache Avro lib):
     * preceded by a header of 2-byte marker ({@code 0xC3 0x01}) and 8-byte
     * fingerprint of the writer schema. Parser fails with
     * {@link tools.jackson.core.exc.StreamReadException} if the marker is wrong, or the
     * fingerprint is not that of the writer schema used for reading
     * (see {@link AvroSchema#getWriterFingerprint()}).
     *<p>
     * Disabled by default, since the header can not be reliably detected:
     * "raw" content may start with the same two bytes.
     *
     * @since 3.4
     */
    READ_SINGLE_OBJECT_ENCODING(false)
    ;

    private final boolean _defaultState;
    private final int _mask;

    /**
     * Method that calculates bit set (flags) of all features that
     * are enabled by default.
     */
    public static int collectDefaults()
    {
        int flags = 0;
        for (AvroReadFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }

    private AvroReadFeature(boolean defaultState) {
        _defaultState = defaultState;
        _mask = (1 << ordinal());
    }

    @Override
    public boolean enabledByDefault() { return _defaultState; }
    @Override
    public int getMask() { return _mask; }
    @Override
    public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
}
