package tools.jackson.dataformat.protobuf;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;

import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnumHandlingTest extends ProtobufTestBase
{
    public enum TinyEnum {
        X;
    }

    public enum BigEnum {
        A, B, C, D, E,
        F, G, H, I, J;
    }

    public enum StandardEnum {
        FIRST,
        @JsonEnumDefaultValue
        SECOND,
        THIRD;
    }

    public enum NonStandardEnum {
        FIRST,

        @JsonEnumDefaultValue
        SECOND;
    }

    public static class TinyEnumWrapper {
        public TinyEnum value;

        public TinyEnumWrapper() { }
        public TinyEnumWrapper(TinyEnum v) { value = v; }
    }

    public static class BigEnumWrapper {
        public BigEnum value;

        public BigEnumWrapper() { }
        public BigEnumWrapper(BigEnum v) { value = v; }
    }

    public static class StandardEnumWrapper {
        public StandardEnum value;
    }

    public static class NonStandardEnumWrapper {
        public NonStandardEnum value;
    }

    final protected static String PROTOC_STANDARD_ENUM =
            "message StandardEnumWrapper {\n"
            +" enum StandardEnum {\n"
            +"   FIRST = 0;\n"
            +"   SECOND = 1;\n"
            +" }\n"
            +" optional StandardEnum value = 1;\n"
            +"}\n"
    ;

    final protected static String PROTOC_NON_STANDARD_ENUM =
            "message NonStandardEnumWrapper {\n"
            +" enum NonStandardEnum {\n"
            +"   FIRST = 10;\n"
            +"   SECOND = 20;\n"
            +" }\n"
            +" optional NonStandardEnum value = 1;\n"
            +"}\n"
    ;

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = newObjectMapper();

    @Test
    public void testBigEnum() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(BigEnumWrapper.class);
        final ObjectWriter w = MAPPER.writer(schema);
        BigEnumWrapper input = new BigEnumWrapper(BigEnum.H);

        byte[] bytes = w.writeValueAsBytes(input);

        assertNotNull(bytes);
        // type + short id == 2 bytes
        assertEquals(2, bytes.length);

        ObjectReader r =  MAPPER.readerFor(new TypeReference<BigEnumWrapper> () {}).with(schema);
        BigEnumWrapper result = r.readValue(bytes);
        assertEquals(input.value, result.value);
    }

    @Test
    public void testTinyEnum() throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(TinyEnumWrapper.class);
        final ObjectWriter w = MAPPER.writer(schema);
        TinyEnumWrapper input = new TinyEnumWrapper(TinyEnum.X);

        byte[] bytes = w.writeValueAsBytes(input);

        assertNotNull(bytes);
        // type + short id == 2 bytes
        assertEquals(2, bytes.length);

        ObjectReader r =  MAPPER.readerFor(TinyEnumWrapper.class).with(schema);
        TinyEnumWrapper result = r.readValue(bytes);
        assertEquals(input.value, result.value);
    }

    @Test
    public void testUnknownNonStandardEnumFailsByDefault() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_NON_STANDARD_ENUM);
        byte[] bytes = { 0x08, 0x01 };

        Exception e = assertThrows(Exception.class, () ->
                MAPPER.readerFor(NonStandardEnumWrapper.class)
                        .with(schema)
                        .readValue(bytes));
        assertTrue(e.getMessage().contains("Unknown id 1 (for enum field value)"));
    }

    @Test
    public void testUnknownNonStandardEnumUsesProtobufDefault() throws Exception
    {
        ProtobufMapper mapper = ProtobufMapper.builder()
                .enable(ProtobufReadFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .build();
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_NON_STANDARD_ENUM);
        byte[] bytes = { 0x08, 0x1e };

        try (JsonParser p = mapper.reader().with(schema).createParser(bytes)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("value", p.currentName());
            assertEquals(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("FIRST", p.getString());
            assertEquals(JsonToken.END_OBJECT, p.nextToken());
        }

        NonStandardEnumWrapper result = mapper.readerFor(NonStandardEnumWrapper.class)
                .with(schema)
                .readValue(bytes);
        assertEquals(NonStandardEnum.FIRST, result.value);
    }

    @Test
    public void testUnknownStandardEnumUsesProtobufDefault() throws Exception
    {
        ProtobufMapper mapper = ProtobufMapper.builder()
                .enable(ProtobufReadFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .build();
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STANDARD_ENUM);
        byte[] bytes = { 0x08, 0x02 };

        try (JsonParser p = mapper.reader().with(schema).createParser(bytes)) {
            assertEquals(JsonToken.START_OBJECT, p.nextToken());
            assertEquals(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("value", p.currentName());
            assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(0, p.getIntValue());
            assertEquals(JsonToken.END_OBJECT, p.nextToken());
        }

        StandardEnumWrapper result = mapper.readerFor(StandardEnumWrapper.class)
                .with(schema)
                .readValue(bytes);

        assertEquals(StandardEnum.FIRST, result.value);
    }

    @Test
    public void testUnknownStandardEnumRetainsExistingBehaviorByDefault() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STANDARD_ENUM);
        byte[] bytes = { 0x08, 0x02 };

        StandardEnumWrapper result = MAPPER.readerFor(StandardEnumWrapper.class)
                .with(schema)
                .readValue(bytes);

        assertEquals(StandardEnum.THIRD, result.value);
    }
}
