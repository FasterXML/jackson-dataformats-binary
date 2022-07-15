package tools.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import tools.jackson.dataformat.avro.schema.AvroSchemaGenerator;
import tools.jackson.dataformat.avro.testsupport.ThrottledInputStream;

public class BinaryDataTest extends AvroTestBase
{
    @JsonPropertyOrder({ "filename", "data", "size" })
    static class FilePojo {
        protected FilePojo() { }
        public FilePojo(String text) throws IOException {
            data = text.getBytes("UTF-8");
            size = data.length;
        }
        
        public String filename = "TestFile.txt";
        public byte[] data;
        public long size;
    }

    private final AvroMapper AVRO_JACKSON_MAPPER = newMapper();
    private final AvroMapper AVRO_APACHE_MAPPER =  newApacheMapper();

    public void testAvroSchemaGenerationWithJackson() throws Exception
    {
        _testAvroSchemaGenerationWithJackson(AVRO_JACKSON_MAPPER);
        _testAvroSchemaGenerationWithJackson(AVRO_APACHE_MAPPER);
    }

    public void _testAvroSchemaGenerationWithJackson(AvroMapper mapper) throws Exception
    {
        AvroSchemaGenerator visitor = new AvroSchemaGenerator();
        mapper.acceptJsonFormatVisitor(FilePojo.class, visitor);
        AvroSchema schema = visitor.getGeneratedSchema();
        byte[] ser = mapper.writer(schema).writeValueAsBytes(new FilePojo(
                "ABCDEFGERRJEOOJKDPKPEWVEW PKWEVPKEVEW"));
        assertNotNull(ser);
        
        // plus should probably also read back, right?
        FilePojo result = mapper.readerFor(FilePojo.class)
                .with(schema)
                .readValue(ser);
        assertNotNull(result);
        assertNotNull(result.data);
        assertEquals(37, result.data.length);
        assertEquals(37, result.size);

        // same, but via parser
        JsonParser p = mapper.reader()
                .with(schema)
                .createParser(ThrottledInputStream.wrap(
                new ByteArrayInputStream(ser), 7));
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("filename", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("data", p.currentName());
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        // skip, don't read!
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("size", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(result.size, p.getLongValue());
        assertEquals((int) result.size, p.getIntValue());
        assertEquals(BigInteger.valueOf(result.size), p.getBigIntegerValue());

        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
        assertNull(p.nextTextValue());
    }
}
