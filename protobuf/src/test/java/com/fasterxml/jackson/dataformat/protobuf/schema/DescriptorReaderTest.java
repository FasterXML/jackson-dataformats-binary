package com.fasterxml.jackson.dataformat.protobuf.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.squareup.protoparser.ProtoFile;

import javax.xml.bind.DatatypeConverter;
import java.util.Map;

public class DescriptorReaderTest extends ProtobufTestBase
{
    public void testParsing() throws Exception
    {
        DescriptorReader reader = new DescriptorReader();
        assertNotNull(reader);
        Map<String, ProtoFile> protoFileMap = reader.readFileDescriptorSet("main.desc");
        NativeProtobufSchema nativeSchema = NativeProtobufSchema.construct(protoFileMap.get("main.proto"));

        ProtobufSchema schema = nativeSchema.forType("main1");
        System.out.println(schema.toString());
        assertNotNull(schema);

    }
}

