package com.fasterxml.jackson.dataformat.protobuf.schema;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.squareup.protoparser.DataType;
import com.squareup.protoparser.DataType.ScalarType;
import com.squareup.protoparser.EnumConstantElement;
import com.squareup.protoparser.EnumElement;
import com.squareup.protoparser.FieldElement;
import com.squareup.protoparser.MessageElement;
import com.squareup.protoparser.OptionElement;
import com.squareup.protoparser.ProtoFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DescriptorReader
{
    private final String DESCRIPTOR_PROTO = "/descriptor.proto";
    private final ProtobufMapper descriptorMapper;
    private final ProtobufSchema descriptorFileSchema;

    public DescriptorReader() throws IOException
    {
        // read Descriptor Proto
        descriptorMapper = new ProtobufMapper();
        InputStream in = getClass().getResourceAsStream(DESCRIPTOR_PROTO);
        descriptorFileSchema = ProtobufSchemaLoader.std.load(in, "FileDescriptorSet");
        in.close();
    }

    public Map<String, ProtoFile> readFileDescriptorSet(String descriptorFilePath) throws IOException
    {
        InputStream fin;
        fin = this.getClass().getClassLoader().getResourceAsStream(descriptorFilePath);
        if (fin == null) {
            URL url = new URL(descriptorFilePath);
            fin = url.openConnection().getInputStream();
        }

        JsonNode descriptorFile = descriptorMapper.readerFor(JsonNode.class)
                                                  .with(descriptorFileSchema)
                                                  .readValue(fin);

        JsonNode fileDescriptorSet = descriptorFile.get("file");
        Map<String, JsonNode> fileDescMap = new HashMap<>();

        for (JsonNode proto : fileDescriptorSet) {
            String protoName = proto.get("name").asText();
            fileDescMap.put(protoName, proto);
        }
        return buildProtoFileMap(fileDescMap);
    }

    private Map<String, ProtoFile> buildProtoFileMap(Map<String, JsonNode> fileDescMap)
    {
        Map<String, ProtoFile> protoFileMap = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : fileDescMap.entrySet()) {
            String name = entry.getKey();
            JsonNode proto = entry.getValue();
            ProtoFile protoFile = buildProtoFile(proto, fileDescMap);
            protoFileMap.put(name, protoFile);
        }
        return protoFileMap;
    }

    private ProtoFile buildProtoFile(JsonNode fileDescriptor, Map<String, JsonNode> fileDescMap)
    {
        String filePath = fileDescriptor.get("name").asText();
        String packageName = fileDescriptor.get("package").asText();

        ProtoFile.Builder builder = ProtoFile.builder(filePath);
        builder.syntax(ProtoFile.Syntax.PROTO_3); // FIXME: does the desc file has the syntax version?  if not, can proto2 be interpreted as proto3?
        builder.packageName(packageName);

        // dependency file names.
        if (fileDescriptor.has("dependency")) {
            for (JsonNode n : fileDescriptor.get("dependency")) {
                JsonNode dep = fileDescMap.get(n.asText());
                for(JsonNode mt: dep.get("message_type")) {
                    MessageElement me = buildMessageElement(mt);
                    builder.addType(me);
                }
            }
        }

        // FIXME: public dependency file names.
//        if (fileDescriptor.has("public_dependency")) {
//            for (JsonNode n : fileDescriptor.get("public_dependency")) {
//                String dep = fileDescriptor.get("dependency").get(n.asInt()).asText();
//                builder.addPublicDependency(dep);
//            }
//        }

        // types
        for (JsonNode n : fileDescriptor.get("message_type")) {
            MessageElement me = buildMessageElement(n);
            builder.addType(me);
        }

        // FIXME: implement following features
        // services
        // extendDeclarations
        // options

        return builder.build();
    }


    /**
     * Register field types, and declare message types at the same time.
     */
    private MessageElement buildMessageElement(JsonNode m)
    {
        MessageElement.Builder messageElementBuilder = MessageElement.builder();
        messageElementBuilder.name(m.get("name").asText());
        // fields
        if (m.has("field")) {
            for (JsonNode f : m.get("field")) {
                DataType dataType;
                String fieldName = f.get("name").asText();
                String type = f.get("type").asText();
                FieldElement.Label label = FieldElement.Label.valueOf(f.get("label")
                                                                       .asText()
                                                                       .substring(6));
                // message and enum fields are named fields
                if (type.equals("TYPE_MESSAGE") || type.equals("TYPE_ENUM")) {
                    String fullyQualifiedtypeName = f.get("type_name").asText(); // fully qualified name including package name.
                    String typeName = fullyQualifiedtypeName.substring(fullyQualifiedtypeName.indexOf(".", 2) + 1);
                    dataType = DataType.NamedType.create(typeName);
                } else {
                    dataType = ScalarType.valueOf(type.substring(5));
                }

                // build field
                FieldElement.Builder fieldBuilder = FieldElement
                    .builder()
                    .name(fieldName)
                    .type(dataType)
                    .label(label)
                    .tag(f.get("number").asInt());

                // add field options to the field
                for (String optionFieldName : Arrays.asList("json_name")) {
                    if (f.has(optionFieldName)) {
                        JsonNode o = f.get(optionFieldName);
                        OptionElement.Kind kind = OptionElement.Kind.STRING;
                        OptionElement option = OptionElement.create(optionFieldName, kind, o.asText());
                        fieldBuilder.addOption(option);
                    }
                }

                if (f.has("options")) {
                    JsonNode os = f.get("options");
                    for (String optionFieldName : Arrays.asList("packed", "default")) {
                        if (os.has(optionFieldName)) {
                            JsonNode o = os.get(optionFieldName);
                            OptionElement.Kind kind = OptionElement.Kind.STRING;
                            OptionElement option = OptionElement.create(optionFieldName, kind, o.asText());
                            fieldBuilder.addOption(option);
                        }
                    }
                }
                // add the field to the message
                messageElementBuilder.addField(fieldBuilder.build());
            }
        }

        // message type declarations
        if (m.has("nested_type")) {
            for (JsonNode n : m.get("nested_type")) {
                messageElementBuilder.addType(buildMessageElement(n));
            }
        }

        // enum declarations
        if (m.has("enum_type")) {
            for (JsonNode e : m.get("enum_type")) {
                EnumElement.Builder nestedEnumElement = EnumElement
                    .builder()
                    .name(e.get("name").asText());
                for (JsonNode v : e.get("value")) {
                    EnumConstantElement.Builder c = EnumConstantElement.builder()
                                                                       .name(v.get("name").asText())
                                                                       .tag(v.get("number").asInt());
                    nestedEnumElement.addConstant(c.build());
                }
                messageElementBuilder.addType(nestedEnumElement.build());
            }
        }
        return messageElementBuilder.build();
    }


}
