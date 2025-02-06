package com.fasterxml.jackson.dataformat.avro;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;

public class EnumTest extends AvroTestBase
{
    // gender as Avro enum
	protected final static String ENUM_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Employee\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"gender\", \"type\": { \"type\" : \"enum\","
            +" \"name\": \"Gender\", \"symbols\": [\"M\",\"F\"] }"
            +"}\n"
            +"]}";

    // gender as Avro string
    protected final static String STRING_SCHEMA_JSON = "{"
            +" \"type\": \"record\", "
            +" \"name\": \"Employee\", "
            +" \"fields\": ["
            +" {\"name\": \"gender\", \"type\": \"string\"}"
            +"]}";

    protected enum Gender { M, F; }

    protected static class Employee {
        public Gender gender;
    }

    protected static class EmployeeStr {
        public String gender;
    }

    protected enum ABC {
        A,
        B,
        @JsonEnumDefaultValue
        C; 
    }

    protected static class ABCDefaultClass {
        public String name;
        @JsonProperty(required = true)
        public ABC abc;
    }

    private final AvroMapper MAPPER = newMapper();

    public void test_avroSchemaWithEnum_fromEnumValueToEnumValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(ENUM_SCHEMA_JSON);
        Employee input = new Employee();
        input.gender = Gender.F;

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        // Enum Gender.M is encoded as bytes array: {0}, where DEC 0 is encoded long value 0, Gender.M ordinal value
        // Enum Gender.F is encoded as bytes array: {2}, where DEC 2 is encoded long value 1, Gender.F ordinal value
        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }

    public void test_avroSchemaWithEnum_fromStringValueToEnumValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(ENUM_SCHEMA_JSON);
        EmployeeStr input = new EmployeeStr();
        input.gender = "F";

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(1, bytes.length); // measured to be current exp size

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }

    public void test_avroSchemaWithString_fromEnumValueToEnumValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(STRING_SCHEMA_JSON);
        Employee input = new Employee();
        input.gender = Gender.F;

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        // Enum Gender.F as string is encoded as {2, 70} bytes array.
        // Where
        //  - DEC 2, HEX 0x2, is a long value 1 written using variable-length zig-zag coding.
        //    It represents number of following characters in string "F"
        //  - DEC 70, HEX 0x46, is UTF-8 code for letter F
        //
        // Enum Gender.M as string is encoded as {2, 77} bytes array.
        // Where
        //   - DEC 2, HEX 0x2, is a long value 1. It is number of following characters in string "M"),
        //     written using variable-length zig-zag coding.
        //   - DEC 77, HEX 0x4D, is UTF-8 code for letter M
        //
		// See https://avro.apache.org/docs/1.8.2/spec.html#Encodings
        assertEquals(2, bytes.length); // measured to be current exp size
        assertEquals(0x2, bytes[0]);
        assertEquals(0x46, bytes[1]);

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }

    // Not sure this test makes sense
    public void test_avroSchemaWithString_fromStringValueToEnumValue() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom(STRING_SCHEMA_JSON);
        EmployeeStr input = new EmployeeStr();
        input.gender = "F";

        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(2, bytes.length); // measured to be current exp size

        // and then back
        Employee output = MAPPER.readerFor(Employee.class).with(schema)
                .readValue(bytes);
        assertNotNull(output);
        assertEquals(Gender.F, output.gender);
    }

    // [dataformats-binary#388]: Default value for enums with class
    public void testClassEnumWithDefault() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();

        MAPPER.acceptJsonFormatVisitor(ABCDefaultClass.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();
        assertNotNull(schema);

        String json = schema.getAvroSchema().toString(true);
        assertNotNull(json);

        // And read it back too just for fun
        AvroSchema s2 = MAPPER.schemaFrom(json);
        assertNotNull(s2);

        Schema avroSchema = s2.getAvroSchema();

        // String name, int value
        assertEquals(Type.RECORD, avroSchema.getType());
        Schema.Field f = avroSchema.getField("abc");
        assertNotNull(f);
        assertEquals("abc", f.name());

        assertEquals(Type.ENUM, f.schema().getType());
        assertEquals(ABC.C.toString(), f.schema().getEnumDefault());
        assertEquals(Stream.of(ABC.values())
                               .map(ABC::name)
                               .collect(Collectors.toList()), f.schema().getEnumSymbols());
    }   
}
