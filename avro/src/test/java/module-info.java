// Avro unit test Module descriptor
module tools.jackson.dataformat.avro
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies
    
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires org.apache.avro;

    // Additional test lib/framework dependencies
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al

    opens tools.jackson.dataformat.avro;
    opens tools.jackson.dataformat.avro.annotation;
    opens tools.jackson.dataformat.avro.dos;
    opens tools.jackson.dataformat.avro.fuzz;
    opens tools.jackson.dataformat.avro.gen;
    opens tools.jackson.dataformat.avro.interop;
    opens tools.jackson.dataformat.avro.interop.annotations;
    opens tools.jackson.dataformat.avro.interop.arrays;
    opens tools.jackson.dataformat.avro.interop.maps;
    opens tools.jackson.dataformat.avro.interop.records;
    opens tools.jackson.dataformat.avro.jsr310;
    opens tools.jackson.dataformat.avro.schema;
    opens tools.jackson.dataformat.avro.schemaev;
    opens tools.jackson.dataformat.avro.testsupport;
    opens tools.jackson.dataformat.avro.testutil.failure;
    opens tools.jackson.dataformat.avro.tofix;
}
