package tools.jackson.dataformat.smile.mapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [dataformats-binary#873]: `SmileParser.nextNameMatch()` misread short
// non-ASCII property names not known to the matcher
public class UnicodeNameMatch873Test extends BaseTestForSmile
{
    static class Bean {
        public int a;
        public String b;
    }

    static class AnyBean {
        public int a;
        public Map<String, Object> other = new LinkedHashMap<>();

        @JsonAnySetter
        public void set(String k, Object v) { other.put(k, v); }
    }

    private final ObjectMapper MAPPER = smileMapperBuilder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    // Symbol-table hit did not advance input (fails on 2nd read)
    @Test
    public void testUnknownShortName() throws Exception
    {
        _verifyBean("é");
    }

    // 12-byte name: lookup used unfilled `_quadBuffer`
    @Test
    public void testUnknownName12Bytes() throws Exception
    {
        _verifyBean("éééééé");
    }

    // All short Unicode name lengths (2 - 56 bytes)
    @Test
    public void testUnknownNamesAllLengths() throws Exception
    {
        for (int len = 2; len <= 56; ++len) {
            StringBuilder sb = new StringBuilder("é");
            while (sb.toString().getBytes(StandardCharsets.UTF_8).length < len) {
                sb.append('x');
            }
            _verifyBean(sb.toString());
        }
    }

    // 4-byte name looked up with stale 2nd quad from preceding 5-byte name
    @Test
    public void testStaleQuadAnySetter() throws Exception
    {
        byte[] doc = _smileDoc(MAPPER, "{\"a\":1,\"ééxy\":2,\"éé\":3}", true);
        for (int i = 0; i < 3; ++i) {
            AnyBean bean = MAPPER.readValue(doc, AnyBean.class);
            assertEquals(1, bean.a);
            Map<String, Object> exp = new LinkedHashMap<>();
            exp.put("ééxy", 2);
            exp.put("éé", 3);
            assertEquals(exp, bean.other);
        }
    }

    // Names differing only by leading NUL bytes must not be confused
    @Test
    public void testLeadingNullsAscii() throws Exception
    {
        _verifyAnyBean("x", "\u0000x");
        _verifyAnyBean("abcd", "\u0000abcd");
    }

    @Test
    public void testLeadingNullsUnicode() throws Exception
    {
        _verifyAnyBean("é", "\u0000é");
        _verifyAnyBean("éé", "\u0000éé");
    }

    // 57 bytes is the longest short Unicode name; not written by `SmileGenerator`
    // (which uses at most 56) but valid to read
    @Test
    public void testUnknownName57Bytes() throws Exception
    {
        byte[] nameBytes = new byte[57];
        nameBytes[0] = (byte) 0xC3;
        nameBytes[1] = (byte) 0xA9;
        for (int i = 2; i < nameBytes.length; ++i) {
            nameBytes[i] = 'x';
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xFA); // START_OBJECT
        bytes.write(new byte[] { (byte) 0x80, 'a', (byte) 0xC4 }); // "a": 2
        bytes.write(0xF7); // short Unicode name, 57 bytes
        bytes.write(nameBytes);
        bytes.write(0xC2); // 1
        bytes.write(new byte[] { (byte) 0x80, 'b', 0x40, 'x' }); // "b": "x"
        bytes.write(0xFB); // END_OBJECT
        byte[] doc = bytes.toByteArray();

        for (int i = 0; i < 3; ++i) {
            Bean bean = MAPPER.readValue(doc, Bean.class);
            assertEquals(2, bean.a, "read #" + i);
            assertEquals("x", bean.b, "read #" + i);
        }
    }

    private void _verifyAnyBean(String name1, String name2) throws Exception
    {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("a", 1);
        input.put(name1, 2);
        input.put(name2, 3);
        byte[] doc = MAPPER.writeValueAsBytes(input);
        Map<String, Object> exp = new LinkedHashMap<>(input);
        exp.remove("a");
        for (int i = 0; i < 3; ++i) {
            AnyBean bean = MAPPER.readValue(doc, AnyBean.class);
            assertEquals(1, bean.a);
            assertEquals(exp, bean.other, "read #" + i);
        }
    }

    private void _verifyBean(String name) throws Exception
    {
        byte[] doc = _smileDoc(MAPPER, "{\"a\":2,\"" + name + "\":1,\"b\":\"x\"}", true);
        // Same mapper several times, to get symbol table hits
        for (int i = 0; i < 3; ++i) {
            Bean bean = MAPPER.readValue(doc, Bean.class);
            assertEquals(2, bean.a, "name '" + name + "', read #" + i);
            assertEquals("x", bean.b, "name '" + name + "', read #" + i);
        }
    }
}
