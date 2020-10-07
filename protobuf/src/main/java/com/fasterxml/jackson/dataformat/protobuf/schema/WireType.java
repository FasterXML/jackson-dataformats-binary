package com.fasterxml.jackson.dataformat.protobuf.schema;

/**
 * Enumeration of wire types that protobuf specification defines
 */
public interface WireType
{
    int VINT = 0;
    int FIXED_64BIT = 1;
    int LENGTH_PREFIXED = 2;
    int GROUP_START = 3;
    int GROUP_END = 4;
    int FIXED_32BIT = 5;
}
