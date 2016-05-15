package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class TestGeneratorSymbols extends BaseTestForSmile
{
    /**
     * Simple test to verify that second reference will not output new String, but
     * rather references one output earlier.
     */
    public void testSharedNameSimple() throws Exception
    {
        // false, no header (or frame marker)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = smileGenerator(out, false);
        gen.writeStartArray();
        gen.writeStartObject();
        gen.writeNumberField("abc", 1);
        gen.writeEndObject();
        gen.writeStartObject();
        gen.writeNumberField("abc", 2);
        gen.writeEndObject();
        gen.writeEndArray();
        gen.close();
        byte[] result = out.toByteArray();
        assertEquals(13, result.length);
    }

    // same as above, but with name >= 64 characters
    public void testSharedNameSimpleLong() throws Exception
    {
        String digits = "01234567899";

        // Base is 76 chars; loop over couple of shorter ones too
        
        final String LONG_NAME = "a"+digits+"b"+digits+"c"+digits+"d"+digits+"e"+digits+"f"+digits+"ABCD";
        
        for (int i = 0; i < 4; ++i) {
            int strLen = LONG_NAME.length() - i;
            String field = LONG_NAME.substring(0, strLen);
            // false, no header (or frame marker)
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            SmileGenerator gen = smileGenerator(out, false);
            gen.writeStartArray();
            gen.writeStartObject();
            gen.writeNumberField(field, 1);
            gen.writeEndObject();
            gen.writeStartObject();
            gen.writeNumberField(field, 2);
            gen.writeEndObject();
            gen.writeEndArray();
            gen.close();
            byte[] result = out.toByteArray();
            assertEquals(11 + field.length(), result.length);
    
            // better also parse it back...
            JsonParser parser = _smileParser(result);
            assertToken(JsonToken.START_ARRAY, parser.nextToken());
    
            assertToken(JsonToken.START_OBJECT, parser.nextToken());
            assertToken(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals(field, parser.getCurrentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(1, parser.getIntValue());
            assertToken(JsonToken.END_OBJECT, parser.nextToken());
    
            assertToken(JsonToken.START_OBJECT, parser.nextToken());
            assertToken(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals(field, parser.getCurrentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(2, parser.getIntValue());
            assertToken(JsonToken.END_OBJECT, parser.nextToken());
    
            assertToken(JsonToken.END_ARRAY, parser.nextToken());
            parser.close();
        }
    }

    public void testLongNamesNonShared() throws Exception
    {
        _testLongNames(false);
    }
    
    public void testLongNamesShared() throws Exception
    {
        _testLongNames(true);
    }
    
    // [Issue#8] Test by: M. Tarik Yurt  / mtyurt@gmail.com
    public void testExpandSeenNames() throws Exception
    {
        byte[] data = _smileDoc("{\"a1\":null,\"a2\":null,\"a3\":null,\"a4\":null,\"a5\":null,\"a6\":null,\"a7\":null,\"a8\":null," +
                "\"a9\":null,\"a10\":null,\"a11\":null,\"a12\":null,\"a13\":null,\"a14\":null,\"a15\":null,\"a16\":null,\"a17\":null,\"a18\":null," +
                "\"a19\":null,\"a20\":null,\"a21\":null,\"a22\":null,\"a23\":null,\"a24\":null,\"a25\":null,\"a26\":null,\"a27\":null,\"a28\":null,\"a29\":null," +
                "\"a30\":null,\"a31\":null,\"a32\":null,\"a33\":null,\"a34\":null,\"a35\":null,\"a36\":null,\"a37\":null,\"a38\":null,\"a39\":null,\"a40\":null," +
                "\"a41\":null,\"a42\":null,\"a43\":null,\"a44\":null,\"a45\":null,\"a46\":null,\"a47\":null,\"a48\":null,\"a49\":null,\"a50\":null,\"a51\":null," +
                "\"a52\":null,\"a53\":null,\"a54\":null,\"a55\":null,\"a56\":null,\"a57\":null,\"a58\":null,\"a59\":null,\"a60\":null,\"a61\":null,\"a62\":null," +
                "\"a63\":null,\"a64\":null,"+
                "\"a65\":{\"a32\":null}}", false);
        /*
         * {@code "a54".hashCode() & 63} has same value as {@code "a32".hashCode() & 63}
         * "a32" is the next node of "a54" before expanding.
         * 33: Null token
         * -6: Start object token
         * -5: End object token
         */
        String expectedResult = "-6,-127,97,49,33,-127,97,50,33,-127,97,51,33,-127,97,52,33,-127,97,53,33,-127,97,54,33,-127,97,55,33,-127,97,56,33,-127,97,57,33," +
                "-126,97,49,48,33,-126,97,49,49,33,-126,97,49,50,33,-126,97,49,51,33,-126,97,49,52,33,-126,97,49,53,33,-126,97,49,54,33,-126,97,49,55,33,-126,97,49,56,33," +
                "-126,97,49,57,33,-126,97,50,48,33,-126,97,50,49,33,-126,97,50,50,33,-126,97,50,51,33,-126,97,50,52,33,-126,97,50,53,33,-126,97,50,54,33,-126,97,50,55,33," +
                "-126,97,50,56,33,-126,97,50,57,33,-126,97,51,48,33,-126,97,51,49,33,-126,97,51,50,33,-126,97,51,51,33,-126,97,51,52,33,-126,97,51,53,33,-126,97,51,54,33," +
                "-126,97,51,55,33,-126,97,51,56,33,-126,97,51,57,33,-126,97,52,48,33,-126,97,52,49,33,-126,97,52,50,33,-126,97,52,51,33,-126,97,52,52,33,-126,97,52,53,33," +
                "-126,97,52,54,33,-126,97,52,55,33,-126,97,52,56,33,-126,97,52,57,33,-126,97,53,48,33,-126,97,53,49,33,-126,97,53,50,33,-126,97,53,51,33,-126,97,53,52,33," +
                "-126,97,53,53,33,-126,97,53,54,33,-126,97,53,55,33,-126,97,53,56,33,-126,97,53,57,33,-126,97,54,48,33,-126,97,54,49,33,-126,97,54,50,33,-126,97,54,51,33," +
                "-126,97,54,52,33,"+
                // "a65":{"a32":null}} :
                "-126,97,54,53,-6,95,33,-5,-5";
                /*
                 * First "a32" is encoded as follows: -126,97,51,50
                 * Second one should be referenced: 95
                 */
        assertEquals(expectedResult,_dataToString(data));
    }

    // [Issue#8] Test by: M. Tarik Yurt  / mtyurt@gmail.com
    public void testExpandSeenStringValues() throws Exception
    {
        String json = "{\"a1\":\"v1\",\"a2\":\"v2\",\"a3\":\"v3\",\"a4\":\"v4\",\"a5\":\"v5\",\"a6\":\"v6\",\"a7\":\"v7\",\"a8\":\"v8\"," +
                "\"a9\":\"v9\",\"a10\":\"v10\",\"a11\":\"v11\",\"a12\":\"v12\",\"a13\":\"v13\",\"a14\":\"v14\",\"a15\":\"v15\",\"a16\":\"v16\",\"a17\":\"v17\",\"a18\":\"v18\"," +
                "\"a19\":\"v19\",\"a20\":\"v20\",\"a21\":\"v21\",\"a22\":\"v22\",\"a23\":\"v23\",\"a24\":\"v24\",\"a25\":\"v25\",\"a26\":\"v26\",\"a27\":\"v27\",\"a28\":\"v28\",\"a29\":\"v29\"," +
                "\"a30\":\"v30\",\"a31\":\"v31\",\"a32\":\"v32\",\"a33\":\"v33\",\"a34\":\"v34\",\"a35\":\"v35\",\"a36\":\"v36\",\"a37\":\"v37\",\"a38\":\"v38\",\"a39\":\"v39\",\"a40\":\"v40\"," +
                "\"a41\":\"v41\",\"a42\":\"v42\",\"a43\":\"v43\",\"a44\":\"v44\",\"a45\":\"v45\",\"a46\":\"v46\",\"a47\":\"v47\",\"a48\":\"v48\",\"a49\":\"v49\",\"a50\":\"v50\",\"a51\":\"v51\"," +
                "\"a52\":\"v52\",\"a53\":\"v53\",\"a54\":\"v54\",\"a55\":\"v55\",\"a56\":\"v56\",\"a57\":\"v57\",\"a58\":\"v58\",\"a59\":\"v59\",\"a60\":\"v60\",\"a61\":\"v61\",\"a62\":\"v62\"," +
                "\"a63\":\"v63\",\"a64\":\"v64\",\"a65\":\"v65\",\"a66\":\"v30\"}";
        /*
         * {@code "v52".hashCode() & 63} has same value as {@code "v30".hashCode() & 63}
         * "v30" is next node of "v52" before expanding.
         */
        //Enable string value sharing
        JsonFactory jf = new JsonFactory();
        JsonParser jp = jf.createParser(json);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileFactory sf = new SmileFactory();

        sf.configure(SmileGenerator.Feature.WRITE_HEADER, true);
        sf.configure(SmileGenerator.Feature.CHECK_SHARED_NAMES,true);
        sf.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES,true);
        JsonGenerator jg = sf.createGenerator(out, null);

        while (jp.nextToken() != null) {
            jg.copyCurrentEvent(jp);
        }
        jp.close();
        jg.close();
        /*
         * -126,-127: Tiny key string token with length
         * 65,66: Tiny value string token with length
         * 97: 'a'
         * -6: Start object token
         * -5: End object token
         */
        String expectedResult = "58,41,10,3,-6,-127,97,49,65,118,49,-127,97,50,65,118,50,-127,97,51,65,118,51,-127,97,52,65,118,52,-127,97,53,65,118,53,-127,97,54,65,118,54," +
                "-127,97,55,65,118,55,-127,97,56,65,118,56,-127,97,57,65,118,57,-126,97,49,48,66,118,49,48,-126,97,49,49,66,118,49,49,-126,97,49,50,66,118,49,50," +
                "-126,97,49,51,66,118,49,51,-126,97,49,52,66,118,49,52,-126,97,49,53,66,118,49,53,-126,97,49,54,66,118,49,54,-126,97,49,55,66,118,49,55," +
                "-126,97,49,56,66,118,49,56,-126,97,49,57,66,118,49,57,-126,97,50,48,66,118,50,48,-126,97,50,49,66,118,50,49,-126,97,50,50,66,118,50,50," +
                "-126,97,50,51,66,118,50,51,-126,97,50,52,66,118,50,52,-126,97,50,53,66,118,50,53,-126,97,50,54,66,118,50,54,-126,97,50,55,66,118,50,55," +
                "-126,97,50,56,66,118,50,56,-126,97,50,57,66,118,50,57,-126,97,51,48," +
                "66,118,51,48," +       //Here is first "v30"
                "-126,97,51,49,66,118,51,49,-126,97,51,50,66,118,51,50," +
                "-126,97,51,51,66,118,51,51,-126,97,51,52,66,118,51,52,-126,97,51,53,66,118,51,53,-126,97,51,54,66,118,51,54,-126,97,51,55,66,118,51,55," +
                "-126,97,51,56,66,118,51,56,-126,97,51,57,66,118,51,57,-126,97,52,48,66,118,52,48,-126,97,52,49,66,118,52,49,-126,97,52,50,66,118,52,50," +
                "-126,97,52,51,66,118,52,51,-126,97,52,52,66,118,52,52,-126,97,52,53,66,118,52,53,-126,97,52,54,66,118,52,54,-126,97,52,55,66,118,52,55," +
                "-126,97,52,56,66,118,52,56,-126,97,52,57,66,118,52,57,-126,97,53,48,66,118,53,48,-126,97,53,49,66,118,53,49,-126,97,53,50,66,118,53,50," +
                "-126,97,53,51,66,118,53,51,-126,97,53,52,66,118,53,52,-126,97,53,53,66,118,53,53,-126,97,53,54,66,118,53,54,-126,97,53,55,66,118,53,55," +
                "-126,97,53,56,66,118,53,56,-126,97,53,57,66,118,53,57,-126,97,54,48,66,118,54,48,-126,97,54,49,66,118,54,49,-126,97,54,50,66,118,54,50," +
                "-126,97,54,51,66,118,54,51,-126,97,54,52,66,118,54,52,-126,97,54,53,66,118,54,53,-126,97,54,54," +

                //The second "v30"
                // broken version would be:
                //"66,118,51,48," +       
                // and correct one:
                "30,"+
                "-5";
        /* First "v30" is encoded as follows: 66,118,51,48
         * Second one should be referenced: 30
         * But in this example, because this part is not fixed, it's encoded again: 66,118,51,48
         */
        assertEquals(expectedResult,_dataToString(out.toByteArray()));
    }

    /*
    /**********************************************************
    /* Secondary methods
    /**********************************************************
     */
    
    public void _testLongNames(boolean shareNames) throws Exception
    {
        // 68 bytes long (on boundary)
        final String FIELD_NAME = "dossier.domaine.supportsDeclaratifsForES.SupportDeclaratif.reference";
        final String VALUE = "11111";
        
        SmileFactory factory = new SmileFactory();
        factory.configure(SmileGenerator.Feature.CHECK_SHARED_NAMES, shareNames);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator gen = factory.createGenerator(os);
        gen.writeStartObject();
        gen.writeObjectFieldStart("query");
        gen.writeStringField(FIELD_NAME, VALUE);
        gen.writeEndObject();
        gen.writeEndObject();
        gen.close();
        
        JsonParser parser = factory.createParser(os.toByteArray());
        assertNull(parser.getCurrentToken());
        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("query", parser.getCurrentName());
        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(FIELD_NAME, parser.getCurrentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals(VALUE, parser.getText());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    private String _dataToString(byte[] data){
        StringBuilder sb = new StringBuilder();
        for(byte b:data){
            sb.append(b).append(",");
        }
        sb.deleteCharAt(sb.length()-1);
        return sb.toString();
    }    
}
