package tools.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;

import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileWriteFeature;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that {@code writeString(char[],int,int)} tracks shared String
 * values exactly the way decoder does: only values written as "short" (at most
 * 64 byte) Strings may be added, or back-reference indexes get out of sync.
 */
public class SharedStringValuesCharArrayTest extends BaseTestForSmile
{
    private final SmileFactory SHARED_F = SmileFactory.builder()
            .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
            .build();

    @Test
    public void testShortAsciiValues() throws Exception {
        _verifyRoundTrip("abc", "def", "abc", "def", "abc");
    }

    @Test
    public void testShortUnicodeValues() throws Exception {
        _verifyRoundTrip("föö", "bär", "föö", "bär");
    }

    // Value that fits in shared-value length limit as chars, but not as bytes:
    // written as "long" Unicode String and hence NOT shareable
    @Test
    public void testUnicodeExpandingPastShortLimit() throws Exception {
        _verifyRoundTrip(_repeat('é', 40), "abc", "abc");
    }

    // Lengths around "short String" (64 bytes) and shared-value (65 bytes) limits
    @Test
    public void testAsciiLengthLimits() throws Exception {
        for (int len : new int[] { 63, 64, 65, 66 }) {
            String value = _repeat('a', len);
            _verifyRoundTrip(value, "abc", value, "abc");
        }
    }

    private String _repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; ++i) {
            sb.append(c);
        }
        return sb.toString();
    }

    private void _verifyRoundTrip(String... values) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator g = SHARED_F.createGenerator(ObjectWriteContext.empty(), out)) {
            g.writeStartArray();
            for (String value : values) {
                char[] ch = value.toCharArray();
                // offset intentionally non-zero for some, to verify offset handling
                char[] buffer = new char[ch.length + 3];
                System.arraycopy(ch, 0, buffer, 3, ch.length);
                g.writeString(buffer, 3, ch.length);
            }
            g.writeEndArray();
        }
        byte[] doc = out.toByteArray();

        try (JsonParser p = SHARED_F.createParser(ObjectReadContext.empty(), doc)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            for (String value : values) {
                assertToken(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(value, p.getString());
            }
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
        }
    }
}
