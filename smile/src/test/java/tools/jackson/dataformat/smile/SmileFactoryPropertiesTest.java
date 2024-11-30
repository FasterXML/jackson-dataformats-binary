package tools.jackson.dataformat.smile;

import java.io.*;

import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.async.NonBlockingByteArrayParser;

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

        assertEquals(SmileReadFeature.REQUIRE_HEADER.enabledByDefault(),
                f.isEnabled(SmileReadFeature.REQUIRE_HEADER));

        assertEquals(SmileWriteFeature.WRITE_HEADER.enabledByDefault(),
                f.isEnabled(SmileWriteFeature.WRITE_HEADER));
        assertEquals(SmileWriteFeature.CHECK_SHARED_NAMES.enabledByDefault(),
                f.isEnabled(SmileWriteFeature.CHECK_SHARED_NAMES));
        assertEquals(SmileWriteFeature.CHECK_SHARED_STRING_VALUES.enabledByDefault(),
                f.isEnabled(SmileWriteFeature.CHECK_SHARED_STRING_VALUES));
    }

    public void testFactorySerializable() throws Exception
    {
        // Need to handle this in more detail to ensure freeze/thaw'd instances
        // are used
        SmileFactory f0 = SmileFactory.builder()
                .enable(SmileWriteFeature.WRITE_HEADER)
                .build();
        ObjectMapper m = new ObjectMapper(f0);
        byte[] doc = _smileDoc(m, SIMPLE_DOC_AS_JSON, true);
        assertNotNull(doc);

        // Ok: freeze dry factory, thaw, and try to use again:
        byte[] frozen = jdkSerialize(m.tokenStreamFactory());
        SmileFactory f2 = jdkDeserialize(frozen);
        assertNotNull(f2);
        byte[] docOut = _copyDoc(f2, doc);
        assertArrayEquals(doc, docOut);
    }

    public void testFactoryCopy() throws Exception
    {
        SmileFactory f2 = SMILE_F.copy();
        assertNotNull(f2);

        // and somewhat functional: do minimal work...
        byte[] doc = _smileDoc(SIMPLE_DOC_AS_JSON, true);

        SmileParser sp = (SmileParser) f2.createParser(ObjectReadContext.empty(), doc);
        assertToken(JsonToken.START_OBJECT, sp.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, sp.nextToken());
        sp.close();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SmileGenerator sg = (SmileGenerator) f2.createGenerator(ObjectWriteContext.empty(), bytes);
        sg.writeStartObject();
        sg.writeEndObject();
        sg.close();
    }

    public void testVersions() throws Exception
    {
        SmileFactory f = SMILE_F;
        assertNotNull(f.version());

        JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), new ByteArrayOutputStream());
        assertNotNull(g.version());
        assertEquals(f.version(), g.version());
        g.close();

        JsonParser p = f.createParser(ObjectReadContext.empty(), _smileDoc(SIMPLE_DOC_AS_JSON, true));
        assertNotNull(p.version());
        assertEquals(f.version(), p.version());
        p.close();
    }

    public void testCapabilities() throws Exception
    {
        assertTrue(SMILE_F.canHandleBinaryNatively());
        assertEquals(SmileReadFeature.class, SMILE_F.getFormatReadFeatureType());
        assertEquals(SmileWriteFeature.class, SMILE_F.getFormatWriteFeatureType());
    }

    public void testInabilityToReadChars() throws Exception
    {
        final String EXP = "for character-based";
        try {
            SMILE_F.createParser(ObjectReadContext.empty(), "foo");
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            SMILE_F.createParser(ObjectReadContext.empty(), "foo".toCharArray());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            SMILE_F.createParser(ObjectReadContext.empty(), new StringReader("foo"));
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
    }

    public void testInabilityToWriteChars() throws Exception
    {
        try {
            SMILE_F.createGenerator(ObjectWriteContext.empty(), new StringWriter());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, "for character-based");
        }

    }

    public void testCanonicalization() throws Exception
    {
        try (NonBlockingByteArrayParser parser = new SmileFactory()
                .createNonBlockingByteArrayParser(ObjectReadContext.empty())) {
            assertTrue(parser._symbolsCanonical);
        }
        try (NonBlockingByteArrayParser parser = SmileFactory.builder()
                .disable(JsonFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
                .build()
                .createNonBlockingByteArrayParser(ObjectReadContext.empty())) {
            assertFalse(parser._symbolsCanonical);
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

    protected byte[] _copyDoc(SmileFactory f, byte[] doc) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), bytes);
        _copyDoc(f, doc, g);
        g.close();
        return bytes.toByteArray();
    }

    protected void _copyDoc(SmileFactory f, byte[] doc, JsonGenerator g) throws IOException
    {
        JsonParser p = f.createParser(ObjectReadContext.empty(), doc);
        while (p.nextToken() != null) {
            g.copyCurrentEvent(p);
        }
        p.close();
    }
}
