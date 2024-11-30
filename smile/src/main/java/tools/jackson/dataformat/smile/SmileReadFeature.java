package tools.jackson.dataformat.smile;

import tools.jackson.core.FormatFeature;

/**
 * Enumeration that defines all togglable features for Smile parsers.
 *<p>
 * NOTE: in Jackson 2.x this was named {@code SmileParser.Feature}.
 */
public enum SmileReadFeature implements FormatFeature
{
    /**
     * Feature that determines whether 4-byte Smile header is mandatory in input,
     * or optional. If enabled, it means that only input that starts with the header
     * is accepted as valid; if disabled, header is optional. In latter case,
     * settings for content are assumed to be defaults.
     */
    REQUIRE_HEADER(true)
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
        for (SmileReadFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }

    private SmileReadFeature(boolean defaultState) {
        _defaultState = defaultState;
        _mask = (1 << ordinal());
    }

    @Override public boolean enabledByDefault() { return _defaultState; }
    @Override public int getMask() { return _mask; }
    @Override public boolean enabledIn(int flags) { return (flags & getMask()) != 0; }
}
