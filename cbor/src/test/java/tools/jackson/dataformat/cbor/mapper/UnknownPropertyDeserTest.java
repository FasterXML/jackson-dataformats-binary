package tools.jackson.dataformat.cbor.mapper;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.core.*;
import tools.jackson.core.type.TypeReference;

import tools.jackson.databind.*;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for checking handling of unknown properties
 */
public class UnknownPropertyDeserTest
    extends CBORTestBase
{
    final static String JSON_UNKNOWN_FIELD = "{ \"a\" : 1, \"foo\" : [ 1, 2, 3], \"b\" : -1 }";

    /*
    /**********************************************************
    /* Helper classes
    /**********************************************************
     */

    final static class TestBean
    {
        String _unknown;

        int _a, _b;

        public TestBean() { }

        public void setA(int a) { _a = a; }
        public void setB(int b) { _b = b; }

        public void markUnknown(String unk) { _unknown = unk; }
    }

    /**
     * Simple {@link DeserializationProblemHandler} sub-class that
     * just marks unknown property/ies when encountered, along with
     * Json value of the property.
     */
    static class MyHandler
        extends DeserializationProblemHandler
    {
        @Override
        public boolean handleUnknownProperty(DeserializationContext ctxt,
                JsonParser jp, ValueDeserializer<?> deserializer,
                Object bean, String propertyName)
        {
            // very simple, just to verify that we do see correct token type
            ((TestBean) bean).markUnknown(propertyName+":"+jp.currentToken().toString());
            // Yup, we are good to go; must skip whatever value we'd have:
            jp.skipChildren();
            return true;
        }
    }

    @JsonIgnoreProperties({"b", "c"})
    static class IgnoreSome
    {
        public int a, b;
        private String c, d;

        public IgnoreSome() { }

        public String c() { return c; }
        public void setC(String value) { c = value; }
        public String d() { return d; }
        public void setD(String value) { d = value; }
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    static class IgnoreUnknown {
        public int a;
    }

    @SuppressWarnings("serial")
    @JsonIgnoreProperties({"a", "d"})
    static class IgnoreMap extends HashMap<String,Object> { }

    static class ImplicitIgnores {
        @JsonIgnore public int a;
        @JsonIgnore public void setB(int b) { }
        public int c;
    }

    // // Ignored as per [JACKSON-787]

    static class XYZWrapper1 {
        @JsonIgnoreProperties({"x"})
        public YZ value;
    }

    static class YZ {
        public int y, z;
    }

    static class XYZWrapper2 {
        @JsonIgnoreProperties({"y"})
        public X value;
    }

    @JsonIgnoreProperties({"z"})
    static class X {
        public int x;
    }

    static class MapWithoutX {
        @JsonIgnoreProperties("x")
        public Map<String,Integer> values;
    }

    // [databind#987]
    static class Bean987 {
        public String aProperty;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = cborMapper();

    /**
     * By default we should just get an exception if an unknown property
     * is encountered
     */
    @Test
    public void testUnknownHandlingDefault() throws Exception
    {
        try {
            MAPPER.readValue(cborDoc(JSON_UNKNOWN_FIELD), TestBean.class);
        } catch (UnrecognizedPropertyException jex) {
            verifyException(jex, "Unrecognized property \"foo\"");
        }
    }

    /**
     * Test that verifies that it is possible to ignore unknown properties using
     * {@link DeserializationProblemHandler}.
     */
    @Test
    public void testUnknownHandlingIgnoreWithHandler() throws Exception
    {
        ObjectMapper mapper = cborMapperBuilder()
                .addHandler(new MyHandler())
                .build();
        TestBean result = mapper.readValue(cborDoc(JSON_UNKNOWN_FIELD), TestBean.class);
        assertNotNull(result);
        assertEquals(1, result._a);
        assertEquals(-1, result._b);
        assertEquals("foo:START_ARRAY", result._unknown);
    }

    /**
     * Test that verifies that it is possible to ignore unknown properties using
     * {@link DeserializationProblemHandler} and an ObjectReader.
     */
    @Test
    public void testUnknownHandlingIgnoreWithHandlerAndObjectReader() throws Exception
    {
        ObjectMapper mapper = cborMapper();
        TestBean result = mapper.readerFor(TestBean.class)
        		.withHandler(new MyHandler()).readValue(cborDoc(JSON_UNKNOWN_FIELD));
        assertNotNull(result);
        assertEquals(1, result._a);
        assertEquals(-1, result._b);
        assertEquals("foo:START_ARRAY", result._unknown);
    }

    /**
     * Test for checking that it is also possible to simply suppress
     * error reporting for unknown properties.
     */
    @Test
    public void testUnknownHandlingIgnoreWithFeature() throws Exception
    {
        ObjectMapper mapper = cborMapperBuilder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
        TestBean result = mapper.readValue(cborDoc(JSON_UNKNOWN_FIELD), TestBean.class);
        assertNotNull(result);
        assertEquals(1, result._a);
        assertNull(result._unknown);
        assertEquals(-1, result._b);
    }

    @Test
    public void testWithClassIgnore() throws Exception
    {
        IgnoreSome result = MAPPER.readValue(
        		cborDoc("{ \"a\":1,\"b\":2,\"c\":\"x\",\"d\":\"y\"}"),
                IgnoreSome.class);
        // first: should deserialize 2 of properties normally
        assertEquals(1, result.a);
        assertEquals("y", result.d());
        // and not take other 2
        assertEquals(0, result.b);
        assertNull(result.c());
    }

    @Test
    public void testClassIgnoreWithMap() throws Exception
    {
        // Let's actually use incompatible types for "a" and "d"; should not matter when ignored
        IgnoreMap result = MAPPER.readValue(
            cborDoc("{ \"a\":[ 1],\n"
                +"\"b\":2,\n"
                +"\"c\": \"x\",\n"
                +"\"d\":false }"), IgnoreMap.class);
        assertEquals(2, result.size());
        Object ob = result.get("b");
        assertEquals(Integer.class, ob.getClass());
        assertEquals(Integer.valueOf(2), ob);
        assertEquals("x", result.get("c"));
        assertFalse(result.containsKey("a"));
        assertFalse(result.containsKey("d"));
    }

    @Test
    public void testClassWithIgnoreUnknown() throws Exception
    {
        IgnoreUnknown result = MAPPER.readValue(
        		cborDoc("{\"b\":3,\"c\":[1,2],\"x\":{ },\"a\":-3}"),
        		IgnoreUnknown.class);
        assertEquals(-3, result.a);
    }

    /**
     * Test that verifies that use of {@link JsonIgnore} will add implicit
     * skipping of matching properties.
     */
    @Test
    public void testClassWithUnknownAndIgnore() throws Exception
    {
    	byte[] doc = cborDoc("{\"a\":1,\"b\":2,\"c\":3 }");
		// should be ok: "a" and "b" ignored, "c" mapped:
        ImplicitIgnores result = MAPPER.readValue(doc,
        		ImplicitIgnores.class);
        assertEquals(3, result.c);

        // but "d" is not defined, so should still error
        doc = cborDoc("{\"a\":1,\"b\":2,\"c\":3,\"fff\":4 }");
        try {
            MAPPER.readValue(doc, ImplicitIgnores.class);
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "Unrecognized property \"fff\"");
        }
    }

    @Test
    public void testPropertyIgnoral() throws Exception
    {
        XYZWrapper1 result = MAPPER.readValue(
        		cborDoc("{\"value\":{\"y\":2,\"x\":1,\"z\":3}}"),
        		XYZWrapper1.class);
        assertEquals(2, result.value.y);
        assertEquals(3, result.value.z);
    }

    @Test
    public void testPropertyIgnoralWithClass() throws Exception
    {
        XYZWrapper2 result = MAPPER.readValue(cborDoc("{\"value\":{\"y\":2,\"x\":1,\"z\":3}}"),
                XYZWrapper2.class);
        assertEquals(1, result.value.x);
    }

    @Test
    public void testPropertyIgnoralForMap() throws Exception
    {
        MapWithoutX result = MAPPER.readValue(cborDoc("{\"values\":{\"x\":1,\"y\":2}}"),
        		MapWithoutX.class);
        assertNotNull(result.values);
        assertEquals(1, result.values.size());
        assertEquals(Integer.valueOf(2), result.values.get("y"));
    }

    @Test
    public void testIssue987() throws Exception
    {
        ObjectMapper mapper = cborMapperBuilder()
                .addHandler(new DeserializationProblemHandler() {
                @Override
                public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                        ValueDeserializer<?> deserializer, Object beanOrClass, String propertyName)
                {
                    p.skipChildren();
                    return true;
                }
                })
                .build();

        byte[] input = cborDoc("[{\"aProperty\":\"x\",\"unknown\":{\"unknown\":{}}}]");
        List<Bean987> deserializedList = mapper.readValue(input,
                new TypeReference<List<Bean987>>() { });
        assertEquals(1, deserializedList.size());
    }
}
