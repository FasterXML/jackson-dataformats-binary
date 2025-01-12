package com.fasterxml.jackson.dataformat.smile.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.*;

public class ParserSymbolHandlingTest
	extends BaseTestForSmile
{
    /*
    /**********************************************************
    /* Helper types, constants
    /**********************************************************
     */

    private final static String[] SHARED_SYMBOLS = new String[] {
            "g", "J", "v", "B", "S", "JAVA",
            "h", "J", "LARGE",
            "JAVA", "J", "SMALL"
    };

    static class MediaItem
    {
        public Content content;
        public Image[] images;
    }

    public enum Size { SMALL, LARGE; }
    public enum Player { JAVA, FLASH; }

    static class Image
    {
        public String uri;
        public String title;
        public int width;
        public int height;
        public Size size;

        public Image() { }
        public Image(String uri, String title, int w, int h, Size s)
        {
            this.uri = uri;
            this.title = title;
            width = w;
            height = h;
            size = s;
        }
    }

    static class Content
    {
        public Player player;
        public String uri;
        public String title;
        public int width;
        public int height;
        public String format;
        public long duration;
        public long size;
        public int bitrate;
        public String[] persons;
        public String copyright;
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */


    @Test
    public void testSharedNames() throws IOException
    {
        final int COUNT = 19000;

        SmileFactory f = new SmileFactory();
        f.configure(SmileGenerator.Feature.WRITE_HEADER, false);
        f.configure(SmileGenerator.Feature.CHECK_SHARED_NAMES, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = f.createGenerator(out);
        gen.writeStartArray();
        Random rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            gen.writeStartObject();
            int nr = rnd.nextInt() % 1200;
            gen.writeNumberField("f"+nr, nr);
            gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.close();
        byte[] json = out.toByteArray();

        // And verify
        f.configure(SmileParser.Feature.REQUIRE_HEADER, false);
        JsonParser jp = f.createParser(json);
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            assertToken(JsonToken.START_OBJECT, jp.nextToken());
            int nr = rnd.nextInt() % 1200;
            String name = "f"+nr;
            assertToken(JsonToken.FIELD_NAME, jp.nextToken());
            assertEquals(name, jp.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
            assertEquals(nr, jp.getIntValue());
            assertToken(JsonToken.END_OBJECT, jp.nextToken());

        }
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
    }

    @Test
    public void testSharedStrings() throws IOException
    {
        final int count = 19000;
        byte[] baseline = writeStringValues(false, count);
        assertEquals(763589, baseline.length);
        verifyStringValues(baseline, count);

        // and then shared; should be much smaller
        byte[] shared = writeStringValues(true, count);
        if (shared.length >= baseline.length) {
            fail("Expected shared String length < "+baseline.length+", was "+shared.length);
        }
        verifyStringValues(shared, count);
    }

    @Test
    public void testSharedStringsInArrays() throws IOException
    {
        SmileFactory f = new SmileFactory();
        f.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = f.createGenerator(out);
        gen.writeStartArray();
        for (String value : SHARED_SYMBOLS) {
            gen.writeString(value);
        }
        gen.writeEndArray();
        gen.close();

        byte[] smile = out.toByteArray();

        JsonParser jp = f.createParser(smile);
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        for (String value : SHARED_SYMBOLS) {
            assertToken(JsonToken.VALUE_STRING, jp.nextToken());
            assertEquals(value, jp.getText());
        }
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
    }

    @Test
    public void testSharedStringsInObject() throws IOException
    {
        SmileFactory f = new SmileFactory();
        f.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = f.createGenerator(out);
        gen.writeStartObject();
        for (int i = 0; i < SHARED_SYMBOLS.length; ++i) {
            gen.writeFieldName("a"+i);
            gen.writeString(SHARED_SYMBOLS[i]);
        }
        gen.writeEndObject();
        gen.close();

        byte[] smile = out.toByteArray();

        JsonParser jp = f.createParser(smile);
        assertToken(JsonToken.START_OBJECT, jp.nextToken());
        for (int i = 0; i < SHARED_SYMBOLS.length; ++i) {
            assertToken(JsonToken.FIELD_NAME, jp.nextToken());
            assertEquals("a"+i, jp.currentName());
            assertToken(JsonToken.VALUE_STRING, jp.nextToken());
            assertEquals(SHARED_SYMBOLS[i], jp.getText());
        }
        assertToken(JsonToken.END_OBJECT, jp.nextToken());
        jp.close();
    }

    @Test
    public void testSharedStringsMixed() throws IOException
    {
        SmileFactory f = new SmileFactory();
        f.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = f.createGenerator(out);
        gen.writeStartObject();

        gen.writeFieldName("media");
        gen.writeStartObject();

        gen.writeStringField("uri", "g");
        gen.writeStringField("title", "J");
        gen.writeNumberField("width", 640);
        gen.writeStringField("format", "v");
        gen.writeFieldName("persons");
        gen.writeStartArray();
        gen.writeString("B");
        gen.writeString("S");
        gen.writeEndArray();
        gen.writeStringField("player", "JAVA");
        gen.writeStringField("copyright", "NONE");

        gen.writeEndObject(); // media

        gen.writeFieldName("images");
        gen.writeStartArray();

        // 3 instances of identical entries
        for (int i = 0; i < 3; ++i) {
            gen.writeStartObject();
            gen.writeStringField("uri", "h");
            gen.writeStringField("title", "J");
            gen.writeNumberField("width", 1024);
            gen.writeNumberField("height", 768);
            gen.writeEndObject();
        }
        gen.writeEndArray();

        gen.writeEndObject();
        gen.close();

        byte[] smile = out.toByteArray();

        JsonParser jp = f.createParser(smile);
        assertToken(JsonToken.START_OBJECT, jp.nextToken());

        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("media", jp.currentName());

        assertToken(JsonToken.START_OBJECT, jp.nextToken());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("uri", jp.currentName());
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("g", jp.getText());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("title", jp.currentName());
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("J", jp.getText());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("width", jp.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
        assertEquals(640, jp.getIntValue());

        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("format", jp.currentName());
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("v", jp.getText());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("persons", jp.currentName());
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("B", jp.getText());
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("S", jp.getText());
        assertToken(JsonToken.END_ARRAY, jp.nextToken());

        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("player", jp.currentName());
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("JAVA", jp.getText());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("copyright", jp.currentName());
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals("NONE", jp.getText());

        assertToken(JsonToken.END_OBJECT, jp.nextToken()); // media

        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertEquals("images", jp.currentName());
        assertToken(JsonToken.START_ARRAY, jp.nextToken());

        // 3 instances of identical entries:
        for (int i = 0; i < 3; ++i) {
            assertToken(JsonToken.START_OBJECT, jp.nextToken());
            assertToken(JsonToken.FIELD_NAME, jp.nextToken());
            assertEquals("uri", jp.currentName());
            assertToken(JsonToken.VALUE_STRING, jp.nextToken());
            assertEquals("h", jp.getText());
            assertToken(JsonToken.FIELD_NAME, jp.nextToken());
            assertEquals("title", jp.currentName());
            assertToken(JsonToken.VALUE_STRING, jp.nextToken());
            assertEquals("J", jp.getText());
            assertToken(JsonToken.FIELD_NAME, jp.nextToken());
            assertEquals("width", jp.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
            assertEquals(1024, jp.getIntValue());
            assertToken(JsonToken.FIELD_NAME, jp.nextToken());
            assertEquals("height", jp.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
            assertEquals(768, jp.getIntValue());
            assertToken(JsonToken.END_OBJECT, jp.nextToken());
        }

        assertToken(JsonToken.END_ARRAY, jp.nextToken()); // images

        assertToken(JsonToken.END_OBJECT, jp.nextToken());
        jp.close();
    }

    @Test
    public void testDataBindingAndShared() throws IOException
    {
        SmileFactory f = new SmileFactory();
        f.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, true);
        MediaItem item = new MediaItem();
        Content c = new Content();
        c.uri = "g";
        c.title = "J";
        c.width = 640;
        c.height = 480;
        c.format = "v";
        c.duration = 18000000L;
        c.size = 58982400L;
        c.bitrate = 262144;
        c.persons = new String[] { "B", "S" };
        c.player = Player.JAVA;
        c.copyright = "NONE";
        item.content = c;
        item.images = new Image[] {
            new Image("h", "J", 1024, 768, Size.LARGE),
            new Image("h", "J", 320, 240, Size.LARGE)
        };

        // Ok: let's just do quick comparison (yes/no)...
        ObjectMapper plain = new ObjectMapper();
        ObjectMapper smiley = new ObjectMapper(f);
        String exp = plain.writeValueAsString(item);
        byte[] smile = smiley.writeValueAsBytes(item);
        MediaItem result = smiley.readValue(smile, 0, smile.length, MediaItem.class);
        String actual = plain.writeValueAsString(result);
        assertEquals(exp, actual);
    }

    /**
     * Reproducing [JACKSON-561] (and [JACKSON-562])
     */
    @Test
    public void testIssue562() throws IOException
    {
        JsonFactory factory = new SmileFactory();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator gen = factory.createGenerator(bos);
        gen.writeStartObject();
        gen.writeFieldName("z_aaaabbbbccccddddee");
        gen.writeString("end");
        gen.writeFieldName("a_aaaabbbbccccddddee");
        gen.writeString("start");
        gen.writeEndObject();
        gen.close();

        JsonParser parser = factory.createParser(bos.toByteArray());
        assertToken(JsonToken.START_OBJECT, parser.nextToken());

        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("z_aaaabbbbccccddddee", parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("end", parser.getText());

        // This one fails...
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals("a_aaaabbbbccccddddee", parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("start", parser.getText());

        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    /**
     * Verification that [JACKSON-564] was fixed.
     */
    @Test
    public void testIssue564() throws Exception
    {
        JsonFactory factory = new SmileFactory();

        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        JsonGenerator generator = factory.createGenerator(bos1);
        generator.writeStartObject();
        generator.writeFieldName("query");
        generator.writeStartObject();
        generator.writeFieldName("term");
        generator.writeStartObject();
        generator.writeStringField("doc.payload.test_record_main.string_not_analyzed__s", "foo");
        generator.writeEndObject();
        generator.writeEndObject();
        generator.writeEndObject();
        generator.close();

        JsonParser parser = factory.createParser(bos1.toByteArray());
        JsonToken token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.FIELD_NAME, token);
        assertEquals("query", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.FIELD_NAME, token);
        assertEquals("term", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.FIELD_NAME, token);
        assertEquals("doc.payload.test_record_main.string_not_analyzed__s", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.VALUE_STRING, token);
        assertEquals("foo", parser.getText());
        parser.close();

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        generator = factory.createGenerator(bos2);
        generator.writeStartObject();
        generator.writeFieldName("query");
        generator.writeStartObject();
        generator.writeFieldName("term");
        generator.writeStartObject();
        // note the difference here, teh field is analyzed2 and not analyzed as in the first doc, as well
        // as having a different value, though don't think it matters
        generator.writeStringField("doc.payload.test_record_main.string_not_analyzed2__s", "bar");
        generator.writeEndObject();
        generator.writeEndObject();
        generator.writeEndObject();
        generator.close();

        parser = factory.createParser(bos2.toByteArray());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.FIELD_NAME, token);
        assertEquals("query", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.FIELD_NAME, token);
        assertEquals("term", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.FIELD_NAME, token);
        // here we fail..., seems to be a problem with field caching factory level???
        // since we get the field name of the previous (bos1) document field value (withou the 2)
        assertEquals("doc.payload.test_record_main.string_not_analyzed2__s", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.VALUE_STRING, token);
        assertEquals("bar", parser.getText());

        parser.close();
    }

    @Test
    public void testCorruptName34() throws Exception
    {
        SmileFactory factory = new SmileFactory();
        // 65 chars/bytes, and not one less, to trigger it
        final String NAME = "Something else that's long enough (65 char) to cause fail: 123456";
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        SmileGenerator gen = factory.createGenerator(bout);
        gen.writeStartArray();
        gen.writeStartObject();
        gen.writeNullField(NAME);
        gen.writeEndObject();
        gen.writeEndArray();
        gen.close();

        byte[] data = bout.toByteArray();

        // 23-Feb-2016, tatu: [dataformat-smile#34] is very particular and only affects
        //   particular call path, triggered by call to read JsonNode

        JsonNode n = smileMapper().readTree(data);
        assertNotNull(n);

        /*
        JsonParser parser = factory.createParser(data);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonTokenId.ID_START_OBJECT, parser.getCurrentTokenId());

        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(JsonTokenId.ID_FIELD_NAME, parser.getCurrentTokenId());
        assertEquals(NAME, parser.currentName());

        assertToken(JsonToken.VALUE_NULL, parser.nextToken());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        assertToken(JsonToken.END_ARRAY, parser.nextToken());
        parser.close();
        */
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private final String CHARS_40 = "0123456789012345678901234567890123456789";

    private byte[] writeStringValues(boolean enableSharing, int COUNT) throws IOException
    {
        SmileFactory f = new SmileFactory();
        f.configure(SmileGenerator.Feature.WRITE_HEADER, true);
        f.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, enableSharing);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = f.createGenerator(out);
        gen.writeStartArray();
        Random rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            gen.writeString(generateString(rnd.nextInt()));
        }
        gen.writeEndArray();
        gen.close();
        return out.toByteArray();
    }

    private void verifyStringValues(byte[] json, int COUNT) throws IOException
    {
        SmileFactory f = new SmileFactory();
        JsonParser jp = f.createParser(json);
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        Random rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            String str = generateString(rnd.nextInt());
            assertToken(JsonToken.VALUE_STRING, jp.nextToken());
            assertEquals(str, jp.getText());
        }
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
    }

    private String generateString(int rawNr)
    {
        int nr = rawNr % 1100;
        // Actually, let's try longer ones too
        String str = "some kind of String value we use"+nr;
        if (nr > 900) {
            str += CHARS_40;
        }
        return str;
    }
}
