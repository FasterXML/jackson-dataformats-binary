package tools.jackson.dataformat.cbor.constraints;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#823]: `maxStringLength` must be enforced for chunked
// (indefinite-length) text values regardless of accessor used
public class LongChunkedStringCBORReadTest extends CBORTestBase
{
    private final static int MAX_STRING_LEN = 10;

    private final CBORMapper MAPPER = new CBORMapper(CBORFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxStringLength(MAX_STRING_LEN)
                    .build())
            .build());

    @Test
    public void testChunkedAsciiViaStringCharacters() throws Exception
    {
        _verifyFailViaStringCharacters(_chunked(3, "abcdefghij"));
    }

    @Test
    public void testChunkedNonAsciiViaStringCharacters() throws Exception
    {
        _verifyFailViaStringCharacters(_chunked(3, "ééééé"));
    }

    @Test
    public void testChunkedViaStringLength() throws Exception
    {
        try (JsonParser p = MAPPER.createParser(_chunked(3, "abcdefghij"))) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            int len = p.getStringLength();
            fail("Should not pass, got length " + len);
        } catch (StreamConstraintsException e) {
            verifyException(e, "String value length");
        }
    }

    @Test
    public void testChunkedViaString() throws Exception
    {
        try (JsonParser p = MAPPER.createParser(_chunked(3, "abcdefghij"))) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            String str = p.getString();
            fail("Should not pass, got String of length " + str.length());
        } catch (StreamConstraintsException e) {
            verifyException(e, "String value length");
        }
    }

    // Large chunked value that would fit in a (recycled) segment grown by
    // earlier, rejected, definite-length value
    @Test
    public void testLargeChunkedAfterBufferReuse() throws Exception
    {
        final String chunk = _repeat('a', 200);
        for (int i = 0; i < 3; ++i) {
            try (JsonParser p = MAPPER.createParser(_definite(_repeat('b', 300_000)))) {
                p.nextToken();
                p.getString();
                fail("Should not pass");
            } catch (StreamConstraintsException e) {
                verifyException(e, "String value length");
            }
        }
        _verifyFailViaStringCharacters(_chunked(1000, chunk));
    }

    @Test
    public void testChunkedWithinLimit() throws Exception
    {
        try (JsonParser p = MAPPER.createParser(_chunked(2, "abcde"))) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("abcdeabcde", new String(p.getStringCharacters(),
                    p.getStringOffset(), p.getStringLength()));
            assertNull(p.nextToken());
        }
    }

    private void _verifyFailViaStringCharacters(byte[] doc) throws Exception
    {
        try (JsonParser p = MAPPER.createParser(doc)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getStringCharacters();
            fail("Should not pass, got " + p.getStringLength() + " chars");
        } catch (StreamConstraintsException e) {
            verifyException(e, "String value length");
        }
    }

    private static byte[] _chunked(int chunks, String chunk) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0x7F); // indefinite-length text
        byte[] enc = _definite(chunk);
        for (int i = 0; i < chunks; ++i) {
            bytes.write(enc);
        }
        bytes.write(0xFF); // break
        return bytes.toByteArray();
    }

    private static byte[] _definite(String str) throws Exception
    {
        byte[] utf8 = str.getBytes("UTF-8");
        int len = utf8.length;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (len < 24) {
            bytes.write(0x60 | len);
        } else if (len < 256) {
            bytes.write(0x78);
            bytes.write(len);
        } else if (len < 65536) {
            bytes.write(0x79);
            bytes.write(len >> 8);
            bytes.write(len);
        } else {
            bytes.write(0x7A);
            bytes.write(len >>> 24);
            bytes.write(len >> 16);
            bytes.write(len >> 8);
            bytes.write(len);
        }
        bytes.write(utf8);
        return bytes.toByteArray();
    }

    private static String _repeat(char c, int count)
    {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; ++i) {
            sb.append(c);
        }
        return sb.toString();
    }
}
