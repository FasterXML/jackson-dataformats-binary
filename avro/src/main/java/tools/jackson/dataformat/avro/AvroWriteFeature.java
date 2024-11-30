package tools.jackson.dataformat.avro;

import tools.jackson.core.FormatFeature;

/**
 * Enumeration that defines all togglable features for Avro generators
 *<p>
 * NOTE: in Jackson 2.x this was named {@code AvroGenerator.Feature}.
 */
public enum AvroWriteFeature
    implements FormatFeature
{
    /**
     * Feature that can be disabled to prevent Avro from buffering any more
     * data then absolutely necessary.
     * This affects buffering by underlying codec.
     * Note that disabling buffer is likely to reduce performance if the underlying
     * input/output is unbuffered.
     *<p>
     * Enabled by default to preserve the existing behavior.
     */
    AVRO_BUFFERING(true),

    /**
     * Feature that tells Avro to write data in file format (i.e. including the schema with the data)
     * rather than the RPC format which is otherwise default
     *<p>
     * NOTE: reader-side will have to be aware of distinction as well, since possible inclusion
     * of this header is not 100% reliably auto-detectable (while header has distinct marker,
     * "raw" Avro content has no limitations and could theoretically have same pre-amble from data).
     */
    AVRO_FILE_OUTPUT(false),

    /**
     * Feature that enables addition of {@code null} as default value in generated schema
     * when no real default value is defined and {@code null} is legal value for type
     * (union type with {@code null} included).
     *<p>
     * Disabled by default.
     *
     * @since 3.0
     * 
     */
    ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA(false)
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
        for (AvroWriteFeature f : values()) {
            if (f.enabledByDefault()) {
                flags |= f.getMask();
            }
        }
        return flags;
    }
    
    private AvroWriteFeature(boolean defaultState) {
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
