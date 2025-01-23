package tools.jackson.dataformat.smile.filter;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.dataformat.smile.*;
import tools.jackson.dataformat.smile.testutil.PrefixInputDecorator;
import tools.jackson.dataformat.smile.testutil.PrefixOutputDecorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StreamingDecoratorsTest extends BaseTestForSmile
{
    @Test
    public void testInputDecorators() throws Exception
    {
        final byte[] DOC = _smileDoc("42   37");
        final SmileFactory streamF = smileFactoryBuilder(false,  true,  false)
                .inputDecorator(new PrefixInputDecorator(DOC))
                .build();
        final SmileMapper mapper = SmileMapper.builder(streamF).build();
        JsonParser p = mapper.createParser(new byte[0], 0, 0);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(42, p.getIntValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(37, p.getIntValue());
        assertNull(p.nextToken());
        p.close();
    }

    @Test
    public void testOutputDecorators() throws Exception
    {
        final byte[] DOC = _smileDoc(" 137");
        // important! Do not add document header for this test
        final SmileFactory streamF = smileFactoryBuilder(false,  false,  false)
                .outputDecorator(new PrefixOutputDecorator(DOC))
                .build();
        final SmileMapper mapper = SmileMapper.builder(streamF).build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        JsonGenerator g = mapper.createGenerator(bytes);
        g.writeString("foo");
        g.close();

        JsonParser p = mapper.createParser(bytes.toByteArray());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(137, p.getIntValue());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("foo", p.getString());
        assertNull(p.nextToken());
        p.close();
    }
}
