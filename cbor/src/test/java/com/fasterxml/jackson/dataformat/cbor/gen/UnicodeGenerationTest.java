
import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import org.junit.Assert;

import com.fasterxml.jackson.core.JsonGenerationException;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.cbor.CBORConstants;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class UnicodeGenerationTest extends CBORTestBase
{   
    /**
     * Test that encoding a String containing invalid surrogates fail with an exception
     */
    public void testFailForInvalidSurrogate() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);

        assertEquals(0, gen.getOutputBuffered());

        // Unmatched first surrogate character
        try { 
            gen.writeString("x\ud83d");
        } catch (IllegalArgumentException e) {
        }
        assertEquals(0, gen.getOutputBuffered());

        // Unmatched second surrogate character
        try { 
            gen.writeString("x\ude01");
        } catch (IllegalArgumentException e) {
        }
        assertEquals(0, gen.getOutputBuffered());

        // Unmatched second surrogate character (2)
        try { 
            gen.writeString("x\ude01x");
        } catch (IllegalArgumentException e) {
        }
        assertEquals(0, gen.getOutputBuffered());

        // Broken surrogate pair
        try { 
            gen.writeString("x\ud83dx");
        } catch (IllegalArgumentException e) {
        }
        assertEquals(0, gen.getOutputBuffered());
    }

    /**
     * Test that when the lenient unicode feature is enabled, the replacement character is used to fix invalid sequences
     */
    public void testRecoverInvalidSurrogate() throws Exception
    {
        ByteArrayOutputStream out;
        CBORGenerator gen;
        byte[] b;

        out = new ByteArrayOutputStream();
        gen = lenientUnicodeCborGenerator(out);
        assertEquals(0, gen.getOutputBuffered());
    
        // Unmatched first surrogate character
        gen.writeString("x\ud83d");
        gen.close();
        b = "x\ufffd".getBytes("utf-8");
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + b.length), b);

        out = new ByteArrayOutputStream();
        gen = lenientUnicodeCborGenerator(out);
        assertEquals(0, gen.getOutputBuffered());
    
        // Unmatched second surrogate character
        gen.writeString("x\ude01");
        gen.close();
        b = "x\ufffd".getBytes("utf-8");
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + b.length), b);

        out = new ByteArrayOutputStream();
        gen = lenientUnicodeCborGenerator(out);
        assertEquals(0, gen.getOutputBuffered());
    
        // Unmatched second surrogate character (2)
        gen.writeString("x\ude01x");
        gen.close();
        b = "x\ufffdx".getBytes("utf-8");
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + b.length), b);

        out = new ByteArrayOutputStream();
        gen = lenientUnicodeCborGenerator(out);
        assertEquals(0, gen.getOutputBuffered());
    
        // Broken surrogate pair
        gen.writeString("x\ud83dx");
        gen.close();
        b = "x\ufffdx".getBytes("utf-8");
        _verifyBytes(out.toByteArray(),
                (byte) (CBORConstants.PREFIX_TYPE_TEXT + b.length), b);

    }

}