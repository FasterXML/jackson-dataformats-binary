package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import org.apache.avro.reflect.AvroName;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

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
    public void testRecordWithRenamedField() throws Exception{
        RecordWithRenamed original = new RecordWithRenamed();
        original.someField = "blah";
        RecordWithRenamed result = roundTrip(original);
        assertThat(result).isEqualTo(original);
    }

    // 02-Nov-2023, tatu: This test had been disabled for long time:
    //   "Jackson Schema" case did not consider conflict where it was
    //   possible to select precedence... so needed to add another
    //   annotation to force problem.

    @Test
    public void testRecordWithNameCollision() throws Exception {
        try {
            schemaFunctor.apply(RecordWithNameCollision.class);
            fail("Should not pass");
        } catch (DatabindException e) {
            // InvalidDefinitionException with Avro schema
            // JsonMapping with Jackson Schema

            final String msg = e.toString();

            if (!msg.contains("double field entry: otherField")
                    && !msg.contains("property \"otherField\"")) {
                fail("Got exception but without matching message: "+msg);
            }
        }
    }
}
