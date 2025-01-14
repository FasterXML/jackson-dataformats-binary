package tools.jackson.dataformat.smile.async;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectReader;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleNestedTest extends AsyncTestBase
{
    private final ObjectReader READER = _smileReader(true); // require headers

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    @Test
    public void testStuffInObject() throws Exception
    {
        byte[] data = _smileDoc(aposToQuotes("{'foobar':[1,2,-999],'other':{'':null} }"), true);
        _testStuffInObject(data, 0, 100);
        _testStuffInObject(data, 0, 3);
        _testStuffInObject(data, 0, 1);

        _testStuffInObject(data, 1, 100);
        _testStuffInObject(data, 1, 3);
        _testStuffInObject(data, 1, 1);
    }

    private void _testStuffInObject(byte[] data, int offset, int readSize) throws Exception
    {
        AsyncReaderWrapper r = asyncForBytes(READER, readSize, data, offset);
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertFalse(r.parser().hasStringCharacters());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
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

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("other", r.currentName());
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("", r.currentName());
        assertToken(JsonToken.VALUE_NULL, r.nextToken());
        assertToken(JsonToken.END_OBJECT, r.nextToken());

        assertToken(JsonToken.END_OBJECT, r.nextToken());

        // another twist: close in the middle, verify
        r = asyncForBytes(READER, readSize, data, offset);
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        r.parser().close();
        assertTrue(r.parser().isClosed());
        assertNull(r.parser().nextToken());
    }

    @Test
    public void testStuffInArray() throws Exception
    {
        byte[] data = _smileDoc(aposToQuotes("[true,{'extraOrdinary':''},[null],{'extraOrdinary':23}]"), true);

        _testStuffInArray(data, 0, 100);
        _testStuffInArray(data, 0, 3);
        _testStuffInArray(data, 0, 1);

        _testStuffInArray(data, 1, 100);
        _testStuffInArray(data, 1, 3);
        _testStuffInArray(data, 1, 1);
    }

    private void _testStuffInArray(byte[] data, int offset, int readSize) throws Exception
    {
        AsyncReaderWrapper r = asyncForBytes(READER, readSize, data, offset);
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertFalse(r.parser().hasStringCharacters());

        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertEquals("{", r.currentText());
        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("extraOrdinary", r.currentName());
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        assertEquals("", r.currentText());
        assertToken(JsonToken.END_OBJECT, r.nextToken());

        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_NULL, r.nextToken());
        assertToken(JsonToken.END_ARRAY, r.nextToken());

        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("extraOrdinary", r.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
        assertEquals(23, r.getIntValue());
        assertToken(JsonToken.END_OBJECT, r.nextToken());

        assertToken(JsonToken.END_ARRAY, r.nextToken());
    }
}
