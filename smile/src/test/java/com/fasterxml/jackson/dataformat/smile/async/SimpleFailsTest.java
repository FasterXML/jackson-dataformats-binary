package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

public class SimpleFailsTest extends AsyncTestBase
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

    public void testHeaderFailWithSmile() throws IOException
    {
        byte[] data = _smileDoc("[ true, false ]", false);
        // and finally, error case too
        _testHeaderFailWithSmile(F_REQ_HEADERS, data, 0, 100);
        _testHeaderFailWithSmile(F_REQ_HEADERS, data, 0, 3);
        _testHeaderFailWithSmile(F_REQ_HEADERS, data, 0, 1);
    }

    private void _testHeaderFailWithSmile(SmileFactory f,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, 100, data, 0);
        try {
            r.nextToken();
            fail("Should not pass");
        } catch (JsonParseException e) {
            verifyException(e, "Input does not start with Smile format header");
        }
    }
    
    public void testHeaderFailWithJSON() throws IOException
    {
        byte[] data = "[ true ]".getBytes("UTF-8");
        // and finally, error case too
        _testHeaderFailWithJSON(F_REQ_HEADERS, data, 0, 100);
        _testHeaderFailWithJSON(F_REQ_HEADERS, data, 0, 3);
        _testHeaderFailWithJSON(F_REQ_HEADERS, data, 0, 1);

        data = "{\"f\" : 123 }".getBytes("UTF-8");
        // and finally, error case too
        _testHeaderFailWithJSON(F_REQ_HEADERS, data, 0, 100);
        _testHeaderFailWithJSON(F_REQ_HEADERS, data, 0, 3);
        _testHeaderFailWithJSON(F_REQ_HEADERS, data, 0, 1);
    }

    private void _testHeaderFailWithJSON(SmileFactory f,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, 100, data, 0);
        try {
            r.nextToken();
            fail("Should not pass");
        } catch (JsonParseException e) {
            verifyException(e, "Input does not start with Smile format header");
            verifyException(e, "plain JSON input?");
        }
    }
}
