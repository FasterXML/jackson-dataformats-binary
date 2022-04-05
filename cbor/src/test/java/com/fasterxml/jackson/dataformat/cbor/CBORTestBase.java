package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

public abstract class CBORTestBase
    extends junit.framework.TestCase
{
    /*
    /**********************************************************
    /* Factory methods
    /**********************************************************
     */

    protected CBORParser cborParser(ByteArrayOutputStream bytes) throws IOException {
        return cborParser(bytes.toByteArray());
    }

    protected CBORParser cborParser(byte[] input) throws IOException {
        return cborParser(cborFactory(), input);
    }

    protected CBORParser cborParser(InputStream in) throws IOException {
        CBORFactory f = cborFactory();
        return cborParser(f, in);
    }

    protected CBORParser cborParser(CBORFactory f, byte[] input) throws IOException {
        return f.createParser(input);
    }

    protected CBORParser cborParser(CBORFactory f, InputStream in) throws IOException {
        return f.createParser(in);
    }

    protected CBORMapper cborMapper() {
        return new CBORMapper(cborFactory());
    }

    protected CBORFactory cborFactory() {
        return new CBORFactory();
    }

    protected CBORFactoryBuilder cborFactoryBuilder() {
        return CBORFactory.builder();
    }

    protected byte[] cborDoc(String json) throws IOException {
        return cborDoc(cborFactory(), json);
    }

    protected byte[] cborDoc(TokenStreamFactory cborF, String json) throws IOException
    {
        JsonFactory jf = new JsonFactory();
        JsonParser p = jf.createParser(json);
        ByteArrayOutputStream out = new ByteArrayOutputStream(json.length());
        JsonGenerator dest = cborF.createGenerator(out);
    	
        while (p.nextToken() != null) {
            dest.copyCurrentEvent(p);
        }
        p.close();
        dest.close();
        return out.toByteArray();
    }

    protected CBORGenerator cborGenerator(ByteArrayOutputStream result)
        throws IOException
    {
        return cborGenerator(cborFactory(), result);
    }

    protected CBORGenerator cborGenerator(CBORFactory f,
            ByteArrayOutputStream result)
        throws IOException
    {
        return f.createGenerator(result, null);
    }

    // @since 2.12
    protected CBORGenerator lenientUnicodeCborGenerator(ByteArrayOutputStream result)
        throws IOException
    {
        return cborFactoryBuilder()
                .enable(CBORGenerator.Feature.LENIENT_UTF_ENCODING)
                .build()
                .createGenerator(result);
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
    
    protected void _verifyBytes(byte[] actBytes, byte... expBytes) {
        Assert.assertArrayEquals(expBytes, actBytes);
    }

    protected void _verifyBytes(byte[] actBytes, byte exp1, byte[] expRest) {
        byte[] expBytes = new byte[expRest.length+1];
        System.arraycopy(expRest, 0, expBytes, 1, expRest.length);
        expBytes[0] = exp1;
        Assert.assertArrayEquals(expBytes, actBytes);
    }

    protected void _verifyBytes(byte[] actBytes, byte exp1, byte exp2, byte[] expRest) {
        byte[] expBytes = new byte[expRest.length+2];
        System.arraycopy(expRest, 0, expBytes, 2, expRest.length);
        expBytes[0] = exp1;
        expBytes[1] = exp2;
        Assert.assertArrayEquals(expBytes, actBytes);
    }

    protected void _verifyBytes(byte[] actBytes, byte exp1, byte exp2, byte exp3, byte[] expRest) {
        byte[] expBytes = new byte[expRest.length+3];
        System.arraycopy(expRest, 0, expBytes, 3, expRest.length);
        expBytes[0] = exp1;
        expBytes[1] = exp2;
        expBytes[2] = exp3;
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
    /* Text generation
    /**********************************************************
     */

    protected static String generateUnicodeString(int length) {
        return generateUnicodeString(length, new Random(length));
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
