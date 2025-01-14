package com.fasterxml.jackson.dataformat.protobuf.tofix;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.testutil.failure.JacksonTestFailureExpected;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GenerateNestedType73Test extends ProtobufTestBase
{
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = new ProtobufMapper();

    // [dataformats-binary#68]
    @JacksonTestFailureExpected
    @Test
    public void testNestedTypes() throws Exception
    {
        final String SCHEMA_STR =
"        package mypackage;\n"
+"        message t1 {\n"
+"                message i1 {\n"
+"                        optional uint32 x = 1;\n"
+"                        optional uint32 y = 2;\n"
+"                }\n"
+"        }\n"
+"        message t2 {\n"
+"                optional t1.i1 z = 1;\n"
+"        }\n"
                ;

        ProtobufSchema schema = MAPPER.schemaLoader()
                .load(new StringReader(SCHEMA_STR), "t2");
        assertNotNull(schema);
    }
}
