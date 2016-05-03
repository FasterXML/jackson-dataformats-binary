package com.fasterxml.jackson.dataformat.cbor;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Tests that use the json.org sample document.
 */
public class ParserWithJsonOrgSampleTest extends CBORTestBase
{
    // From JSON specification, sample doc...
    protected final static int SAMPLE_SPEC_VALUE_WIDTH = 800;
    protected final static int SAMPLE_SPEC_VALUE_HEIGHT = 600;
    protected final static String SAMPLE_SPEC_VALUE_TITLE = "View from 15th Floor";
    protected final static String SAMPLE_SPEC_VALUE_TN_URL = "http://www.example.com/image/481989943";
    protected final static int SAMPLE_SPEC_VALUE_TN_HEIGHT = 125;
    protected final static String SAMPLE_SPEC_VALUE_TN_WIDTH = "100";
    protected final static int SAMPLE_SPEC_VALUE_TN_ID1 = 116;
    protected final static int SAMPLE_SPEC_VALUE_TN_ID2 = 943;
    protected final static int SAMPLE_SPEC_VALUE_TN_ID3 = 234;
    protected final static int SAMPLE_SPEC_VALUE_TN_ID4 = 38793;    

    protected final static String SAMPLE_DOC_JSON_SPEC = 
            "{\n"
            +"  \"Image\" : {\n"
            +"    \"Width\" : "+SAMPLE_SPEC_VALUE_WIDTH+",\n"
            +"    \"Height\" : "+SAMPLE_SPEC_VALUE_HEIGHT+","
            +"\"Title\" : \""+SAMPLE_SPEC_VALUE_TITLE+"\",\n"
            +"    \"Thumbnail\" : {\n"
            +"      \"Url\" : \""+SAMPLE_SPEC_VALUE_TN_URL+"\",\n"
            +"\"Height\" : "+SAMPLE_SPEC_VALUE_TN_HEIGHT+",\n"
            +"      \"Width\" : \""+SAMPLE_SPEC_VALUE_TN_WIDTH+"\"\n"
            +"    },\n"
            +"    \"IDs\" : ["+SAMPLE_SPEC_VALUE_TN_ID1+","+SAMPLE_SPEC_VALUE_TN_ID2+","+SAMPLE_SPEC_VALUE_TN_ID3+","+SAMPLE_SPEC_VALUE_TN_ID4+"]\n"
            +"  }"
            +"}"
            ;
    
    public void testJsonSampleDoc() throws IOException
    {
        byte[] data = cborDoc(SAMPLE_DOC_JSON_SPEC);
        verifyJsonSpecSampleDoc(cborParser(data), true, true);
        verifyJsonSpecSampleDoc(cborParser(data), true, false);
        verifyJsonSpecSampleDoc(cborParser(data), false, false);
        verifyJsonSpecSampleDoc(cborParser(data), false, true);
    }

    protected void verifyJsonSpecSampleDoc(JsonParser jp, boolean verifyContents,
            boolean requireNumbers) throws IOException
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
