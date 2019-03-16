// Generated 15-Mar-2019 using Moditect maven plugin
module com.fasterxml.jackson.dataformat.cbor {
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.dataformat.cbor;
    exports com.fasterxml.jackson.dataformat.cbor.databind;

    provides com.fasterxml.jackson.core.JsonFactory with
        com.fasterxml.jackson.dataformat.cbor.CBORFactory;
}
