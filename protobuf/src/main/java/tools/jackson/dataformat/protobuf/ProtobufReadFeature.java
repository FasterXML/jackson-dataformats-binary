package tools.jackson.dataformat.protobuf;

import tools.jackson.core.FormatFeature;
import tools.jackson.core.JsonToken;

/**
 * Enumeration that defines all togglable features for Protobuf parsers.
 */
public enum ProtobufReadFeature implements FormatFeature
{
    /**
     * Feature that determines whether unknown enum ids are decoded as the
     * schema's default enum value instead of failing immediately at streaming
     * level.
     *<p>
     * The Protobuf enum default is the first declared enum value; this is
     * independent of databind-level annotations such as
     * {@code @JsonEnumDefaultValue}.
     *<p>
     * Feature is disabled by default to preserve existing parser behavior.
     */
    READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE(false)
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
        for (ProtobufReadFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }

    private ProtobufReadFeature(boolean defaultState) {
        _defaultState = defaultState;
        _mask = (1 << ordinal());
    }

    @Override public boolean enabledByDefault() { return _defaultState; }
    @Override public int getMask() { return _mask; }
    @Override public boolean enabledIn(int flags) { return (flags & getMask()) != 0; }
}
