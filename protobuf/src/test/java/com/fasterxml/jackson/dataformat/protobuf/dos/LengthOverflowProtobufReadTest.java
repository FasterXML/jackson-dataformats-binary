package com.fasterxml.jackson.dataformat.protobuf.dos;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;

import com.fasterxml.jackson.dataformat.protobuf.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.Assert.*;

// Length-prefixed values with length close to Integer.MAX_VALUE must not
// overflow bounds checks (`_inputPtr + len`) nor trigger huge allocations;
// should fail with a regular parse exception instead of OutOfMemoryError.
public class LengthOverflowProtobufReadTest extends ProtobufTestBase
{
    private final static String PROTOC =
        "message R { optional string s=1; optional int32 i=2; optional int64 l=3;\n"
        + " optional bool b=4; optional bytes bb=5; repeated int32 arr=6;\n"
        + " repeated string sarr=7; optional Inner inner=8;\n"
        + " repeated int32 packed=9 [packed=true]; }\n"
        + "message Inner { optional string s2=1; }\n";

    // VInt encoding of Integer.MAX_VALUE
    private final static byte[] MAX_INT_VINT = {
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x07
    };

    private final ProtobufMapper MAPPER = newObjectMapper();

    private final ProtobufSchema SCHEMA;
    {
        try {
            SCHEMA = ProtobufSchemaLoader.std.parse(PROTOC);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Payload as reported: repeated string field with huge length
    @Test
    public void testStringArrayHugeLength() throws Exception {
        final byte[] doc = {
            (byte)0x3a,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0x07,
            (byte)0x10,(byte)0xff,(byte)0x70,(byte)0x18,(byte)0xff,(byte)0xff,
            (byte)0x70,(byte)0x18,(byte)0xff,(byte)0xff,(byte)0x70,(byte)0x18,
            (byte)0xff,(byte)0xff,(byte)0x70,(byte)0x18,(byte)0xff,(byte)0xff,
            (byte)0x70,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0x70,(byte)0xff,
            (byte)0xff,(byte)0xff,(byte)0x70
        };
        _verifyFailure(doc);
    }

    @Test
    public void testStringHugeLength() throws Exception {
        _verifyFailure(_doc(0x0A));
    }

    @Test
    public void testStringHugeLengthViaNextTextValue() throws Exception {
        try (JsonParser p = MAPPER.createParser(_doc(0x0A))) {
            p.setSchema(SCHEMA);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("s", p.nextFieldName());
            p.nextTextValue();
            fail("Should not pass");
        } catch (JsonProcessingException e) {
            // fine, any parse failure
        }
    }

    @Test
    public void testStringHugeLengthViaGetValueAsString() throws Exception {
        try (JsonParser p = MAPPER.createParser(_doc(0x0A))) {
            p.setSchema(SCHEMA);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getValueAsString();
            fail("Should not pass");
        } catch (JsonProcessingException e) {
            // fine, any parse failure
        }
    }

    @Test
    public void testBytesHugeLength() throws Exception {
        _verifyFailure(_doc(0x2A));
    }

    @Test
    public void testNestedMessageHugeLength() throws Exception {
        _verifyFailure(_doc(0x42));
    }

    @Test
    public void testPackedArrayHugeLength() throws Exception {
        _verifyFailure(_doc(0x4A));
    }

    // Bytes field with large (800 MB) but not near-overflow length, and no content:
    // must fail without allocating declared length
    @Test
    public void testBytesBogusLengthNoContent() throws Exception {
        // tag for field 5 (bytes); VInt 800,000,000
        final byte[] doc = {
            (byte) 0x2A, (byte) 0x80, (byte) 0x90, (byte) 0xBC, (byte) 0xFD, (byte) 0x02
        };
        _verifyBytesEOF(MAPPER.createParser(doc), 800_000_000, 0);
        _verifyBytesEOF(MAPPER.createParser(new ByteArrayInputStream(doc)), 800_000_000, 0);
    }

    // And legit long binary content (longer than input buffer) must still work,
    // as well as report truncated content accurately
    @Test
    public void testBytesLongValid() throws Exception {
        final byte[] data = new byte[700_000];
        for (int i = 0; i < data.length; ++i) {
            data[i] = (byte) (i * 7);
        }
        // tag for field 5 (bytes); VInt 700,000 (0x0AAE60)
        final byte[] header = { (byte) 0x2A, (byte) 0xE0, (byte) 0xDC, (byte) 0x2A };
        final byte[] doc = new byte[header.length + data.length];
        System.arraycopy(header, 0, doc, 0, header.length);
        System.arraycopy(data, 0, doc, header.length, data.length);

        _verifyBytes(MAPPER.createParser(doc), data);
        _verifyBytes(MAPPER.createParser(new ByteArrayInputStream(doc)), data);

        final byte[] truncated = Arrays.copyOf(doc, doc.length - 1000);
        _verifyBytesEOF(MAPPER.createParser(truncated), 700_000, 699_000);
        _verifyBytesEOF(MAPPER.createParser(new ByteArrayInputStream(truncated)), 700_000, 699_000);
    }

    private void _verifyBytes(JsonParser p, byte[] exp) throws Exception {
        try (JsonParser p2 = p) {
            _advanceToBytes(p2);
            assertArrayEquals(exp, p2.getBinaryValue());
            assertToken(JsonToken.END_OBJECT, p2.nextToken());
        }
    }

    private void _verifyBytesEOF(JsonParser p, int expLen, int found) throws Exception {
        try (JsonParser p2 = p) {
            _advanceToBytes(p2);
            p2.getBinaryValue();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Unexpected end-of-input");
            verifyException(e, "expected "+expLen+" bytes, only found "+found);
        }
    }

    private void _advanceToBytes(JsonParser p) throws Exception {
        p.setSchema(SCHEMA);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("bb", p.currentName());
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
    }

    private byte[] _doc(int tag) {
        // tag, then MAX_INT length, then some filler
        byte[] doc = new byte[1 + MAX_INT_VINT.length + 20];
        doc[0] = (byte) tag;
        System.arraycopy(MAX_INT_VINT, 0, doc, 1, MAX_INT_VINT.length);
        for (int i = 1 + MAX_INT_VINT.length; i < doc.length; ++i) {
            doc[i] = (byte) 'a';
        }
        return doc;
    }

    private void _verifyFailure(byte[] doc) throws Exception {
        try {
            MAPPER.readerFor(Map.class).with(SCHEMA).readValue(doc);
            fail("Should not pass");
        } catch (JsonProcessingException e) {
            // fine, any parse failure
        }
        try {
            MAPPER.readerFor(Map.class).with(SCHEMA)
                .readValue(new ByteArrayInputStream(doc));
            fail("Should not pass");
        } catch (JsonProcessingException e) {
            // fine, any parse failure
        }
    }
}
