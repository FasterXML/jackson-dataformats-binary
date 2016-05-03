package com.fasterxml.jackson.dataformat.smile;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class TestParser extends SmileTestBase
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
        SmileParser p = _smileParser(data, false);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertNull(p.getCurrentName());
        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertNull(p.getCurrentName());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.getCurrentName());
        assertNull(p.nextToken());
        p.close();
    }

    public void testSimple() throws IOException
    {
        byte[] data = _smileDoc("[ true, null, false ]");
        SmileParser p = _smileParser(data);
        assertNull(p.getCurrentToken());
        assertNull(p.getCurrentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertNull(p.getCurrentName());
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertNull(p.getCurrentName());
        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertNull(p.getCurrentName());
        assertToken(JsonToken.VALUE_FALSE, p.nextToken());
        assertNull(p.getCurrentName());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.getCurrentName());
        assertNull(p.nextToken());
        p.close();
    }

    public void testIntInArray() throws IOException
    {
        byte[] data = _smileDoc("[ 25.0 ]");
        SmileParser p = _smileParser(data);
        assertNull(p.getCurrentToken());
        assertNull(p.getCurrentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(25, p.getIntValue());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.getCurrentName());
        p.close();
    }
    
    public void testArrayWithString() throws IOException
    {
        byte[] data = _smileDoc("[ \"abc\" ]");
        SmileParser p = _smileParser(data);
        assertNull(p.getCurrentToken());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("abc", p.getText());
        assertEquals(0, p.getTextOffset());
        assertEquals(3, p.getTextLength());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    public void testEmptyStrings() throws IOException
    {
    	// first, empty key
    	byte[] data = _smileDoc("{ \"\":true }");
    	SmileParser p = _smileParser(data);
    	assertNull(p.getCurrentToken());
    	assertToken(JsonToken.START_OBJECT, p.nextToken());
    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("", p.getCurrentName());
    	assertToken(JsonToken.VALUE_TRUE, p.nextToken());
    	assertToken(JsonToken.END_OBJECT, p.nextToken());
    	assertNull(p.nextToken());
    	p.close();

    	// then empty value
    	data = _smileDoc("{ \"abc\":\"\" }");
    	p = _smileParser(data);
    	assertNull(p.getCurrentToken());
    	assertToken(JsonToken.START_OBJECT, p.nextToken());
    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("abc", p.getCurrentName());
    	assertToken(JsonToken.VALUE_STRING, p.nextToken());
    	assertEquals("", p.getText());
    	assertToken(JsonToken.END_OBJECT, p.nextToken());
    	assertNull(p.nextToken());
    	p.close();
    	
    	// and combinations
    	data = _smileDoc("{ \"\":\"\", \"\":\"\" }");
    	p = _smileParser(data);
    	assertNull(p.getCurrentToken());
    	assertToken(JsonToken.START_OBJECT, p.nextToken());
    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("", p.getCurrentName());
    	assertToken(JsonToken.VALUE_STRING, p.nextToken());
    	assertEquals("", p.getText());
    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("", p.getCurrentName());
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

    	SmileParser p = _smileParser(data);
    	assertNull(p.getCurrentToken());
    	assertToken(JsonToken.VALUE_STRING, p.nextToken());
    	assertEquals(LONG, p.getText());
    	assertNull(p.nextToken());
     p.close();
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

    	SmileParser p = _smileParser(data);
    	assertNull(p.getCurrentToken());
    	assertToken(JsonToken.VALUE_STRING, p.nextToken());
    	assertEquals(LONG, p.getText());
    	assertNull(p.nextToken());
     p.close();
    }
    
    public void testTrivialObject() throws IOException
    {
    	byte[] data = _smileDoc("{\"abc\":13}");
    	SmileParser p = _smileParser(data);
    	assertNull(p.getCurrentToken());

    	assertToken(JsonToken.START_OBJECT, p.nextToken());
    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("abc", p.getCurrentName());
    	assertEquals("abc", p.getText());
    	assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
    	assertEquals(13, p.getIntValue());    	
    	assertToken(JsonToken.END_OBJECT, p.nextToken());
     p.close();
    }
    
    public void testSimpleObject() throws IOException
    {
    	byte[] data = _smileDoc("{\"a\":8, \"b\" : [ true ], \"c\" : { }, \"d\":{\"e\":null}}");
    	SmileParser p = _smileParser(data);
    	assertNull(p.getCurrentToken());
    	assertToken(JsonToken.START_OBJECT, p.nextToken());

    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("a", p.getCurrentName());
    	assertEquals("a", p.getText());
    	assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
    	assertEquals(8, p.getIntValue());

    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("b", p.getCurrentName());
    	assertToken(JsonToken.START_ARRAY, p.nextToken());
    	assertToken(JsonToken.VALUE_TRUE, p.nextToken());
    	assertToken(JsonToken.END_ARRAY, p.nextToken());

    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("c", p.getCurrentName());
    	assertToken(JsonToken.START_OBJECT, p.nextToken());
    	assertToken(JsonToken.END_OBJECT, p.nextToken());

    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("d", p.getCurrentName());

    	assertToken(JsonToken.START_OBJECT, p.nextToken());
    	assertToken(JsonToken.FIELD_NAME, p.nextToken());
    	assertEquals("e", p.getCurrentName());
    	assertToken(JsonToken.VALUE_NULL, p.nextToken());
    	assertToken(JsonToken.END_OBJECT, p.nextToken());

    	assertToken(JsonToken.END_OBJECT, p.nextToken());
    	p.close();
    }

    public void testNestedObject() throws IOException
    {
        byte[] data = _smileDoc("[{\"a\":{\"b\":[1]}}]");
        SmileParser p = _smileParser(data);
        assertNull(p.getCurrentToken());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // a
        assertEquals("a", p.getCurrentName());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken()); // b
        assertEquals("b", p.getCurrentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertEquals("b", p.getCurrentName());
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
        SmileParser p = _smileParser(data);
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
        SmileParser p = _smileParser(data);
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
        assertEquals(uc, p.getCurrentName());
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
        SmileParser p = _smileParser(data);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        // And now should get an error
        try {
            JsonToken t = p.nextToken();
            fail("Expected parse error, got: "+t);
        } catch (IOException e) {
            verifyException(e, "Invalid type marker byte 0x0");
        }
        p.close();
    }

    // [JACKSON-629]
    public void testNameBoundary() throws IOException
    {
        SmileFactory f = smileFactory(true, true, false);
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
            f.configure(SmileGenerator.Feature.CHECK_SHARED_NAMES, false);
            SmileGenerator gen = f.createGenerator(bytes);
            
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
            SmileParser jp = f.createParser(new ByteArrayInputStream(json, offset, json.length-offset));
            int i = 0;

            while (i < count) {
                assertToken(JsonToken.START_OBJECT, jp.nextToken());
                assertToken(JsonToken.FIELD_NAME, jp.nextToken());
                assertEquals(FIELD, jp.getCurrentName());
                assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
                assertEquals((i % 17), jp.getIntValue());
                assertToken(JsonToken.END_OBJECT, jp.nextToken());
                ++i;
            }
            // and should be done now
            assertNull(jp.nextToken());
            jp.close();
        }
    }

    // [JACKSON-640]: Problem with getTextCharacters/Offset/Length
    public void testCharacters() throws IOException
    {
        // ensure we are using both back-ref types
        SmileFactory sf = new SmileFactory();
        sf.configure(SmileGenerator.Feature.CHECK_SHARED_NAMES, true);
        sf.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        
        JsonGenerator jgen = sf.createGenerator(bytes);
        jgen.writeStartArray();
        jgen.writeStartObject();
        jgen.writeStringField("key", "value");
        jgen.writeEndObject();
        jgen.writeStartObject();
        jgen.writeStringField("key", "value");
        jgen.writeEndObject();
        jgen.writeEndArray();
        jgen.close();

        SmileParser p = _smileParser(bytes.toByteArray());

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

    // for [dataformat-smile#26]
    public void testIssue26ArrayOutOfBounds() throws Exception
    {
        SmileFactory f = new SmileFactory();
        ObjectMapper mapper = new ObjectMapper(new SmileFactory());
        byte[] buffer = _generateHugeDoc(f);

        // split the buffer in two smaller buffers
        int len = 160;
        byte[] buf1 = new byte[len];
        byte[] buf2 = new byte[buffer.length - len];
        System.arraycopy(buffer, 0, buf1, 0, len);
        System.arraycopy(buffer, len, buf2, 0, buffer.length - len);

        // aggregate the two buffers via a SequenceInputStream
        ByteArrayInputStream in1 = new ByteArrayInputStream(buf1);
        ByteArrayInputStream in2 = new ByteArrayInputStream(buf2);
        SequenceInputStream inputStream = new SequenceInputStream(in1, in2);

        JsonNode jsonNode = mapper.readTree(inputStream);
        assertNotNull(jsonNode);

        // let's actually verify
        ArrayNode arr = (ArrayNode) jsonNode;
        assertEquals(26, arr.size());
    }

    private byte[] _generateHugeDoc(SmileFactory f) throws IOException
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        JsonGenerator g = f.createGenerator(b);
        g.writeStartArray();

        for (int c = 'a'; c <= 'z'; ++c) {
            g.writeStartObject();
            for (int ix = 0; ix < 1000; ++ix) {
                String name = "" + ((char) c) + ix;
                g.writeNumberField(name, ix);
            }
            g.writeEndObject();
        }
        g.writeEndArray();
        g.close();
        return b.toByteArray();
    }

    /*
    /**********************************************************
    /* Helper methods for use with json spec sample doc
    /**********************************************************
     */

    protected void verifyJsonSpecSampleDoc(JsonParser jp, boolean verifyContents)
        throws IOException
    {
        verifyJsonSpecSampleDoc(jp, verifyContents, true);
    }

    protected void verifyJsonSpecSampleDoc(JsonParser jp, boolean verifyContents,
            boolean requireNumbers)
        throws IOException
    {
        if (!jp.hasCurrentToken()) {
            jp.nextToken();
        }
        assertToken(JsonToken.START_OBJECT, jp.getCurrentToken()); // main object

        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Image'
        if (verifyContents) {
            verifyFieldName(jp, "Image");
        }

        assertToken(JsonToken.START_OBJECT, jp.nextToken()); // 'image' object

        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Width'
        if (verifyContents) {
            verifyFieldName(jp, "Width");
        }

        verifyIntToken(jp.nextToken(), requireNumbers);
        if (verifyContents) {
            verifyIntValue(jp, SAMPLE_SPEC_VALUE_WIDTH);
        }

        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Height'
        if (verifyContents) {
            verifyFieldName(jp, "Height");
        }

        verifyIntToken(jp.nextToken(), requireNumbers);
        if (verifyContents) {
            verifyIntValue(jp, SAMPLE_SPEC_VALUE_HEIGHT);
        }
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Title'
        if (verifyContents) {
            verifyFieldName(jp, "Title");
        }
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(SAMPLE_SPEC_VALUE_TITLE, getAndVerifyText(jp));
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Thumbnail'
        if (verifyContents) {
            verifyFieldName(jp, "Thumbnail");
        }

        assertToken(JsonToken.START_OBJECT, jp.nextToken()); // 'thumbnail' object
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Url'
        if (verifyContents) {
            verifyFieldName(jp, "Url");
        }
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        if (verifyContents) {
            assertEquals(SAMPLE_SPEC_VALUE_TN_URL, getAndVerifyText(jp));
        }
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Height'
        if (verifyContents) {
            verifyFieldName(jp, "Height");
        }
        verifyIntToken(jp.nextToken(), requireNumbers);
        if (verifyContents) {
            verifyIntValue(jp, SAMPLE_SPEC_VALUE_TN_HEIGHT);
        }
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Width'
        if (verifyContents) {
            verifyFieldName(jp, "Width");
        }
        // Width value is actually a String in the example
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        if (verifyContents) {
            assertEquals(SAMPLE_SPEC_VALUE_TN_WIDTH, getAndVerifyText(jp));
        }

        assertToken(JsonToken.END_OBJECT, jp.nextToken()); // 'thumbnail' object
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'IDs'
        assertToken(JsonToken.START_ARRAY, jp.nextToken()); // 'ids' array
        verifyIntToken(jp.nextToken(), requireNumbers); // ids[0]
        if (verifyContents) {
            verifyIntValue(jp, SAMPLE_SPEC_VALUE_TN_ID1);
        }
        verifyIntToken(jp.nextToken(), requireNumbers); // ids[1]
        if (verifyContents) {
            verifyIntValue(jp, SAMPLE_SPEC_VALUE_TN_ID2);
        }
        verifyIntToken(jp.nextToken(), requireNumbers); // ids[2]
        if (verifyContents) {
            verifyIntValue(jp, SAMPLE_SPEC_VALUE_TN_ID3);
        }
        verifyIntToken(jp.nextToken(), requireNumbers); // ids[3]
        if (verifyContents) {
            verifyIntValue(jp, SAMPLE_SPEC_VALUE_TN_ID4);
        }
        assertToken(JsonToken.END_ARRAY, jp.nextToken()); // 'ids' array

        assertToken(JsonToken.END_OBJECT, jp.nextToken()); // 'image' object

        assertToken(JsonToken.END_OBJECT, jp.nextToken()); // main object
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
    
    protected void verifyFieldName(JsonParser jp, String expName)
        throws IOException
    {
        assertEquals(expName, jp.getText());
        assertEquals(expName, jp.getCurrentName());
    }

    protected void verifyIntValue(JsonParser jp, long expValue)
        throws IOException
    {
        // First, via textual
        assertEquals(String.valueOf(expValue), jp.getText());
    }

}
