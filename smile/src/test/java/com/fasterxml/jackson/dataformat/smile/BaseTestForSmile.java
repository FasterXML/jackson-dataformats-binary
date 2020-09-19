package com.fasterxml.jackson.dataformat.smile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.junit.Assert;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;

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

    private final static ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final static ObjectMapper SMILE_MAPPER = new ObjectMapper(new SmileFactory());

    /*
    /**********************************************************
    /* Factory methods
    /**********************************************************
     */

    protected JsonParser _smileParser(byte[] input) throws IOException {
        return _smileParser(input, false);
    }

    protected JsonParser _smileParser(InputStream in) throws IOException {
        return _smileParser(in, false);
    }
    
    protected JsonParser _smileParser(byte[] input, boolean requireHeader) throws IOException
    {
        return _smileReader(requireHeader).createParser(input);
    }

    protected JsonParser _smileParser(InputStream in, boolean requireHeader) throws IOException
    {
        return _smileReader(requireHeader).createParser(in);
    }

    protected ObjectReader _smileReader() {
        return _smileReader(false);
    }

    protected ObjectReader _smileReader(boolean requireHeader) {
        ObjectReader r = SMILE_MAPPER.reader();
        if (requireHeader) {
            r = r.with(SmileParser.Feature.REQUIRE_HEADER);
        } else {
            r = r.without(SmileParser.Feature.REQUIRE_HEADER);
        }
        return r;
    }

    protected SmileMapper newSmileMapper() {
        return new SmileMapper(new SmileFactory());
    }

    protected SmileMapper smileMapper() {
        return smileMapper(false);
    }
    
    protected SmileMapper.Builder smileMapperBuilder() {
        return SmileMapper.builder(smileFactory(false, false, false));
    }

    protected SmileMapper smileMapper(boolean requireHeader) {
        return smileMapper(requireHeader, requireHeader, false);
    }

    protected SmileMapper smileMapper(boolean requireHeader,
            boolean writeHeader, boolean writeEndMarker)
    {
        return new SmileMapper(smileFactory(requireHeader, writeHeader, writeEndMarker));
    }

    protected SmileFactory smileFactory(boolean requireHeader,
            boolean writeHeader, boolean writeEndMarker)
    {
        return smileFactoryBuilder(requireHeader, writeHeader, writeEndMarker)
                .build();
    }

    protected SmileFactoryBuilder smileFactoryBuilder(boolean requireHeader,
            boolean writeHeader, boolean writeEndMarker)
    {
        return SmileFactory.builder()
                .configure(SmileParser.Feature.REQUIRE_HEADER, requireHeader)
                .configure(SmileGenerator.Feature.WRITE_HEADER, writeHeader)
                .configure(SmileGenerator.Feature.WRITE_END_MARKER, writeEndMarker);
    }
    
    protected byte[] _smileDoc(String json) throws IOException
    {
        return _smileDoc(json, true);
    }

    protected byte[] _smileDoc(ObjectMapper mapper, String json, boolean writeHeader) throws IOException
    {
        ObjectWriter w = mapper.writer();
        if (writeHeader) {
            w = w.with(SmileGenerator.Feature.WRITE_HEADER);
        } else {
            w = w.without(SmileGenerator.Feature.WRITE_HEADER);
        }
        return _smileDoc(w, json);
    }

    protected byte[] _smileDoc(ObjectWriter w, String json)
        throws IOException
    {
        try (JsonParser p = JSON_MAPPER.createParser(json)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (JsonGenerator g = w.createGenerator(out)) {
                _copy(p, g);
            }
            return out.toByteArray();
        }
    }
    
    protected byte[] _smileDoc(String json, boolean writeHeader) throws IOException
    {
        try (JsonParser p = JSON_MAPPER.createParser(json)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (JsonGenerator g = _smileGenerator(out, writeHeader)) {
                _copy(p, g);
            }
            return out.toByteArray();
        }
    }

    private void _copy(JsonParser p, JsonGenerator g) throws IOException
    {
        while (p.nextToken() != null) {
        	g.copyCurrentEvent(p);
        }
    }

    protected SmileGenerator _smileGenerator(OutputStream result, boolean addHeader)
        throws IOException
    {
        return  (SmileGenerator) _smileWriter(addHeader).createGenerator(result);
    }

    protected ObjectWriter _smileWriter() {
        return _smileWriter(true);
    }
    
    protected ObjectWriter _smileWriter(boolean addHeader) {
        ObjectWriter w = SMILE_MAPPER.writer();
        if (addHeader) {
            w = w.with(SmileGenerator.Feature.WRITE_HEADER);
        } else {
            w = w.without(SmileGenerator.Feature.WRITE_HEADER);
        }
        return w;
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
        assertToken(expToken, p.currentToken());
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
            fail("Internal problem (p.token == "+p.currentToken()+"): p.getText().length() ['"+str+"'] == "+str.length()+"; p.getTextLength() == "+actLen);
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
