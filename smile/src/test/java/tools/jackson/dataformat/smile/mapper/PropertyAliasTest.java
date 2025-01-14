package tools.jackson.dataformat.smile.mapper;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PropertyAliasTest extends BaseTestForSmile
{
    static class AliasBean {
        @JsonAlias({ "nm", "Name" })
        public String name;

        int _xyz;

        int _a;

        @JsonCreator
        public AliasBean(@JsonProperty("a")
            @JsonAlias("A") int a) {
            _a = a;
        }

        @JsonAlias({ "Xyz" })
        public void setXyz(int x) {
            _xyz = x;
        }
    }

    static class PolyWrapperForAlias {
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
                include = JsonTypeInfo.As.WRAPPER_ARRAY)
        @JsonSubTypes({
            @JsonSubTypes.Type(value = AliasBean.class,name = "ab"),
        })
        public Object value;

        protected PolyWrapperForAlias() { }
        public PolyWrapperForAlias(Object v) { value = v; }
    }

    /*
    /********************************************************
    /* Test methods
    /********************************************************
     */

    private final ObjectMapper MAPPER = smileMapper();

    // [databind#1029]
    @Test
    public void testSimpleAliases() throws Exception
    {
        AliasBean bean;

        // first, one indicated by field annotation, set via field
        bean = MAPPER.readValue(_smileDoc(aposToQuotes("{'Name':'Foobar','a':3,'xyz':37}")),
                AliasBean.class);
        assertEquals("Foobar", bean.name);
        assertEquals(3, bean._a);
        assertEquals(37, bean._xyz);

        // then method-bound one
        bean = MAPPER.readValue(_smileDoc(aposToQuotes("{'name':'Foobar','a':3,'Xyz':37}")),
                AliasBean.class);
        assertEquals("Foobar", bean.name);
        assertEquals(3, bean._a);
        assertEquals(37, bean._xyz);

        // and finally, constructor-backed one
        bean = MAPPER.readValue(_smileDoc(aposToQuotes("{'name':'Foobar','A':3,'xyz':37}")),
                AliasBean.class);
        assertEquals("Foobar", bean.name);
        assertEquals(3, bean._a);
        assertEquals(37, bean._xyz);
    }

    @Test
    public void testAliasWithPolymorphic() throws Exception
    {
        PolyWrapperForAlias value = MAPPER.readValue(_smileDoc(aposToQuotes(
                "{'value': ['ab', {'nm' : 'Bob', 'A' : 17} ] }"
                )), PolyWrapperForAlias.class);
        assertNotNull(value.value);
        AliasBean bean = (AliasBean) value.value;
        assertEquals("Bob", bean.name);
        assertEquals(17, bean._a);
    }
}
