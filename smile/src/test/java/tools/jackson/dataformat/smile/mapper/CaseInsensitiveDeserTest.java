package tools.jackson.dataformat.smile.mapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.*;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.dataformat.smile.BaseTestForSmile;

public class CaseInsensitiveDeserTest extends BaseTestForSmile
{
    // [databind#1036]
    static class BaseResponse {
        public int errorCode;
        public String debugMessage;
    }

    static class Issue476Bean {
        public Issue476Type value1, value2;
    }
    static class Issue476Type {
        public String name, value;
    }

    // [databind#1438]
    static class InsensitiveCreator
    {
        int v;

        @JsonCreator
        public InsensitiveCreator(@JsonProperty("value") int v0) {
            v = v0;
        }
    }

    /*
    /********************************************************
    /* Test methods
    /********************************************************
     */

    private final ObjectMapper INSENSITIVE_MAPPER = smileMapperBuilder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build();

    // [databind#566]
    public void testCaseInsensitiveDeserialization() throws Exception
    {
        byte[] DOC = _smileDoc(aposToQuotes(
                "{'Value1' : {'nAme' : 'fruit', 'vALUe' : 'apple'}, 'valUE2' : {'NAME' : 'color', 'value' : 'red'}}"));

        // first, verify default settings which do not accept improper case
        ObjectMapper mapper = newSmileMapper();
        assertFalse(mapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES));
        try {
            mapper.readerFor(Issue476Bean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(DOC);
            fail("Should not accept improper case properties by default");
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "Unrecognized property ");
        }

        ObjectReader r = INSENSITIVE_MAPPER.readerFor(Issue476Bean.class);

        Issue476Bean result = r.readValue(DOC);
        assertEquals(result.value1.name, "fruit");
        assertEquals(result.value1.value, "apple");
        assertEquals(result.value2.name, "color");
        assertEquals(result.value2.value, "red");

        DOC = _smileDoc(aposToQuotes(
                "{'valuE1' : {'name' : 'fruit', 'Value' : 'pear'}, 'value2' : {'Name' : 'color', 'VALUE' : 'blue'}}"));
        result = r.readValue(DOC);
        assertEquals(result.value1.name, "fruit");
        assertEquals(result.value1.value, "pear");
        assertEquals(result.value2.name, "color");
        assertEquals(result.value2.value, "blue");
    }

    // [databind#1036]
    public void testCaseInsensitive1036() throws Exception
    {
        byte[] DOC = _smileDoc("{\"ErrorCode\":2,\"DebugMessage\":\"Signature not valid!\"}");

        BaseResponse response = INSENSITIVE_MAPPER.readValue(DOC, BaseResponse.class);
        assertEquals(2, response.errorCode);
        assertEquals("Signature not valid!", response.debugMessage);

        // but also test that other spellings work
        DOC = _smileDoc("{\"errorCode\":3,\"debugMessage\":\"Error 2812\"}");

        response = INSENSITIVE_MAPPER.readValue(DOC, BaseResponse.class);
        assertEquals(3, response.errorCode);
        assertEquals("Error 2812", response.debugMessage);

        DOC = _smileDoc("{\"errorCODE\":1,\"debugMessage\":\"Error 2812\"}");

        response = INSENSITIVE_MAPPER.readValue(DOC, BaseResponse.class);
        assertEquals(1, response.errorCode);
        assertEquals("Error 2812", response.debugMessage);
    }

    // [databind#1438]
    public void testCreatorWithInsensitive() throws Exception
    {
        final byte[] DOC = _smileDoc(aposToQuotes("{'VALUE':3}"));
        InsensitiveCreator bean = INSENSITIVE_MAPPER.readValue(DOC, InsensitiveCreator.class);
        assertEquals(3, bean.v);
    }
}
