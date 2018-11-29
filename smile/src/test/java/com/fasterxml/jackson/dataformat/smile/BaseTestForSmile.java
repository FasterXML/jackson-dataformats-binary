package com.fasterxml.jackson.dataformat.smile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.junit.Assert;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

public abstract class BaseTestForSmile
    extends junit.framework.TestCase
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

    /*
    /**********************************************************
    /* Factory methods
    /**********************************************************
     */

    protected SmileParser _smileParser(byte[] input) throws IOException {
        return _smileParser(input, false);
    }

    protected SmileParser _smileParser(InputStream in) throws IOException {
        return _smileParser(in, false);
    }
    
    protected SmileParser _smileParser(byte[] input, boolean requireHeader) throws IOException
    {
        SmileFactory f = smileFactory(requireHeader, false, false);
        return _smileParser(f, input);
    }

    protected SmileParser _smileParser(InputStream in, boolean requireHeader) throws IOException
    {
        SmileFactory f = smileFactory(requireHeader, false, false);
        return _smileParser(f, in);
    }
    
    protected SmileParser _smileParser(SmileFactory f, byte[] input) throws IOException {
        return f.createParser(input);
    }

    protected SmileParser _smileParser(SmileFactory f, InputStream in) throws IOException {
        return f.createParser(in);
    }
    
    protected ObjectMapper smileMapper() {
        return smileMapper(false);
    }
    
    protected ObjectMapper smileMapper(boolean requireHeader) {
        return smileMapper(requireHeader, requireHeader, false);
    }
    
    protected ObjectMapper smileMapper(boolean requireHeader,
            boolean writeHeader, boolean writeEndMarker)
    {
        return new ObjectMapper(smileFactory(requireHeader, writeHeader, writeEndMarker));
    }
    
    protected SmileFactory smileFactory(boolean requireHeader,
            boolean writeHeader, boolean writeEndMarker)
    {
        SmileFactory f = new SmileFactory();
        f.configure(SmileParser.Feature.REQUIRE_HEADER, requireHeader);
        f.configure(SmileGenerator.Feature.WRITE_HEADER, writeHeader);
        f.configure(SmileGenerator.Feature.WRITE_END_MARKER, writeEndMarker);
        return f;
    }

    protected byte[] _smileDoc(String json) throws IOException
    {
        return _smileDoc(json, true);
    }

    protected byte[] _smileDoc(String json, boolean writeHeader) throws IOException
    {
        return _smileDoc(new SmileFactory(), json, writeHeader);
    }

    protected byte[] _smileDoc(SmileFactory smileFactory, String json, boolean writeHeader) throws IOException
    {
        JsonFactory jf = new JsonFactory();
        JsonParser p = jf.createParser(json);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator g = smileGenerator(out, writeHeader);
    	
        while (p.nextToken() != null) {
        	g.copyCurrentEvent(p);
        }
        p.close();
        g.close();
        return out.toByteArray();
    }

    protected SmileGenerator smileGenerator(OutputStream result, boolean addHeader)
        throws IOException
    {
        return smileGenerator(new SmileFactory(), result, addHeader);
    }

    protected SmileGenerator smileGenerator(SmileFactory f,
            OutputStream result, boolean addHeader)
        throws IOException
    {
        f.configure(SmileGenerator.Feature.WRITE_HEADER, addHeader);
        return f.createGenerator(result, null);
    }

    /*
    /**********************************************************
    /* Additional assertion methods
    /**********************************************************
     */

    protected void assertToken(JsonToken expToken, JsonToken actToken)
    {
        if (actToken != expToken) {
            fail("Expected token "+expToken+", current token "+actToken);
        }
    }

    protected void assertToken(JsonToken expToken, JsonParser p)
    {
        assertToken(expToken, p.getCurrentToken());
    }

    protected void assertType(Object ob, Class<?> expType)
    {
        if (ob == null) {
            fail("Expected an object of type "+expType.getName()+", got null");
        }
        Class<?> cls = ob.getClass();
        if (!expType.isAssignableFrom(cls)) {
            fail("Expected type "+expType.getName()+", got "+cls.getName());
        }
    }

    protected void verifyException(Throwable e, String... matches)
    {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        for (String match : matches) {
            String lmatch = match.toLowerCase();
            if (lmsg.indexOf(lmatch) >= 0) {
                return;
            }
        }
        fail("Expected an exception with one of substrings ("+Arrays.asList(matches)+"): got one with message \""+msg+"\"");
    }
    
    protected void _verifyBytes(byte[] actBytes, byte... expBytes)
    {
        Assert.assertArrayEquals(expBytes, actBytes);
    }

    /**
     * Method that gets textual contents of the current token using
     * available methods, and ensures results are consistent, before
     * returning them
     */
    protected String getAndVerifyText(JsonParser p) throws IOException
    {
        // Ok, let's verify other accessors
        int actLen = p.getTextLength();
        char[] ch = p.getTextCharacters();
        String str2 = new String(ch, p.getTextOffset(), actLen);
        String str = p.getText();

        if (str.length() !=  actLen) {
            fail("Internal problem (p.token == "+p.getCurrentToken()+"): p.getText().length() ['"+str+"'] == "+str.length()+"; p.getTextLength() == "+actLen);
        }
        assertEquals("String access via getText(), getTextXxx() must be the same", str, str2);

        return str;
    }
    
    /*
    /**********************************************************
    /* Other helper methods
    /**********************************************************
     */

    public String quote(String str) {
        return '"'+str+'"';
    }

    protected static String aposToQuotes(String str) {
        return str.replace("'", "\"");
    }
}
