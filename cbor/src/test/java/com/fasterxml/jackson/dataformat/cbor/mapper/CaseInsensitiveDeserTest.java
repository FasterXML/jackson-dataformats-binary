package com.fasterxml.jackson.dataformat.cbor.mapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.*;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class CaseInsensitiveDeserTest extends CBORTestBase
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

    private final ObjectMapper INSENSITIVE_MAPPER = cborMapperBuilder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build();

    // [databind#566]
    public void testCaseInsensitiveDeserialization() throws Exception
    {
        byte[] DOC = cborDoc(aposToQuotes(
                "{'Value1' : {'nAme' : 'fruit', 'vALUe' : 'apple'}, 'valUE2' : {'NAME' : 'color', 'value' : 'red'}}"));

        // first, verify default settings which do not accept improper case
        ObjectMapper mapper = sharedMapper();
        assertFalse(mapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES));
        try {
            mapper.readValue(DOC, Issue476Bean.class);
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

        DOC = cborDoc(aposToQuotes(
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
        byte[] DOC = cborDoc("{\"ErrorCode\":2,\"DebugMessage\":\"Signature not valid!\"}");

        BaseResponse response = INSENSITIVE_MAPPER.readValue(DOC, BaseResponse.class);
        assertEquals(2, response.errorCode);
        assertEquals("Signature not valid!", response.debugMessage);

        // but also test that other spellings work
        DOC = cborDoc("{\"errorCode\":3,\"debugMessage\":\"Error 2812\"}");

        response = INSENSITIVE_MAPPER.readValue(DOC, BaseResponse.class);
        assertEquals(3, response.errorCode);
        assertEquals("Error 2812", response.debugMessage);

        DOC = cborDoc("{\"errorCODE\":1,\"debugMessage\":\"Error 2812\"}");

        response = INSENSITIVE_MAPPER.readValue(DOC, BaseResponse.class);
        assertEquals(1, response.errorCode);
        assertEquals("Error 2812", response.debugMessage);
    }

    // [databind#1438]
    public void testCreatorWithInsensitive() throws Exception
    {
        final byte[] CBOR = cborDoc(aposToQuotes("{'VALUE':3}"));
        InsensitiveCreator bean = INSENSITIVE_MAPPER.readValue(CBOR, InsensitiveCreator.class);
        assertEquals(3, bean.v);
    }
}
