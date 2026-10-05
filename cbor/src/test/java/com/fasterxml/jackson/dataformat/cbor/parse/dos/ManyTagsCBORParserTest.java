package com.fasterxml.jackson.dataformat.cbor.parse.dos;

import java.time.Duration;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify that a long run of tags preceding a value is handled in (amortized)
 * linear time: tag list must not grow by a fixed increment.
 */
public class ManyTagsCBORParserTest extends CBORTestBase
{
    private final CBORFactory CBOR_F = new CBORFactory();

    private final static int TAG_COUNT = 2_000_000;

    @Test
    public void testManyTagsBeforeValue() throws Exception
    {
        final byte[] doc = _manyTags(TAG_COUNT, (byte) 0x01); // int 1
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try (JsonParser p = CBOR_F.createParser(doc)) {
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(1, p.getIntValue());
                assertEquals(TAG_COUNT, ((CBORParser) p).getCurrentTags().size());
                assertNull(p.nextToken());
            }
        });
    }

    @Test
    public void testManyTagsBeforeFieldName() throws Exception
    {
        final byte[] tags = _manyTags(TAG_COUNT, (byte) 0x01);
        // { <tags> 1 : true }
        final byte[] doc = new byte[tags.length + 3];
        doc[0] = (byte) 0xBF; // indefinite-length map
        System.arraycopy(tags, 0, doc, 1, tags.length);
        doc[tags.length + 1] = (byte) 0xF5; // true
        doc[tags.length + 2] = (byte) 0xFF; // break
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try (JsonParser p = CBOR_F.createParser(doc)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertToken(JsonToken.FIELD_NAME, p.nextToken());
                assertEquals("1", p.currentName());
                assertToken(JsonToken.VALUE_TRUE, p.nextToken());
                assertToken(JsonToken.END_OBJECT, p.nextToken());
            }
        });
    }

    private static byte[] _manyTags(int count, byte value) {
        byte[] doc = new byte[count + 1];
        // tag 0 (0xC0), single byte each
        Arrays.fill(doc, 0, count, (byte) CBORConstants.PREFIX_TYPE_TAG);
        doc[count] = value;
        return doc;
    }
}
