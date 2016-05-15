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

    public void testNextFieldName() throws Exception
    {
        final int TESTROUNDS = 223;

        final SmileFactory f = new SmileFactory();
        
        // build the big document to trigger issue
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(2000);
        JsonGenerator g = f.createGenerator(bytes);
        for (int i = 0; i < TESTROUNDS; ++i) {
            g.writeStartObject();
            g.writeNumberField("fieldName", 1);
            g.writeEndObject();
        }
        g.close();
        final byte[] DOC = bytes.toByteArray();
        
        SerializableString fieldName = new SerializedString("fieldName");
        JsonParser parser = f.createParser(DOC);

        for (int i = 0; i < TESTROUNDS - 1; i++) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());

            // These will succeed
            assertTrue(parser.nextFieldName(fieldName));

            parser.nextLongValue(-1);
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        }

        assertEquals(JsonToken.START_OBJECT, parser.nextToken());

        // This will fail
        assertTrue(parser.nextFieldName(fieldName));
        parser.close();
    }

    public void testIssue38() throws Exception
    {
        final SmileFactory f = new SmileFactory();
        byte[] DOC = _smileDoc(f, "{\"field\" :\"value\"}", true);
        
        SerializableString fieldName = new SerializedString("field");
        JsonParser parser = f.createParser(DOC);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextFieldName(fieldName));
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("value", parser.getText());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());
        parser.close();
    }

    public void testNextNameWithLongContent() throws Exception
    {
        final SmileFactory f = new SmileFactory();

        // do 3 meg thingy
        final int SIZE = 3 * 1024 * 1024;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(SIZE + 20);

        JsonGenerator g = f.createGenerator(bytes);

        g.writeStartObject();
        Random rnd = new Random(1);
        int count = 0;

        while (bytes.size() < SIZE) {
            ++count;
            int val = rnd.nextInt();
            g.writeFieldName("f"+val);
            g.writeNumber(val % 1000);
        }
        g.writeEndObject();
        g.close();
        final byte[] DOC = bytes.toByteArray();
    
        JsonParser parser = f.createParser(DOC);
        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        rnd = new Random(1);
        for (int i = 0; i < count; ++i) {
            int exp = rnd.nextInt();
            SerializableString expName = new SerializedString("f"+exp);
            assertTrue(parser.nextFieldName(expName));
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
        byte[] docBytes = _smileDoc(f, doc, true);
        JsonParser p = _smileParser(docBytes);

        assertToken(JsonToken.START_ARRAY, p.nextToken());
 
        assertEquals(textValue, p.nextTextValue());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.VALUE_TRUE, p.currentToken());

        assertNull(p.nextTextValue());
        assertToken(JsonToken.START_OBJECT, p.currentToken());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("a", p.getCurrentName());
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
        SmileFactory f = new SmileFactory();
        final byte[] DOC = _smileDoc(f, "{\"name\":123,\"name2\":14,\"x\":\"name\"}", true);
        JsonParser p = f.createParser(DOC);
        final SerializedString NAME = new SerializedString("name");
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.START_OBJECT, p.getCurrentToken());
        assertTrue(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals(NAME.getValue(), p.getCurrentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.getCurrentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals("name2", p.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals("x", p.getCurrentName());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.getCurrentToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, p.getCurrentToken());

        assertFalse(p.nextFieldName(NAME));
        assertNull(p.getCurrentToken());

        p.close();

        // Actually, try again with slightly different sequence...
        p = f.createParser(DOC);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertFalse(p.nextFieldName(new SerializedString("Nam")));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals(NAME.getValue(), p.getCurrentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.getCurrentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals("name2", p.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals("x", p.getCurrentName());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.getCurrentToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, p.getCurrentToken());

        assertFalse(p.nextFieldName(NAME));
        assertNull(p.getCurrentToken());

        p.close();
    }

    private void _testIsNextTokenName2() throws Exception
    {
        SmileFactory f = new SmileFactory();
        final byte[] DOC = _smileDoc(f, "{\"name\":123,\"name2\":14,\"x\":\"name\"}", true);
        JsonParser p = f.createParser(DOC);
        SerializableString NAME = new SerializedString("name");
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.START_OBJECT, p.getCurrentToken());
        assertTrue(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals(NAME.getValue(), p.getCurrentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.getCurrentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals("name2", p.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.getCurrentToken());
        assertEquals("x", p.getCurrentName());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.getCurrentToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, p.getCurrentToken());

        assertFalse(p.nextFieldName(NAME));
        assertNull(p.getCurrentToken());

        p.close();
    }
}
