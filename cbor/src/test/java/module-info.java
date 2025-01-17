// CBOR unit test Module descriptor
module tools.jackson.dataformat.cbor
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies
    
    requires tools.jackson.core;
    requires tools.jackson.databind;

    // Additional test lib/framework dependencies
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.dataformat.cbor;
    opens tools.jackson.dataformat.cbor.constraints;
    opens tools.jackson.dataformat.cbor.dos;
    opens tools.jackson.dataformat.cbor.filter;
    opens tools.jackson.dataformat.cbor.fuzz;
    opens tools.jackson.dataformat.cbor.gen;
    opens tools.jackson.dataformat.cbor.gen.dos;
    opens tools.jackson.dataformat.cbor.mapper;
    opens tools.jackson.dataformat.cbor.parse;
    opens tools.jackson.dataformat.cbor.seq;
    opens tools.jackson.dataformat.cbor.testutil;
    opens tools.jackson.dataformat.cbor.testutil.failure;
}
