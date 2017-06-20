package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.EnumMap;
import java.util.Map;

import com.squareup.protoparser.DataType;
import com.squareup.protoparser.EnumConstantElement;
import com.squareup.protoparser.EnumElement;
import com.squareup.protoparser.FieldElement;
import com.squareup.protoparser.MessageElement;
import com.squareup.protoparser.OptionElement;
import com.squareup.protoparser.ProtoFile;

/**
 * @since 2.9
 */
public class FileDescriptorSet
{
    protected FileDescriptorProto[] file;

    // for deserializer
    protected FileDescriptorSet() { }

    public FileDescriptorSet(FileDescriptorProto[] f) {
        file = f;
    }

    // needed to "expose" non-public setter without annotations
    public FileDescriptorProto[] getFile() { return file; }

    /**
     * Accessor for finding low-level definition with given name,
     * if one contained.
     *
     * @return Descriptor matching the name, if any; `null` if none
     */
    public FileDescriptorProto findDescriptor(String fileName)
    {
        for (FileDescriptorProto fdp : file) {
            if (fdp.name.equals(fileName)) {
                return fdp;
            }
        }
        return null;
    }

    /**
     * Accessor for getting low-level definition with given name, contained
     * in this descriptor set.
     * 
     * @return Descriptor matching the name, if any; `null` if none
     *
     * @throws IllegalArgumentException if no descriptor with given name found
     */
    public FileDescriptorProto getDescriptor(String fileName)
    {
        FileDescriptorProto p = findDescriptor(fileName);
        if (p == null) {
            throw new IllegalArgumentException(fileName + " not found");
        }
        return p;
    }

    public ProtobufSchema schemaForFirstType()
    {
        ProtoFile protoFile = buildProtoFile(file[0].name);
        return NativeProtobufSchema.construct(protoFile).forFirstType();
    }

    public ProtobufSchema schemaFor(String rootTypeName)
    {
        for (FileDescriptorProto fdp : file) {
            for (DescriptorProto dp : fdp.message_type) {
                if (dp.name.equals(rootTypeName)) {
                    ProtoFile protoFile = buildProtoFile(fdp.name);
                    NativeProtobufSchema nps = NativeProtobufSchema.construct(protoFile);
                    return nps.forType(rootTypeName);
                }
            }
        }
        throw new IllegalArgumentException(rootTypeName + " not found");
    }

    private ProtoFile buildProtoFile(String fileName)
    {
        FileDescriptorProto fdp = getDescriptor(fileName);
        ProtoFile.Builder builder = ProtoFile.builder(fdp.name);
        builder.syntax(fdp.getSyntax());
        builder.packageName(fdp.getPackage());

        // dependency file names.
        if (fdp.dependency != null) {
            for (String dependency : fdp.dependency) {
                FileDescriptorProto dep = getDescriptor(dependency);
                for (DescriptorProto dp : dep.message_type) {
                    MessageElement me = dp.buildMessageElement();
                    builder.addType(me);
                }
            }
        }

        // FIXME: public dependency file names.
//        if (fdp.public_dependency) {
//            for (DescriptorProto dp : fdp.public_dependency) {
//                String dep = fdp.getDependency();
//                builder.addPublicDependency(dep);
//            }
//        }

        // types
        for (DescriptorProto dp : fdp.message_type) {
            MessageElement me = dp.buildMessageElement();
            builder.addType(me);
        }

        // FIXME: implement following features
        // services
        // extendDeclarations
        // options

        return builder.build();
    }

    // POJOs for the .desc file Protobuf
    public static class FileDescriptorProto
    {
        public String name;

        // Need to use different name as `package` is reserved name in Java
        protected String _package; // 'packageName'

        public String[] dependency;
        public int[] public_dependency;
        public int[] weak_dependency;
        public DescriptorProto[] message_type;
        public EnumDescriptorProto[] enum_type;
        public ServiceDescriptorProto[] service;
        public FieldDescriptorProto[] extension;
        public FileOptions options;
        public SourceCodeInfo source_code_info;
        public String syntax;

        public ProtoFile.Syntax getSyntax()
        {
            if (syntax == null) {
                return ProtoFile.Syntax.PROTO_2;
            }
            return ProtoFile.Syntax.valueOf(syntax);
        }

        public void setPackage(String p) { _package = p; }
        public String getPackage() { return _package; }
    }

    public static class DescriptorProto
    {
        public String name;
        public FieldDescriptorProto[] field;
        public FieldDescriptorProto[] extension;
        public DescriptorProto[] nested_type;
        public EnumDescriptorProto[] enum_type;

        static class ExtensionRange
        {
            public int start;
            public int end;
        }

        public ExtensionRange[] extension_range;
        public OneofDescriptorProto[] oneof_decl;
        public MessageOptions options;

        static class ReservedRange
        {
            public int start; // Inclusive.
            public int end;   // Exclusive.
        }

        public ReservedRange[] reserved_range;
        public String[] reserved_name;

