package tools.jackson.dataformat.smile.gen;

import static tools.jackson.dataformat.smile.SmileConstants.*;

import java.io.*;
import java.util.HashMap;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.io.SerializedString;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.*;

public class TestGenerator extends BaseTestForSmile
{
    /**
     * Test for verifying handling of 'true', 'false' and 'null' literals
     */
    public void testSimpleLiterals() throws Exception
    {
        // false, no header (or frame marker)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, false);
        assertEquals(0, gen.streamWriteOutputBuffered());
        gen.writeBoolean(true);
        assertEquals(1, gen.streamWriteOutputBuffered());
        gen.close();
        assertEquals(0, gen.streamWriteOutputBuffered());
        _verifyBytes(out.toByteArray(), SmileConstants.TOKEN_LITERAL_TRUE);

        // false, no header or frame marker
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeBoolean(false);
        gen.close();
        _verifyBytes(out.toByteArray(), SmileConstants.TOKEN_LITERAL_FALSE);

        // null, no header or frame marker
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeNull();
        gen.close();
        _verifyBytes(out.toByteArray(), SmileConstants.TOKEN_LITERAL_NULL);

        // And then with some other combinations:
        // true, but with header
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, true);
        gen.writeBoolean(true);
        gen.close();

        // note: version, and 'check shared names', but not 'check shared strings' or 'raw binary'
        int b4 = HEADER_BYTE_4 | SmileConstants.HEADER_BIT_HAS_SHARED_NAMES;

        _verifyBytes(out.toByteArray(),
                HEADER_BYTE_1, HEADER_BYTE_2, HEADER_BYTE_3, (byte) b4,
                SmileConstants.TOKEN_LITERAL_TRUE);

