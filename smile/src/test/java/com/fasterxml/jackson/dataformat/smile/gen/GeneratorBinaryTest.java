package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.*;

import org.junit.Assert;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator.Feature;
import com.fasterxml.jackson.dataformat.smile.testutil.ThrottledInputStream;

public class GeneratorBinaryTest extends BaseTestForSmile
{
    public void testStreamingBinaryRaw() throws Exception
    {
        _testStreamingBinary(true, false);
        _testStreamingBinary(true, true);
    }

    public void testStreamingBinary7Bit() throws Exception
    {
        _testStreamingBinary(false, false);
        _testStreamingBinary(false, true);
    }
    
    public void testBinaryWithoutLength() throws Exception
    {
        final SmileFactory f = new SmileFactory();
        JsonGenerator g = f.createGenerator(new ByteArrayOutputStream());
        try {
            g.writeBinary(new ByteArrayInputStream(new byte[1]), -1);
            fail("Should have failed");
        } catch (UnsupportedOperationException e) {
            verifyException(e, "must pass actual length");
        }
        g.close();
    }
    
    public void testStreamingBinaryPartly() throws Exception {
        _testStreamingBinaryPartly(false, false);
        _testStreamingBinaryPartly(false, true);
        _testStreamingBinaryPartly(true, false);
        _testStreamingBinaryPartly(true, true);
    }
    
    private void _testStreamingBinaryPartly(boolean rawBinary, boolean throttle)
            throws Exception
    {
        final SmileFactory f = new SmileFactory();
        f.configure(Feature.ENCODE_BINARY_AS_7BIT, rawBinary);

        final byte[] INPUT = TEXT4.getBytes("UTF-8");
        InputStream in;
        if (throttle) {
            in = new ThrottledInputStream(INPUT, 3);
        } else {
            in = new ByteArrayInputStream(INPUT);
        }
    	
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator g = f.createGenerator(out);
        g.writeStartArray();
    	    g.writeBinary(in, 1);
    	    g.writeEndArray();
    	    g.close();
    	    in.close();
    	
        JsonParser p = f.createParser(out.toByteArray());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        byte[] b = p.getBinaryValue();
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    	
        assertEquals(1, b.length);
    }
    
    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private final static String TEXT = "Some content so that we can test encoding of base64 data; must"
            +" be long enough include a line wrap or two...";
    private final static String TEXT4 = TEXT + TEXT + TEXT + TEXT;

    private void _testStreamingBinary(boolean rawBinary, boolean throttle) throws Exception
    {
        final SmileFactory f = new SmileFactory();
        f.configure(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT, !rawBinary);
        
        final byte[] INPUT = TEXT4.getBytes("UTF-8");
        for (int chunkSize : new int[] { 1, 2, 3, 4, 7, 11, 29, 5000 }) {
            JsonGenerator gen;

            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            gen = f.createGenerator(bytes);

            gen.writeStartArray();
            InputStream data = new ThrottledInputStream(INPUT, chunkSize);
            gen.writeBinary(data, INPUT.length);
            gen.writeEndArray();
            gen.close();
            gen.close();
            data.close();

            final byte[] b2 = bytes.toByteArray();
            InputStream in;
            if (throttle) {
                in = new ThrottledInputStream(b2, 3);
            } else {
                in = new ByteArrayInputStream(b2);
            }
            JsonParser p = f.createParser(in);
            
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            byte[] b = p.getBinaryValue();
            Assert.assertArrayEquals(INPUT, b);
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            p.close();
        }
    }
}
