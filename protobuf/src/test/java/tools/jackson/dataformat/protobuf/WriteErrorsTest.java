package tools.jackson.dataformat.protobuf;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for generation that trigger exceptions (or would
 * without suppression).
 */
public class WriteErrorsTest extends ProtobufTestBase
{
    public static class Point3D extends Point {
        public int z;

        public Point3D() { }
        public Point3D(int x, int y, int z) {
            super(x, y);
            this.z = z;
        }
    }

    private final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    @Test
    public void testUnknownProperties() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Point");
        final ObjectWriter w = MAPPER.writerFor(Point3D.class)
                .with(schema);

        // First: if disabled, should get an error
        try {
            /*byte[] bytes =*/ w
                .without(StreamWriteFeature.IGNORE_UNKNOWN)
                .writeValueAsBytes(new Point3D(1, 2, 3));
        } catch (StreamWriteException e) {
            verifyException(e, "Unrecognized field 'z'");
        }

        // but then not, if we disable checks
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = MAPPER.writer()
                .with(StreamWriteFeature.IGNORE_UNKNOWN)
                .with(schema)
                .createGenerator(bytes);

        g.writeStartObject();
        g.writeName("x");
        g.writeNumber((short) 290);
        // unknown field
        g.writeName("foo");
        g.writeNumber(1.0f);
        // also, should be fine to let generator close the object...
        g.close();

        byte[] b = bytes.toByteArray();
        assertEquals(3, b.length);

        Point3D result = MAPPER.readerFor(Point3D.class).with(schema)
                .readValue(b);
        assertEquals(290, result.x);
    }

    // [dataformats-binary#715]: error must name the value type actually written,
    // not always report `string`
    @Test
    public void testWrongWireTypeNamesActualType() throws Exception
    {
        // "Point" declares `x` as an int32, so any non-integral value is a mismatch
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Point");

        _verifyWrongWireType(schema, "double", new ValueWriter() {
            @Override
            public void write(JsonGenerator g) throws Exception {
                g.writeNumber(0.25);
            }
        });
        _verifyWrongWireType(schema, "binary", new ValueWriter() {
            @Override
            public void write(JsonGenerator g) throws Exception {
                g.writeBinary(new byte[] { 1, 2, 3 });
            }
        });
        _verifyWrongWireType(schema, "string", new ValueWriter() {
            @Override
            public void write(JsonGenerator g) throws Exception {
                g.writeString("foo");
            }
        });
    }

    private interface ValueWriter {
        void write(JsonGenerator g) throws Exception;
    }

    private void _verifyWrongWireType(ProtobufSchema schema, String expType,
            ValueWriter w) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = MAPPER.writer()
                .with(schema)
                .createGenerator(bytes);
        g.writeStartObject();
        g.writeName("x");
        try {
            w.write(g);
            fail("Should not pass: `x` is an int32 field");
        } catch (StreamWriteException e) {
            verifyException(e, "Can not write `"+expType+"` value for 'x'");
        } finally {
            g.close();
        }
    }
}
