package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.Random;

import org.junit.Assert;

import com.fasterxml.jackson.databind.jsontype.NamedType;

public class FixedFieldTest extends AvroTestBase
{
    private static final String FIXED_SCHEMA_JSON = "{\n"
            + "    \"type\": \"record\",\n"
            + "    \"name\": \"WithFixedField\",\n"
            +"     \"namespace\": \"com.fasterxml.jackson.dataformat.avro.FixedFieldTest$\",\n"
            + "    \"fields\": [\n"
            + "        {\"name\": \"fixedField\", \"type\": {\"type\": \"fixed\", \"name\": \"FixedFieldBytes\", \"size\": 4}}\n"
            + "    ]\n"
            + "}";

    public void testFixedField() throws IOException {
        AvroMapper mapper = getMapper();
        mapper.registerSubtypes(new NamedType(byte[].class, "com.fasterxml.jackson.dataformat.avro.FixedFieldTest$FixedFieldBytes"));
        AvroSchema schema = mapper.schemaFrom(FIXED_SCHEMA_JSON);

        WithFixedField in = new WithFixedField();
        byte[] bytes = {0, 1, 2, (byte) new Random().nextInt(256)};
        in.fixedField = bytes;
        byte[] serialized = mapper.writer(schema).writeValueAsBytes(in);
        WithFixedField deser = mapper.readerFor(WithFixedField.class).with(schema).readValue(serialized);
        Assert.assertArrayEquals(bytes, deser.fixedField);
    }

    static class WithFixedField {
        public byte[] fixedField;
    }
}
