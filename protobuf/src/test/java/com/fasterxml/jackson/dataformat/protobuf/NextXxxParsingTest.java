package com.fasterxml.jackson.dataformat.protobuf;

import java.io.StringWriter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

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

    public void testNextXxx() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STRINGS);
        final ObjectWriter w = MAPPER.writerFor(Strings.class)
                .with(schema);
        Strings input = new Strings("Dogs", "like", "Baco\u00F1");
        byte[] bytes = w.writeValueAsBytes(input);

        JsonParser p = MAPPER.getFactory().createParser(bytes);
        p.setSchema(schema);

        assertFalse(p.nextFieldName(new SerializedString("values")));
        assertToken(JsonToken.START_OBJECT, p.getCurrentToken());
        
        assertTrue(p.nextFieldName(new SerializedString("values")));
        assertEquals("values", p.getCurrentName());

        // 23-May-2016, tatu: Not working properly yet:
//        assertEquals("{values}", p.getParsingContext().toString());

        assertNull(p.nextFieldName());
        assertToken(JsonToken.START_ARRAY, p.getCurrentToken());

        assertEquals(input.values[0], p.nextTextValue());
        assertEquals(input.values[0], p.getText());

        assertEquals(-1, p.nextIntValue(-1));
        assertToken(JsonToken.VALUE_STRING, p.getCurrentToken());
        assertEquals(input.values[1], p.getText());
        assertEquals(input.values[2], p.nextTextValue());
        assertEquals(input.values[2], p.getText());

        assertNull(p.nextTextValue());
        assertToken(JsonToken.END_ARRAY, p.getCurrentToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }
}
