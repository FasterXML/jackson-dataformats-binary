package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Assert;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class ReadComplexPojoTest extends ProtobufTestBase
{
    final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testMediaItemSimple() throws Exception
    {
        _testMediaItem(false);
    }

    
    public void testMediaItemWithSmallReads() throws Exception
    {
        _testMediaItem(true);
    }

    @SuppressWarnings("resource")
    private void _testMediaItem(boolean smallReads) throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_MEDIA_ITEM);
        final ObjectWriter w = MAPPER.writer(schema);
        MediaItem input = MediaItem.buildItem();
        byte[] bytes = w.writeValueAsBytes(input);

        assertNotNull(bytes);
        assertEquals(252, bytes.length);

        ObjectReader r =  MAPPER.readerFor(MediaItem.class).with(schema);
        MediaItem result;
        InputStream in = new ByteArrayInputStream(bytes);

        if (smallReads) {
            in = new LimitingInputStream(in, 123);
        }
        result = r.readValue(in);
        
        assertNotNull(result);
        byte[] b2 = w.writeValueAsBytes(result);
        assertEquals(bytes.length, b2.length);

        Assert.assertArrayEquals(bytes, b2);

        assertEquals(input, result);
    }
}
