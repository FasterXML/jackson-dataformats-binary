package tools.jackson.dataformat.smile.mapper;

import com.fasterxml.jackson.annotation.*;
import tools.jackson.databind.*;
import tools.jackson.dataformat.smile.BaseTestForSmile;

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

        byte[] smile = mapper.writeValueAsBytes(d);

        D de = mapper.readValue(smile, D.class);
        assertNotNull(de);
        assertSame(de, de.next);
    }
}
