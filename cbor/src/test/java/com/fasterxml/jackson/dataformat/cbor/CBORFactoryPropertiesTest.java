package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;

import com.fasterxml.jackson.core.*;

import static org.junit.Assert.assertArrayEquals;

/**
 * Miscellaneous tests for {@link CBORFactory}, and for some aspects
 * of generators and parsers it creates.
 */
public class CBORFactoryPropertiesTest extends CBORTestBase
{
    private final static String SIMPLE_DOC_AS_JSON = "{\"simple\":[1,true,{}]}";

    private final static CBORFactory CBOR_F = new CBORFactory();
    
    public void testCBORFactorySerializable() throws Exception
    {
        CBORFactory f = new CBORFactory();
        byte[] doc = cborDoc(f, SIMPLE_DOC_AS_JSON);
        assertNotNull(doc);

        // Ok: freeze dry factory, thaw, and try to use again:
        byte[] frozen = jdkSerialize(f);
        CBORFactory f2 = jdkDeserialize(frozen);
        assertNotNull(f2);
        byte[] docOut = _copyDoc(f2, doc);
        assertArrayEquals(doc, docOut);
    }

    public void testCBORFactoryCopy() throws Exception
    {
        CBORFactory f2 = CBOR_F.copy();
        assertNotNull(f2);
        // and somewhat functional
        byte[] doc = cborDoc(f2, SIMPLE_DOC_AS_JSON);
        assertNotNull(doc);
    }

    public void testVersions() throws Exception
    {
        CBORFactory f = CBOR_F;
        assertNotNull(f.version());

        JsonGenerator g = f.createGenerator(new ByteArrayOutputStream());
        assertNotNull(g.version());
        assertEquals(f.version(), g.version());
        g.close();

        JsonParser p = f.createParser(cborDoc(f, SIMPLE_DOC_AS_JSON));
        assertNotNull(p.version());
        assertEquals(f.version(), p.version());
        p.close();
    }

    public void testCapabilities() throws Exception
    {
        assertTrue(CBOR_F.canHandleBinaryNatively());
        assertFalse(CBOR_F.canUseCharArrays());
        assertEquals(CBORParser.Feature.class, CBOR_F.getFormatReadFeatureType());
        assertEquals(CBORGenerator.Feature.class, CBOR_F.getFormatWriteFeatureType());
    }

    public void testInabilityToReadChars() throws Exception
    {
        final String EXP = "non-byte-based source";
        try {
            CBOR_F.createParser("foo");
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            CBOR_F.createParser("foo".toCharArray());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            CBOR_F.createParser(new StringReader("foo"));
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
    }

    public void testInabilityToWriteChars() throws Exception
    {
        try {
            CBOR_F.createGenerator(new StringWriter());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, "non-byte-based target");
        }
        
    }
    
    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    protected byte[] jdkSerialize(Object o) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1000);
        ObjectOutputStream obOut = new ObjectOutputStream(bytes);
        obOut.writeObject(o);
        obOut.close();
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    protected <T> T jdkDeserialize(byte[] raw) throws IOException
    {
        ObjectInputStream objIn = new ObjectInputStream(new ByteArrayInputStream(raw));
        try {
            return (T) objIn.readObject();
        } catch (ClassNotFoundException e) {
            fail("Missing class: "+e.getMessage());
            return null;
        } finally {
            objIn.close();
        }
    }

    protected byte[] _copyDoc(CBORFactory f, byte[] doc) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = f.createGenerator(bytes);
        _copyDoc(f, doc, g);
        g.close();
        return bytes.toByteArray();
    }
        
    protected void _copyDoc(JsonFactory f, byte[] doc, JsonGenerator g) throws IOException
    {
        JsonParser p = f.createParser(doc);
        while (p.nextToken() != null) {
            g.copyCurrentEvent(p);
        }
        p.close();
    }
}
