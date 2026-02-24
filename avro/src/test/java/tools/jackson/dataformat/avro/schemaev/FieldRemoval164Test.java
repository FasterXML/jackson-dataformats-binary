package tools.jackson.dataformat.avro.schemaev;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [dataformats-binary#164]
public class FieldRemoval164Test extends AvroTestBase
{
    static class MyClass {
        public String stringField;
        public long longField;
    }

    private final AvroMapper MAPPER = getMapper();

    // [dataformats-binary#164]
    @Test
    public void testSimpleFieldRemove() throws Exception
    {
        final String WRITER_SCHEMA_SRC = "{\n" +
                "  \"type\": \"record\",\n" +
                "  \"name\": \"MyClass\",\n" +
                "  \"fields\": [\n" +
                "    { \"name\": \"longField\", \"type\": \"long\"\n },\n" +
                "    { \"name\": \"stringField\",\n" +
                "      \"type\": [\n" +
                "        \"null\",\n" +
                "        \"string\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        final String READER_SCHEMA_SRC = "{\n" +
                "  \"type\": \"record\",\n" +
                "  \"name\": \"MyClass\",\n" +
                "  \"fields\": [\n" +
                "    {\"name\": \"stringField\",\n" +
                "      \"type\": [\n" +
                "        \"null\",\n" +
                "        \"string\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        final AvroSchema writerSchema = MAPPER.schemaFrom(WRITER_SCHEMA_SRC);
        final AvroSchema readerSchema = MAPPER.schemaFrom(READER_SCHEMA_SRC);
        // Must use combined schema that knows both writer (for data layout) and
        // reader (for desired output) schemas, to properly skip removed fields
        final AvroSchema combinedSchema = writerSchema.withReaderSchema(readerSchema);

        MyClass aClass = new MyClass();
        aClass.stringField = "String value";
        aClass.longField = 42;
        byte[] avro = MAPPER.writer()
                .with(writerSchema)
                .writeValueAsBytes(aClass);
        MyClass result = MAPPER.readerFor(MyClass.class)
                .with(combinedSchema)
                .readValue(avro);
        assertEquals(aClass.stringField, result.stringField);
    }
}
