package com.fasterxml.jackson.dataformat.cbor.parse;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.dataformat.cbor.CBORConstants;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

/**
 * Unit tests geared at testing issues that were raised due to
 * inter-operability with other CBOR codec implementations
 */
public class ParserInteropTest extends CBORTestBase
{
    private final static byte[] SELF_DESC_PLUS_TRUE = new byte[] {
        (byte) 0xD9,
        (byte) 0xD9,
        (byte) 0xF7,
        CBORConstants.BYTE_TRUE
    };

    // for [cbor#5]; Perl CBOR::XS module uses binary encoding for
    // Map/Object keys; presumably in UTF-8.
    public void testBinaryEncodedKeys() throws Exception
    {
        // from equivalent of '{"query":{} }'
        final byte[] INPUT = { (byte) 0xa1, 0x45, 0x71, 0x75, 0x65, 0x72, 0x79, (byte) 0xa0 };
        JsonParser p = cborParser(INPUT);

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("query", p.currentName());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        assertNull(p.nextToken());
        p.close();
    }

    // for [Issue#6]: should be fine to have self-desc tag in general
    public void testSelfDescribeTagRead() throws Exception
    {
        CBORParser p = cborParser(SELF_DESC_PLUS_TRUE);
        
        assertEquals(-1, p.getCurrentTag());

        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(CBORConstants.TAG_ID_SELF_DESCRIBE, p.getCurrentTag());

        assertNull(p.nextToken());
        assertEquals(-1, p.getCurrentTag());

        p.close();
    }
}
