// Generated 15-Mar-2019 using Moditect maven plugin
module com.fasterxml.jackson.dataformat.smile {
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    exports com.fasterxml.jackson.dataformat.smile;
    exports com.fasterxml.jackson.dataformat.smile.async;
    exports com.fasterxml.jackson.dataformat.smile.databind;

    provides com.fasterxml.jackson.core.JsonFactory with
        com.fasterxml.jackson.dataformat.smile.SmileFactory;
}
