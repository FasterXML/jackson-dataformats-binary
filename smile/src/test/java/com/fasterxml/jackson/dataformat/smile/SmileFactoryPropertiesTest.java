package com.fasterxml.jackson.dataformat.smile;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.IOContext;

import static org.junit.Assert.assertArrayEquals;

/**
 * Miscellaneous tests for {@link SmileFactory}, and for some aspects
 * of generators and parsers it creates.
 */
public class SmileFactoryPropertiesTest extends BaseTestForSmile
{
    private final static String SIMPLE_DOC_AS_JSON = "{\"simple\":[1,true,{}]}";

    private final static SmileFactory SMILE_F = new SmileFactory();
    public void testFactoryDefaults() {
        SmileFactory f = new SmileFactory();

        assertEquals(SmileParser.Feature.REQUIRE_HEADER.enabledByDefault(),
                f.isEnabled(SmileParser.Feature.REQUIRE_HEADER));

        assertEquals(SmileGenerator.Feature.WRITE_HEADER.enabledByDefault(),
                f.isEnabled(SmileGenerator.Feature.WRITE_HEADER));
        assertEquals(SmileGenerator.Feature.CHECK_SHARED_NAMES.enabledByDefault(),
                f.isEnabled(SmileGenerator.Feature.CHECK_SHARED_NAMES));
        assertEquals(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES.enabledByDefault(),
                f.isEnabled(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES));
    }

    public void testFactorySerializable() throws Exception
    {
        SmileFactory f = new SmileFactory();
        byte[] doc = _smileDoc(f, SIMPLE_DOC_AS_JSON, true);
        assertNotNull(doc);

        // Ok: freeze dry factory, thaw, and try to use again:
        byte[] frozen = jdkSerialize(f);
        SmileFactory f2 = jdkDeserialize(frozen);
        assertNotNull(f2);
        byte[] docOut = _copyDoc(f2, doc);
        assertArrayEquals(doc, docOut);
    }

    public void testFactoryCopy() throws Exception
    {
        SmileFactory f2 = SMILE_F.copy();
        assertNotNull(f2);
        // and somewhat functional
        byte[] doc = _smileDoc(f2, SIMPLE_DOC_AS_JSON, true);
        assertNotNull(doc);
    }

    public void testVersions() throws Exception
    {
        SmileFactory f = SMILE_F;
        assertNotNull(f.version());

        JsonGenerator g = f.createGenerator(new ByteArrayOutputStream());
        assertNotNull(g.version());
        assertEquals(f.version(), g.version());
        g.close();

        JsonParser p = f.createParser(_smileDoc(f, SIMPLE_DOC_AS_JSON, true));
        assertNotNull(p.version());
        assertEquals(f.version(), p.version());
        p.close();
    }

    public void testCapabilities() throws Exception
    {
        assertTrue(SMILE_F.canHandleBinaryNatively());
        assertFalse(SMILE_F.canUseCharArrays());
        assertEquals(SmileParser.Feature.class, SMILE_F.getFormatReadFeatureType());
        assertEquals(SmileGenerator.Feature.class, SMILE_F.getFormatWriteFeatureType());
    }

    public void testInabilityToReadChars() throws Exception
    {
        final String EXP = "for character-based";
        try {
            SMILE_F.createParser("foo");
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            SMILE_F.createParser("foo".toCharArray());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            SMILE_F.createParser(new StringReader("foo"));
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
    }

    public void testInabilityToWriteChars() throws Exception
    {
        try {
            SMILE_F.createGenerator(new StringWriter());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, "for character-based");
        }
        
    }

    // One lesser known feature is the ability to fall back to using JSON...
    public void testFallbackReadFromJson() throws Exception
    {
        SmileFactory f = new SmileFactory();
        f.delegateToTextual(true);
        JsonParser p = f.createParser("[ ]");
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        p.close();
    }

    // One lesser known feature is the ability to fall back to using JSON...
    public void testFallbackWriteAsJson() throws Exception
    {
        SmileFactory f = new SmileFactory();
        f.delegateToTextual(true);
        StringWriter w = new StringWriter();
        JsonGenerator g = f.createGenerator(w);
        g.writeStartArray();
        g.writeEndArray();
        g.close();

        assertEquals("[]", w.toString());
    }

    // There is one constructor designed for direct generator instantiation,
    // not used by factory; need to ensure it does not fail spectacularly
    public void testGeneratorConstruction() throws Exception
    {
        SmileFactory f = new SmileFactory();
        IOContext ctxt = new IOContext(f._getBufferRecycler(), "doc", false);
        OutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[1000];
        SmileGenerator g = new SmileGenerator(ctxt, 0, 0,
                null, bytes, buf, 0, false);
        g.writeStartArray();
        g.writeEndArray();
        g.close();
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

    protected byte[] _copyDoc(SmileFactory f, byte[] doc) throws IOException
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
