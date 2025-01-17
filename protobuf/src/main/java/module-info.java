// Protobuf Main artifact Module descriptor
module tools.jackson.dataformat.protobuf
{
    requires transitive tools.jackson.core;
    requires transitive tools.jackson.databind;

    // No module-info nor Automatic-Module-Name; relies on jar name:
    requires protoparser;

    exports tools.jackson.dataformat.protobuf;
    exports tools.jackson.dataformat.protobuf.schema;
    exports tools.jackson.dataformat.protobuf.schemagen;

    // Need to "opens" to allow reading resource `descriptor.proto`
    opens tools.jackson.dataformat.protobuf.schema;

    provides tools.jackson.core.TokenStreamFactory with
        tools.jackson.dataformat.protobuf.ProtobufFactory;
    provides tools.jackson.databind.ObjectMapper with
        tools.jackson.dataformat.protobuf.ProtobufMapper;
}
