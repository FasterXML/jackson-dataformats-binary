package com.fasterxml.jackson.dataformat.smile.parse;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for verifying that multiple document output and document
 * boundaries and/or header mark handling works as expected
 */
public class DocBoundaryTest
    extends BaseTestForSmile
{
    @Test
    public void testNoHeadersNoEndMarker() throws Exception
    {
        _verifyMultiDoc(false, false);
    }

    @Test
    public void testHeadersNoEndMarker() throws Exception
    {
        _verifyMultiDoc(true, false);
    }

    @Test
    public void testEndMarkerNoHeader() throws Exception
    {
        _verifyMultiDoc(false, true);
    }

    @Test
    public void testHeaderAndEndMarker() throws Exception
    {
        _verifyMultiDoc(true, true);
    }

    @Test
    public void testExtraHeader() throws Exception
    {
        // also; sprinkling headers can be used to segment document
        for (boolean addHeader : new boolean[] { false, true }) {
            SmileFactory f = smileFactory(false, false, false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            SmileGenerator jg = f.createGenerator(out);
            jg.writeNumber(1);
            if (addHeader) jg.writeHeader();
            jg.writeNumber(2);
            if (addHeader) jg.writeHeader();
            jg.writeNumber(3);
            jg.close();

            SmileParser jp = f.createParser(out.toByteArray());
            assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
            assertEquals(1, jp.getIntValue());
            if (addHeader) {
                assertNull(jp.nextToken());
            }
            assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
            assertEquals(2, jp.getIntValue());
            if (addHeader) {
                assertNull(jp.nextToken());
            }
            assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
            assertEquals(3, jp.getIntValue());
            assertNull(jp.nextToken());
            jp.close();
            jg.close();
        }
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    protected void _verifyMultiDoc(boolean addHeader, boolean addEndMarker) throws Exception
    {
        SmileFactory f = smileFactory(false, addHeader, addEndMarker);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator jg = f.createGenerator(out);
        // First doc, JSON Object
        jg.writeStartObject();
        jg.writeEndObject();
        jg.close();
        // and second, array
        jg = f.createGenerator(out);
        jg.writeStartArray();
        jg.writeEndArray();
        jg.close();

        // and read it back
        SmileParser jp = f.createParser(out.toByteArray());
        assertToken(JsonToken.START_OBJECT, jp.nextToken());
        assertToken(JsonToken.END_OBJECT, jp.nextToken());

        // now: if one of header or end marker (or, both) enabled, should get null here:
        if (addHeader || addEndMarker) {
            assertNull(jp.nextToken());
        }

        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        assertToken(JsonToken.END_ARRAY, jp.nextToken());

        // end
        assertNull(jp.nextToken());
        // and no more:
        assertNull(jp.nextToken());
        jp.close();
    }
}
