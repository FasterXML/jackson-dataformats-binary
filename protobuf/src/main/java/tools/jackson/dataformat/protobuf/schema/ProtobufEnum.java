package tools.jackson.dataformat.protobuf.schema;

import java.util.Map;

public class ProtobufEnum
{
    protected final String _name;

    protected final Map<String,Integer> _valuesByName;

    protected final String _defaultValue;

    protected final int _defaultIndex;

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
        if (valuesByName.isEmpty()) {
            _defaultValue = null;
            _defaultIndex = -1;
        } else {
            Map.Entry<String,Integer> first = valuesByName.entrySet().iterator().next();
            _defaultValue = first.getKey();
            _defaultIndex = first.getValue().intValue();
        }
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

    public String getDefaultValue() {
        return _defaultValue;
    }

    public int getDefaultIndex() {
        return _defaultIndex;
    }
}
