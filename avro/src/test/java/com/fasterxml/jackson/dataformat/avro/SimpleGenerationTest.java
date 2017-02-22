package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Assert;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;

public class SimpleGenerationTest extends AvroTestBase
{
    protected final String SCHEMA_WITH_BINARY_JSON_SRC = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Binary\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"name\", \"type\": \"string\"},\n"
            +" {\"name\": \"value\", \"type\": \"bytes\"}\n"
            +"]}";

    protected static class Binary {
        public String name;
        public byte[] value;

        public Binary() { }
        public Binary(String n, byte[] v) {
            name = n;
            value = v;
        }
    }

    // order such that missing field is in the middle
    @JsonPropertyOrder({ "name", "number", "value" })
    protected static class BinaryAndNumber extends Binary {
        public int number;

        public BinaryAndNumber() { super(); }
        public BinaryAndNumber(String name, int nr) {
            super(name, null);
            number = nr;
            value = new byte[1];
        }
    }
    
    // another, bigger test for skipping
    @JsonPropertyOrder({ "name", "stuff", "value" })
    protected static class BinaryAndArray extends Binary {
        public String[] stuff;

        public BinaryAndArray() { super(); }
        public BinaryAndArray(String name) {
            super(name, null);
            value = new byte[1];
            stuff = new String[] { "abc" };
        }
    }

    private final AvroSchema SCHEMA_WITH_BINARY_JSON;
    
    public SimpleGenerationTest() throws IOException
    {
        SCHEMA_WITH_BINARY_JSON = getMapper().schemaFrom(SCHEMA_WITH_BINARY_JSON_SRC);
    }
    
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */
    
    public void testSimplest() throws Exception
    {
        Employee empl = new Employee();
        empl.name = "Bobbee";
        empl.age = 39;
        empl.emails = new String[] { "bob@aol.com", "bobby@gmail.com" };
        empl.boss = null;
        
        ObjectMapper mapper = new ObjectMapper(new AvroFactory());

        AvroSchema schema = getEmployeeSchema();
        byte[] bytes = mapper.writer(schema).writeValueAsBytes(empl);
        assertNotNull(bytes);

        // Currently we get this result... need to verify in future
        assertEquals(39, bytes.length);
        
        // read back actually
        Employee output = mapper.reader(schema).forType(Employee.class).readValue(bytes);
        assertNotNull(output);
        assertEquals(output.name, empl.name);
        assertEquals(output.age, empl.age);
    }

    public void testBinaryOk() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper(new AvroFactory());
        Binary bin = new Binary("Foo", new byte[] { 1, 2, 3, 4 });
        byte[] bytes = mapper.writer(SCHEMA_WITH_BINARY_JSON).writeValueAsBytes(bin);
        assertEquals(9, bytes.length);
        assertNotNull(bytes);
        Binary output = mapper.reader(SCHEMA_WITH_BINARY_JSON).forType(Binary.class).readValue(bytes);
        assertNotNull(output);
        assertEquals("Foo", output.name);
        assertNotNull(output.value);
        Assert.assertArrayEquals(bin.value, output.value);
    }

    @SuppressWarnings("resource")
    public void testIgnoringOfUnknownScalar() throws Exception
    {
        AvroFactory af = new AvroFactory();
        ObjectMapper mapper = new ObjectMapper(af);
        // we can repurpose "Binary" from above for schema
        BinaryAndNumber input = new BinaryAndNumber("Bob", 15);
        JsonGenerator gen = mapper.getFactory().createGenerator(new ByteArrayOutputStream());
        try {
             mapper.writer(SCHEMA_WITH_BINARY_JSON).writeValue(gen, input);
             fail("Should have thrown exception");
        } catch (JsonMappingException e) {
            verifyException(e, "no field named");
        }

        // But should be fine if (and only if!) we enable support for skipping
        af.enable(JsonGenerator.Feature.IGNORE_UNKNOWN);

        gen = mapper.getFactory().createGenerator(new ByteArrayOutputStream());
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        mapper.writer(SCHEMA_WITH_BINARY_JSON).writeValue(b, input);
        byte[] bytes = b.toByteArray();
        assertEquals(6, bytes.length);

        // and should be able to get it back too
        BinaryAndNumber output = mapper.reader(SCHEMA_WITH_BINARY_JSON).forType(BinaryAndNumber.class).readValue(bytes);
        assertEquals("Bob", output.name);
    }

    public void testIgnoringOfUnknownObject() throws Exception
    {
        AvroFactory af = new AvroFactory();
        ObjectMapper mapper = new ObjectMapper(af);

        BinaryAndArray input = new BinaryAndArray("Bob");
        try {
             mapper.writer(SCHEMA_WITH_BINARY_JSON).writeValueAsBytes(input);
             fail("Should have thrown exception");
        } catch (JsonMappingException e) {
            verifyException(e, "no field named 'stuff'");
        }

        // But should be fine if (and only if!) we enable support for skipping
        af.enable(JsonGenerator.Feature.IGNORE_UNKNOWN);
        byte[] bytes = mapper.writer(SCHEMA_WITH_BINARY_JSON).writeValueAsBytes(input);
        assertEquals(6, bytes.length);

        // and should be able to get it back too
        BinaryAndNumber output = mapper.reader(SCHEMA_WITH_BINARY_JSON).forType(BinaryAndNumber.class).readValue(bytes);
        assertEquals("Bob", output.name);
    }
}
