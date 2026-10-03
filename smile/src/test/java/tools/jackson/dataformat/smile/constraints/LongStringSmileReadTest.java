package tools.jackson.dataformat.smile.constraints;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.async.AsyncReaderWrapper;
import tools.jackson.dataformat.smile.async.AsyncTestBase;
import tools.jackson.dataformat.smile.testutil.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#824]: `maxStringLength` must be enforced for long
// (over 64 bytes) String values regardless of accessor used, and not only
// when value does not fit in a single text buffer segment
public class LongStringSmileReadTest extends AsyncTestBase
{
    private final static int MAX_STRING_LEN = 10;

    private final SmileMapper MAPPER_VANILLA = new SmileMapper();

    private final SmileMapper MAPPER_CONSTRAINED = new SmileMapper(
            SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(MAX_STRING_LEN)
                        .build())
                .build());

    // Long ASCII and long Unicode values, both still short enough to fit in
    // the initial text buffer segment
    private final static String[] LONG_VALUES = new String[] {
            "a".repeat(100),
            "é".repeat(50),
            "abcé".repeat(25)
    };

    private final static int[] BYTES_PER_FEED = new int[] { 1, 7, 1000 };

    @Test
    public void testLongValueViaStringCharactersBlocking() throws Exception
    {
        for (String value : LONG_VALUES) {
            for (int mode = 0; mode < 3; ++mode) {
                _verifyFails(_doc(value), mode, JsonParser::getStringCharacters);
            }
        }
    }

    @Test
    public void testLongValueViaStringLengthBlocking() throws Exception
    {
        for (String value : LONG_VALUES) {
            for (int mode = 0; mode < 3; ++mode) {
                _verifyFails(_doc(value), mode, JsonParser::getStringLength);
            }
        }
    }

    @Test
    public void testLongValueViaStringBlocking() throws Exception
    {
        for (String value : LONG_VALUES) {
            for (int mode = 0; mode < 3; ++mode) {
                _verifyFails(_doc(value), mode, JsonParser::getString);
            }
        }
    }

    @Test
    public void testLongValueViaStringWriterBlocking() throws Exception
    {
        for (String value : LONG_VALUES) {
            for (int mode = 0; mode < 3; ++mode) {
                _verifyFails(_doc(value), mode, p -> p.getString(new StringWriter()));
            }
        }
    }

    @Test
    public void testLongValueViaStringCharactersAsync() throws Exception
    {
        for (String value : LONG_VALUES) {
            for (int bytesPerFeed : BYTES_PER_FEED) {
                _verifyFailsAsync(_doc(value), bytesPerFeed, JsonParser::getStringCharacters);
            }
        }
    }

    @Test
    public void testLongValueViaStringLengthAsync() throws Exception
    {
        for (String value : LONG_VALUES) {
            for (int bytesPerFeed : BYTES_PER_FEED) {
                _verifyFailsAsync(_doc(value), bytesPerFeed, JsonParser::getStringLength);
            }
        }
    }

    @Test
    public void testLongValueViaStringAsync() throws Exception
    {
        for (String value : LONG_VALUES) {
            for (int bytesPerFeed : BYTES_PER_FEED) {
                _verifyFailsAsync(_doc(value), bytesPerFeed, JsonParser::getString);
            }
        }
    }

    @Test
    public void testLongValueViaStringWriterAsync() throws Exception
    {
        for (String value : LONG_VALUES) {
            for (int bytesPerFeed : BYTES_PER_FEED) {
                _verifyFailsAsync(_doc(value), bytesPerFeed, p -> p.getString(new StringWriter()));
            }
        }
    }

    // If caller catches failure for too-long value and continues, parser must
    // continue with the next value (and not with rest of rejected one)
    @Test
    public void testContinueAfterLongValueAsync() throws Exception
    {
        for (String value : LONG_VALUES) {
            final byte[] doc = MAPPER_VANILLA.writeValueAsBytes(new String[] { value, "ok" });
            for (int bytesPerFeed : BYTES_PER_FEED) {
                AsyncReaderWrapper r = asyncForBytes(MAPPER_CONSTRAINED, bytesPerFeed, doc, 0);
                assertToken(JsonToken.START_ARRAY, r.nextToken());
                try {
                    JsonToken t = r.nextToken();
                    fail("Should not pass (bytesPerFeed "+bytesPerFeed+"), got: "+t);
                } catch (StreamConstraintsException e) {
                    verifyException(e, "String value length");
                }
                assertToken(JsonToken.VALUE_STRING, r.nextToken());
                assertEquals("ok", r.currentText());
                assertToken(JsonToken.END_ARRAY, r.nextToken());
                assertNull(r.nextToken());
                r.close();
            }
        }
    }

    // Values within limit must still be accepted
    @Test
    public void testValueWithinLimit() throws Exception
    {
        final String value = "abcdeé";
        final byte[] doc = _doc(value);
        for (int mode = 0; mode < 3; ++mode) {
            try (JsonParser p = _parser(doc, mode)) {
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(value, new String(p.getStringCharacters(),
                        p.getStringOffset(), p.getStringLength()));
                assertNull(p.nextToken());
            }
        }
        for (int bytesPerFeed : BYTES_PER_FEED) {
            AsyncReaderWrapper r = asyncForBytes(MAPPER_CONSTRAINED, bytesPerFeed, doc, 0);
            assertToken(JsonToken.VALUE_STRING, r.nextToken());
            JsonParser p = r.parser();
            assertEquals(value, new String(p.getStringCharacters(),
                    p.getStringOffset(), p.getStringLength()));
            assertNull(r.nextToken());
            r.close();
        }
    }

    // mode: 0 -> byte[], 1 -> InputStream, 2 -> throttled InputStream
    private JsonParser _parser(byte[] doc, int mode)
    {
        switch (mode) {
        case 0:
            return MAPPER_CONSTRAINED.createParser(doc);
        case 1:
            return MAPPER_CONSTRAINED.createParser(new ByteArrayInputStream(doc));
        default:
            return MAPPER_CONSTRAINED.createParser(new ThrottledInputStream(doc, 1));
        }
    }

    private void _verifyFails(byte[] doc, int mode, Function<JsonParser, Object> accessor)
        throws Exception
    {
        try (JsonParser p = _parser(doc, mode)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            Object result = accessor.apply(p);
            fail("Should not pass (mode "+mode+"), got: "+_desc(result));
        } catch (StreamConstraintsException e) {
            verifyException(e, "String value length");
        }
    }

    private void _verifyFailsAsync(byte[] doc, int bytesPerFeed,
            Function<JsonParser, Object> accessor)
        throws Exception
    {
        AsyncReaderWrapper r = asyncForBytes(MAPPER_CONSTRAINED, bytesPerFeed, doc, 0);
        try {
            assertToken(JsonToken.VALUE_STRING, r.nextToken());
            Object result = accessor.apply(r.parser());
            fail("Should not pass (bytesPerFeed "+bytesPerFeed+"), got: "+_desc(result));
        } catch (StreamConstraintsException e) {
            verifyException(e, "String value length");
        } finally {
            r.close();
        }
    }

    private static String _desc(Object result) {
        if (result instanceof char[]) {
            return "char["+((char[]) result).length+"]";
        }
        return String.valueOf(result);
    }

    private byte[] _doc(String value) throws Exception {
        return MAPPER_VANILLA.writeValueAsBytes(value);
    }
}
