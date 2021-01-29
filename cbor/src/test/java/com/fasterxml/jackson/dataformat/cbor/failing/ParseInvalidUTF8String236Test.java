package com.fasterxml.jackson.dataformat.cbor.failing;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class ParseInvalidUTF8String236Test extends CBORTestBase
{
    // [dataformats-binary#236]: Ends with the first byte of alleged 2-byte
    // UTF-8 character; parser trying to access second byte beyond end.
    public void testArrayIssue236() throws Exception
    {
        final byte[] input = {0x66, (byte) 0xef, 0x7d, 0x7d, 0xa, 0x2d, (byte) 0xda};
        try (CBORParser p = cborParser(input)) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("foobar", p.getText());
            assertNull(p.nextToken());
        }
    }
}
