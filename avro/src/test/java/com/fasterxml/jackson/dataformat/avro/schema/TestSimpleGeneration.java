package com.fasterxml.jackson.dataformat.avro.schema;

import java.nio.ByteBuffer;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.dataformat.avro.*;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroDefault;
import org.apache.avro.reflect.AvroName;

public class TestSimpleGeneration extends AvroTestBase
{
    public static class RootType
    {
        @JsonAlias({"nm", "Name"})
        public String name;
        
        public int value;
        
        List<String> other;
    }

    @SuppressWarnings("serial")
    public static class StringMap extends HashMap<String,String> { }

    static class WithDate {
        public Date date;
    }

    static class WithFixedField {
        @JsonProperty(required = true)
        @AvroFixedSize(typeName = "FixedFieldBytes", size = 4)
        public byte[] fixedField;

        @JsonProperty(value = "wff", required = true)
        @AvroFixedSize(typeName = "WrappedFixedFieldBytes", size = 8)
        public WrappedByteArray wrappedFixedField;

        void setValue(byte[] bytes) {
            this.fixedField = bytes;
        }

        static class WrappedByteArray {
            @JsonValue
            public ByteBuffer getBytes() {
                return null;
            }
        }
    }

    static class WithDefaults {
        @AvroDefault("null")
        public String avro;
        @JsonProperty(defaultValue = "null")
        public String json;
        public String noDefault;
        public int simpleInt;
        public Integer integer;
        @JsonProperty(required = true)
        public String required;

        public void setAvro(String avro) {
            this.avro = avro;
        }

        public void setJson(String json) {
            this.json = json;
        }

        public void setNoDefault(String noDefault) {
            this.noDefault = noDefault;
        }

        public void setSimpleInt(int simpleInt) {
            this.simpleInt = simpleInt;
        }

        public void setInteger(Integer integer) {
            this.integer = integer;
        }

        public void setRequired(String required) {
            this.required = required;
        }
    }

    /*
    /**********************************************************
    /* Tests
    /**********************************************************
     */

    private final AvroMapper MAPPER = newMapper();

    public void testBasic() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(RootType.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();
        assertNotNull(schema);

        String json = schema.getAvroSchema().toString(true);
        assertNotNull(json);

        // And read it back too just for fun
        AvroSchema s2 = MAPPER.schemaFrom(json);
        assertNotNull(s2);

//        System.out.println("Basic schema:\n"+json);
        Schema avroSchema = s2.getAvroSchema();

        // String name, int value
        assertEquals(2, avroSchema.getFields().size());
        Schema.Field f = avroSchema.getField("name");
        assertNotNull(f);
        assertEquals("name", f.name());

        // also verify that aliases are passed.
        assertEquals(new HashSet<String>(Arrays.asList("nm", "Name")), f.aliases());
    }

    public void testEmployee() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(Employee.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();
        assertNotNull(schema);

        String json = schema.getAvroSchema().toString(true);        
        assertNotNull(json);
        AvroSchema s2 = MAPPER.schemaFrom(json);
        assertNotNull(s2);
        
        Employee empl = new Employee();
        empl.name = "Bobbee";
        empl.age = 39;
        empl.emails = new String[] { "bob@aol.com", "bobby@gmail.com" };
        empl.boss = null;
        
        // So far so good: try producing actual Avro data...
        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(empl);
        assertNotNull(bytes);
        
        // and bring it back, too
        Employee e2 = getMapper().readerFor(Employee.class)
            .with(schema)
            .readValue(bytes);
        assertNotNull(e2);
        assertEquals(39, e2.age);
        assertEquals("Bobbee", e2.name);
    }

    public void testMap() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(StringMap.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();
        assertNotNull(schema);

        String json = schema.getAvroSchema().toString(true);
        assertNotNull(json);
        AvroSchema s2 = MAPPER.schemaFrom(json);
        assertNotNull(s2);

        // should probably verify, maybe... ?
        
//        System.out.println("Map schema:\n"+json);
    }

    // [Issue#8]
    public void testWithDate() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(WithDate.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();
        assertNotNull(schema);
    }

    public void testFixed() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(WithFixedField.class, gen);
        Schema generated = gen.getAvroSchema();
        Schema fixedFieldSchema = generated.getField("fixedField").schema();
        assertEquals(Schema.Type.FIXED, fixedFieldSchema.getType());
        assertEquals(4, fixedFieldSchema.getFixedSize());

        Schema wrappedFieldSchema = generated.getField("wff").schema();
        assertEquals(Schema.Type.FIXED, wrappedFieldSchema.getType());
        assertEquals(8, wrappedFieldSchema.getFixedSize());
    }

    // as per [dataformats-binary#98], no can do (unless we start supporting polymorphic
    // handling or something...)
    public void testSchemaForUntypedMap() throws Exception
    {
        try {
            MAPPER.schemaFor(Map.class);
            fail("Not expected to work yet");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "\"Any\" type");
            verifyException(e, "not supported");
            verifyException(e, "`java.lang.Object`");
        }
    }

    public void testDefaultValues() throws JsonMappingException {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(WithDefaults.class, gen);
        Schema schema = gen.getAvroSchema();
        assertEquals(JsonProperties.NULL_VALUE, schema.getField("avro").defaultVal());
        assertEquals(JsonProperties.NULL_VALUE, schema.getField("json").defaultVal());
        assertNull(schema.getField("noDefault").defaultVal());
        assertNull(schema.getField("simpleInt").defaultVal());
        assertNull(schema.getField("integer").defaultVal());
        assertNull(schema.getField("required").defaultVal());
    }

    public void testEnabledDefaultValues() throws JsonMappingException {
        AvroMapper mapper = new AvroMapper(AvroFactory.builder().enable(AvroGenerator.Feature.AVRO_DEFAULT_ENABLED).build());
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        mapper.acceptJsonFormatVisitor(WithDefaults.class, gen);
        Schema schema = gen.getAvroSchema();
        assertEquals(JsonProperties.NULL_VALUE, schema.getField("avro").defaultVal());
        assertEquals(JsonProperties.NULL_VALUE, schema.getField("json").defaultVal());
        assertEquals(JsonProperties.NULL_VALUE, schema.getField("noDefault").defaultVal());
        assertNull(schema.getField("simpleInt").defaultVal());
        assertEquals(JsonProperties.NULL_VALUE, schema.getField("integer").defaultVal());
        assertNull(schema.getField("required").defaultVal());
    }
}
