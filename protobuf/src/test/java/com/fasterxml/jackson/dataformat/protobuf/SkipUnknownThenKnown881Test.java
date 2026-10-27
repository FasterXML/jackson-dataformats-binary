package com.fasterxml.jackson.dataformat.protobuf;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#881]: known field following a skipped unknown one:
// all ways of advancing must agree
public class SkipUnknownThenKnown881Test extends ProtobufTestBase
{
    final static String PROTOC =
            "message M {\n"
            +" optional int32 c = 1;\n"
            +" repeated int32 v = 2;\n"
            +"}\n"
    ;

    // unknown field 9, wire type 2 (length-delimited), 1 byte payload
    final static byte[] UNKNOWN = { 74, 1, 0 };

    interface NameReader {
        String nextName(JsonParser p) throws IOException;
    }

    final static NameReader NEXT_TOKEN = p ->
        (p.nextToken() == JsonToken.FIELD_NAME) ? p.currentName() : null;
    final static NameReader NEXT_FIELD_NAME = p -> p.nextFieldName();
    final static NameReader NEXT_FIELD_NAME_SSTR = p -> {
        // a non-matching name, to get the name itself back via currentName()
        if (p.nextFieldName(new SerializedString("xxx"))) {
            fail("Should not match");
        }
        return (p.currentToken() == JsonToken.FIELD_NAME) ? p.currentName() : null;
    };

    private final ProtobufMapper MAPPER = newObjectMapper();

    private final ProtobufSchema SCHEMA = _schema();

    // Validation of the known field must use its own wire type
    @Test public void testScalarNextToken() throws Exception { _scalar(NEXT_TOKEN); }
    @Test public void testScalarNextFieldName() throws Exception { _scalar(NEXT_FIELD_NAME); }
    @Test public void testScalarNextFieldNameSStr() throws Exception { _scalar(NEXT_FIELD_NAME_SSTR); }

    // Repeated (unpacked) field must be an array
    @Test public void testUnpackedNextToken() throws Exception { _array(NEXT_TOKEN, _unpacked()); }
    @Test public void testUnpackedNextFieldName() throws Exception { _array(NEXT_FIELD_NAME, _unpacked()); }
    @Test public void testUnpackedNextFieldNameSStr() throws Exception { _array(NEXT_FIELD_NAME_SSTR, _unpacked()); }

    // Packed data for a field declared unpacked: decided by actual wire type [#134]
    @Test public void testPackedNextToken() throws Exception { _array(NEXT_TOKEN, _packed()); }
    @Test public void testPackedNextFieldName() throws Exception { _array(NEXT_FIELD_NAME, _packed()); }
    @Test public void testPackedNextFieldNameSStr() throws Exception { _array(NEXT_FIELD_NAME_SSTR, _packed()); }

    // Location of name following skipped unknown field: its own tag, at offset 3
    @Test public void testLocationNextToken() throws Exception { _location(NEXT_TOKEN); }
    @Test public void testLocationNextFieldName() throws Exception { _location(NEXT_FIELD_NAME); }
    @Test public void testLocationNextFieldNameSStr() throws Exception { _location(NEXT_FIELD_NAME_SSTR); }

    // Matching name after skipped unknown field must be reported as a match
    @Test
    public void testNextFieldNameSStrMatch() throws Exception {
        try (JsonParser p = _parser(_concat(UNKNOWN, new byte[] { 8, 7 }))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertTrue(p.nextFieldName(new SerializedString("c")));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(7, p.getIntValue());
        }
    }

    private void _scalar(NameReader r) throws Exception {
        try (JsonParser p = _parser(_concat(UNKNOWN, new byte[] { 8, 7 }))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("c", r.nextName(p));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(7, p.getIntValue());
            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
        }
    }

    private void _array(NameReader r, byte[] doc) throws Exception {
        try (JsonParser p = _parser(doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("v", r.nextName(p));
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(2, p.getIntValue());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
        }
    }

    private void _location(NameReader r) throws Exception {
        try (JsonParser p = _parser(_concat(UNKNOWN, new byte[] { 8, 7 }))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("c", r.nextName(p));
            assertEquals(3, p.currentTokenLocation().getByteOffset());
        }
    }

    private JsonParser _parser(byte[] doc) throws IOException {
        return MAPPER.reader(SCHEMA)
                .with(JsonParser.Feature.IGNORE_UNDEFINED)
                .createParser(doc);
    }

    private static byte[] _unpacked() { return _concat(UNKNOWN, new byte[] { 16, 1, 16, 2 }); }
    private static byte[] _packed() { return _concat(UNKNOWN, new byte[] { 18, 2, 1, 2 }); }

    private static byte[] _concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private ProtobufSchema _schema() {
        try {
            return ProtobufSchemaLoader.std.parse(PROTOC);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
