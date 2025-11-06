package com.fasterxml.jackson.dataformat.avro.schemaev;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for issue #275: Avro backward compatibility when adding fields with default values.
 * <p>
 * This test demonstrates that Jackson Avro DOES support backward compatibility correctly,
 * but users MUST use the {@code withReaderSchema()} method to enable schema resolution.
 * <p>
 * The key insight is that Avro binary format does not include schema metadata in the
 * serialized data. Therefore, when reading data that was written with schema A using
 * schema B, the library needs to be explicitly told about both schemas through the
 * {@code withReaderSchema()} API.
 * <p>
 * Common mistake: Trying to read old data with a new schema directly leads to
 * "Unexpected end-of-input" errors because the parser tries to read fields that
 * don't exist in the binary data.
 * <p>
 * Correct usage pattern:
 * <pre>
 * // Write with old schema
 * AvroSchema writerSchema = mapper.schemaFrom(OLD_SCHEMA_JSON);
 * byte[] data = mapper.writer(writerSchema).writeValueAsBytes(object);
 *
 * // Read with new schema (that has additional fields with defaults)
 * AvroSchema readerSchema = mapper.schemaFrom(NEW_SCHEMA_JSON);
 * AvroSchema resolved = writerSchema.withReaderSchema(readerSchema);
 * MyObject result = mapper.readerFor(MyObject.class)
 *     .with(resolved)  // Use resolved schema, not readerSchema directly!
 *     .readValue(data);
 * </pre>
 */
public class Evolution275Test extends AvroTestBase
{
    // Original schema with 8 fields (simulating the issue scenario)
    static String SCHEMA_V1_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'Employee',\n"+
            " 'fields':[\n"+
            "    { 'name':'code', 'type':'string' },\n"+
            "    { 'name':'countryCode', 'type':'string' },\n"+
            "    { 'name':'createdBy', 'type':'string' },\n"+
            "    { 'name':'createdDate', 'type':'string' },\n"+
            "    { 'name':'id', 'type':'long' },\n"+
            "    { 'name':'lastModifiedBy', 'type':'string' },\n"+
            "    { 'name':'lastModifiedDate', 'type':'string' },\n"+
            "    { 'name':'name', 'type':'string' }\n"+
            " ]\n"+
            "}\n");

    // Updated schema adding a 9th field with null default at the end
    static String SCHEMA_V2_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'Employee',\n"+
            " 'fields':[\n"+
            "    { 'name':'code', 'type':'string' },\n"+
            "    { 'name':'countryCode', 'type':'string' },\n"+
            "    { 'name':'createdBy', 'type':'string' },\n"+
            "    { 'name':'createdDate', 'type':'string' },\n"+
            "    { 'name':'id', 'type':'long' },\n"+
            "    { 'name':'lastModifiedBy', 'type':'string' },\n"+
            "    { 'name':'lastModifiedDate', 'type':'string' },\n"+
            "    { 'name':'name', 'type':'string' },\n"+
            "    { 'name':'phone', 'type':['null', 'string'], 'default':null }\n"+
            " ]\n"+
            "}\n");

    // Simpler test with just 2 fields + new field with null default
    static String SCHEMA_SIMPLE_V1_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'SimpleRecord',\n"+
            " 'fields':[\n"+
            "    { 'name':'id', 'type':'int' },\n"+
            "    { 'name':'name', 'type':'string' }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_SIMPLE_V2_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'SimpleRecord',\n"+
            " 'fields':[\n"+
            "    { 'name':'id', 'type':'int' },\n"+
            "    { 'name':'name', 'type':'string' },\n"+
            "    { 'name':'phone', 'type':['null', 'string'], 'default':null }\n"+
            " ]\n"+
            "}\n");

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class Employee {
        public String code;
        public String countryCode;
        public String createdBy;
        public String createdDate;
        public long id;
        public String lastModifiedBy;
        public String lastModifiedDate;
        public String name;
        public String phone;

        protected Employee() { }

        public Employee(String code, String countryCode, String createdBy,
                String createdDate, long id, String lastModifiedBy,
                String lastModifiedDate, String name) {
            this.code = code;
            this.countryCode = countryCode;
            this.createdBy = createdBy;
            this.createdDate = createdDate;
            this.id = id;
            this.lastModifiedBy = lastModifiedBy;
            this.lastModifiedDate = lastModifiedDate;
            this.name = name;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class SimpleRecord {
        public int id;
        public String name;
        public String phone;

        protected SimpleRecord() { }

        public SimpleRecord(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testSimpleAddNullableFieldWithDefault() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_SIMPLE_V1_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_SIMPLE_V2_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        // Write data using old schema (without phone field)
        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new SimpleRecord(1, "Alice"));

        // Read using new schema (with phone field defaulting to null)
        // This should NOT throw "Unexpected end-of-input in FIELD_NAME"
        SimpleRecord result = MAPPER.readerFor(SimpleRecord.class)
                .with(xlate)
                .readValue(avro);

        assertEquals(1, result.id);
        assertEquals("Alice", result.name);
        assertNull(result.phone); // Should use default value
    }

    // This test demonstrates INCORRECT usage: trying to read data serialized with an old schema
    // using a new schema directly, without calling withReaderSchema().
    // This is expected to fail because Avro binary format doesn't include schema metadata,
    // so the reader can't know the data was written with a different schema.
    // Users MUST call withReaderSchema() when reading data written with a different schema.
    @Test
    public void testSimpleAddNullableFieldWithDefaultWrongUsage() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_SIMPLE_V1_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_SIMPLE_V2_JSON);

        // Write data using old schema (without phone field)
        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new SimpleRecord(1, "Alice"));

        // INCORRECT: Try to read with new schema directly without using withReaderSchema
        // This triggers EOF error because the reader expects to find the phone field in binary data
        // but the data doesn't contain it.
        JsonEOFException thrown = assertThrows(JsonEOFException.class, () -> {
            MAPPER.readerFor(SimpleRecord.class)
                    .with(dstSchema)  // Using dstSchema directly instead of xlate
                    .readValue(avro);
        });

        verifyException(thrown, "Unexpected end-of-input in FIELD_NAME");
    }

    @Test
    public void testAddNullableFieldWithDefault() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_V1_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_V2_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        // Write data using old schema (without phone field)
        Employee emp = new Employee("EMP001", "US", "admin", "2024-01-01",
                123L, "admin", "2024-01-01", "John Doe");
        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(emp);

        // Read using new schema (with phone field defaulting to null)
        // This should NOT throw "Unexpected end-of-input in FIELD_NAME"
        Employee result = MAPPER.readerFor(Employee.class)
                .with(xlate)
                .readValue(avro);

        assertEquals("EMP001", result.code);
        assertEquals("US", result.countryCode);
        assertEquals("admin", result.createdBy);
        assertEquals("2024-01-01", result.createdDate);
        assertEquals(123L, result.id);
        assertEquals("admin", result.lastModifiedBy);
        assertEquals("2024-01-01", result.lastModifiedDate);
        assertEquals("John Doe", result.name);
        assertNull(result.phone); // Should use default value
    }
}
