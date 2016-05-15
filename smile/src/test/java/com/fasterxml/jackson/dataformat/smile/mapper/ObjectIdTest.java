package com.fasterxml.jackson.dataformat.smile.mapper;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class ObjectIdTest extends BaseTestForSmile
{
    @JsonIdentityInfo(generator = ObjectIdGenerators.UUIDGenerator.class, property = "@id")
    public static class D {
        public D next;
    }

    // [smile#19]
    public void testObjectIdAsUUID() throws Exception
    {
        ObjectMapper mapper = smileMapper();
        D d = new D();
        d.next = d;

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        byte[] smile = mapper.writeValueAsBytes(d);

        D de = mapper.readValue(smile, D.class);
        assertNotNull(de);
        assertSame(de, de.next);
    }
}
