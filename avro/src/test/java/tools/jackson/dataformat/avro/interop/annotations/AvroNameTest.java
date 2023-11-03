package tools.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;

import org.apache.avro.reflect.AvroName;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.dataformat.avro.AvroTestBase;
import tools.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests the {@link AvroName @AvroName} annotation
 */
public class AvroNameTest extends InteropTestBase
{
    public static class RecordWithRenamed {
        @AvroName("newName")
        public String someField;

        @Override
        public boolean equals(Object o) {
            return ((RecordWithRenamed) o).someField.equals(someField);
        }
    }

    public static class RecordWithNameCollision {
        @AvroName("otherField")
        public String firstField;

        @JsonProperty
        public String otherField;
    }

    @Test
    public void testRecordWithRenamedField() throws IOException{
        RecordWithRenamed original = new RecordWithRenamed();
        original.someField = "blah";
        RecordWithRenamed result = roundTrip(original);
        assertThat(result).isEqualTo(original);
    }

    @Test
    public void testRecordWithNameCollision() throws IOException {
        try {
            schemaFunctor.apply(RecordWithNameCollision.class);
            fail("Should not pass");
        } catch (InvalidDefinitionException e) {
            // Exception type and message vary between Jackson and Avro-schema
            // cases... but both are DatabindExceptions and contain "otherField"
            AvroTestBase.verifyException(e, "otherField");
        }
    }
}
