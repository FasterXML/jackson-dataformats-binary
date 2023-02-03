package tools.jackson.dataformat.smile;

import java.io.*;

import tools.jackson.core.*;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.io.ContentReference;

import tools.jackson.databind.ObjectMapper;

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
        // Need to handle this in more detail to ensure freeze/thaw'd instances
        // are used
        SmileFactory f0 = SmileFactory.builder()
                .enable(SmileGenerator.Feature.WRITE_HEADER)
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
        assertEquals(SmileParser.Feature.class, SMILE_F.getFormatReadFeatureType());
        assertEquals(SmileGenerator.Feature.class, SMILE_F.getFormatWriteFeatureType());
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

    // There is one constructor designed for direct generator instantiation,
    // not used by factory; need to ensure it does not fail spectacularly
    public void testGeneratorConstruction() throws Exception
    {
        SmileFactory f = new SmileFactory();
        IOContext ctxt = new IOContext(StreamReadConstraints.defaults(),
                f._getBufferRecycler(),
                ContentReference.rawReference("doc"), false, null);
        OutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[1000];
        SmileGenerator g = new SmileGenerator(ObjectWriteContext.empty(), ctxt,
                0, 0,
                bytes, buf, 0, false);
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
