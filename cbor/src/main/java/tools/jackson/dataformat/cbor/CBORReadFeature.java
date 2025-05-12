package tools.jackson.dataformat.cbor;

import tools.jackson.core.FormatFeature;
import tools.jackson.core.JsonToken;

/**
 * Enumeration that defines all togglable features for CBOR parser.
 *<p>
 * NOTE: in Jackson 2.x this was named {@code CBORParser.Feature}.
 */
public enum CBORReadFeature implements FormatFeature
{
    /**
     * Feature that determines how binary tagged negative BigInteger values are
     * decoded: either assuming CBOR standard encoding logic (as per spec),
     * or the legacy Jackson encoding logic (encoding up to Jackson 2.19).
     * When enabled, ensures proper encoding of negative values
     * (e.g., {@code [0xC3, 0x41, 0x00]} is decoded as -1)
     * When disabled, maintains compatible behavior to versions prior to 3.0.
     * (e.g., {@code [0xC3, 0x41, 0x00]} is decoded as 0).
     *<p>
     * Note that there is the counterpart
     * {@link CBORWriteFeature#ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING}
     * for encoding.
     *<p>
     * The default value is {@code true} in Jackson 3.x (was {@code false} in Jackson 2.x).
     */
    DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING(true),

    /**
     * Feature that determines how an {@code undefined} value ({@code 0xF7}) is exposed
     * by parser.
     * <p>
     * When enabled, the parser returns {@link JsonToken#VALUE_EMBEDDED_OBJECT} with
     * a value of {@code null}, allowing the caller to distinguish {@code undefined} from actual
     * {@link JsonToken#VALUE_NULL}.
     * When disabled, {@code undefined} value is reported as simple {@link JsonToken#VALUE_NULL}.
     *<p>
     * The default value is {@code true} in Jackson 3.x (was {@code false} in Jackson 2.x).
     */
    READ_UNDEFINED_AS_EMBEDDED_OBJECT(true),

    /**
     * Feature that determines how a CBOR "simple value" of major type 7 is exposed by parser.
     * <p>
     * When enabled, the parser returns {@link JsonToken#VALUE_EMBEDDED_OBJECT} with
     * an embedded value of type {@link CBORSimpleValue}, allowing the caller to distinguish
     * these values from actual {@link JsonToken#VALUE_NUMBER_INT}s.
     * When disabled, simple values are returned as {@link JsonToken#VALUE_NUMBER_INT}s.
     *<p>
     * The default value is {@code true} in Jackson 3.x (was {@code false} in Jackson 2.x).
     */
    READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT(true)
    ;

    private final boolean _defaultState;
    private final int _mask;

    /**
     * Method that calculates bit set (flags) of all features that are
     * enabled by default.
     */
    public static int collectDefaults() {
        int flags = 0;
        for (CBORReadFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }

    private CBORReadFeature(boolean defaultState) {
        _defaultState = defaultState;
        _mask = (1 << ordinal());
    }

    @Override
    public boolean enabledByDefault() {
        return _defaultState;
    }

    @Override
    public boolean enabledIn(int flags) {
        return (flags & getMask()) != 0;
    }

    @Override
    public int getMask() {
        return _mask;
    }
}
