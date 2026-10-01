package tools.jackson.dataformat.protobuf.dos;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.dataformat.protobuf.*;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.*;

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

    // Payload as reported: field 7 (`sarr`, repeated string) with length
    // Integer.MAX_VALUE, followed by garbage
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
    public void testStringHugeLengthViaNextStringValue() throws Exception {
        try (JsonParser p = _parser(_doc(0x0A))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("s", p.nextName());
            p.nextStringValue();
            fail("Should not pass");
        } catch (JacksonException e) {
            // fine, any parse failure
        }
    }

    @Test
    public void testStringHugeLengthViaGetValueAsString() throws Exception {
        try (JsonParser p = _parser(_doc(0x0A))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getValueAsString();
            fail("Should not pass");
        } catch (JacksonException e) {
            // fine, any parse failure
        }
    }

    @Test
    public void testBytesHugeLength() throws Exception {
        _verifyFailure(_doc(0x2A));
    }

    @Test
    public void testNestedMessageHugeLength() throws Exception {
        _verifyFailure(_doc(0x42), "Message length overflows for field 'inner'");
    }

    @Test
    public void testPackedArrayHugeLength() throws Exception {
        _verifyFailure(_doc(0x4A), "Packed array length overflows for field 'packed'");
    }

    // `map` with `string` key that has huge length: must not overflow (3.x only)
    @Test
    public void testMapStringKeyHugeLength() throws Exception {
        final ProtobufSchema mapSchema = ProtobufSchemaLoader.std.parse(
                "syntax = \"proto3\";\n"
                + "message M { map<string, int32> counts = 1; }\n", "M");
        // tag for field 1 (map entry); entry length; then key tag (field 1, string),
        // key length of Integer.MAX_VALUE, then filler
        final byte[] doc = new byte[2 + 1 + MAX_INT_VINT.length + 20];
        doc[0] = (byte) 0x0A;
        doc[1] = (byte) (doc.length - 2);
        doc[2] = (byte) 0x0A;
        System.arraycopy(MAX_INT_VINT, 0, doc, 3, MAX_INT_VINT.length);
        Arrays.fill(doc, 3 + MAX_INT_VINT.length, doc.length, (byte) 'a');
        for (Object input : new Object[] { doc, new ByteArrayInputStream(doc) }) {
            try {
                if (input instanceof byte[]) {
                    MAPPER.readerFor(Map.class).with(mapSchema).readValue((byte[]) input);
                } else {
                    MAPPER.readerFor(Map.class).with(mapSchema).readValue((InputStream) input);
                }
                fail("Should not pass");
            } catch (JacksonException e) {
                // fine, any parse failure
            }
        }
    }

    // String with declared length below input buffer size, but truncated content:
    // should report actual number of missing bytes
    @Test
    public void testStringTruncatedViaInputStream() throws Exception {
        // tag for field 1 (string); VInt 4000 (0x0FA0); then just 10 bytes
        final byte[] doc = new byte[3 + 10];
        doc[0] = (byte) 0x0A;
        doc[1] = (byte) 0xA0;
        doc[2] = (byte) 0x1F;
        Arrays.fill(doc, 3, doc.length, (byte) 'a');
        try (JsonParser p = _parser(new ByteArrayInputStream(doc))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getString();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Needed to read 4000 bytes, missed 3990 before end-of-input");
        }
    }

    // Bytes field with large (800 MB) but not near-overflow length, and no content:
    // must fail without allocating declared length
    @Test
    public void testBytesBogusLengthNoContent() throws Exception {
        // tag for field 5 (bytes); VInt 800,000,000
        final byte[] doc = {
            (byte) 0x2A, (byte) 0x80, (byte) 0x90, (byte) 0xBC, (byte) 0xFD, (byte) 0x02
        };
        _verifyBytesEOF(_parser(doc), 800_000_000, 0);
        _verifyBytesEOF(_parser(new ByteArrayInputStream(doc)), 800_000_000, 0);
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

        _verifyBytes(_parser(doc), data);
        _verifyBytes(_parser(new ByteArrayInputStream(doc)), data);

        final byte[] truncated = Arrays.copyOf(doc, doc.length - 1000);
        _verifyBytesEOF(_parser(truncated), 700_000, 699_000);
        _verifyBytesEOF(_parser(new ByteArrayInputStream(truncated)), 700_000, 699_000);
    }

    // Similarly long String content (longer than input buffer) must work,
    // and truncated content be reported with expected/actual length
    @Test
    public void testStringLongValidAndTruncated() throws Exception {
        // mix of 1-, 2- and 3-byte UTF-8 characters
        final StringBuilder sb = new StringBuilder();
        while (sb.length() < 300_000) {
            sb.append("abc\u00E9\u20AC");
        }
        final String str = sb.toString();
        final byte[] utf8 = str.getBytes("UTF-8");
        // tag for field 1 (string); VInt length
        final byte[] header = { (byte) 0x0A,
            (byte) (0x80 | (utf8.length & 0x7F)),
            (byte) (0x80 | ((utf8.length >> 7) & 0x7F)),
            (byte) (utf8.length >> 14) };
        final byte[] doc = new byte[header.length + utf8.length];
        System.arraycopy(header, 0, doc, 0, header.length);
        System.arraycopy(utf8, 0, doc, header.length, utf8.length);

        _verifyString(_parser(doc), str);
        _verifyString(_parser(new ByteArrayInputStream(doc)), str);

        // NOTE: repeated chunk is 8 bytes, so cutting 1000 bytes lands on character
        // boundary; mid-character truncation would get generic EOF error instead
        final byte[] truncated = Arrays.copyOf(doc, doc.length - 1000);
        final int found = utf8.length - 1000;
        _verifyStringEOF(_parser(truncated), utf8.length, found);
        _verifyStringEOF(_parser(new ByteArrayInputStream(truncated)),
                utf8.length, found);
    }

    private void _verifyString(JsonParser p, String exp) throws Exception {
        try (JsonParser p2 = p) {
            _advanceToString(p2);
            assertEquals(exp, p2.getString());
            assertToken(JsonToken.END_OBJECT, p2.nextToken());
        }
    }

    private void _verifyStringEOF(JsonParser p, int expLen, int found) throws Exception {
        try (JsonParser p2 = p) {
            _advanceToString(p2);
            p2.getString();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Unexpected end-of-input");
            verifyException(e, "for String value: expected "+expLen+" bytes, only found "+found);
        }
    }

    private void _advanceToString(JsonParser p) throws Exception {
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("s", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
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
            verifyException(e, "for Binary value: expected "+expLen+" bytes, only found "+found);
        }
    }

    private void _advanceToBytes(JsonParser p) throws Exception {
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("bb", p.currentName());
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
    }

    private JsonParser _parser(byte[] doc) {
        return MAPPER.reader().with(SCHEMA).createParser(doc);
    }

    private JsonParser _parser(InputStream in) {
        return MAPPER.reader().with(SCHEMA).createParser(in);
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
        _verifyFailure(doc, null);
    }

    // If `expMsg` is null, any parse failure is fine
    private void _verifyFailure(byte[] doc, String expMsg) throws Exception {
        try {
            MAPPER.readerFor(Map.class).with(SCHEMA).readValue(doc);
            fail("Should not pass");
        } catch (JacksonException e) {
            if (expMsg != null) {
                verifyException(e, expMsg);
            }
        }
        try {
            MAPPER.readerFor(Map.class).with(SCHEMA)
                .readValue(new ByteArrayInputStream(doc));
            fail("Should not pass");
        } catch (JacksonException e) {
            if (expMsg != null) {
                verifyException(e, expMsg);
            }
        }
    }
}
