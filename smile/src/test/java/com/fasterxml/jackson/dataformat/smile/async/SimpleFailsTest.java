package com.fasterxml.jackson.dataformat.smile.async;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.ObjectReader;

public class SimpleFailsTest extends AsyncTestBase
{
    private final ObjectReader READER = _smileReader(true); // require headers

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    public void testHeaderFailWithSmile()
    {
        byte[] data = _smileDoc("[ true, false ]", false);
        // and finally, error case too
        _testHeaderFailWithSmile(READER, data, 0, 100);
        _testHeaderFailWithSmile(READER, data, 0, 3);
        _testHeaderFailWithSmile(READER, data, 0, 1);
    }

    private void _testHeaderFailWithSmile(ObjectReader or,
            byte[] data, int offset, int readSize)
    {
        AsyncReaderWrapper r = asyncForBytes(or, 100, data, 0);
        try {
            r.nextToken();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Input does not start with Smile format header");
        }
    }
    
    public void testHeaderFailWithJSON()
    {
        byte[] data = "[ true ]".getBytes(StandardCharsets.UTF_8);
        // and finally, error case too
        _testHeaderFailWithJSON(READER, data, 0, 100);
        _testHeaderFailWithJSON(READER, data, 0, 3);
        _testHeaderFailWithJSON(READER, data, 0, 1);

        data = "{\"f\" : 123 }".getBytes(StandardCharsets.UTF_8);
        // and finally, error case too
        _testHeaderFailWithJSON(READER, data, 0, 100);
        _testHeaderFailWithJSON(READER, data, 0, 3);
        _testHeaderFailWithJSON(READER, data, 0, 1);
    }

    private void _testHeaderFailWithJSON(ObjectReader or,
            byte[] data, int offset, int readSize)
    {
        AsyncReaderWrapper r = asyncForBytes(or, 100, data, 0);
        try {
            r.nextToken();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Input does not start with Smile format header");
            verifyException(e, "plain JSON input?");
        }
    }
}
