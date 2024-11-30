package tools.jackson.dataformat.cbor;

import tools.jackson.core.FormatFeature;

/**
 * Enumeration that defines all togglable features for CBOR generator.
 *<p>
 * NOTE: in Jackson 2.x this was named {@code CBORParser.Feature}.
 */
public enum CBORWriteFeature implements FormatFeature
{
    /**
     * Feature that determines whether generator should try to use smallest
     * (size-wise) integer representation: if true, will use smallest
     * representation that is enough to retain value; if false, will use
     * length indicated by argument type (4-byte for <code>int</code>,
     * 8-byte for <code>long</code> and so on).
     */
    WRITE_MINIMAL_INTS(true),

    /**
     * Feature that determines whether CBOR "Self-Describe Tag" (value
     * 55799, encoded as 3-byte sequence of <code>0xD9, 0xD9, 0xF7</code>)
     * should be written at the beginning of document or not.
     * <p>
     * Default value is {@code false} meaning that type tag will not be
     * written at the beginning of a new document.
     */
    WRITE_TYPE_HEADER(false),

    /**
     * Feature that determines if an invalid surrogate encoding found in the
     * incoming String should fail with an exception or silently be output
     * as the Unicode 'REPLACEMENT CHARACTER' (U+FFFD) or not; if not,
     * an exception will be thrown to indicate invalid content.
     * <p>
     * Default value is {@code false} (for backwards compatibility) meaning that
     * an invalid surrogate will result in exception ({@code StreamWriteException}).
     */
    LENIENT_UTF_ENCODING(false),

    /**
     * Feature that determines if string references are generated based on the
     * <a href="http://cbor.schmorp.de/stringref">stringref</a>) extension. This can save
     * storage space, parsing time, and pool string memory when parsing. Readers of the output
     * must also support the stringref extension to properly decode the data. Extra overhead may
     * be added to generation time and memory usage to compute the shared binary and text
     * strings.
     * <p>
     * Default value is {@code false} meaning that the stringref extension will not be used.
     */
    STRINGREF(false),

    /**
     * Feature that determines whether generator should try to write doubles
     * as floats: if {@code true}, will write a {@code double} as a 4-byte float if no
     * precision loss will occur; if {@code false}, will always write a {@code double}
     * as an 8-byte double.
     * <p>
     * Default value is {@code false} meaning that doubles will always be written as
     * 8-byte values.
     */
    WRITE_MINIMAL_DOUBLES(false),
    ;

    private final boolean _defaultState;
    private final int _mask;

    /**
     * Method that calculates bit set (flags) of all features that are
     * enabled by default.
     */
    public static int collectDefaults() {
        int flags = 0;
        for (CBORWriteFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }

    private CBORWriteFeature(boolean defaultState) {
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
