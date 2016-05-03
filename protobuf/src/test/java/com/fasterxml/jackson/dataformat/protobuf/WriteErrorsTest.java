package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

/**
 * Unit tests for generation that trigger exceptions (or would
 * without suppression).
 */
public class WriteErrorsTest extends ProtobufTestBase
{
    static class Point3D extends Point {
        public int z;

        protected Point3D() { }
        public Point3D(int x, int y, int z) {
            super(x, y);
            this.z = z;
        }
    }

    private final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    public void testUnknownProperties() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Point");
        final ObjectWriter w = MAPPER.writerFor(Point3D.class)
                .with(schema);
        
        // First: if disabled, should get an error
        try {
            /*byte[] bytes =*/ w
                .without(JsonGenerator.Feature.IGNORE_UNKNOWN)
                .writeValueAsBytes(new Point3D(1, 2, 3));
        } catch (JsonProcessingException e) {
            verifyException(e, "Unrecognized field 'z'");
        }

        // but then not, if we disable checks
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = MAPPER.getFactory().createGenerator(bytes);
        g.enable(JsonGenerator.Feature.IGNORE_UNKNOWN);
        g.setSchema(schema);

        g.writeStartObject();
        g.writeFieldName("x");
        g.writeNumber((short) 290);
        // unknown field
        g.writeFieldName("foo");
        g.writeNumber(1.0f);
        // also, should be fine to let generator close the object...
        g.close();

        byte[] b = bytes.toByteArray();
        assertEquals(3, b.length);

        Point3D result = MAPPER.readerFor(Point3D.class).with(schema)
                .readValue(b);
        assertEquals(290, result.x);
    }
}
