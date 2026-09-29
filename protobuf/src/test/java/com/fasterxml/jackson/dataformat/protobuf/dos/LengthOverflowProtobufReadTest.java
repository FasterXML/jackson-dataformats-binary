package com.fasterxml.jackson.dataformat.protobuf.dos;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;

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
