package com.fasterxml.jackson.dataformat.smile;

import java.io.*;

import org.junit.Assert;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator.Feature;

public class TestSmileGeneratorBinary extends SmileTestBase
{
    static class ThrottledInputStream extends FilterInputStream
    {
        protected final int _maxBytes;

        public ThrottledInputStream(byte[] data, int maxBytes)
        {
            this(new ByteArrayInputStream(data), maxBytes);
        }
        
        public ThrottledInputStream(InputStream in, int maxBytes)
        {
            super(in);
            _maxBytes = maxBytes;
        }

        @Override
        public int read(byte[] buf) throws IOException {
            return read(buf, 0, buf.length);
        }
        
        @Override
        public int read(byte[] buf, int offset, int len) throws IOException {
            return in.read(buf, offset, Math.min(_maxBytes, len));
        }
        
    }
    
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testStreamingBinary() throws Exception
    {
        _testStreamingBinary(true);
        _testStreamingBinary(false);
    }

    public void testBinaryWithoutLength() throws Exception
    {
        final SmileFactory f = new SmileFactory();
        JsonGenerator jg = f.createGenerator(new ByteArrayOutputStream());
        try {
            jg.writeBinary(new ByteArrayInputStream(new byte[1]), -1);
            fail("Should have failed");
        } catch (UnsupportedOperationException e) {
            verifyException(e, "must pass actual length");
        }
        jg.close();
    }
    
    public void testStreamingBinaryPartly() throws Exception {
    	_testStreamingBinaryPartly(true);
    	_testStreamingBinaryPartly(false);
    }
    
    private void _testStreamingBinaryPartly(boolean rawBinary) throws Exception
    {
    	final SmileFactory f = new SmileFactory();
    	f.configure(Feature.ENCODE_BINARY_AS_7BIT, rawBinary);
    	
    	final byte[] INPUT = TEXT4.getBytes("UTF-8");
    	ByteArrayInputStream in = new ByteArrayInputStream(INPUT);
    	
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	JsonGenerator jg = f.createGenerator(out);
    	jg.writeStartArray();
    	jg.writeBinary(in, 1);
    	jg.writeEndArray();
    	jg.close();
    	in.close();
    	
        JsonParser jp = f.createParser(out.toByteArray());
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, jp.nextToken());
        byte[] b = jp.getBinaryValue();
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
    	
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

    private void _testStreamingBinary(boolean rawBinary) throws Exception
    {
        final SmileFactory f = new SmileFactory();
        f.configure(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT, !rawBinary);
        
        final byte[] INPUT = TEXT4.getBytes("UTF-8");
        for (int chunkSize : new int[] { 1, 2, 3, 4, 7, 11, 29, 5000 }) {
            JsonGenerator jgen;
            
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            jgen = f.createGenerator(bytes);
            jgen.writeStartArray();
            InputStream data = new ThrottledInputStream(INPUT, chunkSize);
            jgen.writeBinary(data, INPUT.length);
            jgen.writeEndArray();
            jgen.close();
            jgen.close();
            data.close();

            JsonParser jp = f.createParser(bytes.toByteArray());
            assertToken(JsonToken.START_ARRAY, jp.nextToken());
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, jp.nextToken());
            byte[] b = jp.getBinaryValue();
            Assert.assertArrayEquals(INPUT, b);
            assertToken(JsonToken.END_ARRAY, jp.nextToken());
            jp.close();
        }
    }
}
