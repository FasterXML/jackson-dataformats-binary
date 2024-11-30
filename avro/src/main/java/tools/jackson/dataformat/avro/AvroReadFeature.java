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
    AVRO_BUFFERING(true)
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
