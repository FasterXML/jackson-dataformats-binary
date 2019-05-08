// Generated 15-Mar-2019 using Moditect maven plugin
module com.fasterxml.jackson.dataformat.cbor {
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.dataformat.cbor;
    exports com.fasterxml.jackson.dataformat.cbor.databind;

    provides com.fasterxml.jackson.core.TokenStreamFactory with
        com.fasterxml.jackson.dataformat.cbor.CBORFactory;
    provides com.fasterxml.jackson.databind.ObjectMapper with
        com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
}
