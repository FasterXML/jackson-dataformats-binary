package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.util.Random;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;

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

        final CBORFactory f = new CBORFactory();
        
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
        final CBORFactory f = new CBORFactory();
        byte[] DOC = cborDoc(f, "{\"field\" :\"value\"}");
        
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
        final CBORFactory f = new CBORFactory();

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
    
    /*
    /********************************************************
    /* Actual test code
    /********************************************************
     */

    private void _testIsNextTokenName1() throws Exception
    {
        CBORFactory f = new CBORFactory();
        final byte[] DOC = cborDoc(f, "{\"name\":123,\"name2\":14,\"x\":\"name\"}");
        JsonParser jp = f.createParser(DOC);
        final SerializedString NAME = new SerializedString("name");
        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.START_OBJECT, jp.getCurrentToken());
        assertTrue(jp.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals(NAME.getValue(), jp.getCurrentName());
        assertEquals(NAME.getValue(), jp.getText());
        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.getCurrentToken());
        assertEquals(123, jp.getIntValue());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals("name2", jp.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals("x", jp.getCurrentName());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, jp.getCurrentToken());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, jp.getCurrentToken());

        assertFalse(jp.nextFieldName(NAME));
        assertNull(jp.getCurrentToken());

        jp.close();

        // Actually, try again with slightly different sequence...
        jp = f.createParser(DOC);
        assertToken(JsonToken.START_OBJECT, jp.nextToken());
        assertFalse(jp.nextFieldName(new SerializedString("Nam")));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals(NAME.getValue(), jp.getCurrentName());
        assertEquals(NAME.getValue(), jp.getText());
        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.getCurrentToken());
        assertEquals(123, jp.getIntValue());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals("name2", jp.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals("x", jp.getCurrentName());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, jp.getCurrentToken());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, jp.getCurrentToken());

        assertFalse(jp.nextFieldName(NAME));
        assertNull(jp.getCurrentToken());

        jp.close();
    }

    private void _testIsNextTokenName2() throws Exception
    {
        CBORFactory f = new CBORFactory();
        final byte[] DOC = cborDoc(f, "{\"name\":123,\"name2\":14,\"x\":\"name\"}");
        JsonParser jp = f.createParser(DOC);
        SerializableString NAME = new SerializedString("name");
        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.START_OBJECT, jp.getCurrentToken());
        assertTrue(jp.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals(NAME.getValue(), jp.getCurrentName());
        assertEquals(NAME.getValue(), jp.getText());
        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.getCurrentToken());
        assertEquals(123, jp.getIntValue());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals("name2", jp.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.FIELD_NAME, jp.getCurrentToken());
        assertEquals("x", jp.getCurrentName());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.VALUE_STRING, jp.getCurrentToken());

        assertFalse(jp.nextFieldName(NAME));
        assertToken(JsonToken.END_OBJECT, jp.getCurrentToken());

        assertFalse(jp.nextFieldName(NAME));
        assertNull(jp.getCurrentToken());

        jp.close();
    }
}
