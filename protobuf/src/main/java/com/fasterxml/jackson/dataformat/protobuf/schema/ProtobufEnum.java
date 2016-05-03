package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.Map;

public class ProtobufEnum
{
    protected final String _name;

    protected final Map<String,Integer> _valuesByName;

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
}
