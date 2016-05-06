package com.fasterxml.jackson.dataformat.avro;

public class ComplexPojoTest extends AvroTestBase
{
    protected final static Image IMAGE1 = new Image("http://javaone.com/keynote_large.jpg",
            "Javaone Keynote", 1024, 768, Size.LARGE);
    protected final static String DESC2 = "Javaone Keynote (small)";
    protected final static Image IMAGE2 = new Image("http://javaone.com/keynote_small.jpg",
            DESC2, 320, 240, Size.SMALL);

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final AvroMapper MAPPER = new AvroMapper();
    
    public void testRoundtrip() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFor(MediaItem.class);
        MediaItem input = getStdItem();
        byte[] avro = MAPPER.writerFor(MediaItem.class)
                .with(schema)
                .writeValueAsBytes(input);
        assertNotNull(avro);
        assertEquals(243, avro.length);

        MediaItem output = MAPPER.readerFor(MediaItem.class)
                .with(schema)
                .readValue(avro);
        assertNotNull(output);
        assertNotNull(output.getContent());
        assertEquals("None", output.getContent().getCopyright());
        assertNotNull(output.getImages());
        assertEquals(2, output.getImages().size());
        assertEquals(DESC2, output.getImages().get(1).getTitle());
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
