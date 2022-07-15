package tools.jackson.dataformat.protobuf;

import java.io.*;

import tools.jackson.core.*;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class FactoryPropertiesTest extends ProtobufTestBase
{
    final ProtobufSchema POINT_SCHEMA;

    final ProtobufMapper MAPPER = ProtobufMapper.shared();

    private final ProtobufFactory PROTO_F = MAPPER.tokenStreamFactory();

    public FactoryPropertiesTest() throws IOException {
        POINT_SCHEMA = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Point");
    }

    public void testProtoFactorySerializable() throws Exception
    {
        byte[] doc = _writeDoc(MAPPER);
        TokenStreamFactory f = MAPPER.tokenStreamFactory();
        assertNotNull(doc);

        // Ok: freeze dry factory, thaw, and try to use again:
        byte[] frozen = jdkSerialize(f);
        ProtobufFactory f2 = jdkDeserialize(frozen);
        assertNotNull(f2);
    }

    public void testVersions() throws Exception
    {
        final Version expV = MAPPER.tokenStreamFactory().version();
        assertNotNull(expV);

        JsonGenerator g = MAPPER
                .writer()
                .with(POINT_SCHEMA)
                .createGenerator(new ByteArrayOutputStream());
        assertNotNull(g.version());
        assertEquals(expV, g.version());
        g.close();

        JsonParser p = MAPPER
                .reader()
                .with(POINT_SCHEMA)
                .createParser(_writeDoc(MAPPER));
        assertNotNull(p.version());
        assertEquals(expV, p.version());
        p.close();
    }

    public void testCapabilities() throws Exception
    {
        assertTrue(PROTO_F.canHandleBinaryNatively());
        assertNull(PROTO_F.getFormatReadFeatureType());
        assertNull(PROTO_F.getFormatWriteFeatureType());
    }

    public void testInabilityToReadChars() throws Exception
    {
        final String EXP = "Cannot create parser for character-based";
        try {
            MAPPER.createParser("foo");
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            MAPPER.createParser("foo".toCharArray());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            MAPPER.createParser(new StringReader("foo"));
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
    }

    public void testInabilityToWriteChars() throws Exception
    {
        try {
            MAPPER.createGenerator(new StringWriter());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Cannot create generator for character-based");
        }
    }

    public void testStreamReadCapabilities() throws Exception
    {
        byte[] doc = _writeDoc(MAPPER);
        try (JsonParser p = MAPPER.createParser(doc)) {
            // 15-Jan-2021, tatu: 2.14 added this setting, not enabled in
            //    default set
            assertTrue(p.streamReadCapabilities().isEnabled(StreamReadCapability.EXACT_FLOATS));
        }
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private byte[] _writeDoc(ObjectMapper m) throws IOException {
        return m.writerFor(Point.class)
                .with(POINT_SCHEMA)
                .writeValueAsBytes(new Point(1, 2));
    }
    
    private byte[] jdkSerialize(Object o) throws IOException
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
}