        public MessageElement buildMessageElement()
        {
            MessageElement.Builder messageElementBuilder = MessageElement.builder();
            messageElementBuilder.name(name);
            // fields
            if (field != null) {
                for (FieldDescriptorProto f : field) {
                    DataType dataType;
                    String fieldName = f.name;
                    FieldDescriptorProto.Type type = f.type;
                    FieldElement.Label label = f.getLabel();
                    // message and enum fields are named fields
                    if (type.equals(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        || type.equals(FieldDescriptorProto.Type.TYPE_ENUM)) {
                        String fullyQualifiedtypeName = f.type_name; // fully qualified name including package name.
                        String typeName = fullyQualifiedtypeName.substring(fullyQualifiedtypeName.indexOf(".", 2) + 1);
                        dataType = DataType.NamedType.create(typeName);
                    } else {
                        dataType = f.getDataType();
                    }

                    // build field
                    FieldElement.Builder fieldBuilder = FieldElement
                        .builder()
                        .name(fieldName)
                        .type(dataType)
                        .label(label)
                        .tag(f.number);

                    // add field options to the field
                    if (f.json_name != null) {
                        OptionElement.Kind kind = OptionElement.Kind.STRING;
                        OptionElement option = OptionElement.create("json_name", kind, f.json_name);
                        fieldBuilder.addOption(option);
                    }

                    if (f.options != null) {
                        if (f.options.packed) {
                            OptionElement.Kind kind = OptionElement.Kind.STRING;
                            OptionElement option = OptionElement.create("packed", kind, "true");
                            fieldBuilder.addOption(option);
                        }
                    }
                    // add the field to the message
                    messageElementBuilder.addField(fieldBuilder.build());
                }
            }

            // message type declarations
            if (nested_type != null) {
                for (DescriptorProto n : nested_type) {
                    messageElementBuilder.addType(n.buildMessageElement());
                }
            }

            // enum declarations
            if (enum_type != null) {
                for (EnumDescriptorProto e : enum_type) {
                    EnumElement.Builder nestedEnumElement = EnumElement
                        .builder()
                        .name(e.name);
                    for (EnumValueDescriptorProto v : e.value) {
                        EnumConstantElement.Builder c = EnumConstantElement.builder()
                                                                           .name(v.name)
                                                                           .tag(v.number);
                        nestedEnumElement.addConstant(c.build());
                    }
                    messageElementBuilder.addType(nestedEnumElement.build());
                }
            }
            return messageElementBuilder.build();
        }

    }

    public static class FieldDescriptorProto
    {
        public enum Type
        {
            TYPE_DOUBLE,
            TYPE_FLOAT,
            TYPE_INT64,
            TYPE_UINT64,
            TYPE_INT32,
            TYPE_FIXED64,
            TYPE_FIXED32,
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_GROUP,
            TYPE_MESSAGE,
            TYPE_BYTES,
            TYPE_UINT32,
            TYPE_ENUM,
            TYPE_SFIXED32,
            TYPE_SFIXED64,
            TYPE_SINT32,
            TYPE_SINT64
        }

        public enum Label
        {
            LABEL_OPTIONAL,
            LABEL_REQUIRED,
            LABEL_REPEATED
        }

        public String name;
        public int number;
        public Label label;
        public Type type;
        public String type_name;
        public String extendee;
        public String default_value;
        public int oneof_index;
        public String json_name;
        public FieldOptions options;

        static private Map<Type, DataType> scalarTypeMap = new EnumMap<>(Type.class);
        static private Map<Label, FieldElement.Label> labelMap = new EnumMap<>(Label.class);

        static {
            scalarTypeMap.put(Type.TYPE_DOUBLE, DataType.ScalarType.DOUBLE);
            scalarTypeMap.put(Type.TYPE_FLOAT, DataType.ScalarType.FLOAT);
            scalarTypeMap.put(Type.TYPE_INT64, DataType.ScalarType.INT64);
            scalarTypeMap.put(Type.TYPE_UINT64, DataType.ScalarType.UINT64);
            scalarTypeMap.put(Type.TYPE_INT32, DataType.ScalarType.INT32);
            scalarTypeMap.put(Type.TYPE_FIXED64, DataType.ScalarType.FIXED64);
            scalarTypeMap.put(Type.TYPE_FIXED32, DataType.ScalarType.FIXED32);
            scalarTypeMap.put(Type.TYPE_BOOL, DataType.ScalarType.BOOL);
            scalarTypeMap.put(Type.TYPE_STRING, DataType.ScalarType.STRING);
            scalarTypeMap.put(Type.TYPE_BYTES, DataType.ScalarType.BYTES);
            scalarTypeMap.put(Type.TYPE_UINT32, DataType.ScalarType.UINT32);
            scalarTypeMap.put(Type.TYPE_SFIXED32, DataType.ScalarType.SFIXED32);
            scalarTypeMap.put(Type.TYPE_SFIXED64, DataType.ScalarType.SFIXED64);
            scalarTypeMap.put(Type.TYPE_SINT32, DataType.ScalarType.SINT32);
            scalarTypeMap.put(Type.TYPE_SINT64, DataType.ScalarType.SINT64);

            labelMap.put(Label.LABEL_OPTIONAL, FieldElement.Label.OPTIONAL);
            labelMap.put(Label.LABEL_REQUIRED, FieldElement.Label.REQUIRED);
            labelMap.put(Label.LABEL_REPEATED, FieldElement.Label.REPEATED);
        }

