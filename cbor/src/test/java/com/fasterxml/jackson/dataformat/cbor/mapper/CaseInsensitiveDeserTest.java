package com.fasterxml.jackson.dataformat.cbor.mapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.*;
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

    private final ObjectMapper INSENSITIVE_MAPPER = cborMapper();
    {
        INSENSITIVE_MAPPER.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
    }

    // [databind#566]
    public void testCaseInsensitiveDeserialization() throws Exception
    {
        final byte[] CBOR = cborDoc("{\"Value1\" : {\"nAme\" : \"fruit\", \"vALUe\" : \"apple\"}, \"valUE2\" : {\"NAME\" : \"color\", \"value\" : \"red\"}}");
        
        // first, verify default settings which do not accept improper case
        ObjectMapper mapper = sharedMapper();
        assertFalse(mapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES));
        try {
            mapper.readValue(CBOR, Issue476Bean.class);
            fail("Should not accept improper case properties by default");
        } catch (JsonProcessingException e) {
            verifyException(e, "Unrecognized field");
        }

        // Definitely not OK to enable dynamically - the BeanPropertyMap (which is the consumer of this particular feature) gets cached.
        ObjectReader r = INSENSITIVE_MAPPER.readerFor(Issue476Bean.class);
        Issue476Bean result = r.readValue(CBOR);
        assertEquals(result.value1.name, "fruit");
        assertEquals(result.value1.value, "apple");
    }

    // [databind#1036]
    public void testCaseInsensitive1036() throws Exception
    {
        final byte[] CBOR = cborDoc("{\"ErrorCode\":2,\"DebugMessage\":\"Signature not valid!\"}");
//        final String json = "{\"errorCode\":2,\"debugMessage\":\"Signature not valid!\"}";

        BaseResponse response = INSENSITIVE_MAPPER.readValue(CBOR, BaseResponse.class);
        assertEquals(2, response.errorCode);
        assertEquals("Signature not valid!", response.debugMessage);
    }

    // [databind#1438]
    public void testCreatorWithInsensitive() throws Exception
    {
        final byte[] CBOR = cborDoc(aposToQuotes("{'VALUE':3}"));
        InsensitiveCreator bean = INSENSITIVE_MAPPER.readValue(CBOR, InsensitiveCreator.class);
        assertEquals(3, bean.v);
    }
}