        // null, with header and end marker
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, true);
        gen.enable(SmileGenerator.Feature.WRITE_END_MARKER);
        gen.writeNull();
        // header (4 bytes) and boolen (1 byte)
        assertEquals(5, gen.streamWriteOutputBuffered());
        gen.close();
        _verifyBytes(out.toByteArray(),
                HEADER_BYTE_1, HEADER_BYTE_2, HEADER_BYTE_3, (byte) b4,
                TOKEN_LITERAL_NULL, BYTE_MARKER_END_OF_CONTENT);
    }

    public void testSimpleArray() throws Exception
    {
    	// First: empty array (2 bytes)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, false);
        gen.writeStartArray();
        gen.writeEndArray();
        gen.close();
        _verifyBytes(out.toByteArray(), SmileConstants.TOKEN_LITERAL_START_ARRAY,
        		SmileConstants.TOKEN_LITERAL_END_ARRAY);

        // then simple array with 3 literals
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeStartArray();
        gen.writeBoolean(true);
        gen.writeNull();
        gen.writeBoolean(false);
        gen.writeEndArray();
        gen.close();
        assertEquals(5, out.toByteArray().length);

        // and then array containing another array and short String
        out = new ByteArrayOutputStream();
        gen = _smileGenerator(out, false);
        gen.writeStartArray();
        gen.writeStartArray();
        gen.writeEndArray();
        gen.writeString("12");
        gen.writeEndArray();
        gen.close();
        // 4 bytes for start/end arrays; 3 bytes for short ascii string
        assertEquals(7, out.toByteArray().length);
    }

    public void testShortAscii() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, false);
        gen.writeString("abc");
        gen.close();
        _verifyBytes(out.toByteArray(), (byte)0x42, (byte) 'a', (byte) 'b', (byte) 'c');
    }


    public void testTrivialObject() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, false);
        gen.writeStartObject();
        gen.writeNumberProperty("a", 6);
        gen.writeEndObject();
        gen.close();
        _verifyBytes(out.toByteArray(), SmileConstants.TOKEN_LITERAL_START_OBJECT,
        		(byte) 0x80, (byte) 'a', (byte) (0xC0 + SmileUtil.zigzagEncode(6)),
        		SmileConstants.TOKEN_LITERAL_END_OBJECT);
    }

    public void test2FieldObject() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, false);
        gen.writeStartObject();
        gen.writeNumberProperty("a", 1);
        gen.writeNumberProperty("b", 2);
        gen.writeEndObject();
        gen.close();
        _verifyBytes(out.toByteArray(), SmileConstants.TOKEN_LITERAL_START_OBJECT,
        		(byte) 0x80, (byte) 'a', (byte) (0xC0 + SmileUtil.zigzagEncode(1)),
        		(byte) 0x80, (byte) 'b', (byte) (0xC0 + SmileUtil.zigzagEncode(2)),
        		SmileConstants.TOKEN_LITERAL_END_OBJECT);
    }

    public void testAnotherObject() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = _smileGenerator(out, false);
        gen.writeStartObject();
        gen.writeNumberProperty("a", 8);
        gen.writeName("b");
        gen.writeStartArray();
        gen.writeBoolean(true);
        gen.writeEndArray();
        gen.writeName("c");
        gen.writeStartObject();
        gen.writeEndObject();

        gen.writeName("d");
        gen.writeStartObject();
        gen.writeName("3");
        gen.writeNull();
        gen.writeEndObject();

        gen.writeEndObject();
        gen.close();
        assertEquals(21, out.toByteArray().length);
    }

    // [dataformat-smile#30]: problems with empty string key
    public void testObjectWithEmptyKey() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileFactory f = smileFactory(false, true, false).rebuild()
                .enable(SmileGenerator.Feature.CHECK_SHARED_NAMES,
                        SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
                .build();
        JsonGenerator gen = f.createGenerator(ObjectWriteContext.empty(), out);
        gen.writeStartObject();
        gen.writeName("foo");
        gen.writeStartObject();
        gen.writeName("");
        gen.writeString("bar");
        gen.writeEndObject();
        gen.writeEndObject();

        gen.close();
        final byte[] b = out.toByteArray();

        _verifyWithEmpty(f, b, 0); // simple
        _verifyWithEmpty(f, b, 1); // nextName, any
        _verifyWithEmpty(f, b, 2); // nextName, mismatch
        _verifyWithEmpty(f, b, 3); // nextName, match
    }

    private void _verifyWithEmpty(SmileFactory f, byte[] b, int mode) throws Exception
    {
        // Important: test 3 variants we have for name access:
        JsonParser p = f.createParser(ObjectReadContext.empty(), b);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        _verifyName(p, mode, "foo");

        assertToken(JsonToken.START_OBJECT, p.nextToken());

        _verifyName(p, mode, "");

        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("bar", p.getText());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    private void _verifyName(JsonParser p, int mode, String exp) throws Exception
    {
        switch (mode) {
        case 0:
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            break;
        case 1:
            String name = p.nextName();
            assertEquals(exp, name);
            break;
        case 2:
            assertFalse(p.nextName(new SerializedString(exp+"1")));
            break;
        default:
            assertTrue(p.nextName(new SerializedString(exp)));
        }
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals(exp, p.currentName());
    }

    /**
     * Test to verify that
     */
    public void testSharedStrings() throws Exception
    {
        // first, no sharing, 2 separate Strings
        final String VALUE = "abcde12345";
        byte[] data = writeRepeatedString(false, VALUE);
        int BASE_LEN = 28;
        assertEquals(BASE_LEN, data.length);
        data = writeRepeatedString(true, VALUE);
        if (data.length >= BASE_LEN) { // should be less
            fail("Expected shared String length to be < "+BASE_LEN+", was "+data.length);
        }
    }

    public void testWithMap() throws Exception
    {
        final SmileFactory smileFactory = SmileFactory.builder()
                .disable(SmileGenerator.Feature.WRITE_HEADER)
                .disable(SmileParser.Feature.REQUIRE_HEADER)
                .build();
        final ObjectMapper smileObjectMapper = new ObjectMapper(smileFactory);
        final HashMap<String, String> data = new HashMap<String,String>();
        data.put("key", "value");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SmileGenerator smileGenerator = (SmileGenerator) smileObjectMapper.createGenerator(out);
        // NOTE: not optimal way -- should use "gen.writeStartArray()" -- but exposed a problem
        out.write(SmileConstants.TOKEN_LITERAL_START_ARRAY);
        smileObjectMapper.writeValue(smileGenerator, data);
        smileGenerator.flush();
        // as above, should use generator
        out.write(SmileConstants.TOKEN_LITERAL_END_ARRAY);
        smileGenerator.close();
        byte[] doc = out.toByteArray();
        JsonNode root = smileObjectMapper.readTree(doc);
        assertNotNull(root);
        assertTrue(root.isArray());
        assertEquals(1, root.size());
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private byte[] writeRepeatedString(boolean shared, String value) throws Exception
    {
        // need header to enable shared string values
        SmileFactory f = SmileFactory.builder()
                .enable(SmileGenerator.Feature.WRITE_HEADER)
                .configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, shared)
                .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = f.createGenerator(ObjectWriteContext.empty(), out);
        gen.writeStartArray();
        gen.writeString(value);
        gen.writeString(value);
        gen.writeEndArray();
        gen.close();
        return out.toByteArray();
    }
}
