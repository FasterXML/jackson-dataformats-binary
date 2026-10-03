package tools.jackson.dataformat.cbor.constraints;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.cbor.CBORConstants;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#823]: `maxStringLength` must be enforced for chunked
// (indefinite-length) text values regardless of accessor used
public class LongChunkedStringCBORReadTest extends CBORTestBase
{
    private final static int MAX_STRING_LEN = 10;

    private final CBORFactory FACTORY = CBORFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxStringLength(MAX_STRING_LEN)
                    .build())
            .build();

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
        final byte[] doc = _chunked(3, "abcdefghij");
        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(FACTORY, doc, throttled)) {
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                int len = p.getStringLength();
                fail("Should not pass, got length " + len);
            } catch (StreamConstraintsException e) {
                verifyException(e, "String value length");
            }
        }
    }

    @Test
    public void testChunkedViaString() throws Exception
    {
        final byte[] doc = _chunked(3, "abcdefghij");
        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(FACTORY, doc, throttled)) {
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                String str = p.getString();
                fail("Should not pass, got String of length " + str.length());
            } catch (StreamConstraintsException e) {
                verifyException(e, "String value length");
            }
        }
    }

    // Large chunked value that would fit in a (recycled) segment grown by
    // earlier, rejected, definite-length value
    @Test
    public void testLargeChunkedAfterBufferReuse() throws Exception
    {
        final String chunk = "a".repeat(200);
        for (int i = 0; i < 3; ++i) {
            try (JsonParser p = cborParser(FACTORY, _definite("b".repeat(300_000)))) {
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
        final byte[] doc = _chunked(2, "abcde");
        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(FACTORY, doc, throttled)) {
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals("abcdeabcde", new String(p.getStringCharacters(),
                        p.getStringOffset(), p.getStringLength()));
                assertNull(p.nextToken());
            }
        }
    }

    // Verifies with both byte[] and (throttled) InputStream input
    private void _verifyFailViaStringCharacters(byte[] doc)
    {
        for (boolean throttled : new boolean[] { false, true }) {
            try (JsonParser p = cborParser(FACTORY, doc, throttled)) {
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                p.getStringCharacters();
                fail("Should not pass, got " + p.getStringLength() + " chars");
            } catch (StreamConstraintsException e) {
                verifyException(e, "String value length");
            }
        }
    }

    private static byte[] _chunked(int chunks, String chunk)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(CBORConstants.BYTE_STRING_INDEFINITE);
        byte[] enc = _definite(chunk);
        for (int i = 0; i < chunks; ++i) {
            bytes.writeBytes(enc);
        }
        bytes.write(CBORConstants.INT_BREAK);
        return bytes.toByteArray();
    }

    private static byte[] _definite(String str)
    {
        byte[] utf8 = str.getBytes(StandardCharsets.UTF_8);
        int len = utf8.length;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (len < CBORConstants.SUFFIX_UINT8_ELEMENTS) {
            bytes.write(CBORConstants.PREFIX_TYPE_TEXT + len);
        } else if (len < 256) {
            bytes.write(CBORConstants.PREFIX_TYPE_TEXT + CBORConstants.SUFFIX_UINT8_ELEMENTS);
            bytes.write(len);
        } else if (len < 65536) {
            bytes.write(CBORConstants.PREFIX_TYPE_TEXT + CBORConstants.SUFFIX_UINT16_ELEMENTS);
            bytes.write(len >> 8);
            bytes.write(len);
        } else {
            bytes.write(CBORConstants.PREFIX_TYPE_TEXT + CBORConstants.SUFFIX_UINT32_ELEMENTS);
            bytes.write(len >>> 24);
            bytes.write(len >> 16);
            bytes.write(len >> 8);
            bytes.write(len);
        }
        bytes.writeBytes(utf8);
        return bytes.toByteArray();
    }
}
