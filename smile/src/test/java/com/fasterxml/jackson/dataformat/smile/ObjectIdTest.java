package com.fasterxml.jackson.dataformat.smile;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;

public class ObjectIdTest extends SmileTestBase
{
    @JsonIdentityInfo(generator = ObjectIdGenerators.UUIDGenerator.class, property = "@id")
    public static class D {
        public D next;
    }

    // [Issue#19]
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
