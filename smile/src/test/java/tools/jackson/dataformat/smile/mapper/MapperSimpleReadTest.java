package tools.jackson.dataformat.smile.mapper;

import java.io.*;
import java.util.*;

import org.junit.Assert;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.type.TypeReference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileGenerator;
import tools.jackson.dataformat.smile.SmileParser;
import tools.jackson.dataformat.smile.databind.SmileMapper;

public class MapperSimpleReadTest extends BaseTestForSmile
{
    static class BytesBean {
        public byte[] bytes;
        
        public BytesBean() { }
        public BytesBean(byte[] b) { bytes = b; }
    }

    // [dataformats-binary#1711]
    static class ByteWrapper1711 {
        final byte[] val;

        @JsonCreator // (mode=JsonCreator.Mode.DELEGATING)
        public ByteWrapper1711(byte[] val) {
            this.val = val;
        }

        @JsonValue public byte[] getValue() { return val;}
    }

    static class Wrapper<V> {
        public V value;

        protected Wrapper() { }
        public Wrapper(V v) { value = v; }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = smileMapper();

    public void testBinary() throws IOException
    {
        byte[] input = new byte[] { 1, 2, 3, -1, 8, 0, 42 };
        byte[] smile = MAPPER.writeValueAsBytes(new BytesBean(input));
        BytesBean result = MAPPER.readValue(smile, BytesBean.class);
        
        assertNotNull(result.bytes);
        Assert.assertArrayEquals(input, result.bytes);
    }

    // [dataformats-binary#1711]
    public void testWrappedBinary() throws IOException
    {
        byte[] bytes = {1, 2, 3, 4, 5};
        byte[] smile = MAPPER.writeValueAsBytes(new ByteWrapper1711(bytes));
        ByteWrapper1711 read = MAPPER.readValue(smile, ByteWrapper1711.class);
        if (!Arrays.equals(bytes, read.val)) {
            throw new IllegalStateException("Arrays not equal");
        }

        // also, verify exception we get if there's no match...
        smile = MAPPER.writeValueAsBytes(new Wrapper<>(new ByteWrapper1711(bytes)));
        try {
            Wrapper<?> ob = MAPPER.readValue(smile, new TypeReference<Wrapper<BytesBean>>() { });
            Object val = ob.value;
            fail("Should not pass, Wrapper value should be `BytesBean`, got: "+val.getClass().getName());
        } catch (MismatchedInputException e) {
            verifyException(e, "Cannot deserialize value of type");
            verifyException(e, BytesBean.class.getName());
            verifyException(e, "incompatible types");
        }
    }

    // UUIDs should be written as binary (starting with 2.3)
    public void testUUIDs() throws IOException
    {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = MAPPER.writeValueAsBytes(uuid);

        // first, just verify we can decode it
        UUID out = MAPPER.readValue(bytes, UUID.class);
        assertEquals(uuid, out);

        // then verify it comes back as binary
        JsonParser p = MAPPER.createParser(bytes);
        assertEquals(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        byte[] b = p.getBinaryValue();
        assertNotNull(b);
        assertEquals(16, b.length);
        p.close();
    }

    public void testWithNestedMaps() throws IOException
    {
        Map<Object,Object> map = new HashMap<Object,Object>();
        map.put("foo", Collections.singletonMap("", "bar"));
        byte[] bytes = MAPPER.writeValueAsBytes(map);
        JsonNode n = MAPPER.readTree(new ByteArrayInputStream(bytes));
        assertTrue(n.isObject());
        assertEquals(1, n.size());
        JsonNode n2 = n.get("foo");
        assertNotNull(n2);
        assertEquals(1, n2.size());
        JsonNode n3 = n2.get("");
        assertNotNull(n3);
        assertTrue(n3.isTextual());
    }

    // for [dataformat-smile#26]
    public void testIssue26ArrayOutOfBounds() throws Exception
    {
        byte[] buffer = _generateHugeDoc();

        // split the buffer in two smaller buffers
        int len = 160;
        byte[] buf1 = new byte[len];
        byte[] buf2 = new byte[buffer.length - len];
        System.arraycopy(buffer, 0, buf1, 0, len);
        System.arraycopy(buffer, len, buf2, 0, buffer.length - len);

        // aggregate the two buffers via a SequenceInputStream
        ByteArrayInputStream in1 = new ByteArrayInputStream(buf1);
        ByteArrayInputStream in2 = new ByteArrayInputStream(buf2);
        SequenceInputStream inputStream = new SequenceInputStream(in1, in2);

        JsonNode jsonNode = smileMapper().readTree(inputStream);
        assertNotNull(jsonNode);

        // let's actually verify
        ArrayNode arr = (ArrayNode) jsonNode;
        assertEquals(26, arr.size());
    }

    private byte[] _generateHugeDoc() throws IOException
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        JsonGenerator g = _smileGenerator(b, true);
        g.writeStartArray();

        for (int c = 'a'; c <= 'z'; ++c) {
            g.writeStartObject();
            for (int ix = 0; ix < 1000; ++ix) {
                String name = "" + ((char) c) + ix;
                g.writeNumberProperty(name, ix);
            }
            g.writeEndObject();
        }
        g.writeEndArray();
        g.close();
        return b.toByteArray();
    }

    /*
    /**********************************************************
    /* Tests for [dataformats-binary#301]
    /**********************************************************
     */

    public void testStreamingFeaturesViaMapper() throws Exception
    {
        SmileMapper mapperWithHeaders = SmileMapper.builder()
                .enable(SmileGenerator.Feature.WRITE_HEADER)
                .enable(SmileParser.Feature.REQUIRE_HEADER)
                .build();
        byte[] encodedWithHeader = mapperWithHeaders.writeValueAsBytes("foo");
        assertEquals(8, encodedWithHeader.length);

        SmileMapper mapperNoHeaders = SmileMapper.builder()
                .disable(SmileGenerator.Feature.WRITE_HEADER)
                .disable(SmileParser.Feature.REQUIRE_HEADER)
                .build();
        byte[] encodedNoHeader  = mapperNoHeaders.writeValueAsBytes("foo");
        assertEquals(4, encodedNoHeader.length);

        // And then see that we can parse; with header always
        assertEquals("foo", mapperWithHeaders.readValue(encodedWithHeader, Object.class));
        assertEquals("foo", mapperNoHeaders.readValue(encodedWithHeader, Object.class));

        // without if not required
        assertEquals("foo", mapperNoHeaders.readValue(encodedNoHeader, Object.class));

        // But the reverse will fail
        try {
            mapperWithHeaders.readValue(encodedNoHeader, Object.class);
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Input does not start with Smile format header");
        }
    }
}
