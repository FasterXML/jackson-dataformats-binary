package tools.jackson.dataformat.avro.failing;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;
import tools.jackson.dataformat.avro.AvroTestBase;

public class POJOEvolution164Test extends AvroTestBase
{
    static class MyClass {
        public String stringField;
        public long longField;
    }

    private final AvroMapper MAPPER = getMapper();

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
