package com.fasterxml.jackson.dataformat.smile.parse;

import java.io.*;
import java.util.Random;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class ParserNextXxxTest extends BaseTestForSmile
{
    public void testIsNextTokenName() throws Exception
    {
        _testIsNextTokenName1();
        _testIsNextTokenName2();
    }

    public void testNextName() throws Exception
    {
        final int TESTROUNDS = 223;

        // build the big document to trigger issue
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(2000);
        try (JsonGenerator g = _smileGenerator(bytes, true)) {
            for (int i = 0; i < TESTROUNDS; ++i) {
                g.writeStartObject();
                g.writeNumberProperty("fieldName", 1);
                g.writeEndObject();
            }
        }
        final byte[] DOC = bytes.toByteArray();

        SerializableString fieldName = new SerializedString("fieldName");
        try (JsonParser parser = _smileParser(DOC)) {
            for (int i = 0; i < TESTROUNDS - 1; i++) {
                assertEquals(JsonToken.START_OBJECT, parser.nextToken());
                // These will succeed
                assertTrue(parser.nextName(fieldName));
                parser.nextLongValue(-1);
                assertEquals(JsonToken.END_OBJECT, parser.nextToken());
            }
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            // This will fail
            assertTrue(parser.nextName(fieldName));
        }
        // 20-Mar-2021, tatu: How about negative test too, just in case?
        try (JsonParser parser = _smileParser(DOC)) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            assertFalse(parser.nextName(new SerializedString("fieldNamX")));
            assertEquals(JsonToken.PROPERTY_NAME, parser.currentToken());
            assertEquals("fieldName", parser.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertToken(JsonToken.END_OBJECT, parser.nextToken());
        }
    }

    public void testIssue38() throws Exception
    {
        byte[] DOC = _smileDoc("{\"field\" :\"value\"}", true);
        
        SerializableString fieldName = new SerializedString("field");
        JsonParser parser = _smileParser(DOC);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextName(fieldName));
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("value", parser.getText());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
        parser.close();
    }

    public void testNextNameWithLongContent() throws Exception
    {
        // do 3 meg thingy
        final int SIZE = 3 * 1024 * 1024;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(SIZE + 20);

        JsonGenerator g = _smileGenerator(bytes, true);

        g.writeStartObject();
        Random rnd = new Random(1);
        int count = 0;

        while (bytes.size() < SIZE) {
            ++count;
            int val = rnd.nextInt();
            g.writeName("f"+val);
            g.writeNumber(val % 1000);
        }
        g.writeEndObject();
        g.close();
        final byte[] DOC = bytes.toByteArray();
    
        JsonParser parser = _smileParser(DOC);
        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        rnd = new Random(1);
        for (int i = 0; i < count; ++i) {
            int exp = rnd.nextInt();
            SerializableString expName = new SerializedString("f"+exp);
            assertTrue(parser.nextName(expName));
            assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(exp % 1000, parser.getIntValue());
        }
        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    public void testNextTextValue() throws Exception
    {
        final SmileFactory f = new SmileFactory();

        _testNextTextValue(f, "ascii");
        _testNextTextValue(f, "Something much longer to ensure short-text handling is not invoked 12345677890");
        _testNextTextValue(f, "Short but with UTF-8: \u00A9 & \u00E8...");
        _testNextTextValue(f, "Longer .................................................................. \u30D5...");
    }

    private void _testNextTextValue(SmileFactory f, String textValue) throws Exception
    {
        String doc = aposToQuotes(String.format(
                "['%s',true,{'a':'%s'},123, 0.5]",
                textValue, textValue));
        byte[] docBytes = _smileDoc(doc, true);
        JsonParser p = _smileParser(docBytes);

        assertToken(JsonToken.START_ARRAY, p.nextToken());
 
        assertEquals(textValue, p.nextTextValue());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.VALUE_TRUE, p.currentToken());

        assertNull(p.nextTextValue());
        assertToken(JsonToken.START_OBJECT, p.currentToken());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals("a", p.currentName());
        assertEquals(textValue, p.nextTextValue());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.END_OBJECT, p.currentToken());

        assertNull(p.nextTextValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.currentToken());
        assertEquals(123, p.getIntValue());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.currentToken());
        assertEquals(0.5, p.getDoubleValue());
        
        assertNull(p.nextTextValue());
        assertToken(JsonToken.END_ARRAY, p.currentToken());
        p.close();
    }

    /*
    /********************************************************
    /* Actual test code
    /********************************************************
     */

    private void _testIsNextTokenName1() throws Exception
    {
        final byte[] DOC = _smileDoc("{\"name\":123,\"name2\":14,\"x\":\"name\"}", true);
        JsonParser p = _smileParser(DOC);
        final SerializedString NAME = new SerializedString("name");
        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.START_OBJECT, p.currentToken());
        assertTrue(p.nextName(NAME));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals(NAME.getValue(), p.currentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.currentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals("name2", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals("x", p.currentName());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.currentToken());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.END_OBJECT, p.currentToken());

        assertFalse(p.nextName(NAME));
        assertNull(p.currentToken());

        p.close();

        // Actually, try again with slightly different sequence...
        p = _smileParser(DOC);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertFalse(p.nextName(new SerializedString("Nam")));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals(NAME.getValue(), p.currentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.currentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals("name2", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals("x", p.currentName());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.currentToken());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.END_OBJECT, p.currentToken());

        assertFalse(p.nextName(NAME));
        assertNull(p.currentToken());

        p.close();
    }

    private void _testIsNextTokenName2() throws Exception
    {
        final byte[] DOC = _smileDoc("{\"name\":123,\"name2\":14,\"x\":\"name\"}", true);
        JsonParser p = _smileParser(DOC);
        SerializableString NAME = new SerializedString("name");
        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.START_OBJECT, p.currentToken());
        assertTrue(p.nextName(NAME));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals(NAME.getValue(), p.currentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.currentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals("name2", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals("x", p.currentName());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.currentToken());

        assertFalse(p.nextName(NAME));
        assertToken(JsonToken.END_OBJECT, p.currentToken());

        assertFalse(p.nextName(NAME));
        assertNull(p.currentToken());

        p.close();
    }
}
