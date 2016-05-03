package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.*;

import com.squareup.protoparser.DataType;

public class FieldTypes
{
    private final static FieldTypes instance = new FieldTypes();

    private final EnumMap<DataType.ScalarType, FieldType> _types;

    private FieldTypes()
    {
        _types = new EnumMap<DataType.ScalarType, FieldType>(DataType.ScalarType.class);
        // Note: since ENUM and MESSAGE have no aliases, they won't be mapped here
        for (FieldType type : FieldType.values()) {
            for (DataType.ScalarType id : type.aliases()) {
                _types.put(id, type);
            }
        }
    }
    
    public static FieldType findType(DataType rawType) {
        return instance._findType(rawType);
    }

    private FieldType _findType(DataType rawType) {
        if (rawType instanceof DataType.ScalarType) {
            return instance._types.get(rawType);
        }
        return null;
    }
}
