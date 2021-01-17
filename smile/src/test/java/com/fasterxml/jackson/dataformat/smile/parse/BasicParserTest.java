package com.fasterxml.jackson.dataformat.smile.parse;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.*;
import com.fasterxml.jackson.dataformat.smile.testutil.ThrottledInputStream;

public class BasicParserTest extends BaseTestForSmile
{
    // Unit tests for verifying that if header/signature is required,
    // lacking it is fatal
    public void testMandatoryHeader() throws IOException
    {
        // first test failing case
        byte[] data = _smileDoc("[ null ]", false);
        try {
            _smileParser(data, true);
            fail("Should have gotten exception for missing header");
        } catch (Exception e) {
            verifyException(e, "does not start with Smile format header");
        }

        // and then test passing one
        JsonParser p = _smileParser(data, false);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertNull(p.currentName());
        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertNull(p.currentName());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.currentName());
        assertNull(p.nextToken());
        p.close();
    }

    public void testSimple() throws IOException
    {
        byte[] data = _smileDoc("[ true, null, false ]", true);
        _testSimple(false, data);
        _testSimple(true, data);
    }
    
    @SuppressWarnings("resource")
    private void _testSimple(boolean throttle, byte[] data) throws IOException
    {
        InputStream in = new ByteArrayInputStream(data);
        if (throttle) {
            in = new ThrottledInputStream(in, 1);
        }
        JsonParser p = _smileParser(in, true);
        
        assertNull(p.currentToken());
        assertNull(p.currentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertNull(p.currentName());
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertNull(p.currentName());
        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertNull(p.currentName());
        assertToken(JsonToken.VALUE_FALSE, p.nextToken());
        assertNull(p.currentName());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.currentName());
        assertNull(p.nextToken());
        p.close();
    }

    public void testIntInArray() throws IOException
    {
        byte[] data = _smileDoc("[ 25.0 ]");
        JsonParser p = _smileParser(data);
        assertNull(p.currentToken());
        assertNull(p.currentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(25, p.getIntValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.currentName());
        p.close();
    }
    
    public void testArrayWithString() throws IOException
    {
        byte[] data = _smileDoc("[ \"abc\" ]");
        JsonParser p = _smileParser(data);
        assertNull(p.currentToken());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("abc", p.getText());
        
        StringWriter w = new StringWriter();
        assertEquals(3, p.getText(w));
        assertEquals("abc", w.toString());
        
        assertEquals(0, p.getTextOffset());
        assertEquals(3, p.getTextLength());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    public void testEmptyStrings() throws IOException
    {
        // first, empty key
        byte[] data = _smileDoc("{ \"\":true }");
        JsonParser p = _smileParser(data);
        assertNull(p.currentToken());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("", p.currentName());

        StringWriter w = new StringWriter();
        assertEquals(0, p.getText(w));
        assertEquals("", w.toString());

        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // then empty value
        data = _smileDoc("{ \"abc\":\"\" }");
        p = _smileParser(data);
        assertNull(p.currentToken());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("abc", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("", p.getText());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // and combinations
        data = _smileDoc("{ \"\":\"\", \"\":\"\" }");
        p = _smileParser(data);
        assertNull(p.currentToken());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("", p.getText());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("", p.getText());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    // Test for ASCII String values longer than 64 bytes; separate
    // since handling differs
    public void testLongAsciiString() throws IOException
    {
        final String DIGITS = "1234567890";
        String LONG = DIGITS + DIGITS + DIGITS + DIGITS;
        LONG = LONG + LONG + LONG + LONG;
        byte[] data = _smileDoc(quote(LONG));

        try (JsonParser p = _smileParser(data)) {
            assertNull(p.currentToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
    	        assertEquals(LONG, p.getText());
    	        assertNull(p.nextToken());
        }
    }

    //Test for non-ASCII String values longer than 64 bytes; separate
    // since handling differs
    public void testLongUnicodeString() throws IOException
    {
        final String DIGITS = "1234567890";
        final String UNIC = "\u00F06"; // o with umlauts
        String LONG = DIGITS + UNIC + DIGITS + UNIC + UNIC + DIGITS + DIGITS;
        LONG = LONG + LONG + LONG;
        byte[] data = _smileDoc(quote(LONG));

        JsonParser p = _smileParser(data);
        assertNull(p.currentToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(LONG, p.getText());

        StringWriter w = new StringWriter();
        assertEquals(LONG.length(), p.getText(w));
        assertEquals(LONG, w.toString());

        assertNull(p.nextToken());
        p.close();
    }
    
    // Simple test for encoding where "Unicode" string value is
    // actually ascii (which is fine, encoders need not ensure it is not,
    // it's just not guaranteeing content IS ascii)
    public void testShortAsciiAsUnicodeString() throws IOException
    {
        byte[] data = new byte[] {
                (byte) 0x82, 0x64, 0x61, 0x74, 0x61
        };
        try (JsonParser p = _smileParser(data)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("data", p.getText());
            assertNull(p.nextToken());
        }
    }

    public void testTrivialObject() throws IOException
    {
        byte[] data = _smileDoc("{\"abc\":13}");
        JsonParser p = _smileParser(data);
        assertNull(p.currentToken());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("abc", p.currentName());
        assertEquals("abc", p.getText());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(13, p.getIntValue());    	
    	    assertToken(JsonToken.END_OBJECT, p.nextToken());
    	    p.close();
    }

    public void testSimpleObject() throws IOException
    {
    	byte[] data = _smileDoc("{\"a\":8, \"b\" : [ true ], \"c\" : { }, \"d\":{\"e\":null}}");
    	JsonParser p = _smileParser(data);
    	assertNull(p.currentToken());
    	assertToken(JsonToken.START_OBJECT, p.nextToken());

    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("a", p.currentName());
    	assertEquals("a", p.getText());
    	assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
    	assertEquals(8, p.getIntValue());

    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("b", p.currentName());
    	assertToken(JsonToken.START_ARRAY, p.nextToken());
    	assertToken(JsonToken.VALUE_TRUE, p.nextToken());
    	assertToken(JsonToken.END_ARRAY, p.nextToken());

    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("c", p.currentName());
    	assertToken(JsonToken.START_OBJECT, p.nextToken());
    	assertToken(JsonToken.END_OBJECT, p.nextToken());

    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("d", p.currentName());

    	assertToken(JsonToken.START_OBJECT, p.nextToken());
    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("e", p.currentName());
    	assertToken(JsonToken.VALUE_NULL, p.nextToken());
    	assertToken(JsonToken.END_OBJECT, p.nextToken());

    	assertToken(JsonToken.END_OBJECT, p.nextToken());
    	p.close();
    }

    public void testNestedObject() throws IOException
    {
        byte[] data = _smileDoc("[{\"a\":{\"b\":[1]}}]");
        JsonParser p = _smileParser(data);
        assertNull(p.currentToken());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // a
        assertEquals("a", p.currentName());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // b
        assertEquals("b", p.currentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertEquals("b", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }
    
    public void testJsonSampleDoc() throws IOException
    {
    	byte[] data = _smileDoc(SAMPLE_DOC_JSON_SPEC);
    	verifyJsonSpecSampleDoc(_smileParser(data), true);
    }

    public void testUnicodeStringValues() throws IOException
    {
        String uc = "\u00f6stl. v. Greenwich \u3333?";
        byte[] data = _smileDoc("[" +quote(uc)+"]");

        // First, just skipping
        JsonParser p = _smileParser(data);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // Then accessing data
        p = _smileParser(data);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(uc, p.getText());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // and then let's create longer text segment as well
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 200) {
            sb.append(uc);
        }
        final String longer = sb.toString();
        data = _smileDoc("["+quote(longer)+"]");

        // Ok once again, first skipping, then accessing
        p = _smileParser(data);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        p = _smileParser(data);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(longer, p.getText());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    public void testUnicodePropertyNames() throws IOException
    {
        String uc = "\u00f6stl. v. Greenwich \u3333";
        byte[] data = _smileDoc("{" +quote(uc)+":true}");

        // First, just skipping
        JsonParser p = _smileParser(data);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        // Then accessing data
        p = _smileParser(data);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals(uc, p.currentName());
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    // Simple test to verify that byte 0 is not used (an implementation
    // might mistakenly consider it a string value reference)
    public void testInvalidByte() throws IOException
    {
        byte[] data = new byte[] { SmileConstants.TOKEN_LITERAL_START_ARRAY,
                (byte) SmileConstants.TOKEN_PREFIX_SHARED_STRING_SHORT,
                (byte) SmileConstants.TOKEN_LITERAL_END_ARRAY
        };
        JsonParser p = _smileParser(data);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        // And now should get an error
        try {
            JsonToken t = p.nextToken();
            fail("Expected parse error, got: "+t);
        } catch (JacksonException e) {
            verifyException(e, "Invalid type marker byte 0x0");
        }
        p.close();
    }

    // [JACKSON-629]
    public void testNameBoundary() throws IOException
    {
        SmileFactory f = smileFactory(true, true, false);
        f = f.rebuild().disable(SmileGenerator.Feature.CHECK_SHARED_NAMES).build();

        // let's create 3 meg docs
        final int LEN = 3 * 1000 * 1000;
        final String FIELD = "field01"; // important: 7 chars

        for (int offset = 0; offset < 12; ++offset) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(LEN);
            // To trigger boundary condition, need to shuffle stuff around a bit...
            for (int i = 0; i < offset; ++i) {
                bytes.write(0);
            }
            
            // force back-refs off, easier to trigger problem
            JsonGenerator gen = f.createGenerator(ObjectWriteContext.empty(), bytes);
            
            int count = 0;
            do {
                gen.writeStartObject();
                // importa
                gen.writeNumberField(FIELD, count % 17);
                gen.writeEndObject();
                ++count;
            } while (bytes.size() < (LEN - 100));
            gen.close();
        
            // and then read back
            byte[] json = bytes.toByteArray();
            JsonParser p = _smileParser(new ByteArrayInputStream(json, offset, json.length-offset));
            int i = 0;

            while (i < count) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals(FIELD, p.currentName());
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals((i % 17), p.getIntValue());
                assertToken(JsonToken.END_OBJECT, p.nextToken());
                ++i;
            }
            // and should be done now
            assertNull(p.nextToken());
            p.close();
        }
    }

    public void testCharacters() throws IOException
    {
        // ensure we are using both back-ref types
        SmileFactory sf = SmileFactory.builder()
                .enable(SmileGenerator.Feature.CHECK_SHARED_NAMES,
                        SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
                .build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        
        JsonGenerator jgen = sf.createGenerator(ObjectWriteContext.empty(), bytes);
        jgen.writeStartArray();
        jgen.writeStartObject();
        jgen.writeStringField("key", "value");
        jgen.writeEndObject();
        jgen.writeStartObject();
        jgen.writeStringField("key", "value");
        jgen.writeEndObject();
        jgen.writeEndArray();
        jgen.close();

        JsonParser p = _smileParser(bytes.toByteArray());

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        String str;

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        str = new String(p.getTextCharacters(), p.getTextOffset(), p.getTextLength());
        assertEquals("key", str);
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        str = new String(p.getTextCharacters(), p.getTextOffset(), p.getTextLength());
        assertEquals("value", str);
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        str = new String(p.getTextCharacters(), p.getTextOffset(), p.getTextLength());
        assertEquals("key", str);
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        str = new String(p.getTextCharacters(), p.getTextOffset(), p.getTextLength());
        assertEquals("value", str);
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }


    public void testBufferRelease() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator generator = _smileGenerator(out, true);
        generator.writeStartObject();
        generator.writeStringField("a", "1");
        generator.writeEndObject();
        generator.flush();
        // add stuff that is NOT part of the Object
        out.write(new byte[] { 1, 2, 3 });
        generator.close();

        JsonParser parser = _smileParser(out.toByteArray());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());

        // Fine; but now should be able to retrieve 3 bytes that are (likely)
        // to have been  buffered

        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        assertEquals(3, parser.releaseBuffered(extra));
        byte[] extraBytes = extra.toByteArray();
        assertEquals((byte) 1, extraBytes[0]);
        assertEquals((byte) 2, extraBytes[1]);
        assertEquals((byte) 3, extraBytes[2]);
        
        parser.close();
    }    
    /*
    /**********************************************************
    /* Helper methods for use with json spec sample doc
    /**********************************************************
     */

    protected void verifyJsonSpecSampleDoc(JsonParser p, boolean verifyContents)
        throws IOException
    {
        verifyJsonSpecSampleDoc(p, verifyContents, true);
    }

    protected void verifyJsonSpecSampleDoc(JsonParser p, boolean verifyContents,
            boolean requireNumbers)
        throws IOException
    {
        if (!p.hasCurrentToken()) {
            p.nextToken();
        }
        assertToken(JsonToken.START_OBJECT, p.currentToken()); // main object

        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'Image'
        if (verifyContents) {
            verifyFieldName(p, "Image");
        }

        assertToken(JsonToken.START_OBJECT, p.nextToken()); // 'image' object

        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'Width'
        if (verifyContents) {
            verifyFieldName(p, "Width");
        }

        verifyIntToken(p.nextToken(), requireNumbers);
        if (verifyContents) {
            verifyIntValue(p, SAMPLE_SPEC_VALUE_WIDTH);
        }

        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'Height'
        if (verifyContents) {
            verifyFieldName(p, "Height");
        }

        verifyIntToken(p.nextToken(), requireNumbers);
        if (verifyContents) {
            verifyIntValue(p, SAMPLE_SPEC_VALUE_HEIGHT);
        }
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'Title'
        if (verifyContents) {
            verifyFieldName(p, "Title");
        }
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(SAMPLE_SPEC_VALUE_TITLE, getAndVerifyText(p));
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'Thumbnail'
        if (verifyContents) {
            verifyFieldName(p, "Thumbnail");
        }

        assertToken(JsonToken.START_OBJECT, p.nextToken()); // 'thumbnail' object
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'Url'
        if (verifyContents) {
            verifyFieldName(p, "Url");
        }
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        if (verifyContents) {
            assertEquals(SAMPLE_SPEC_VALUE_TN_URL, getAndVerifyText(p));
        }
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'Height'
        if (verifyContents) {
            verifyFieldName(p, "Height");
        }
        verifyIntToken(p.nextToken(), requireNumbers);
        if (verifyContents) {
            verifyIntValue(p, SAMPLE_SPEC_VALUE_TN_HEIGHT);
        }
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'Width'
        if (verifyContents) {
            verifyFieldName(p, "Width");
        }
        // Width value is actually a String in the example
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        if (verifyContents) {
            assertEquals(SAMPLE_SPEC_VALUE_TN_WIDTH, getAndVerifyText(p));
        }

        assertToken(JsonToken.END_OBJECT, p.nextToken()); // 'thumbnail' object
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // 'IDs'
        assertToken(JsonToken.START_ARRAY, p.nextToken()); // 'ids' array
        verifyIntToken(p.nextToken(), requireNumbers); // ids[0]
        if (verifyContents) {
            verifyIntValue(p, SAMPLE_SPEC_VALUE_TN_ID1);
        }
        verifyIntToken(p.nextToken(), requireNumbers); // ids[1]
        if (verifyContents) {
            verifyIntValue(p, SAMPLE_SPEC_VALUE_TN_ID2);
        }
        verifyIntToken(p.nextToken(), requireNumbers); // ids[2]
        if (verifyContents) {
            verifyIntValue(p, SAMPLE_SPEC_VALUE_TN_ID3);
        }
        verifyIntToken(p.nextToken(), requireNumbers); // ids[3]
        if (verifyContents) {
            verifyIntValue(p, SAMPLE_SPEC_VALUE_TN_ID4);
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken()); // 'ids' array

        assertToken(JsonToken.END_OBJECT, p.nextToken()); // 'image' object

        assertToken(JsonToken.END_OBJECT, p.nextToken()); // main object
    }

    private void verifyIntToken(JsonToken t, boolean requireNumbers)
    {
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return;
        }
        if (requireNumbers) { // to get error
            assertToken(JsonToken.VALUE_NUMBER_INT, t);
        }
        // if not number, must be String
        if (t != JsonToken.VALUE_STRING) {
            fail("Expected INT or STRING value, got "+t);
        }
    }
    
    protected void verifyFieldName(JsonParser p, String expName)
        throws IOException
    {
        assertEquals(expName, p.getText());
        assertEquals(expName, p.currentName());
    }

    protected void verifyIntValue(JsonParser p, long expValue)
        throws IOException
    {
        // First, via textual
        assertEquals(String.valueOf(expValue), p.getText());
    }

}
