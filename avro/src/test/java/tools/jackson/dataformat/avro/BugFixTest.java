package tools.jackson.dataformat.avro;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for bugs found during code analysis.
 */
public class BugFixTest extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

    /*
    /**********************************************************************
    /* Fix 1: _resolveBigDecimalIndex preferred DOUBLE over STRING/BYTES
    /**********************************************************************
     */

    // Verify BigDecimal in a union with STRING and DOUBLE selects STRING
    @Test
    public void testBigDecimalUnionPrefersStringOverDouble() throws Exception
    {
        // Union with string first, double second
        String schemaJson = a2q("{" +
                "'type':'record'," +
                "'name':'Test'," +
                "'fields':[" +
                "  {'name':'value', 'type':['null','string','double']}" +
                "]" +
                "}");
        AvroSchema schema = MAPPER.schemaFrom(schemaJson);

        Map<String, Object> input = Map.of("value", BigDecimal.valueOf(123456789, 6));
        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);

        Map<String, Object> result = MAPPER.readerFor(Map.class)
                .with(schema)
                .readValue(bytes);
        // The BigDecimal should have been serialized as a string (index 1),
        // not as a double (index 2), preserving full precision
        assertNotNull(result.get("value"));
        // If it went through STRING, the value round-trips as a String
        assertTrue(result.get("value") instanceof String,
                "Expected String but got " + result.get("value").getClass().getSimpleName());
    }

    // Verify BigDecimal in a union with BYTES (decimal logical type) and DOUBLE selects BYTES
    @Test
    public void testBigDecimalUnionPrefersBytesOverDouble() throws Exception
    {
        String schemaJson = a2q("{" +
                "'type':'record'," +
                "'name':'Test'," +
                "'fields':[" +
                "  {'name':'value', 'type':['null'," +
                "    {'type':'bytes','logicalType':'decimal','precision':20,'scale':6}," +
                "    'double']}" +
                "]" +
                "}");
        AvroSchema schema = MAPPER.schemaFrom(schemaJson);

        BigDecimal input = BigDecimal.valueOf(123456789, 6);
        Map<String, Object> data = Map.of("value", input);
        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(data);

        Map<String, Object> result = MAPPER.readerFor(Map.class)
                .with(schema)
                .readValue(bytes);
        assertNotNull(result.get("value"));
        // Should round-trip as BigDecimal via bytes, not lose precision via double
        assertTrue(result.get("value") instanceof BigDecimal,
                "Expected BigDecimal but got " + result.get("value").getClass().getSimpleName());
        assertEquals(0, input.compareTo((BigDecimal) result.get("value")),
                "BigDecimal precision lost: expected " + input + " but got " + result.get("value"));
    }

    /*
    /**********************************************************************
    /* Fix 2: Stale type variable in _createRecord — MAP guard never fired
    /**********************************************************************
     */

    // Verify that a union resolving to MAP is handled correctly in _createRecord
    @SuppressWarnings("unchecked")
    @Test
    public void testUnionResolvingToMapType() throws Exception
    {
        // Schema where the union resolves to a map
        String schemaJson = a2q("{" +
                "'type':'record'," +
                "'name':'Test'," +
                "'fields':[" +
                "  {'name':'data', 'type':['null'," +
                "    {'type':'record','name':'Nested','fields':[" +
                "      {'name':'x','type':'int'}" +
                "    ]}," +
                "    {'type':'map','values':'string'}" +
                "  ]}" +
                "]" +
                "}");
        AvroSchema schema = MAPPER.schemaFrom(schemaJson);

        // Write a record where 'data' is a map (union index 2)
        Map<String, Object> input = Map.of("data", Map.of("key1", "val1", "key2", "val2"));
        byte[] bytes = MAPPER.writer(schema).writeValueAsBytes(input);

        Map<String, Object> result = MAPPER.readerFor(Map.class)
                .with(schema)
                .readValue(bytes);
        assertNotNull(result.get("data"));
        assertTrue(result.get("data") instanceof Map,
                "Expected Map but got " + result.get("data").getClass().getSimpleName());
        Map<String, String> dataMap = (Map<String, String>) result.get("data");
        assertEquals("val1", dataMap.get("key1"));
        assertEquals("val2", dataMap.get("key2"));
    }

    /*
    /**********************************************************************
    /* Fix 3: Integer defaults stored as float — precision loss
    /**********************************************************************
     */

    // Verify that int default values preserve precision (not stored as float)
    @Test
    public void testIntDefaultValuePrecision() throws Exception
    {
        // V1 has x and y; V2 adds 'largeInt' with a default that exceeds float precision
        String v1Json = a2q("{" +
                "'type':'record','name':'RootType'," +
                "'fields':[" +
                "  {'name':'x','type':'int'}," +
                "  {'name':'y','type':'int'}" +
                "]" +
                "}");
        // Default value 20000001 exceeds float's 24-bit mantissa (max exact int: 16777216)
        String v2Json = a2q("{" +
                "'type':'record','name':'RootType'," +
                "'fields':[" +
                "  {'name':'x','type':'int'}," +
                "  {'name':'largeInt','type':'int','default':20000001}," +
                "  {'name':'y','type':'int'}" +
                "]" +
                "}");

        AvroSchema v1Schema = MAPPER.schemaFrom(v1Json);
        AvroSchema v2Schema = MAPPER.schemaFrom(v2Json);

        // Write with V1 (no largeInt field)
        Map<String, Object> input = Map.of("x", 1, "y", 2);
        byte[] bytes = MAPPER.writer(v1Schema).writeValueAsBytes(input);

        // Read with V2 — should get the default value for largeInt
        AvroSchema xlate = v1Schema.withReaderSchema(v2Schema);
        Map<String, Object> result = MAPPER.readerFor(Map.class)
                .with(xlate)
                .readValue(bytes);
        assertEquals(1, result.get("x"));
        assertEquals(2, result.get("y"));
        // The critical assertion: default 20000001 must survive float truncation
        assertEquals(20000001, ((Number) result.get("largeInt")).intValue(),
                "Integer default lost precision — likely stored as float");
    }

    // Verify that long default values preserve precision
    @Test
    public void testLongDefaultValuePrecision() throws Exception
    {
        String v1Json = a2q("{" +
                "'type':'record','name':'RootType'," +
                "'fields':[" +
                "  {'name':'x','type':'int'}" +
                "]" +
                "}");
        // Default value 9007199254740993 exceeds float AND double integer precision
        String v2Json = a2q("{" +
                "'type':'record','name':'RootType'," +
                "'fields':[" +
                "  {'name':'x','type':'int'}," +
                "  {'name':'bigLong','type':'long','default':9007199254740993}" +
                "]" +
                "}");

        AvroSchema v1Schema = MAPPER.schemaFrom(v1Json);
        AvroSchema v2Schema = MAPPER.schemaFrom(v2Json);

        Map<String, Object> input = Map.of("x", 1);
        byte[] bytes = MAPPER.writer(v1Schema).writeValueAsBytes(input);

        AvroSchema xlate = v1Schema.withReaderSchema(v2Schema);
        Map<String, Object> result = MAPPER.readerFor(Map.class)
                .with(xlate)
                .readValue(bytes);
        assertEquals(1, result.get("x"));
        assertEquals(9007199254740993L, ((Number) result.get("bigLong")).longValue(),
                "Long default lost precision — likely stored as float");
    }
}
