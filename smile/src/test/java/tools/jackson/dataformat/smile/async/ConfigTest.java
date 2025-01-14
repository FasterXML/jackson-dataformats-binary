package tools.jackson.dataformat.smile.async;

import java.io.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigTest extends AsyncTestBase
{
    private final SmileFactory DEFAULT_F = new SmileFactory();

    @Test
    public void testFactoryDefaults() throws IOException
    {
        assertTrue(DEFAULT_F.canParseAsync());
    }

    @Test
    public void testAsyncParerDefaults() throws IOException
    {
        byte[] data = _smileDoc("[ true, false ]", true);
        AsyncReaderWrapper r = asyncForBytes(_smileReader(), 100, data, 0);
        JsonParser p = r.parser();

        assertTrue(p.canParseAsync());
        assertNull(p.streamReadInputSource());
        assertEquals(-1, p.releaseBuffered(new StringWriter()));
        assertEquals(0, p.releaseBuffered(new ByteArrayOutputStream()));

        assertToken(JsonToken.START_ARRAY, r.nextToken());
        // two booleans, end array remain so:
        assertEquals(3, p.releaseBuffered(new ByteArrayOutputStream()));

        p.close();
    }
}
