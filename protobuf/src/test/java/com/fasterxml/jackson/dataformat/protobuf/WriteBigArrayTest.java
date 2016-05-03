package com.fasterxml.jackson.dataformat.protobuf;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class WriteBigArrayTest extends ProtobufTestBase
{
    final protected static String PROTOC_STRING_ARRAY_SPARSE = "message Strings {\n"
            +" repeated string values = 1;\n"
            +"}\n"
    ;

    final protected static String PROTOC_STRING_ARRAY_PACKED = "message Strings {\n"
            +" repeated string values = 1 [packed=true];\n"
            +"}\n"
    ;

    final protected static String PROTOC_WRAPPED_STRING = "message WrappedStrings {\n"
            +" required Strings values = 2;\n"
            +"}\n"
            +PROTOC_STRING_ARRAY_SPARSE
    ;

    final ProtobufSchema SPARSE_STRING_SCHEMA;
    final ProtobufSchema PACKED_STRING_SCHEMA;

    public WriteBigArrayTest() throws Exception {
        SPARSE_STRING_SCHEMA = ProtobufSchemaLoader.std.parse(PROTOC_STRING_ARRAY_SPARSE);
        PACKED_STRING_SCHEMA = ProtobufSchemaLoader.std.parse(PROTOC_STRING_ARRAY_PACKED);
    }

    static class StringArray {
        public String[] values;

        public StringArray() { }
        public StringArray(List<String> v) {
            values = v.toArray(new String[v.size()]);
        }

        public int size() { return values.length; }
    }
    
    static class StringArrayWrapper
    {
        public StringArray values;

        public StringArrayWrapper() { values = new StringArray(); }
        public StringArrayWrapper(List<String> v) {
            values = new StringArray(v);
        }
    }
    
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testStringArraySparseWithLongValues() throws Exception
    {
        final ObjectMapper mapper = new ObjectMapper(new ProtobufFactory());
        final ObjectWriter w = mapper.writer(SPARSE_STRING_SCHEMA);
        StringBuilder sb = new StringBuilder();
        do {
            sb.append("Jexabel");
        } while (sb.length() < 137);
        final String LONG_NAME = sb.toString();
        final int longLen = LONG_NAME.length();

        List<String> strings = new ArrayList<String>();
        final int COUNT = 260000 / longLen;
        for (int i = 0; i < COUNT; ++i) {
            strings.add(LONG_NAME);
        }
        byte[] bytes = w.writeValueAsBytes(new StringArray(strings));
        int ptr = 0;
        final byte FIRST_LEN_BYTE = (byte) (0x80 + (longLen & 0x7F));
        final byte SECOND_LEN_BYTE = (byte) (longLen >> 7);

        // in case of sparse, same as N copies of a String field
        for (int i = 0; i < COUNT; ++i) {
            byte b = bytes[ptr++];
            if (b != 0xA) {
                fail("Different for String #"+i+", at "+(ptr-1)+", type not 0xA but "+b);
            }
            assertEquals(FIRST_LEN_BYTE, bytes[ptr++]);
            assertEquals(SECOND_LEN_BYTE, bytes[ptr++]);
            for (int x = 0; x < longLen; ++x) {
                assertEquals((byte) LONG_NAME.charAt(x), bytes[ptr++]);
            }
        }
        assertEquals(bytes.length, ptr);
    }

    // and then do something bit more sizable
    public void testStringArraySparseLong() throws Exception
    {
        final int COUNT = 35000;
        final ObjectMapper mapper = new ObjectMapper(new ProtobufFactory());
        final ObjectWriter w = mapper.writer(SPARSE_STRING_SCHEMA);
        List<String> strings = new ArrayList<String>();
        for (int i = 0; i < COUNT; ++i) {
            strings.add("Value"+i);
        }
        byte[] bytes = w.writeValueAsBytes(new StringArray(strings));
        int ptr = 0;

        // in case of sparse, same as N copies of a String field
        for (int i = 0; i < COUNT; ++i) {
            final String str = "Value"+i;
            byte b = bytes[ptr++];
            if (b != 0xA) {
                fail("Different for String #"+i+", at "+(ptr-1)+", type not 0xA but "+b);
            }
            assertEquals(str.length(), bytes[ptr++]);
            for (int x = 0; x < str.length(); ++x) {
                assertEquals((byte) str.charAt(x), bytes[ptr++]);
            }
        }
        assertEquals(bytes.length, ptr);
    }

    public void testStringArrayPackedLong() throws Exception
    {
        final int COUNT = 39600;
        final ObjectMapper mapper = new ObjectMapper(new ProtobufFactory());

        final ObjectWriter w = mapper.writer(PACKED_STRING_SCHEMA);
        List<String> strings = new ArrayList<String>();
        for (int i = 0; i < COUNT; ++i) {
            strings.add("Value"+i);
        }
        byte[] bytes = w.writeValueAsBytes(new StringArray(strings));
        int ptr = 0;
        
        assertEquals(0xA, bytes[ptr++]);

        // big enough to actually require 3 bytes (above 0x3FFF bytes)
        int len = (bytes[ptr] & 0x7F) + ((bytes[ptr+1] & 0x7F) << 7)
                + (bytes[ptr+2] << 14);
        ptr += 3;

        assertEquals(bytes.length - 4, len);
        
        // in case of sparse, same as N copies of a String field
        for (int i = 0; i < COUNT; ++i) {
            final String str = "Value"+i;
            assertEquals(str.length(), bytes[ptr++]);
            for (int x = 0; x < str.length(); ++x) {
                assertEquals((byte) str.charAt(x), bytes[ptr++]);
            }
        }
        assertEquals(bytes.length, ptr);
    }

    public void testWrappedStringArray() throws Exception
    {
        final int COUNT = 39600;
        final ObjectMapper mapper = new ObjectMapper(new ProtobufFactory());
        final ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_WRAPPED_STRING);

        final ObjectWriter w = mapper.writer(schema);
        List<String> strings = new ArrayList<String>();
        for (int i = 0; i < COUNT; ++i) {
            strings.add("Value"+i);
        }
        byte[] bytes = w.writeValueAsBytes(new StringArrayWrapper(strings));

        // read back as well
        StringArrayWrapper result = mapper.readerFor(StringArrayWrapper.class)
                .with(schema)
                .readValue(bytes);
        assertEquals(COUNT, result.values.size());
    }
}
