// Generated 15-Mar-2019 using Moditect maven plugin
module com.fasterxml.jackson.dataformat.ion {
    requires transitive com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    requires static ion.java;

    requires java.sql;

    exports com.fasterxml.jackson.dataformat.ion;
    exports com.fasterxml.jackson.dataformat.ion.ionvalue;
    exports com.fasterxml.jackson.dataformat.ion.polymorphism;
    exports com.fasterxml.jackson.dataformat.ion.util;
}
