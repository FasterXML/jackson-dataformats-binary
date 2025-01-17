// Ion Main artifact Module descriptor
module tools.jackson.dataformat.ion
{
    requires tools.jackson.core;
    requires tools.jackson.databind;
    requires java.sql;

    // ion-java has no explicit module-info; but automatic name is:
    requires com.amazon.ion;

    exports tools.jackson.dataformat.ion;
    exports tools.jackson.dataformat.ion.ionvalue;
    exports tools.jackson.dataformat.ion.jsr310;
    exports tools.jackson.dataformat.ion.polymorphism;

    provides tools.jackson.core.TokenStreamFactory with
        tools.jackson.dataformat.ion.IonFactory;
    provides tools.jackson.databind.ObjectMapper with
        tools.jackson.dataformat.ion.IonObjectMapper;
}
