// Smile unit test Module descriptor
module tools.jackson.dataformat.smile
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies
    
    requires tools.jackson.core;
    requires tools.jackson.databind;

    // Additional test lib/framework dependencies
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.dataformat.smile;
    opens tools.jackson.dataformat.smile.async;
    opens tools.jackson.dataformat.smile.constraints;
    opens tools.jackson.dataformat.smile.dos;
    opens tools.jackson.dataformat.smile.filter;
    opens tools.jackson.dataformat.smile.fuzz;
    opens tools.jackson.dataformat.smile.gen;
    opens tools.jackson.dataformat.smile.gen.dos;
    opens tools.jackson.dataformat.smile.mapper;
    opens tools.jackson.dataformat.smile.parse;
    opens tools.jackson.dataformat.smile.seq;
    opens tools.jackson.dataformat.smile.testutil;
    opens tools.jackson.dataformat.smile.testutil.failure;
}
