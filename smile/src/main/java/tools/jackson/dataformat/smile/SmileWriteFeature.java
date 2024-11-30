package tools.jackson.dataformat.smile;

import tools.jackson.core.FormatFeature;

/**
 * Enumeration that defines all togglable features for Smile generators.
 *<p>
 * NOTE: in Jackson 2.x this was named {@code AvroGenerator.Feature}.
 */
public enum SmileWriteFeature
    implements FormatFeature
{
    /**
     * Whether to write 4-byte header sequence when starting output or not.
     * If disabled, no header is written; this may be useful in embedded cases
     * where context is enough to know that content is encoded using this format.
     * Note, however, that omitting header means that default settings for
     * shared names/string values can not be changed.
     *<p>
     * Default setting is true, meaning that header will be written.
     */
    WRITE_HEADER(true),

    /**
     * Whether write byte marker that signifies end of logical content segment
     * ({@link SmileConstants#BYTE_MARKER_END_OF_CONTENT}) when
     * {@link SmileParser#close} is called or not. This can be useful when outputting
     * multiple adjacent logical content segments (documents) into single
     * physical output unit (file).
     *<p>
     * Default setting is false meaning that such marker is not written.
     */
    WRITE_END_MARKER(false),

    /**
     * Whether to use simple 7-bit per byte encoding for binary content when output.
     * This is necessary ensure that byte 0xFF will never be included in content output.
     * For other data types this limitation is handled automatically. This setting is enabled
     * by default, however, overhead for binary data (14% size expansion, processing overhead)
     * is non-negligible. If no binary data is output, feature has no effect.
     *<p>
     * Default setting is true, indicating that binary data is quoted as 7-bit bytes
     * instead of written raw.
     */
    ENCODE_BINARY_AS_7BIT(true),

    /**
     * Whether generator should check if it can "share" property names during generating
     * content or not. If enabled, can replace repeating property names with back references,
     * which are more compact and should faster to decode. Downside is that there is some
     * overhead for writing (need to track existing values, check), as well as decoding.
     *<p>
     * Since property names tend to repeat quite often, this setting is enabled by default.
     */
    CHECK_SHARED_NAMES(true),

    /**
     * Whether generator should check if it can "share" short (at most 64 bytes encoded)
     * String value during generating
     * content or not. If enabled, can replace repeating Short String values with back references,
     * which are more compact and should faster to decode. Downside is that there is some
     * overhead for writing (need to track existing values, check), as well as decoding.
     *<p>
     * Since efficiency of this option depends a lot on type of content being produced,
     * this option is disabled by default, and should only be enabled if it is likely that
     * same values repeat relatively often.
     */
    CHECK_SHARED_STRING_VALUES(false),

    /**
     * Feature that determines if an invalid surrogate encoding found in the
     * incoming String should fail with an exception or silently be output
     * as the Unicode 'REPLACEMENT CHARACTER' (U+FFFD) or not; if not,
     * an exception will be thrown to indicate invalid content.
     *<p>
     * Default value is {@code false} (for backwards compatibility) meaning that
     * an invalid surrogate will result in exception ({@code StreamWriteException}).
     *
     * @since 2.13
     */
    LENIENT_UTF_ENCODING(false),
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
        for (SmileWriteFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }

    private SmileWriteFeature(boolean defaultState) {
        _defaultState = defaultState;
        _mask = (1 << ordinal());
    }

    @Override public boolean enabledByDefault() { return _defaultState; }
    @Override public int getMask() { return _mask; }
    @Override public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
}
