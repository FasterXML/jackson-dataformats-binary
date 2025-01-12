package tools.jackson.dataformat.avro.failing;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class POJOEvolution164Test extends AvroTestBase
{
    static class MyClass {
        public String stringField;
        public long longField;
    }

    private final AvroMapper MAPPER = getMapper();

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

        final AvroSchema readerSchema = MAPPER.schemaFrom(READER_SCHEMA_SRC);
        final AvroSchema writerSchema = MAPPER.schemaFrom(WRITER_SCHEMA_SRC);

        MyClass aClass = new MyClass();
        aClass.stringField = "String value";
        aClass.longField = 42;
        byte[] avro = MAPPER.writer()
                .with(writerSchema)
                .writeValueAsBytes(aClass);
        MyClass result = MAPPER.readerFor(MyClass.class)
                .with(readerSchema)
                .readValue(avro);
        assertEquals(aClass.stringField, result.stringField);
      }
}
