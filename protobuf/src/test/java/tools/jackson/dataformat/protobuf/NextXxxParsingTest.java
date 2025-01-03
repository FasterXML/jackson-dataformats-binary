package tools.jackson.dataformat.protobuf;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.io.SerializedString;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class NextXxxParsingTest extends ProtobufTestBase
{
    final private static String PROTOC_STRINGS =
            "message Strings {\n"
            +" repeated string values = 3;\n"
            +"}\n"
    ;

    static class Strings {
        public String[] values;

        public Strings() { }
        public Strings(String... v) { values = v; }
    }

    final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    public void testNextFieldAndText() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STRINGS);
        final ObjectWriter w = MAPPER.writerFor(Strings.class)
                .with(schema);
        Strings input = new Strings("Dogs", "like", "Baco\u00F1");
        byte[] bytes = w.writeValueAsBytes(input);

        JsonParser p = MAPPER.reader()
                .with(schema)
                .createParser(bytes);

        assertFalse(p.nextName(new SerializedString("values")));
        assertToken(JsonToken.START_OBJECT, p.currentToken());

        assertTrue(p.nextName(new SerializedString("values")));
        assertEquals("values", p.currentName());

        // 23-May-2016, tatu: Not working properly yet:
//        assertEquals("{values}", p.getParsingContext().toString());

        assertNull(p.nextName());
        assertToken(JsonToken.START_ARRAY, p.currentToken());

        assertEquals(input.values[0], p.nextStringValue());
        assertEquals(input.values[0], p.getString());

        assertEquals(-1, p.nextIntValue(-1));
        assertToken(JsonToken.VALUE_STRING, p.currentToken());
        assertEquals(input.values[1], p.getString());
        assertEquals(input.values[2], p.nextStringValue());
        assertEquals(input.values[2], p.getString());

        assertNull(p.nextStringValue());
        assertToken(JsonToken.END_ARRAY, p.currentToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    public void testNextInt() throws Exception
    {
        ProtobufSchema point3Schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT3);
        final Point3 input = new Point3(Integer.MAX_VALUE, -1, Integer.MIN_VALUE);
        byte[] bytes = MAPPER.writer(point3Schema).writeValueAsBytes(input);

        JsonParser p = MAPPER.reader()
                .with(point3Schema)
                .createParser(bytes);
        assertEquals(-1, p.nextIntValue(-1));
        assertToken(JsonToken.START_OBJECT, p.currentToken());
        assertEquals(-1, p.nextIntValue(-1));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals(Integer.MAX_VALUE, p.nextIntValue(0));
        assertEquals("y", p.nextName());
        assertEquals(-1L, p.nextLongValue(0L));
        assertEquals("z", p.nextName());
        assertEquals(Integer.MIN_VALUE, p.nextIntValue(0));
        assertNull(p.nextBooleanValue());
        assertToken(JsonToken.END_OBJECT, p.currentToken());
        p.close();
    }
}
