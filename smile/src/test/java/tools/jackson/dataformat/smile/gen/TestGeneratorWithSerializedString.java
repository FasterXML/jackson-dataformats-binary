package tools.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;

import tools.jackson.core.*;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

public class TestGeneratorWithSerializedString extends BaseTestForSmile
{
    final static String NAME_WITH_QUOTES = "\"name\"";
    final static String NAME_WITH_LATIN1 = "P\u00f6ll\u00f6";

    private final SerializedString quotedName = new SerializedString(NAME_WITH_QUOTES);
    private final SerializedString latin1Name = new SerializedString(NAME_WITH_LATIN1);

    private final ObjectMapper MAPPER = smileMapper();

    public void testSimple()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator g = MAPPER.createGenerator(out);
        _writeSimple(g);
        g.close();
        byte[] smileB = out.toByteArray();
        _verifySimple(MAPPER.createParser(smileB));
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _writeSimple(JsonGenerator jgen)
    {
        // Let's just write array of 2 objects
        jgen.writeStartArray();

        jgen.writeStartObject();
        jgen.writeName(quotedName);
        jgen.writeString("a");
        jgen.writeName(latin1Name);
        jgen.writeString("b");
        jgen.writeEndObject();

        jgen.writeStartObject();
        jgen.writeName(latin1Name);
        jgen.writeString("c");
        jgen.writeName(quotedName);
        jgen.writeString("d");
        jgen.writeEndObject();

        jgen.writeEndArray();
    }

    private void _verifySimple(JsonParser p)
    {
        assertToken(JsonToken.START_ARRAY, p.nextToken());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals(NAME_WITH_QUOTES, p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("a", p.getText());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals(NAME_WITH_LATIN1, p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("b", p.getText());
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals(NAME_WITH_LATIN1, p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("c", p.getText());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals(NAME_WITH_QUOTES, p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("d", p.getText());
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
    }
}
