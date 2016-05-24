package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
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
        ProtobufFactory f = new ProtobufFactory();
        byte[] doc = _writeDoc(f);
        assertNotNull(doc);

        // Ok: freeze dry factory, thaw, and try to use again:
        byte[] frozen = jdkSerialize(f);
        ProtobufFactory f2 = jdkDeserialize(frozen);
        assertNotNull(f2);
    }

    public void testCBORFactoryCopy() throws Exception
    {
        ProtobufFactory f2 = PROTO_F.copy();
        assertNotNull(f2);
        // and somewhat functional
        byte[] doc = _writeDoc(f2);
        assertNotNull(doc);
    }

    public void testVersions() throws Exception
    {
        ProtobufFactory f = PROTO_F;
        assertNotNull(f.version());

        JsonGenerator g = f.createGenerator(new ByteArrayOutputStream());
        assertNotNull(g.version());
        assertEquals(f.version(), g.version());
        g.close();

        JsonParser p = f.createParser(_writeDoc(f));
        p.setSchema(POINT_SCHEMA);
        assertNotNull(p.version());
        assertEquals(f.version(), p.version());
        p.close();
    }

    public void testCapabilities() throws Exception
    {
        assertTrue(PROTO_F.canHandleBinaryNatively());
        assertFalse(PROTO_F.canUseCharArrays());
        assertNull(PROTO_F.getFormatReadFeatureType());
        assertNull(PROTO_F.getFormatWriteFeatureType());
    }

    public void testInabilityToReadChars() throws Exception
    {
        final String EXP = "non-byte-based source";
        try {
            PROTO_F.createParser("foo");
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            PROTO_F.createParser("foo".toCharArray());
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
        try {
            PROTO_F.createParser(new StringReader("foo"));
            fail();
        } catch (UnsupportedOperationException e) {
            verifyException(e, EXP);
        }
    }

    public void testInabilityToWriteChars() throws Exception
    {
        try {
            PROTO_F.createGenerator(new StringWriter());
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

    private byte[] _writeDoc(ProtobufFactory f) throws IOException {
        return new ObjectMapper(f).writerFor(Point.class)
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
