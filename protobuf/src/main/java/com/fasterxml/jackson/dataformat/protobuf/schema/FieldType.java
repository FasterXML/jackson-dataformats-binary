package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.Arrays;

import com.squareup.protoparser.DataType;
import com.squareup.protoparser.DataType.ScalarType;

/**
 * Set of distinct types parsed from protoc, as unified considering
 * that Java makes no distinction between signed and unsigned types.
 */
public enum FieldType
{
 /*   
    enum ScalarType implements DataType {
        ANY,
        BOOL,
        BYTES,
        DOUBLE,
        FLOAT,
        FIXED32,
        FIXED64,
        INT32,
        INT64,
        SFIXED32,
        SFIXED64,
        SINT32,
        SINT64,
        STRING,
        UINT32,
        UINT64;
    }
    */
    
    DOUBLE(WireType.FIXED_64BIT, ScalarType.DOUBLE), // fixed-length 64-bit double
    FLOAT(WireType.FIXED_32BIT, ScalarType.FLOAT), // fixed-length, 32-bit single precision
    VINT32_Z(WireType.VINT, ScalarType.SINT32), // variable length w/ ZigZag, intended as 32-bit
    VINT64_Z(WireType.VINT, ScalarType.SINT64), // variable length w/ ZigZag, intended as 64-bit
    VINT32_STD(WireType.VINT, ScalarType.INT32, ScalarType.UINT32), // variable length, intended as 32-bit
    VINT64_STD(WireType.VINT, ScalarType.INT64, ScalarType.UINT64), // variable length, intended as 64-bit

    FIXINT32(WireType.FIXED_32BIT, ScalarType.FIXED32, ScalarType.SFIXED32), // fixed length, 32-bit int
    FIXINT64(WireType.FIXED_64BIT, ScalarType.FIXED64, ScalarType.SFIXED64), // fixed length, 64-bit int
    BOOLEAN(WireType.VINT, ScalarType.BOOL),
    STRING(WireType.LENGTH_PREFIXED, ScalarType.STRING),
    BYTES(WireType.LENGTH_PREFIXED, ScalarType.BYTES), // byte array
    ENUM(WireType.VINT), // encoded as vint
    MESSAGE(WireType.LENGTH_PREFIXED) // object
    ;

    private final int _wireType;
    
    private final  DataType.ScalarType[] _aliases;

    private FieldType(int wt, DataType.ScalarType... aliases) {
        _wireType = wt;
        _aliases = aliases;
    }

    public int getWireType() { return _wireType; }

    public boolean usesZigZag() {
        return (this == VINT32_Z) || (this == VINT64_Z);
    }
    
    public Iterable< DataType.ScalarType> aliases() {
        return Arrays.asList(_aliases);
    }
}
