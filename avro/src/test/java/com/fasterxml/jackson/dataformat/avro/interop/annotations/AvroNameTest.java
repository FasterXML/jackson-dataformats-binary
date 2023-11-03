package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;

import org.apache.avro.reflect.AvroName;
import org.junit.Test;

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

        public String otherField;
    }

    @Test
    public void testRecordWithRenamedField() throws Exception{
        RecordWithRenamed original = new RecordWithRenamed();
        original.someField = "blah";
        RecordWithRenamed result = roundTrip(original);
        assertThat(result).isEqualTo(original);
    }

    // 02-Nov-2023, tatu: This test has been disabled for some reason, but
    //    without commentary. Fixed it a bit but ultimately can't enable yet

    // @Test
    public void testRecordWithNameCollision() throws Exception {
        try {
            schemaFunctor.apply(RecordWithNameCollision.class);
            fail("Should not pass");
        } catch (InvalidDefinitionException e) {
            AvroTestBase.verifyException(e, "double field entry: otherField");
        }
    }
}
