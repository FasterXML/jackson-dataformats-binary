package tools.jackson.dataformat.avro;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.assertj.core.api.Assertions.assertThat;

public class BigDecimalSerializationAndDeserializationTest extends AvroTestBase {
    private static final AvroMapper MAPPER = new AvroMapper();

    static class BigDecimalAndName {
        public final BigDecimal bigDecimalValue;
        public final String name;

        @JsonCreator
        public BigDecimalAndName(
                @JsonProperty("bigDecimalValue") BigDecimal bigDecimalValue,
                @JsonProperty("name") String name) {
            this.bigDecimalValue = bigDecimalValue;
            this.name = name;
        }
    }

    // By default, BigDecimal is serialized to string
    @Test
    public void testSerialization_toString() throws Exception {
        // GIVEN
        String schemaString = "{" +
                "  \"type\" : \"record\"," +
                "  \"name\" : \"BigDecimalAndName\"," +
                "  \"namespace\" : \"test\"," +
                "  \"fields\" : [ {" +
                "    \"name\" : \"bigDecimalValue\"," +
                "    \"type\" : {" +
                "      \"type\" : \"string\"," +
                "      \"java-class\" : \"java.math.BigDecimal\"" +
                "    }" +
                "  }, {" +
                "    \"name\" : \"name\"," +
                "    \"type\" : \"string\"" +
                "  } ]" +
                "}";

        AvroSchema schema = MAPPER.schemaFrom(schemaString);

        // WHEN - serialize
        byte[] bytes = MAPPER.writer(schema)
                .writeValueAsBytes(new BigDecimalAndName(BigDecimal.valueOf(42.2), "peter"));

        // THEN
        assertThat(bytes).isEqualTo(new byte[]{
                // bigDecimalValue
                0x08, // -> 4 dec - bigDecimalValue property string value length
                0x34, 0x32, 0x2E, 0x32, // -> "42.2" in ASCII
                // name
                0x0A, // -> 5 dec - name property string length
                0x70, 0x65, 0x74, 0x65, 0x72 // -> "peter" in ASCII
        });

        // WHEN - deserialize
        BigDecimalAndName result = MAPPER.reader(schema)
                .forType(BigDecimalAndName.class)
                .readValue(bytes);

        // THEN
        assertThat(result.bigDecimalValue).isEqualTo(BigDecimal.valueOf(42.2));
        assertThat(result.name).isEqualTo("peter");
    }

    @Test
    public void testSerialization_toBytesWithLogicalTypeDecimal() throws Exception {
        // GIVEN
        String schemaString = "{" +
                "  \"type\" : \"record\"," +
                "  \"name\" : \"BigDecimalAndName\"," +
                "  \"namespace\" : \"test\"," +
                "  \"fields\" : [ {" +
                "    \"name\" : \"bigDecimalValue\"," +
                "    \"type\" : [ \"null\", {" +
                "      \"type\" : \"bytes\"," +
                "      \"logicalType\" : \"decimal\"," +
                "      \"precision\" : 10," +
                "      \"scale\" : 2" +
                "    } ]" +
                "  }, {" +
                "    \"name\" : \"name\"," +
                "    \"type\" : [ \"null\", \"string\" ]" +
                "  } ]" +
                "}";

        AvroSchema schema = MAPPER.schemaFrom(schemaString);

        // WHEN - serialize
        byte[] bytes = MAPPER.writer(schema)
                .writeValueAsBytes(new BigDecimalAndName(
                        new BigDecimal("42.2"),
                        "peter"));
        // THEN
        assertThat(bytes).isEqualTo(new byte[]{
                // bigDecimalValue
                0x02, // -> 1 dec - second bigDecimalValue property type (bytes)
                0x04, // -> 2 dec - bigDecimalValue property bytes length
                0x10, 0x7C, // -> 0x107C -> 4220 dec - it is 42.2 value in scale 2.
                // name
                0x02, // 1 dec - second name property type (string)
                0x0A, // -> 5 dec - name property string length
                0x70, 0x65, 0x74, 0x65, 0x72 // -> "peter" in ASCII
        });

        // WHEN - deserialize
        BigDecimalAndName result = MAPPER.reader(schema)
                .forType(BigDecimalAndName.class)
                .readValue(bytes);

        // THEN
        // Because scale of decimal logical type is 2, result is with 2 decimal places
        assertThat(result.bigDecimalValue).isEqualTo(new BigDecimal("42.20"));
        assertThat(result.name).isEqualTo("peter");
    }

    @Test
    public void testSerialization_toFixedWithLogicalTypeDecimal() throws Exception {
        // GIVEN
        String schemaString = "{" +
                "  \"type\" : \"record\"," +
                "  \"name\" : \"BigDecimalAndName\"," +
                "  \"namespace\" : \"com.fasterxml.jackson.dataformat.avro.BigDecimalTest\"," +
                "  \"fields\" : [ {" +
                "    \"name\" : \"bigDecimalValue\"," +
                "    \"type\" : [ \"null\", {" +
                "      \"type\" : \"fixed\"," +
                "      \"name\" : \"BigDecimalValueType\"," +
                "      \"namespace\" : \"\"," +
                "      \"size\" : 10," +
                "      \"logicalType\" : \"decimal\"," +
                "      \"precision\" : 10," +
                "      \"scale\" : 2" +
                "    } ]" +
                "  }, {" +
                "    \"name\" : \"name\"," +
                "    \"type\" : [ \"null\", \"string\" ]" +
                "  } ]" +
                "}";

        AvroSchema schema = MAPPER.schemaFrom(schemaString);

        // WHEN - serialize
        byte[] bytes = MAPPER.writer(schema)
                .writeValueAsBytes(new BigDecimalAndName(
                        new BigDecimal("42.2"),
                        "peter"));

        // THEN
        assertThat(bytes).isEqualTo(new byte[]{
                // bigDecimalValue
                0x02, // -> 1 dec - second bigDecimalValue property type (bytes)
                // 10 bytes long fixed value
                0x00 ,0x00 ,0x00 ,0x00 ,0x00 ,0x00 ,0x00 ,0x00 ,0x10 ,0x7C, // -> 0x107C -> 4220 dec - it is 42.2 value in scale 2.
                // name
                0x02, // 1 dec - second name property type (string)
                0x0A, // -> 5 dec - name property string length
                0x70, 0x65, 0x74, 0x65, 0x72 // -> "peter" in ASCII
        });

        // WHEN - deserialize
        BigDecimalAndName result = MAPPER.reader(schema)
                .forType(BigDecimalAndName.class)
                .readValue(bytes);

        // THEN
        // Because scale of decimal logical type is 2, result is with 2 decimal places
        assertThat(result.bigDecimalValue).isEqualTo(new BigDecimal("42.20"));
        assertThat(result.name).isEqualTo("peter");
    }

    // Verify BigDecimal in a union with STRING and DOUBLE selects STRING (not DOUBLE)
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
        assertThat(result.get("value")).isNotNull();
        // If it went through STRING, the value round-trips as a String
        assertThat(result.get("value")).isInstanceOf(String.class);
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
        assertThat(result.get("value")).isNotNull();
        // Should round-trip as BigDecimal via bytes, not lose precision via double
        assertThat(result.get("value")).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result.get("value")).isEqualByComparingTo(input);
    }

}
