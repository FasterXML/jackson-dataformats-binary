package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TokenStreamFactory;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class FactoryPropertiesTest extends ProtobufTestBase
{
    final ProtobufSchema POINT_SCHEMA;

    final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    private final static ProtobufFactory PROTO_F = new ProtobufFactory();

    public FactoryPropertiesTest() throws IOException {
        POINT_SCHEMA = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Point");
    }

    public void testCBORFactorySerializable() throws Exception
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

        JsonGenerator g = MAPPER.createGenerator(new ByteArrayOutputStream());
        assertNotNull(g.version());
        assertEquals(expV, g.version());
        g.close();

        JsonParser p = MAPPER.createParser(_writeDoc(MAPPER));
        p.setSchema(POINT_SCHEMA);
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
        final String EXP = "Can not create parser for character-based";
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
            verifyException(e, "Can not create generator for character-based");
        }
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
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
