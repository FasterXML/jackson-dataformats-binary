package tools.jackson.dataformat.smile.gen;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import tools.jackson.core.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileGenerator;
import tools.jackson.dataformat.smile.SmileParser;

import static org.junit.Assert.assertArrayEquals;

/**
 * Test to verify handling of "raw String value" write methods that by-pass
 * most encoding steps, for potential higher output speed (in cases where
 * input naturally comes as UTF-8 encoded byte arrays).
 */
public class TestGeneratorWithRawUtf8 extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    public void testUtf8RawStrings() throws Exception
    {
        // Let's create set of Strings to output; no ctrl chars as we do raw
        List<byte[]> strings = generateStrings(new Random(28), 750000, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream(16000);
        JsonGenerator g = MAPPER.createGenerator(out, JsonEncoding.UTF8);
        g.writeStartArray();
        for (byte[] str : strings) {
            g.writeRawUTF8String(str, 0, str.length);
        }
        g.writeEndArray();
        g.close();
        byte[] json = out.toByteArray();
        
        // Ok: let's verify that stuff was written out ok
        JsonParser jp = MAPPER.createParser(json);
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        for (byte[] inputBytes : strings) {
            assertToken(JsonToken.VALUE_STRING, jp.nextToken());
            String string = jp.getText();
            byte[] outputBytes = string.getBytes("UTF-8");
            assertEquals(inputBytes.length, outputBytes.length);
            assertArrayEquals(inputBytes, outputBytes);
        }
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
    }

    /**
     * Unit test for "JsonGenerator.writeUTF8String()", which needs
     * to handle escaping properly
     */
    public void testUtf8StringsWithEscaping() throws Exception
    {
        // Let's create set of Strings to output; do include control chars too:
        List<byte[]> strings = generateStrings(new Random(28), 720000, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream(16000);
        JsonGenerator g = MAPPER.createGenerator(out, JsonEncoding.UTF8);
        g.writeStartArray();
        for (byte[] str : strings) {
            g.writeUTF8String(str, 0, str.length);
        }
        g.writeEndArray();
        g.close();
        byte[] json = out.toByteArray();
        
        // Ok: let's verify that stuff was written out ok
        JsonParser jp = MAPPER.createParser(json);
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        for (byte[] inputBytes : strings) {
            assertToken(JsonToken.VALUE_STRING, jp.nextToken());
            String string = jp.getText();
            byte[] outputBytes = string.getBytes("UTF-8");
            assertEquals(inputBytes.length, outputBytes.length);
            assertArrayEquals(inputBytes, outputBytes);
        }
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
    }

    /**
     * Test to point out an issue with "raw" UTF-8 encoding
     * 
     * @author David Yu
     */
    public void testIssue492() throws Exception
    {
        doTestIssue492(false);
        doTestIssue492(true);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    private void doTestIssue492(boolean asUtf8String) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator generator = (SmileGenerator) MAPPER.createGenerator(out);
        
        generator.writeStartObject();
        generator.writeName("name");
        if (asUtf8String) {
            byte[] text = "PojoFoo".getBytes("ASCII");
            generator.writeUTF8String(text, 0, text.length);
        } else {
            generator.writeString("PojoFoo");
        }
        generator.writeName("collection");
        generator.writeStartObject();
        generator.writeName("v");
        generator.writeStartArray();
        if(asUtf8String) {
            byte[] text = "1".getBytes("ASCII");
            generator.writeUTF8String(text, 0, text.length);
        } else {
            generator.writeString("1");
        }
        generator.writeEndArray();
        generator.writeEndObject();
        generator.writeEndObject();
        
        generator.close();
        
        byte[] data = out.toByteArray();
        
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        SmileParser parser = (SmileParser)MAPPER.createParser(in);
        
        assertToken(parser.nextToken(), JsonToken.START_OBJECT);
        
        assertToken(parser.nextToken(), JsonToken.PROPERTY_NAME);
        assertEquals(parser.currentName(), "name");
        assertToken(parser.nextToken(), JsonToken.VALUE_STRING);
        assertEquals(parser.getText(), "PojoFoo");
        
        assertToken(parser.nextToken(), JsonToken.PROPERTY_NAME);
        assertEquals(parser.currentName(), "collection");
        assertToken(parser.nextToken(), JsonToken.START_OBJECT);
        
        assertToken(parser.nextToken(), JsonToken.PROPERTY_NAME);
        assertEquals("Should have property with name 'v'", parser.currentName(), "v");
        assertToken(parser.nextToken(), JsonToken.START_ARRAY);
        
        assertToken(parser.nextToken(), JsonToken.VALUE_STRING);
        assertEquals("Should get String value '1'", parser.getText(), "1");
        
        assertToken(parser.nextToken(), JsonToken.END_ARRAY);
        assertToken(parser.nextToken(), JsonToken.END_OBJECT);
        assertToken(parser.nextToken(), JsonToken.END_OBJECT);
        parser.close();
    }
        
    private List<byte[]> generateStrings(Random rnd, int totalLength, boolean includeCtrlChars)
        throws IOException
    {
        ArrayList<byte[]> strings = new ArrayList<byte[]>();
        do {
            int len = 2;
            int bits = rnd.nextInt(14);
            while (--bits >= 0) {
                len += len;
            }
            len = 1 + ((len + len) / 3);
            String str = generateString(rnd, len, includeCtrlChars);
            byte[] bytes = str.getBytes("UTF-8");
            strings.add(bytes);
            totalLength -= bytes.length;
        } while (totalLength > 0);
        return strings;
    }
        
    private String generateString(Random rnd, int length, boolean includeCtrlChars)
    {
        StringBuilder sb = new StringBuilder(length);
        do {
            int i;
            switch (rnd.nextInt(3)) {
            case 0: // 3 byte one
                i = 2048 + rnd.nextInt(16383);
                break;
            case 1: // 2 byte
                i = 128 + rnd.nextInt(1024);
                break;
            default: // ASCII
                i = rnd.nextInt(192);
                if (!includeCtrlChars) {
                    i += 32;
                    // but also need to avoid backslash, double-quote
                    if (i == '\\' || i == '"') {
                        i = '@'; // just arbitrary choice
                    }
                }
            }
            sb.append((char) i);
        } while (sb.length() < length);
        return sb.toString();
    }
}
