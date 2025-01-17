// Avro Main artifact Module descriptor
module tools.jackson.dataformat.avro
{
    requires transitive com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires org.apache.avro;

    exports tools.jackson.dataformat.avro;
    exports tools.jackson.dataformat.avro.annotation;
    exports tools.jackson.dataformat.avro.apacheimpl;
    exports tools.jackson.dataformat.avro.deser;
    exports tools.jackson.dataformat.avro.schema;
    exports tools.jackson.dataformat.avro.ser;

    provides tools.jackson.core.TokenStreamFactory with
        tools.jackson.dataformat.avro.AvroFactory;
    provides tools.jackson.databind.ObjectMapper with
        tools.jackson.dataformat.avro.AvroMapper;
}
