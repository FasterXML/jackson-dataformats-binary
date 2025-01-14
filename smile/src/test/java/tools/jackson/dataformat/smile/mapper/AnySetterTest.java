package tools.jackson.dataformat.smile.mapper;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.*;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for verifying that {@link JsonAnySetter} annotation
 * works as expected.
 */
public class AnySetterTest extends BaseTestForSmile
{
    static class MapImitator
    {
        HashMap<String,Object> _map;

        public MapImitator() {
            _map = new HashMap<String,Object>();
        }

        @JsonAnySetter
        void addEntry(String key, Object value)
        {
            _map.put(key, value);
        }
    }

    // for [databind#1376]
    static class MapImitatorDisabled extends MapImitator
    {
        @Override
        @JsonAnySetter(enabled=false)
        void addEntry(String key, Object value) {
            throw new RuntimeException("Should not get called");
        }
    }

    /**
     * Let's also verify that it is possible to define different
     * value: not often useful, but possible.
     */
    static class MapImitatorWithValue
    {
        HashMap<String,int[]> _map;

        public MapImitatorWithValue() {
            _map = new HashMap<String,int[]>();
        }

        @JsonAnySetter
        void addEntry(String key, int[] value)
        {
            _map.put(key, value);
        }
    }

    @JsonIgnoreProperties("dummy")
    static class Ignored
    {
        HashMap<String,Object> map = new HashMap<String,Object>();

        @JsonIgnore
        public String bogus;

        @JsonAnySetter
        void addEntry(String key, Object value)
        {
            map.put(key, value);
        }
    }

    static class Bean744
    {
        protected Map<String,Object> additionalProperties;

        @JsonAnySetter
        public void addAdditionalProperty(String key, Object value) {
            if (additionalProperties == null) additionalProperties = new HashMap<String, Object>();
            additionalProperties.put(key,value);
        }

        public void setAdditionalProperties(Map<String, Object> additionalProperties) {
            this.additionalProperties = additionalProperties;
        }

        @JsonAnyGetter
        public Map<String,Object> getAdditionalProperties() { return additionalProperties; }

