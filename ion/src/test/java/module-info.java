// Ion unit test Module descriptor
module tools.jackson.dataformat.ion
{
    // Since we are not split from Main artifact, will not
    // need to depend on Main artifact -- but need its dependencies
    
    requires tools.jackson.core;
    requires tools.jackson.databind;
    requires java.sql;

    requires com.amazon.ion;
    
    // Additional test lib/framework dependencies
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Further, need to open up some packages for JUnit et al
    opens tools.jackson.dataformat.ion;
    opens tools.jackson.dataformat.ion.dos;
    opens tools.jackson.dataformat.ion.fuzz;
    opens tools.jackson.dataformat.ion.ionvalue;
    opens tools.jackson.dataformat.ion.jsr310;
    opens tools.jackson.dataformat.ion.misc;
    opens tools.jackson.dataformat.ion.polymorphism;
    opens tools.jackson.dataformat.ion.sequence;
    opens tools.jackson.dataformat.ion.testutil.failure;
    opens tools.jackson.dataformat.ion.tofix;
}
