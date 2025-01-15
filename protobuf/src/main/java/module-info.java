// Protobuf Main artifact Module descriptor
module tools.jackson.dataformat.protobuf
{
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires protoparser;

    exports tools.jackson.dataformat.protobuf;
// No, should not expose shaded
//    exports tools.jackson.dataformat.protobuf.protoparser.protoparser;
    exports tools.jackson.dataformat.protobuf.schema;
    exports tools.jackson.dataformat.protobuf.schemagen;

    provides tools.jackson.core.TokenStreamFactory with
        tools.jackson.dataformat.protobuf.ProtobufFactory;
    provides tools.jackson.databind.ObjectMapper with
        tools.jackson.dataformat.protobuf.ProtobufMapper;
}
