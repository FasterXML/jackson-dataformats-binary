package tools.jackson.dataformat.protobuf.schema;

import java.util.Map;

public class ProtobufEnum
{
    protected final String _name;

    protected final Map<String,Integer> _valuesByName;

    /**
     * Name of the first declared enum value: Protobuf's schema-level default;
     * {@code null} if enum has no values.
     *
     * @since 3.3
     */
    protected final String _defaultValueName;

    /**
     * Flag that indicates whether mapping from enum value and id is standard or not;
     * standard means that first enum has value 0, and all following enums have value
     * one bigger than preceding one.
     */
    protected final boolean _standardIndexing;

    public ProtobufEnum(String name, Map<String,Integer> valuesByName, boolean standardIndexing)
    {
        _name = name;
        _valuesByName = valuesByName;
        _standardIndexing = standardIndexing;
        _defaultValueName = valuesByName.isEmpty() ? null
                : valuesByName.keySet().iterator().next();
    }

    public Integer findEnum(String name) {
        return _valuesByName.get(name);
    }

    public Map<String,Integer> valueMapping() {
        return _valuesByName;
    }

    public boolean usesStandardIndexing() {
        return _standardIndexing;
    }

    /**
     * Accessor for the name of the first declared enum value, which is
     * Protobuf's schema-level default.
     *
     * @return Name of the default enum value; {@code null} if enum has no values
     *
     * @since 3.3
     */
    public String getDefaultValueName() {
        return _defaultValueName;
    }
}