        public DataType getDataType()
        {
            return scalarTypeMap.get(type);
        }

        public FieldElement.Label getLabel()
        {
            return labelMap.get(label);
        }
    }

    public static class OneofDescriptorProto
    {
        public String name;
        public OneofOptions options;
    }

    public static class EnumDescriptorProto
    {
        public String name;
        public EnumValueDescriptorProto[] value;
        public EnumOptions options;
    }

    public static class EnumValueDescriptorProto
    {
        public String name;
        public int number;
        public EnumValueOptions options;
    }

    public static class ServiceDescriptorProto
    {
        public String name;
        public MethodDescriptorProto[] method;
        public ServiceOptions options;
    }

    public static class MethodDescriptorProto
    {
        public String name;
        public String input_type;
        public String output_type;
        public MethodOptions options;
        public boolean client_streaming; // [default=false];
        public boolean server_streaming; // [default=false];
    }

    public static class FileOptions
    {
        public String java_package;
        public String java_outer_classname;
        public boolean java_multiple_files; // [default=false];
        public boolean java_generate_equals_and_hash; // [deprecated=true];
        public boolean java_String_check_utf8; // [default=false];

        enum OptimizeMode
        {
            SPEED,
            CODE_SIZE,
            LITE_RUNTIME
        }

        public OptimizeMode optimize_for; // [default=SPEED];
        public String go_package;
        public boolean cc_generic_services; //  [default=false];
        public boolean java_generic_services; // [default=false];
        public boolean py_generic_services; // [default=false];
        public boolean deprecated; //  [default=false];
        public boolean cc_enable_arenas; // [default=false];
        public String objc_class_prefix;
        public String csharp_namespace;
        public String swift_prefix;
        public String php_class_prefix;
        public UninterpretedOption[] uninterpreted_option;
//        extensions 1000 to max;
    }

    public static class MessageOptions
    {
        public boolean message_set_wire_format; // [default=false];
        public boolean no_standard_descriptor_accessor; // [default=false];
        public boolean deprecated; // [default=false];
        public boolean map_entry;
        public UninterpretedOption[] uninterpreted_option;
//        extensions 1000 to max;
    }

    public static class FieldOptions
    {
        public CType ctype; // [default = STRING];

        enum CType
        {
            STRING,
            CORD,
            STRING_PIECE
        }

        public boolean packed;
        public JSType jstype; // [default = JS_NORMAL];

        enum JSType
        {
            JS_NORMAL,
            JS_STRING,
            JS_NUMBER
        }

        public boolean lazy; // [default=false];
        public boolean deprecated; // [default=false];
        public boolean weak; //  [default=false];
        public UninterpretedOption[] uninterpreted_option;
//        extensions 1000 to max;
    }

    public static class OneofOptions
    {
        public UninterpretedOption[] uninterpreted_option;
//        extensions 1000 to max;
    }

    public static class EnumOptions
    {
        public boolean allow_alias;
        public boolean deprecated; // [default=false];
        public UninterpretedOption[] uninterpreted_option;
//        extensions 1000 to max;
    }

    public static class EnumValueOptions
    {
        public boolean deprecated; //  [default=false];
        public UninterpretedOption[] uninterpreted_option;
//        extensions 1000 to max;
    }

    public static class ServiceOptions
    {
        public boolean deprecated; // [default=false];
        public UninterpretedOption[] uninterpreted_option;
//        extensions 1000 to max;
    }

    public static class MethodOptions
    {
        public boolean deprecated; // [default=false];

        enum IdempotencyLevel
        {
            IDEMPOTENCY_UNKNOWN,
            NO_SIDE_EFFECTS,
            IDEMPOTENT
        }

        public IdempotencyLevel idempotency_level; // [default=IDEMPOTENCY_UNKNOWN];
        public UninterpretedOption[] uninterpreted_option;
//        extensions 1000 to max;
    }

    public static class UninterpretedOption
    {
        static class NamePart
        {
            public String name_part;
            public boolean is_extension;
        }

        public NamePart[] name;
        public String identifier_value;
        public long positive_int_value;
        public long negative_int_value;
        public double double_value;
        public byte[] string_value;
        public String aggregate_value;
    }

    public static class SourceCodeInfo
    {
        public Location[] location;

        public static class Location
        {
            public int[] path; //  [packed=true];
            public int[] span; //  [packed=true];
            public String leading_comments;
            public String trailing_comments;
            public String[] leading_detached_comments;
        }
    }

    /*
    private static class GeneratedCodeInfo
    {
        public CodeAnnotation[] annotation;

        public static class CodeAnnotation
        {
            public long[] path; // [packed=true];
            public String source_file;
            public int begin;
            public int end;
        }
    }
    */
}
