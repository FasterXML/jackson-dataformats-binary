// Protobuf unit test Module descriptor
module tools.jackson.dataformat.protobuf
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies
    
    requires tools.jackson.core;
    requires tools.jackson.databind;

    requires protoparser;

    // Additional test lib/framework dependencies
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.dataformat.protobuf;
    opens tools.jackson.dataformat.protobuf.dos;
    opens tools.jackson.dataformat.protobuf.fuzz;
    opens tools.jackson.dataformat.protobuf.schema;
    opens tools.jackson.dataformat.protobuf.testutil;
    opens tools.jackson.dataformat.protobuf.testutil.failure;
    opens tools.jackson.dataformat.protobuf.tofix;
}
