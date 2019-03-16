// Generated 15-Mar-2019 using Moditect maven plugin
module com.fasterxml.jackson.dataformat.avro {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    // silly avro Apache impl, its deps:
    requires avro;
    requires jackson.core.asl;
    requires jackson.mapper.asl;

    exports com.fasterxml.jackson.dataformat.avro;
    exports com.fasterxml.jackson.dataformat.avro.apacheimpl;
    exports com.fasterxml.jackson.dataformat.avro.deser;
    exports com.fasterxml.jackson.dataformat.avro.schema;
    exports com.fasterxml.jackson.dataformat.avro.ser;

    provides com.fasterxml.jackson.core.JsonFactory with
        com.fasterxml.jackson.dataformat.avro.AvroFactory;
    provides com.fasterxml.jackson.databind.ObjectMapper with
        com.fasterxml.jackson.dataformat.avro.AvroMapper;
}
