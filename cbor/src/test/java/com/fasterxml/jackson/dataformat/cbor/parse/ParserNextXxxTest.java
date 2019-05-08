package com.fasterxml.jackson.dataformat.cbor.parse;

import java.io.*;
import java.util.Random;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.testutil.ThrottledInputStream;

// note: copied from test of same name from jackson-dataformat-smile
public class ParserNextXxxTest extends CBORTestBase
{
    public void testIsNextTokenName() throws Exception
    {
        _testIsNextTokenName1();
        _testIsNextTokenName2();
    }

    public void testIssue34() throws Exception
    {
        final int TESTROUNDS = 223;

        // build the big document to trigger issue
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(2000);
        JsonGenerator g = cborGenerator(bytes);
        for (int i = 0; i < TESTROUNDS; ++i) {
            g.writeStartObject();
            g.writeNumberField("fieldName", 1);
            g.writeEndObject();
        }
        g.close();
        final byte[] DOC = bytes.toByteArray();
        
        SerializableString fieldName = new SerializedString("fieldName");
        JsonParser parser = cborParser(DOC);

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
        byte[] DOC = cborDoc("{\"field\" :\"value\"}");
        
        SerializableString fieldName = new SerializedString("field");
        JsonParser parser = cborParser(DOC);
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
        // do 3 meg thingy
        final int SIZE = 3 * 1024 * 1024;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(SIZE + 20);

        JsonGenerator g = cborGenerator(bytes);

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
    
        JsonParser parser = cborParser(DOC);
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

    public void testNextValuesMisc() throws Exception
    {
        byte[] DOC = cborDoc("{\"field\" :\"value\", \"array\" : [ \"foo\", true ] }");

        SerializableString fieldName = new SerializedString("field");
        JsonParser parser = cborParser(DOC);
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextFieldName(fieldName));

        assertEquals("value", parser.nextTextValue());
        assertEquals("value", parser.getText());

        assertEquals("array", parser.nextFieldName());
        assertEquals(JsonToken.START_ARRAY, parser.nextToken());
        assertEquals("foo", parser.nextTextValue());
        assertEquals(Boolean.TRUE, parser.nextBooleanValue());
        assertEquals(JsonToken.END_ARRAY, parser.nextToken());

        assertNull(parser.nextFieldName());
        assertEquals(JsonToken.END_OBJECT, parser.currentToken());
        assertNull(parser.nextToken());

        assertNull(parser.nextBooleanValue());
        assertNull(parser.nextTextValue());
        assertNull(parser.nextFieldName());
        
        parser.close();
    }

    public void testNextTextValue() throws Exception
    {
        _testNextTextValue("ascii");
        _testNextTextValue("Something much longer to ensure short-text handling is not invoked 12345677890");
        _testNextTextValue("Short but with UTF-8: \u00A9 & \u00E8...");
        _testNextTextValue("Longer .................................................................. \u30D5...");
    }

    private void _testNextTextValue(String textValue) throws Exception
    {
        _testNextTextValue(textValue, 57, false);
        _testNextTextValue(textValue, -2094, true);
        _testNextTextValue(textValue, 0x10000, false);
        _testNextTextValue(textValue, -0x4900FFFF, true);
    }

    @SuppressWarnings("resource")
    private void _testNextTextValue(String textValue, int intValue, boolean slow)
        throws Exception
    {
        String doc = aposToQuotes(String.format(
                "['%s',true,{'a':'%s'},%d, 0.5]",
                textValue, textValue, intValue));
        InputStream in = new ByteArrayInputStream(cborDoc(doc));
        if (slow) {
            // let's force read for every single byte
            in = new ThrottledInputStream(in, 1);
        }
        JsonParser p = cborParser(in);

        assertToken(JsonToken.START_ARRAY, p.nextToken());
 
        assertEquals(textValue, p.nextTextValue());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.VALUE_TRUE, p.currentToken());

        assertNull(p.nextTextValue());
        assertToken(JsonToken.START_OBJECT, p.currentToken());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("a", p.currentName());
        assertEquals(textValue, p.nextTextValue());
        assertNull(p.nextTextValue());
        assertToken(JsonToken.END_OBJECT, p.currentToken());

        assertNull(p.nextTextValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.currentToken());
        assertEquals(intValue, p.getIntValue());
        assertEquals(intValue, p.getLongValue());
        assertEquals((double) intValue, p.getDoubleValue());

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
        final byte[] DOC = cborDoc("{\"name\":123,\"name2\":14,\"x\":\"name\"}");
        JsonParser p = cborParser(DOC);
        final SerializedString NAME = new SerializedString("name");
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.START_OBJECT, p.currentToken());
        assertTrue(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals(NAME.getValue(), p.currentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.currentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("name2", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("x", p.currentName());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.currentToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, p.currentToken());

        assertFalse(p.nextFieldName(NAME));
        assertNull(p.currentToken());

        p.close();

        // Actually, try again with slightly different sequence...
        p = cborParser(DOC);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertFalse(p.nextFieldName(new SerializedString("Nam")));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals(NAME.getValue(), p.currentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.currentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("name2", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("x", p.currentName());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.currentToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, p.currentToken());

        assertFalse(p.nextFieldName(NAME));
        assertNull(p.currentToken());

        p.close();
    }

    private void _testIsNextTokenName2() throws Exception
    {
        final byte[] DOC = cborDoc("{\"name\":123,\"name2\":-9999999999,\"x\":\"name\"}");
        JsonParser p = cborParser(DOC);
        SerializableString NAME = new SerializedString("name");
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.START_OBJECT, p.currentToken());
        assertTrue(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals(NAME.getValue(), p.currentName());
        assertEquals(NAME.getValue(), p.getText());
        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.currentToken());
        assertEquals(123, p.getIntValue());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("name2", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("x", p.currentName());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, p.currentToken());

        assertFalse(p.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, p.currentToken());

        assertNull(p.nextFieldName());
        assertNull(p.currentToken());

        p.close();
    }
}
