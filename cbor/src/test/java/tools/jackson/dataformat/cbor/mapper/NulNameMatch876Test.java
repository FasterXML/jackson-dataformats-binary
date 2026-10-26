package tools.jackson.dataformat.cbor.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import tools.jackson.core.TokenStreamFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [dataformats-binary#876]: `CBORParser.nextNameMatch()` mixed up unknown
// property names differing only by leading NUL bytes (of the last quad)
public class NulNameMatch876Test extends CBORTestBase
{
    static class AnyBean {
        public int a;
        public Map<String, Object> other = new LinkedHashMap<>();

        @JsonAnySetter
        public void set(String k, Object v) { other.put(k, v); }
    }

    private final ObjectMapper MAPPER = cborMapper();

    private final ObjectMapper NON_CANONICAL_MAPPER = cborMapper(cborFactoryBuilder()
            .disable(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
            .build());

    @Test
    public void testLeadingNullsAscii() throws Exception
    {
        _verifyAnyBean(MAPPER, "x", "\u0000x");
        _verifyAnyBean(MAPPER, "abcd", "\u0000abcd");
        _verifyAnyBean(MAPPER, "abcde", "abcd\u0000e");
        _verifyAnyBean(MAPPER, "abcdefghijklm", "abcdefghijkl\u0000m");
    }

    @Test
    public void testLeadingNullsUnicode() throws Exception
    {
        _verifyAnyBean(MAPPER, "é", "\u0000é");
        _verifyAnyBean(MAPPER, "éé", "\u0000éé");
    }

    @Test
    public void testLeadingNullsNonCanonical() throws Exception
    {
        _verifyAnyBean(NON_CANONICAL_MAPPER, "x", "\u0000x");
        _verifyAnyBean(NON_CANONICAL_MAPPER, "é", "\u0000é");
    }

    private void _verifyAnyBean(ObjectMapper mapper, String name1, String name2) throws Exception
    {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("a", 1);
        input.put(name1, 2);
        input.put(name2, 3);
        byte[] doc = mapper.writeValueAsBytes(input);
        Map<String, Object> exp = new LinkedHashMap<>(input);
        exp.remove("a");
        // Same mapper several times, to get symbol table hits
        for (int i = 0; i < 3; ++i) {
            AnyBean bean = mapper.readValue(doc, AnyBean.class);
            assertEquals(1, bean.a);
            assertEquals(exp, bean.other, "read #" + i);
        }
    }
}
