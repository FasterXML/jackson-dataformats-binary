package com.fasterxml.jackson.dataformat.protobuf;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParseException;
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
            +" optional string s = 3;\n"
            +"}\n"
    ;

    final static String PROTOC_NESTED =
            "message Outer {\n"
            +" optional Inner inner = 1;\n"
            +" optional int32 x = 2;\n"
            +"}\n"
            +"message Inner {\n"
            +" repeated int32 r = 1;\n"
            +" optional int32 c = 2;\n"
            +"}\n"
    ;

    final static String PROTOC_NESTED_MAP =
            "message Outer {\n"
            +" optional Inner inner = 1;\n"
            +" optional int32 x = 2;\n"
            +"}\n"
            +"message Inner {\n"
            +" map<string, int32> m = 1;\n"
            +" optional int32 c = 2;\n"
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

    private final ProtobufSchema SCHEMA = _schema(PROTOC);

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

    // Location of names without preceding unknown field: s at 0, c at 3
    @Test public void testLocationNoUnknownNextToken() throws Exception { _locationNoUnknown(NEXT_TOKEN); }
    @Test public void testLocationNoUnknownNextFieldName() throws Exception { _locationNoUnknown(NEXT_FIELD_NAME); }
    @Test public void testLocationNoUnknownNextFieldNameSStr() throws Exception { _locationNoUnknown(NEXT_FIELD_NAME_SSTR); }

    // Unread String value must be skipped before reading the next name
    @Test public void testUnreadStringNextToken() throws Exception { _unreadString(NEXT_TOKEN); }
    @Test public void testUnreadStringNextFieldName() throws Exception { _unreadString(NEXT_FIELD_NAME); }
    @Test public void testUnreadStringNextFieldNameSStr() throws Exception { _unreadString(NEXT_FIELD_NAME_SSTR); }

    // Unknown field in nested message, after an unpacked array ends: must stay nested
    @Test public void testNestedAfterArrayNextToken() throws Exception { _nestedAfterArray(NEXT_TOKEN); }
    @Test public void testNestedAfterArrayNextFieldName() throws Exception { _nestedAfterArray(NEXT_FIELD_NAME); }
    @Test public void testNestedAfterArrayNextFieldNameSStr() throws Exception { _nestedAfterArray(NEXT_FIELD_NAME_SSTR); }

    // Unknown field in nested message, after a map ends: must stay nested
    @Test public void testNestedAfterMapNextToken() throws Exception { _nestedAfterMap(NEXT_TOKEN); }
    @Test public void testNestedAfterMapNextFieldName() throws Exception { _nestedAfterMap(NEXT_FIELD_NAME); }
    @Test public void testNestedAfterMapNextFieldNameSStr() throws Exception { _nestedAfterMap(NEXT_FIELD_NAME_SSTR); }

    // Truncated nested message, unknown field after unpacked array: must fail, not end
    @Test
    public void testNestedTruncatedAfterArray() throws Exception {
        // `inner` declares 10 bytes but only 4 present
        final byte[] doc = { 10, 10, 8, 1, 72, 5 };
        try (JsonParser p = _parser(_schema(PROTOC_NESTED), doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("inner", p.nextFieldName());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("r", p.nextFieldName());
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            try {
                JsonToken t = p.nextToken();
                fail("Should not pass, got: "+t);
            } catch (JsonParseException e) {
                verifyException(e, "end-of-input");
            }
        }
    }

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

    private void _locationNoUnknown(NameReader r) throws Exception {
        try (JsonParser p = _parser(new byte[] { 26, 1, 'x', 8, 7 })) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("s", r.nextName(p));
            assertEquals(0, p.currentTokenLocation().getByteOffset());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("x", p.getText());
            assertEquals("c", r.nextName(p));
            assertEquals(3, p.currentTokenLocation().getByteOffset());
        }
    }

    private void _unreadString(NameReader r) throws Exception {
        try (JsonParser p = _parser(new byte[] { 26, 3, 'a', 'b', 'c', 8, 7 })) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("s", r.nextName(p));
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            // do NOT read text
            assertEquals("c", r.nextName(p));
            assertEquals(5, p.currentTokenLocation().getByteOffset());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(7, p.getIntValue());
            assertNull(r.nextName(p));
            assertToken(JsonToken.END_OBJECT, p.currentToken());
        }
    }

    private void _nestedAfterArray(NameReader r) throws Exception {
        // Outer{inner=Inner{r=[1], <unknown 9>=5, c=7}, x=3}
        final byte[] doc = { 10, 6, 8, 1, 72, 5, 16, 7, 16, 3 };
        try (JsonParser p = _parser(_schema(PROTOC_NESTED), doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("inner", r.nextName(p));
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("r", r.nextName(p));
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            _assertNestedTail(r, p, 6);
        }
    }

    private void _nestedAfterMap(NameReader r) throws Exception {
        // Outer{inner=Inner{m={"a":1}, <unknown 9>=5, c=7}, x=3}
        final byte[] doc = { 10, 11, 10, 5, 10, 1, 'a', 16, 1, 72, 5, 16, 7, 16, 3 };
        try (JsonParser p = _parser(_schema(PROTOC_NESTED_MAP), doc)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("inner", r.nextName(p));
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("m", r.nextName(p));
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("a", r.nextName(p));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            _assertNestedTail(r, p, 11);
        }
    }

    // rest after skipped unknown: Inner.c=7, end of Inner, Outer.x=3, end of Outer
    private void _assertNestedTail(NameReader r, JsonParser p, int cOffset) throws Exception {
        assertEquals("c", r.nextName(p));
        assertEquals(cOffset, p.currentTokenLocation().getByteOffset());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(7, p.getIntValue());
        assertNull(r.nextName(p));
        assertToken(JsonToken.END_OBJECT, p.currentToken());
        assertEquals("x", r.nextName(p));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(3, p.getIntValue());
        assertNull(r.nextName(p));
        assertToken(JsonToken.END_OBJECT, p.currentToken());
        assertNull(p.nextToken());
    }

    private JsonParser _parser(byte[] doc) throws IOException {
        return _parser(SCHEMA, doc);
    }

    private JsonParser _parser(ProtobufSchema schema, byte[] doc) throws IOException {
        return MAPPER.reader(schema)
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

    private static ProtobufSchema _schema(String protoc) {
        try {
            return ProtobufSchemaLoader.std.parse(protoc);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
