package tools.jackson.dataformat.cbor;

import java.io.*;

import tools.jackson.core.*;

import tools.jackson.databind.ObjectMapper;

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

    private byte[] cborDoc(TokenStreamFactory f, String json) throws IOException
    {
        try (JsonParser p = JSON_MAPPER.createParser(json)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), out)) {
                _copy(p, g);
            }
            return out.toByteArray();
        }
    }
    
    public void testVersions() throws Exception
    {
        ObjectMapper mapper = sharedMapper();
        final Version EXP_VERSION = mapper.tokenStreamFactory().version();
        assertNotNull(EXP_VERSION);

        JsonGenerator g = mapper.createGenerator(new ByteArrayOutputStream());
        assertNotNull(g.version());
        assertEquals(EXP_VERSION, g.version());
        g.close();

        JsonParser p = mapper.createParser(cborDoc(SIMPLE_DOC_AS_JSON));
        assertNotNull(p.version());
        assertEquals(EXP_VERSION, p.version());
        p.close();
    }

    public void testCapabilities() throws Exception
    {
        assertTrue(CBOR_F.canHandleBinaryNatively());
        assertEquals(CBORParser.Feature.class, CBOR_F.getFormatReadFeatureType());
        assertEquals(CBORGenerator.Feature.class, CBOR_F.getFormatWriteFeatureType());
    }

    public void testInabilityToReadChars() throws Exception
    {
        final String EXP = "Cannot create parser for character-based (not byte-based)";
        try {
            sharedMapper().createParser("foo");
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            sharedMapper().createParser("foo".toCharArray());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            sharedMapper().createParser(new StringReader("foo"));
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
    }

    public void testInabilityToWriteChars() throws Exception
    {
        try {
            sharedMapper().createGenerator(new StringWriter());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Cannot create generator for character-based (not byte-based)");
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
    private <T> T jdkDeserialize(byte[] raw) throws IOException
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

    private byte[] _copyDoc(CBORFactory f, byte[] doc) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), bytes);
        _copyDoc(f, doc, g);
        g.close();
        return bytes.toByteArray();
    }
        
    private void _copyDoc(CBORFactory f, byte[] doc, JsonGenerator g) throws IOException
    {
        JsonParser p = f.createParser(ObjectReadContext.empty(), doc);
        while (p.nextToken() != null) {
            g.copyCurrentEvent(p);
        }
        p.close();
    }
}
