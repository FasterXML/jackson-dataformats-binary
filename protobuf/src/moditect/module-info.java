// Generated 15-Mar-2019 using Moditect maven plugin
module com.fasterxml.jackson.dataformat.protobuf {
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.dataformat.protobuf;
// No, should not expose shaded
//    exports com.fasterxml.jackson.dataformat.protobuf.protoparser.protoparser;
    exports com.fasterxml.jackson.dataformat.protobuf.schema;
    exports com.fasterxml.jackson.dataformat.protobuf.schemagen;

    provides com.fasterxml.jackson.core.TokenStreamFactory with
        com.fasterxml.jackson.dataformat.protobuf.ProtobufFactory;
    provides com.fasterxml.jackson.databind.ObjectMapper with
        com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
}
