package com.fasterxml.jackson.dataformat.protobuf.schema;

/**
 * Enumeration of wire types that protobuf specification defines
 */
public interface WireType
{
    public final static int VINT = 0;
    public final static int FIXED_64BIT = 1;
    public final static int LENGTH_PREFIXED = 2;
    public final static int GROUP_START = 3;
    public final static int GROUP_END = 4;
    public final static int FIXED_32BIT = 5;
}