        @JsonIgnore
        public String getName() {
           return (String) additionalProperties.get("name");
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    static abstract class Base { }

    static class Impl extends Base {
        public String value;

        public Impl() { }
        public Impl(String v) { value = v; }
    }

    static class PolyAnyBean
    {
        protected Map<String,Base> props = new HashMap<String,Base>();

        @JsonAnyGetter
        public Map<String,Base> props() {
            return props;
        }

        @JsonAnySetter
        public void prop(String name, Base value) {
            props.put(name, value);
        }
    }

    static class JsonAnySetterOnMap {
        public int id;

        @JsonAnySetter
        protected HashMap<String, String> other = new HashMap<String, String>();

        @JsonAnyGetter
        public Map<String, String> any() {
            return other;
        }
    }

    static class JsonAnySetterOnNullMap {
        public int id;

        @JsonAnySetter
        protected HashMap<String, String> other;

        @JsonAnyGetter
        public Map<String, String> any() {
            return other;
        }
    }

    static class MyGeneric<T>
    {
        private String staticallyMappedProperty;
        private Map<T, Integer> dynamicallyMappedProperties = new HashMap<T, Integer>();

        public String getStaticallyMappedProperty() {
            return staticallyMappedProperty;
        }

        @JsonAnySetter
        public void addDynamicallyMappedProperty(T key, int value) {
            dynamicallyMappedProperties.put(key, value);
        }

        public void setStaticallyMappedProperty(String staticallyMappedProperty) {
            this.staticallyMappedProperty = staticallyMappedProperty;
        }

        @JsonAnyGetter
        public Map<T, Integer> getDynamicallyMappedProperties() {
            return dynamicallyMappedProperties;
        }
    }

    static class MyWrapper
    {
        private MyGeneric<String> myStringGeneric;
        private MyGeneric<Integer> myIntegerGeneric;

        public MyGeneric<String> getMyStringGeneric() {
            return myStringGeneric;
        }

        public void setMyStringGeneric(MyGeneric<String> myStringGeneric) {
            this.myStringGeneric = myStringGeneric;
        }

        public MyGeneric<Integer> getMyIntegerGeneric() {
            return myIntegerGeneric;
        }

        public void setMyIntegerGeneric(MyGeneric<Integer> myIntegerGeneric) {
            this.myIntegerGeneric = myIntegerGeneric;
        }
    }

	/*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = smileMapper();

    @Test
    public void testSimpleMapImitation() throws Exception
    {
        MapImitator mapHolder = MAPPER.readValue(
            _smileDoc("{ \"a\" : 3, \"b\" : true, \"c\":[1,2,3] }"),
            MapImitator.class);
        Map<String,Object> result = mapHolder._map;
        assertEquals(3, result.size());
        assertEquals(Integer.valueOf(3), result.get("a"));
        assertEquals(Boolean.TRUE, result.get("b"));
        Object ob = result.get("c");
        assertTrue(ob instanceof List<?>);
        List<?> l = (List<?>)ob;
        assertEquals(3, l.size());
        assertEquals(Integer.valueOf(3), l.get(2));
    }

    @Test
    public void testAnySetterDisable() throws Exception
    {
        try {
            MAPPER.readerFor(MapImitatorDisabled.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(_smileDoc(aposToQuotes("{'value':3}")));
            fail("Should not pass");
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "Unrecognized property \"value\"");
        }
    }

    @Test
    public void testSimpleTyped() throws Exception
    {
        MapImitatorWithValue mapHolder = MAPPER.readValue(
        		_smileDoc("{ \"a\" : [ 3, -1 ], \"b\" : [ ] }"),
        		MapImitatorWithValue.class);
        Map<String,int[]> result = mapHolder._map;
        assertEquals(2, result.size());
        int[] value = (int[]) result.get("a");
        assertEquals(2, value.length);
        assertEquals(3, value[0]);
        assertEquals(-1, value[1]);

        value = (int[]) result.get("b");
        assertEquals(0, value.length);
    }

    @Test
    public void testIgnored() throws Exception
    {
        ObjectMapper mapper = smileMapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        _testIgnorals(mapper);
    }

    @Test
    public void testIgnoredPart2() throws Exception
    {
        ObjectMapper mapper = smileMapperBuilder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        _testIgnorals(mapper);
    }

    @Test
    public void testProblem744() throws Exception
    {
        Bean744 bean = MAPPER.readValue(_smileDoc("{\"name\":\"Bob\"}"),
                Bean744.class);
        assertNotNull(bean.additionalProperties);
        assertEquals(1, bean.additionalProperties.size());
        assertEquals("Bob", bean.additionalProperties.get("name"));
    }

    @Test
    public void testPolymorphic() throws Exception
    {
        PolyAnyBean input = new PolyAnyBean();
        input.props.put("a", new Impl("xyz"));

        byte[] doc = MAPPER.writeValueAsBytes(input);

//        System.err.println("JSON: "+json);

        PolyAnyBean result = MAPPER.readValue(doc, PolyAnyBean.class);
        assertEquals(1, result.props.size());
        Base ob = result.props.get("a");
        assertNotNull(ob);
        assertTrue(ob instanceof Impl);
        assertEquals("xyz", ((Impl) ob).value);
    }

    @Test
    public void testJsonAnySetterOnMap() throws Exception {
        JsonAnySetterOnMap result = MAPPER.readValue(_smileDoc("{\"id\":2,\"name\":\"Joe\", \"city\":\"New Jersey\"}"),
                JsonAnySetterOnMap.class);
        assertEquals(2, result.id);
        assertEquals("Joe", result.other.get("name"));
        assertEquals("New Jersey", result.other.get("city"));
    }

    @Test
    public void testJsonAnySetterOnNullMap() throws Exception {
        JsonAnySetterOnNullMap result = MAPPER.readValue(_smileDoc("{\"id\":2,\"name\":\"Joe\", \"city\":\"New Jersey\"}"),
                JsonAnySetterOnNullMap.class);
        assertEquals(2, result.id);
        // 01-Aug-2022, tatu: As per [databind#3559] should "just work"...
        assertNotNull(result.other);
        assertEquals("Joe", result.other.get("name"));
        assertEquals("New Jersey", result.other.get("city"));
    }

	// [databind#1035]
    @Test
    public void testGenericAnySetter() throws Exception
    {
        ObjectMapper mapper = smileMapper();

        Map<String, Integer> stringGenericMap = new HashMap<String, Integer>();
        stringGenericMap.put("testStringKey", 5);
        Map<Integer, Integer> integerGenericMap = new HashMap<Integer, Integer>();
        integerGenericMap.put(111, 6);

        MyWrapper deserialized = mapper.readValue(_smileDoc(aposToQuotes(
                "{'myStringGeneric':{'staticallyMappedProperty':'Test','testStringKey':5},'myIntegerGeneric':{'staticallyMappedProperty':'Test2','111':6}}"
                )), MyWrapper.class);
        MyGeneric<String> stringGeneric = deserialized.getMyStringGeneric();
        MyGeneric<Integer> integerGeneric = deserialized.getMyIntegerGeneric();

        assertNotNull(stringGeneric);
        assertEquals(stringGeneric.getStaticallyMappedProperty(), "Test");
        for(Map.Entry<String, Integer> entry : stringGeneric.getDynamicallyMappedProperties().entrySet()) {
            assertTrue(entry.getKey() instanceof String,
                    "A key in MyGeneric<String> is not an String.");
            assertTrue(entry.getValue() instanceof Integer,
                    "A value in MyGeneric<Integer> is not an Integer.");
        }
        assertEquals(stringGeneric.getDynamicallyMappedProperties(), stringGenericMap);

        assertNotNull(integerGeneric);
        assertEquals(integerGeneric.getStaticallyMappedProperty(), "Test2");
        for(Map.Entry<Integer, Integer> entry : integerGeneric.getDynamicallyMappedProperties().entrySet()) {
            Object key = entry.getKey();
            assertEquals(Integer.class, key.getClass(),
                    "A key in MyGeneric<Integer> is not an Integer.");
            Object value = entry.getValue();
            assertEquals(Integer.class, value.getClass(),
                    "A value in MyGeneric<Integer> is not an Integer.");
        }
        assertEquals(integerGeneric.getDynamicallyMappedProperties(), integerGenericMap);
    }

	/*
    /**********************************************************
    /* Private helper methods
    /**********************************************************
     */

    private void _testIgnorals(ObjectMapper mapper) throws Exception
    {
        Ignored bean = mapper.readValue(
                _smileDoc("{\"name\":\"Bob\", \"bogus\": [ 1, 2, 3], \"dummy\" : 13 }"),
                Ignored.class);
        // as of 2.0, @JsonIgnoreProperties does block; @JsonIgnore not
        assertNull(bean.map.get("dummy"));
        assertEquals("[1, 2, 3]", ""+bean.map.get("bogus"));
        assertEquals("Bob", bean.map.get("name"));
        assertEquals(2, bean.map.size());
    }
}
