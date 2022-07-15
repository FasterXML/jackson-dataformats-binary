module tools.jackson.dataformat.cbor {
    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports tools.jackson.dataformat.cbor;
    exports tools.jackson.dataformat.cbor.databind;

    provides tools.jackson.core.TokenStreamFactory with
        tools.jackson.dataformat.cbor.CBORFactory;
    provides tools.jackson.databind.ObjectMapper with
        tools.jackson.dataformat.cbor.databind.CBORMapper;
}
