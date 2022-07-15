// Generated 15-Mar-2019 using Moditect maven plugin
module tools.jackson.dataformat.ion {
    requires transitive com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires static ion.java;

    requires java.sql;

    exports tools.jackson.dataformat.ion;
    exports tools.jackson.dataformat.ion.ionvalue;
    exports tools.jackson.dataformat.ion.polymorphism;
    exports tools.jackson.dataformat.ion.util;

    provides tools.jackson.core.TokenStreamFactory with
        tools.jackson.dataformat.ion.IonFactory;
    provides tools.jackson.databind.ObjectMapper with
        tools.jackson.dataformat.ion.IonObjectMapper;
}
