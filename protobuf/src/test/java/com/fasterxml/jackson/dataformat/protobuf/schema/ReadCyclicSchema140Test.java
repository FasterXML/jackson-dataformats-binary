package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.List;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.*;

public class ReadCyclicSchema140Test extends ProtobufTestBase
{
    final protected static String PROTOC_CYCLIC =
            "message Front {\n"
            +" optional string id = 1;\n"
            +" optional Back next = 2;\n"
            +"}\n"
            +"message Back {\n"
            +" optional string id = 1;\n"
            +" optional string extra = 2;\n"
            +" optional Front next = 3;\n"
            +"}\n"
    ;

    public void testCyclicDefinition() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_CYCLIC);
        assertNotNull(schema);
        List<String> all = schema.getMessageTypes();
        assertEquals(2, all.size());
        assertEquals("Front", all.get(0));
        assertEquals("Back", all.get(1));
        ProtobufMessage msg = schema.getRootType();
        assertEquals("Front", msg.getName());
        assertEquals(2, msg.getFieldCount());
        ProtobufField f = msg.field("id");
        assertNotNull(f);
        assertEquals("id", f.name);

        _verifyMessageFieldLinking(schema.getRootType());
    }

}
