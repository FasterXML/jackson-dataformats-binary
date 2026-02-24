package tools.jackson.dataformat.cbor.parse;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORParser;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParseLongAsciiTextTest extends CBORTestBase
{
    private final CBORFactory CBOR_F = cborFactory();

    @Test
    public void testLongNonChunkedAsciiText() throws Exception
    {
        // run several times to allow the internal buffers
        // to grow
        for (int x = 0; x < 3 ; x++) {
            try (JsonParser p = CBOR_F.createParser(ObjectReadContext.empty(),
                    this.getClass().getResourceAsStream("/data/macbeth-snippet-non-chunked.cbor"))) {
                assertEquals(JsonToken.VALUE_STRING, p.nextToken());
                String expected = new String(readResource("/data/macbeth-snippet.txt"), "UTF-8");
                assertEquals(expected, p.getString());
            }
        }
    }

    @Test
    public void testLongChunkedAsciiText() throws Exception
    {
        // run several times to allow the internal buffers
        // to grow
        for (int x = 0; x < 3 ; x++) {
            try (JsonParser p = CBOR_F.createParser(ObjectReadContext.empty(),
                    this.getClass().getResourceAsStream("/data/macbeth-snippet-chunked.cbor"))) {
                assertEquals(JsonToken.VALUE_STRING, p.nextToken());
                String expected = new String(readResource("/data/macbeth-snippet.txt"), StandardCharsets.UTF_8);
                assertEquals(expected, p.getString());
            }
        }
    }
}
