module tools.jackson.dataformat.smile {
    requires tools.jackson.core;
    requires tools.jackson.databind;

    exports tools.jackson.dataformat.smile;
    exports tools.jackson.dataformat.smile.async;
    exports tools.jackson.dataformat.smile.databind;

    provides tools.jackson.core.TokenStreamFactory with
        tools.jackson.dataformat.smile.SmileFactory;
    provides tools.jackson.databind.ObjectMapper with
        tools.jackson.dataformat.smile.databind.SmileMapper;
}
