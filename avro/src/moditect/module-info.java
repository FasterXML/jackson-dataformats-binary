module tools.jackson.dataformat.avro {
    requires transitive com.fasterxml.jackson.annotation;
    requires tools.jackson.core;
    requires tools.jackson.databind;

    // silly avro Apache impl, its deps:
    requires static avro;
    requires static jackson.core.asl;
    requires static jackson.mapper.asl;

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
