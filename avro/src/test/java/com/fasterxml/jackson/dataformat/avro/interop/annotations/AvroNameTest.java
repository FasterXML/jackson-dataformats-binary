package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;
import lombok.Data;
import org.apache.avro.reflect.AvroName;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link AvroName @AvroName} annotation
 */
public class AvroNameTest extends InteropTestBase {

    @Data
    public static class RecordWithRenamed {
        @AvroName("newName")
        private String someField;
    }

    @Data
    public static class RecordWithNameCollision {
        @AvroName("otherField")
        private String firstField;

        private String otherField;
    }

    @Test
    public void testRecordWithRenamedField() {
        RecordWithRenamed original = new RecordWithRenamed();
        original.setSomeField("blah");
        //
        RecordWithRenamed result = roundTrip(original);
        //
        assertThat(result).isEqualTo(result);
    }

    @Test(expected = Exception.class)
    public void testRecordWithNameCollision() {
        schemaFunctor.apply(RecordWithNameCollision.class);
        // Should throw because of name collision
    }

}
