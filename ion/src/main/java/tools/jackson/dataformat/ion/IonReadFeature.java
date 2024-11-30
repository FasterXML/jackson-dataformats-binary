package tools.jackson.dataformat.ion;

import tools.jackson.core.FormatFeature;

/**
 * Enumeration that defines all togglable features for Ion parsers.
 *<p>
 * NOTE: in Jackson 2.x this was named {@code IonParser.Feature}.
 */
public enum IonReadFeature implements FormatFeature
{
    /**
     * Whether to expect Ion native Type Id construct for indicating type (true);
     * or "generic" type property (false) when deserializing.
     *<p>
     * Enabled by default.
     *
     * @see <a href="https://amzn.github.io/ion-docs/docs/spec.html#annot">The Ion Specification</a>
     */
    USE_NATIVE_TYPE_ID(true),
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
        for (IonReadFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }

    private IonReadFeature(boolean defaultState) {
        _defaultState = defaultState;
        _mask = (1 << ordinal());
    }

    @Override
    public boolean enabledByDefault() { return _defaultState; }
    @Override
    public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
    @Override
    public int getMask() { return _mask; }
}
