package tools.jackson.dataformat.smile.async;

import java.io.*;
import java.util.*;

import tools.jackson.core.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.dataformat.smile.*;

/**
 * Unit tests for verifying that symbol handling works as planned, including
 * efficient reuse of names encountered during parsing.
 * Copied and slightly modified version of `ParserSymbolHandlingTest`
 */
public class AsyncSharedStringsTest
	extends AsyncTestBase
{
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
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = smileMapper();

    public void testSharedNames() throws IOException
    {
        final int COUNT = 19000;

        ObjectReader r = _smileReader(false);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = _smileWriter(false)
                .with(SmileGenerator.Feature.CHECK_SHARED_NAMES)
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
        byte[] smile = out.toByteArray();

        AsyncReaderWrapper p = asyncForBytes(r, 37, smile, 0);

        // And verify 
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

    public void testSharedStringsInArrays() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = _smileWriter()
                .with(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
                .createGenerator(out);
        gen.writeStartArray();
        for (String value : SHARED_SYMBOLS) {
            gen.writeString(value);
        }
        gen.writeEndArray();
        gen.close();
        
        byte[] smile = out.toByteArray();

        AsyncReaderWrapper p = asyncForBytes(_smileReader(), 37, smile, 0);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        for (String value : SHARED_SYMBOLS) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(value, p.currentText());
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }

    public void testSharedStringsInObject() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = _smileWriter()
                .with(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
                .createGenerator(out);
        gen.writeStartObject();
        for (int i = 0; i < SHARED_SYMBOLS.length; ++i) {
            gen.writeName("a"+i);
            gen.writeString(SHARED_SYMBOLS[i]);
        }
        gen.writeEndObject();
        gen.close();
        
        byte[] smile = out.toByteArray();

        AsyncReaderWrapper p = asyncForBytes(_smileReader(), 37, smile, 0);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        for (int i = 0; i < SHARED_SYMBOLS.length; ++i) {
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("a"+i, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(SHARED_SYMBOLS[i], p.currentText());
        }
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }

    public void testSharedStringsMixed() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = _smileWriter().with(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
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

        AsyncReaderWrapper p = asyncForBytes(_smileReader(), 37, smile, 0);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("media", p.currentName());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("uri", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("g", p.currentText());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("title", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("J", p.currentText());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("width", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(640, p.getIntValue());
        
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("format", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("v", p.currentText());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("persons", p.currentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("B", p.currentText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("S", p.currentText());
        assertToken(JsonToken.END_ARRAY, p.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("player", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("JAVA", p.currentText());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("copyright", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("NONE", p.currentText());
        
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
            assertEquals("h", p.currentText());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals("title", p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("J", p.currentText());
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

    public void testDataBindingAndShared() throws IOException
    {
        SmileFactory f = SmileFactory.builder()
            .enable(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
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

    /**
     * Reproducing [JACKSON-561] (and [JACKSON-562])
     */
    public void testIssue562() throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        JsonGenerator gen = MAPPER.createGenerator(bos);
        gen.writeStartObject();
        gen.writeName("z_aaaabbbbccccddddee");
        gen.writeString("end");
        gen.writeName("a_aaaabbbbccccddddee");
        gen.writeString("start");
        gen.writeEndObject();
        gen.close();

        JsonParser parser = MAPPER.createParser(bos.toByteArray());
        assertToken(JsonToken.START_OBJECT, parser.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertEquals("z_aaaabbbbccccddddee", parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("end", parser.getText());

        // This one fails...
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertEquals("a_aaaabbbbccccddddee", parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("start", parser.getText());

        assertToken(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    /**
     * Verification that [JACKSON-564] was fixed.
     */
    public void testIssue564() throws Exception
    {
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        JsonGenerator generator = MAPPER.createGenerator(bos1);
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

        JsonParser parser = MAPPER.createParser(bos1.toByteArray());
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
        assertEquals("foo", parser.getText());
        parser.close();

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        generator = MAPPER.createGenerator(bos2);
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

        parser = MAPPER.createParser(bos2.toByteArray());
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
        assertEquals("bar", parser.getText());

        parser.close();
    }

    public void testCorruptName34() throws Exception
    {
        // 65 chars/bytes, and not one less, to trigger it
        final String NAME = "Something else that's long enough (65 char) to cause fail: 123456";
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        SmileGenerator gen = (SmileGenerator) MAPPER.createGenerator(bout);
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

        /*
        JsonParser parser = factory.createParser(data);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonTokenId.ID_START_OBJECT, parser.getCurrentTokenId());

        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertEquals(JsonTokenId.ID_PROPERTY_NAME, parser.getCurrentTokenId());
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
        ObjectWriter w = _smileWriter(true);
        if (enableSharing) {
            w = w.with(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES);
        } else {
            w = w.without(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(4000);
        JsonGenerator gen = w.createGenerator(out);
        gen.writeStartArray();
        Random rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            gen.writeString(generateString(rnd.nextInt()));
        }
        gen.writeEndArray();
        gen.close();
        return out.toByteArray();
    }

    private void verifyStringValues(byte[] doc, int COUNT) throws IOException
    {
        AsyncReaderWrapper p = asyncForBytes(_smileReader(), 37, doc, 0);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        Random rnd = new Random(COUNT);
        for (int i = 0; i < COUNT; ++i) {
            String str = generateString(rnd.nextInt());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(str, p.currentText());
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
