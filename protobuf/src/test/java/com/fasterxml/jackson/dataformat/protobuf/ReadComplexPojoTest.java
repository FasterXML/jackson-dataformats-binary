package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Assert;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import com.fasterxml.jackson.dataformat.protobuf.testutil.LimitingInputStream;

public class ReadComplexPojoTest extends ProtobufTestBase
{
    @JsonPropertyOrder({ "b", "i", "l", "d" })
    static class PojoWithArrays {
        public boolean[] b;
        
        public int[] i;

        public long[] l;

        public double[] d;

        protected PojoWithArrays() { }

        public PojoWithArrays(boolean[] b,
                int[] i, long[] l, double[] d) {
            this.b = b;
            this.i = i;
            this.l = l;
            this.d = d;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ProtobufMapper MAPPER = new ProtobufMapper();

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

    public void testPojoWithArrays() throws Exception
    {
        _testPojoWithArrays(false);
        _testPojoWithArrays(true);
    }

    @SuppressWarnings("resource")
    private void _testPojoWithArrays(boolean smallReads) throws Exception
    {
        ProtobufSchema schema = MAPPER.generateSchemaFor(PojoWithArrays.class);
        final ObjectWriter w = MAPPER.writer(schema);

        int[] i = new int[] { 1, 2, 3, 5, Integer.MIN_VALUE, Integer.MAX_VALUE };
        long[] l = new long[] { -3l, 0, Long.MAX_VALUE, Long.MIN_VALUE, -1 };
        boolean[] b = new boolean[] { true, false };
        double[] d = new double[] { 1.5, -0.25 };

        PojoWithArrays input = new PojoWithArrays(b, i, l, d);

        byte[] bytes = w.writeValueAsBytes(input);

        assertNotNull(bytes);
        assertEquals(87, bytes.length);

        ObjectReader r =  MAPPER.readerFor(PojoWithArrays.class).with(schema);
        InputStream in = new ByteArrayInputStream(bytes);

        if (smallReads) {
            in = new LimitingInputStream(in, 7);
        }
        PojoWithArrays result = r.readValue(in);

        assertNotNull(result);
        Assert.assertArrayEquals(input.i, result.i);
        Assert.assertArrayEquals(input.l, result.l);
        Assert.assertArrayEquals(input.b, result.b);
        assertEquals(input.d.length, result.d.length);
        for (int ix = 0; ix < input.d.length; ++ix) {
            assertEquals(input.d[ix], result.d[ix]);
        }

        // also let's verify result will serialize identically to source:
        byte[] b2 = w.writeValueAsBytes(result);
        assertEquals(bytes.length, b2.length);

        Assert.assertArrayEquals(bytes, b2);

        // and then see what happens if empty arrays are passed; expecting empty
        // (all fields missing, since zero-length arrays can not be represented)
        PojoWithArrays input2 = new PojoWithArrays(new boolean[0], new int[0],
                new long[0], new double[0]);
        bytes = w.writeValueAsBytes(input2);
        assertEquals(0, bytes.length);
    }
}
