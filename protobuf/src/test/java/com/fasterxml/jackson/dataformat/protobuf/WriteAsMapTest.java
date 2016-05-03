package com.fasterxml.jackson.dataformat.protobuf;

import java.util.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.*;

public class WriteAsMapTest extends ProtobufTestBase
{
    final static String PROTOC = 
            "package tutorial;\n"
+"message Person {\n"
+" required string name = 1;\n"
+"  required int32 id = 2;\n"
+"  optional string email = 3;\n"
+"  enum PhoneType {\n"
+"    MOBILE = 0;\n"
+"    HOME = 1;\n"
+"    WORK = 2;\n"
+"  }\n"
+"  message PhoneNumber {\n"
+"   required string number = 1;\n"
+"   optional PhoneType type = 2 [default = HOME];\n"
+"  }\n"
+"  repeated PhoneNumber phone = 4;\n"
+"}\n"
+"message AddressBook {\n"
+"  repeated Person person = 1;\n"
+"}"
            ;


    public void testWriteAsMap() throws Exception
    {
        ObjectMapper mapper = new ProtobufMapper();

        NativeProtobufSchema fileSchema = ProtobufSchemaLoader.std.parseNative(PROTOC);
        ProtobufSchema schema = fileSchema.forType("Person");

        final ObjectWriter w = mapper.writer(schema);

        List<Map<String,Object>> phones = new ArrayList<>();
        
        Map<String,Object> phone1 = new HashMap<>();
        phone1.put("type", 1);
        phone1.put("number", "123456");
        phones.add(phone1);

        Map<String,Object> phone2 = new HashMap<>();
        phone2.put("type", 0);
        phone2.put("number", "654321");
        phones.add(phone2);

        Map<String,Object> person = new LinkedHashMap<String,Object>(); // it is ok if using HashMap. 
        person.put("id", 1111);
        person.put("name", "aaaa");
        person.put("phone", phones);

        byte[] bytes = w.writeValueAsBytes(person);

        // should add up to 33 bytes; with bug, less written
        assertEquals(33, bytes.length);

        // read
        ObjectReader r =  mapper.readerFor(HashMap.class).with(schema);

        @SuppressWarnings("unchecked")
        Map<String,Object> person2 = (Map<String,Object>) r.readValue(bytes);
        assertEquals(person, person2);
    }
}
