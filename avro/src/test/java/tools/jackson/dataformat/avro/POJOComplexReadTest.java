package tools.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.avro.testsupport.ThrottledInputStream;

public class POJOComplexReadTest extends AvroTestBase
{
    protected final static Image IMAGE1 = new Image("http://javaone.com/keynote_large.jpg",
            "Javaone Keynote", 1024, 768, Size.LARGE);
    protected final static String DESC2 = "Javaone Keynote (small)";
    protected final static Image IMAGE2 = new Image("http://javaone.com/keynote_small.jpg",
            DESC2, 320, 240, Size.SMALL);

    protected final static AvroMapper NATIVE_MAPPER = newMapper();
    protected final static AvroMapper APACHE_MAPPER = newApacheMapper();

    private final AvroSchema MEDIA_ITEM_SCHEMA;

    public POJOComplexReadTest() {
        MEDIA_ITEM_SCHEMA = NATIVE_MAPPER.schemaFor(MediaItem.class);
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testRoundtrip()
    {
        final byte[] doc = getStdItemBytes(NATIVE_MAPPER);
        _testRoundtrip(NATIVE_MAPPER, doc);
        _testRoundtrip(APACHE_MAPPER, doc);
    }

    public void _testRoundtrip(ObjectMapper mapper, byte[] doc)
    {
        _testRoundtrip(mapper, doc, false);
        _testRoundtrip(mapper, doc, true);
    }

    @SuppressWarnings("resource")
    private void _testRoundtrip(ObjectMapper mapper,
            byte[] avro, boolean smallReads)
    {
        InputStream in = new ByteArrayInputStream(avro);
        if (smallReads) {
            in = ThrottledInputStream.wrap(in, 9);
        }
        MediaItem output = mapper.readerFor(MediaItem.class)
                .with(MEDIA_ITEM_SCHEMA)
                .readValue(in);
        try {
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertNotNull(output);
        assertNotNull(output.getContent());
        assertEquals("None", output.getContent().getCopyright());
        assertNotNull(output.getImages());
        assertEquals(2, output.getImages().size());
        assertEquals(DESC2, output.getImages().get(1).getTitle());
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    protected final byte[] getStdItemBytes(ObjectMapper mapper)
    {
        MediaItem input = getStdItem();
        byte[] avro = mapper.writerFor(MediaItem.class)
                .with(MEDIA_ITEM_SCHEMA)
                .writeValueAsBytes(input);
        assertNotNull(avro);
        assertEquals(243, avro.length);
        return avro;
    }
    
    // from good old jvm-serializers
    protected final MediaItem getStdItem()
    {
        MediaContent content = new MediaContent();
        content.setUri("http://javaone.com/keynote.mpg");
        content.setTitle("Javaone Keynote");
        content.setWidth(640);
        content.setHeight(480);
        content.setFormat("video/mpg4");
        content.setDuration(18000000);
        content.setSize(58982400L);
        content.setBitrate(262144);
        content.setPlayer(MediaContent.Player.JAVA);
        content.setCopyright("None");
        content.addPerson("Bill Gates");
        content.addPerson("Steve Jobs");

        MediaItem item = new MediaItem(content);
        item.addPhoto(IMAGE1);
        item.addPhoto(IMAGE2);

        return item;
    }
}
