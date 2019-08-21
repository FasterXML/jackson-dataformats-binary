package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.io.IOException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.junit.Test;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonDeserialize;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonSerialize;
import static org.assertj.core.api.Assertions.assertThat;

public class RecordWithNoFieldsTest {
    private Schema SCHEMA = SchemaBuilder
        .builder(RecordWithNoFieldsTest.class.getName() + "$")
        .record(Empty.class.getSimpleName())
            .fields()
        .endRecord();

    @JsonAutoDetect(
        fieldVisibility = Visibility.NONE,
        creatorVisibility = Visibility.NONE,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE
    )
    static final class Empty {
        @Override
        public boolean equals(Object o) {
            return o instanceof Empty;
        }
    }

    @Test
    public void testEmptyRecord() throws IOException {
        final Empty empty = new Empty();
        final Empty result = jacksonDeserialize(SCHEMA, Empty.class, jacksonSerialize(SCHEMA, empty));

        assertThat(result).isEqualTo(empty);
    }
}
