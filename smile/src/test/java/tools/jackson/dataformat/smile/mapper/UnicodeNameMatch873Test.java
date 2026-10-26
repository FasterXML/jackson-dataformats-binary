package tools.jackson.dataformat.smile.mapper;

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
