package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleNestedTest extends AsyncTestBase
{
    private final SmileFactory F_REQ_HEADERS = new SmileFactory();
    {
        F_REQ_HEADERS.enable(SmileParser.Feature.REQUIRE_HEADER);
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    @Test
    public void testStuffInObject() throws Exception
    {
        byte[] data = _smileDoc(aposToQuotes("{'foobar':[1,2,-999],'other':{'':null} }"), true);

        SmileFactory f = F_REQ_HEADERS;
        _testStuffInObject(f, data, 0, 100);
        _testStuffInObject(f, data, 0, 3);
        _testStuffInObject(f, data, 0, 1);

        _testStuffInObject(f, data, 1, 100);
        _testStuffInObject(f, data, 1, 3);
        _testStuffInObject(f, data, 1, 1);
    }

    private void _testStuffInObject(SmileFactory f,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertFalse(r.parser().hasTextCharacters());

        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("foobar", r.currentName());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertEquals("[", r.currentText());
        assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
        assertEquals(1, r.getIntValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
        assertEquals(2, r.getIntValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
        assertEquals(-999, r.getIntValue());
        assertToken(JsonToken.END_ARRAY, r.nextToken());

        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("other", r.currentName());
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("", r.currentName());
        assertToken(JsonToken.VALUE_NULL, r.nextToken());
        assertToken(JsonToken.END_OBJECT, r.nextToken());

        assertToken(JsonToken.END_OBJECT, r.nextToken());

        // another twist: close in the middle, verify
        r = asyncForBytes(f, readSize, data, offset);
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        r.parser().close();
        assertTrue(r.parser().isClosed());
        assertNull(r.parser().nextToken());
    }

    @Test
    public void testStuffInArray() throws Exception
    {
        byte[] data = _smileDoc(aposToQuotes("[true,{'extraOrdinary':''},[null],{'extraOrdinary':23}]"), true);

        SmileFactory f = F_REQ_HEADERS;
        _testStuffInArray(f, data, 0, 100);
        _testStuffInArray(f, data, 0, 3);
        _testStuffInArray(f, data, 0, 1);

        _testStuffInArray(f, data, 1, 100);
        _testStuffInArray(f, data, 1, 3);
        _testStuffInArray(f, data, 1, 1);
    }

    private void _testStuffInArray(SmileFactory f,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertFalse(r.parser().hasTextCharacters());

        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertEquals("{", r.currentText());
        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("extraOrdinary", r.currentName());
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        assertEquals("", r.currentText());
        assertToken(JsonToken.END_OBJECT, r.nextToken());

        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_NULL, r.nextToken());
        assertToken(JsonToken.END_ARRAY, r.nextToken());

        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals("extraOrdinary", r.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
        assertEquals(23, r.getIntValue());
        assertToken(JsonToken.END_OBJECT, r.nextToken());

        assertToken(JsonToken.END_ARRAY, r.nextToken());
    }
}
