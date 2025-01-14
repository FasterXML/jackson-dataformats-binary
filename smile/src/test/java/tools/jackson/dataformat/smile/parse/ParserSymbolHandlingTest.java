package tools.jackson.dataformat.smile.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for verifying that symbol handling works as planned, including
 * efficient reuse of names encountered during parsing.
 */
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

    private final ObjectMapper MAPPER = newSmileMapper();

    @Test
    public void testSharedNames() throws IOException
    {
        final int COUNT = 19000;

        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = MAPPER.writer()
                .without(SmileWriteFeature.WRITE_HEADER)
                .with(SmileWriteFeature.CHECK_SHARED_NAMES)
                .createGenerator(out);
        gen.writeStartArray();
        Random rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            gen.writeStartObject();
            int nr = rnd.nextInt() % 1200;
            gen.writeNumberProperty("f"+nr, nr);
            gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.close();
        byte[] json = out.toByteArray();

        // And verify
        JsonParser p = MAPPER.reader()
            .without(SmileReadFeature.REQUIRE_HEADER)
            .createParser(json);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            int nr = rnd.nextInt() % 1200;
            String name = "f"+nr;
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals(name, p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(nr, p.getIntValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());

        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
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
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = MAPPER.writer()
                .with(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
                .createGenerator(out);
        gen.writeStartArray();
        for (String value : SHARED_SYMBOLS) {
            gen.writeString(value);
        }
        gen.writeEndArray();
        gen.close();

        JsonParser p = _smileParser(out.toByteArray());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        for (String value : SHARED_SYMBOLS) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(value, p.getString());
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    @Test
    public void testSharedStringsInObject() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = MAPPER.writer()
                .with(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
                .createGenerator(out);

        gen.writeStartObject();
        for (int i = 0; i < SHARED_SYMBOLS.length; ++i) {
            gen.writeName("a"+i);
            gen.writeString(SHARED_SYMBOLS[i]);
        }
        gen.writeEndObject();
        gen.close();

        JsonParser p = _smileParser(out.toByteArray());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        for (int i = 0; i < SHARED_SYMBOLS.length; ++i) {
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("a"+i, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(SHARED_SYMBOLS[i], p.getString());
        }
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    @Test
    public void testSharedStringsMixed() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = MAPPER.writer()
                .with(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
                .createGenerator(out);
        gen.writeStartObject();

        gen.writeName("media");
        gen.writeStartObject();

        gen.writeStringProperty("uri", "g");
        gen.writeStringProperty("title", "J");
        gen.writeNumberProperty("width", 640);
        gen.writeStringProperty("format", "v");
        gen.writeName("persons");
        gen.writeStartArray();
        gen.writeString("B");
        gen.writeString("S");
        gen.writeEndArray();
        gen.writeStringProperty("player", "JAVA");
        gen.writeStringProperty("copyright", "NONE");

        gen.writeEndObject(); // media

        gen.writeName("images");
        gen.writeStartArray();

        // 3 instances of identical entries
        for (int i = 0; i < 3; ++i) {
            gen.writeStartObject();
            gen.writeStringProperty("uri", "h");
            gen.writeStringProperty("title", "J");
            gen.writeNumberProperty("width", 1024);
            gen.writeNumberProperty("height", 768);
            gen.writeEndObject();
        }
        gen.writeEndArray();

        gen.writeEndObject();
        gen.close();

        byte[] smile = out.toByteArray();

        JsonParser p = _smileParser(smile);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("media", p.currentName());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("uri", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("g", p.getString());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("title", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("J", p.getString());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("width", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(640, p.getIntValue());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("format", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("v", p.getString());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("persons", p.currentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("B", p.getString());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("S", p.getString());
        assertToken(JsonToken.END_ARRAY, p.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("player", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("JAVA", p.getString());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("copyright", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("NONE", p.getString());

        assertToken(JsonToken.END_OBJECT, p.nextToken()); // media

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("images", p.currentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());

        // 3 instances of identical entries:
        for (int i = 0; i < 3; ++i) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("uri", p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("h", p.getString());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("title", p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("J", p.getString());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("width", p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1024, p.getIntValue());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("height", p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(768, p.getIntValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
        }

        assertToken(JsonToken.END_ARRAY, p.nextToken()); // images

        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    @Test
    public void testDataBindingAndShared() throws IOException
    {
        SmileFactory f = SmileFactory.builder()
                .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
                .build();
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

    @Test
    public void testIssue562() throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator gen = _smileGenerator(bos, true);
        gen.writeStartObject();
        gen.writeName("z_aaaabbbbccccddddee");
        gen.writeString("end");
        gen.writeName("a_aaaabbbbccccddddee");
        gen.writeString("start");
        gen.writeEndObject();
        gen.close();

        JsonParser parser = _smileParser(bos.toByteArray());
        assertToken(JsonToken.START_OBJECT, parser.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertEquals("z_aaaabbbbccccddddee", parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("end", parser.getString());

        // This one fails...
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertEquals("a_aaaabbbbccccddddee", parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("start", parser.getString());

        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    @Test
    public void testIssue564() throws Exception
    {
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        JsonGenerator generator = _smileGenerator(bos1, true);
        generator.writeStartObject();
        generator.writeName("query");
        generator.writeStartObject();
        generator.writeName("term");
        generator.writeStartObject();
        generator.writeStringProperty("doc.payload.test_record_main.string_not_analyzed__s", "foo");
        generator.writeEndObject();
        generator.writeEndObject();
        generator.writeEndObject();
        generator.close();

        JsonParser parser = _smileParser(bos1.toByteArray());
        JsonToken token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.PROPERTY_NAME, token);
        assertEquals("query", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.PROPERTY_NAME, token);
        assertEquals("term", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.PROPERTY_NAME, token);
        assertEquals("doc.payload.test_record_main.string_not_analyzed__s", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.VALUE_STRING, token);
        assertEquals("foo", parser.getString());
        parser.close();

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        generator = _smileGenerator(bos2, true);
        generator.writeStartObject();
        generator.writeName("query");
        generator.writeStartObject();
        generator.writeName("term");
        generator.writeStartObject();
        // note the difference here, teh field is analyzed2 and not analyzed as in the first doc, as well
        // as having a different value, though don't think it matters
        generator.writeStringProperty("doc.payload.test_record_main.string_not_analyzed2__s", "bar");
        generator.writeEndObject();
        generator.writeEndObject();
        generator.writeEndObject();
        generator.close();

        parser = _smileParser(bos2.toByteArray());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.PROPERTY_NAME, token);
        assertEquals("query", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.PROPERTY_NAME, token);
        assertEquals("term", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.START_OBJECT, token);
        token = parser.nextToken();
        assertToken(JsonToken.PROPERTY_NAME, token);
        // here we fail..., seems to be a problem with field caching factory level???
        // since we get the field name of the previous (bos1) document field value (withou the 2)
        assertEquals("doc.payload.test_record_main.string_not_analyzed2__s", parser.currentName());
        token = parser.nextToken();
        assertToken(JsonToken.VALUE_STRING, token);
        assertEquals("bar", parser.getString());

        parser.close();
    }

    @Test
    public void testCorruptName34() throws Exception
    {
        // 65 chars/bytes, and not one less, to trigger it
        final String NAME = "Something else that's long enough (65 char) to cause fail: 123456";
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        JsonGenerator gen = _smileGenerator(bout, true);
        gen.writeStartArray();
        gen.writeStartObject();
        gen.writeNullProperty(NAME);
        gen.writeEndObject();
        gen.writeEndArray();
        gen.close();

        byte[] data = bout.toByteArray();

        // 23-Feb-2016, tatu: [dataformat-smile#34] is very particular and only affects
        //   particular call path, triggered by call to read JsonNode

        JsonNode n = smileMapper().readTree(data);
        assertNotNull(n);

        JsonParser parser = _smileParser(data);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonTokenId.ID_START_OBJECT, parser.currentTokenId());

        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertEquals(JsonTokenId.ID_PROPERTY_NAME, parser.currentTokenId());
        assertEquals(NAME, parser.currentName());

        assertToken(JsonToken.VALUE_NULL, parser.nextToken());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        assertToken(JsonToken.END_ARRAY, parser.nextToken());
        parser.close();
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private final String CHARS_40 = "0123456789012345678901234567890123456789";

    private byte[] writeStringValues(boolean enableSharing, int COUNT) throws IOException
    {
        SmileFactory f = SmileFactory.builder()
                .enable(SmileWriteFeature.WRITE_HEADER)
                .configure(SmileWriteFeature.CHECK_SHARED_STRING_VALUES, enableSharing)
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = f.createGenerator(ObjectWriteContext.empty(), out);
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
        JsonParser p = _smileParser(json);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        Random rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            String str = generateString(rnd.nextInt());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(str, p.getString());
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
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
