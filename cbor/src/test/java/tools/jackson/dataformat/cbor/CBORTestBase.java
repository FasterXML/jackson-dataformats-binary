package tools.jackson.dataformat.cbor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import tools.jackson.core.*;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

public abstract class CBORTestBase
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

    protected final static JsonMapper JSON_MAPPER = JsonMapper.shared();

    /*
    /**********************************************************
    /* Factory methods
    /**********************************************************
     */

    protected CBORParser cborParser(ByteArrayOutputStream bytes) {
        return cborParser(bytes.toByteArray());
    }
    protected CBORParser cborParser(CBORFactory cborFactory, ByteArrayOutputStream bytes) throws IOException {
        return cborParser(cborFactory, bytes.toByteArray());
    }

    protected CBORParser cborParser(byte[] input) {
        return (CBORParser) sharedMapper().createParser(input);
    }

    protected CBORParser cborParser(CBORFactory f, byte[] input) {
        return (CBORParser) f.createParser(ObjectReadContext.empty(), input);
    }

    protected CBORParser cborParser(InputStream in) {
        return (CBORParser) sharedMapper().createParser(in);
    }

    protected CBORMapper cborMapper() {
        return cborMapper(cborFactory());
    }

    protected CBORMapper cborMapper(CBORFactory f) {
        return new CBORMapper(f);
    }

    protected CBORMapper.Builder cborMapperBuilder() {
        return CBORMapper.builder(cborFactory());
    }

    protected ObjectMapper jsonMapper() {
        return JsonMapper.shared();
    }

    protected CBORFactory cborFactory() {
        return new CBORFactory();
    }

    protected CBORFactoryBuilder cborFactoryBuilder() {
        return CBORFactory.builder();
    }

    protected ObjectMapper sharedMapper() {
        return CBORMapper.shared();
    }

    protected CBORGenerator cborGenerator(OutputStream result)
    {
        return (CBORGenerator) CBORMapper.shared().createGenerator(result);
    }

    /*
    /**********************************************************
    /* Doc conversion
    /**********************************************************
     */

    protected byte[] cborDoc(ObjectMapper cborMapper, String json) {
        return cborDoc(cborMapper.writer(), json);
    }

    /*
    @Deprecated
    protected byte[] cborDoc(TokenStreamFactory f, String json)
    {
        try (JsonParser p = JSON_MAPPER.createParser(json)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), out)) {
                _copy(p, g);
            }
            return out.toByteArray();
        }
    }
    */

    protected byte[] cborDoc(ObjectWriter w, String json)
    {
        try (JsonParser p = JSON_MAPPER.createParser(json)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (JsonGenerator g = w.createGenerator(out)) {
                _copy(p, g);
            }
            return out.toByteArray();
        }
    }

    protected byte[] cborDoc(String json)
    {
        try (JsonParser p = JSON_MAPPER.createParser(json)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (JsonGenerator g = CBORMapper.shared().createGenerator(out)) {
                _copy(p, g);
            }
            return out.toByteArray();
        }
    }

    protected void _copy(JsonParser p, JsonGenerator g)
    {
        while (p.nextToken() != null) {
          g.copyCurrentEvent(p);
        }
    }

    protected CBORGenerator lenientUnicodeCborGenerator(ByteArrayOutputStream result)
    {
        return (CBORGenerator) cborFactoryBuilder()
                .enable(CBORWriteFeature.LENIENT_UTF_ENCODING)
                .build()
                .createGenerator(ObjectWriteContext.empty(), result);
    }

    // @since 2.15
    protected CBORGenerator stringrefCborGenerator(ByteArrayOutputStream result)
        throws IOException
    {
        return (CBORGenerator) cborFactoryBuilder()
                .enable(CBORWriteFeature.STRINGREF)
                .build()
                .createGenerator(ObjectWriteContext.empty(), result);
    }

    /*
    /**********************************************************************
    /* Additional assertion methods
    /**********************************************************************
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

    protected void assertNameToken(JsonToken actToken)
    {
        assertToken(JsonToken.PROPERTY_NAME, actToken);
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

    protected void _verifyBytes(byte[] actBytes, byte... expBytes) {
        assertArrayEquals(expBytes, actBytes);
    }

    protected void _verifyBytes(byte[] actBytes, byte exp1, byte[] expRest) {
        byte[] expBytes = new byte[expRest.length+1];
        System.arraycopy(expRest, 0, expBytes, 1, expRest.length);
        expBytes[0] = exp1;
        assertArrayEquals(expBytes, actBytes);
    }

    protected void _verifyBytes(byte[] actBytes, byte exp1, byte exp2, byte[] expRest) {
        byte[] expBytes = new byte[expRest.length+2];
        System.arraycopy(expRest, 0, expBytes, 2, expRest.length);
        expBytes[0] = exp1;
        expBytes[1] = exp2;
        assertArrayEquals(expBytes, actBytes);
    }

    protected void _verifyBytes(byte[] actBytes, byte exp1, byte exp2, byte exp3, byte[] expRest) {
        byte[] expBytes = new byte[expRest.length+3];
        System.arraycopy(expRest, 0, expBytes, 3, expRest.length);
        expBytes[0] = exp1;
        expBytes[1] = exp2;
        expBytes[2] = exp3;
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
        int actLen = p.getStringLength();
        char[] ch = p.getStringCharacters();
        String str2 = new String(ch, p.getStringOffset(), actLen);
        String str = p.getString();

        if (str.length() !=  actLen) {
            fail("Internal problem (p.token == "+p.currentToken()+"): p.getText().length() ['"+str+"'] == "+str.length()+"; p.getTextLength() == "+actLen);
        }
        assertEquals(str, str2, "String access via getText(), getTextXxx() must be the same");

        return str;
    }

    /*
    /**********************************************************
    /* Text generation
    /**********************************************************
     */

    protected static String generateUnicodeString(int length) {
        return generateUnicodeString(length, new Random(length));
    }

    protected static String generateUnicodeStringWithAsciiPrefix(int asciiPrefixLen, int length) {
        return generateUnicodeStringWithAsciiPrefix(asciiPrefixLen, length, new Random(length));
    }

    protected static String generateUnicodeString(int length, Random rnd)
    {
        StringBuilder sw = new StringBuilder(length+10);
        do {
            // First, add 7 ascii characters
            int num = 4 + (rnd.nextInt() & 7);
            while (--num >= 0) {
                sw.append((char) ('A' + num));
            }
            // Then a unicode char of 2, 3 or 4 bytes long
            switch (rnd.nextInt() % 3) {
            case 0:
                sw.append((char) (256 + rnd.nextInt() & 511));
                break;
            case 1:
                sw.append((char) (2048 + rnd.nextInt() & 4095));
                break;
            default:
                sw.append((char) (65536 + rnd.nextInt() & 0x3FFF));
                break;
            }
        } while (sw.length() < length);
        return sw.toString();
    }

    protected static String generateUnicodeStringWithAsciiPrefix(int asciiLength, int length, Random rnd)
    {
        StringBuilder sw = new StringBuilder(length+10);
        // add a prefix of ascii chars
        int num = asciiLength;
        while (--num >= 0) {
            sw.append((char) ('A' + (num % 32)));
        }
        do {
            // Then a unicode char of 2, 3 or 4 bytes long
            switch (rnd.nextInt() % 3) {
                case 0:
                    sw.append((char) (256 + rnd.nextInt() & 511));
                    break;
                case 1:
                    sw.append((char) (2048 + rnd.nextInt() & 4095));
                    break;
                default:
                    sw.append((char) (65536 + rnd.nextInt() & 0x3FFF));
                    break;
            }
        } while (sw.length() < length);
        return sw.toString();
    }

    protected static String generateLongAsciiString(int length) {
        return generateLongAsciiString(length, new Random(length));
    }

    protected static String generateLongAsciiString(int length, Random rnd)
    {
        StringBuilder sw = new StringBuilder(length+10);
        do {
            // First, add 7 ascii characters
            int num = 4 + (rnd.nextInt() & 7);
            while (--num >= 0) {
                sw.append((char) ('A' + num));
            }
            // and space
            sw.append(' ');
        } while (sw.length() < length);
        return sw.toString();
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

    protected static byte[] utf8Bytes(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    protected static String utf8String(ByteArrayOutputStream bytes) {
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
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

       InputStream in = getClass().getResourceAsStream(ref);
       if (in != null) {
           try {
               int len;
               while ((len = in.read(buf)) > 0) {
                   bytes.write(buf, 0, len);
               }
               in.close();
           } catch (IOException e) {
               throw new RuntimeException("Failed to read resource '"+ref+"': "+e);
           }
       }
       if (bytes.size() == 0) {
           throw new IllegalArgumentException("Failed to read resource '"+ref+"': empty resource?");
       }
       return bytes.toByteArray();
    }
}
