package com.fasterxml.jackson.dataformat.smile;

import java.io.*;
import java.util.Arrays;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;

import static org.junit.jupiter.api.Assertions.*;

public abstract class BaseTestForSmile
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

    protected SmileMapper smileMapper() {
        return smileMapper(false);
    }

    protected SmileMapper smileMapper(boolean requireHeader) {
        return smileMapper(requireHeader, requireHeader, false);
    }

    protected SmileMapper.Builder smileMapperBuilder(boolean requireHeader) {
        return SmileMapper.builder();
    }

    protected SmileMapper smileMapper(boolean requireHeader,
            boolean writeHeader, boolean writeEndMarker)
    {
        return new SmileMapper(smileFactory(requireHeader, writeHeader, writeEndMarker));
    }

    protected SmileFactory smileFactory(boolean requireHeader,
            boolean writeHeader, boolean writeEndMarker)
    {
        return smileFactoryBuilder(requireHeader, writeHeader, writeEndMarker).build();
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

    /**
     * Factory method for creating {@link IOContext}s for tests
     */
    public static IOContext testIOContext() {
        return new IOContext(StreamReadConstraints.defaults(),
                StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                new BufferRecycler(), ContentReference.unknown(), false);
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

    protected void assertNameToken(JsonToken actToken)
    {
        assertToken(JsonToken.FIELD_NAME, actToken);
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
        assertArrayEquals(expBytes, actBytes);
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

    protected static String aposToQuotes(String str) {
        return a2q(str);
    }

    protected static String a2q(String str) {
        return str.replace("'", "\"");
    }

    public String quote(String str) {
        return q(str);
    }

    public String q(String str) {
        return '"'+str+'"';
    }

    protected static byte[] concat(byte[] ... chunks)
    {
        int len = 0;
        for (byte[] chunk : chunks) {
            len += chunk.length;
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream(len);
        for (byte[] chunk : chunks) {
            try {
                bout.write(chunk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return bout.toByteArray();
    }

    protected byte[] readResource(String ref)
    {
       ByteArrayOutputStream bytes = new ByteArrayOutputStream();
       final byte[] buf = new byte[4000];

       try (InputStream in = getClass().getResourceAsStream(ref)) {
           if (in != null) {
               int len;
               while ((len = in.read(buf)) > 0) {
                   bytes.write(buf, 0, len);
               }
           }
       } catch (IOException e) {
           throw new RuntimeException("Failed to read resource '"+ref+"': "+e);
       }
       if (bytes.size() == 0) {
           throw new IllegalArgumentException("Failed to read resource '"+ref+"': empty resource?");
       }
       return bytes.toByteArray();
    }
}
