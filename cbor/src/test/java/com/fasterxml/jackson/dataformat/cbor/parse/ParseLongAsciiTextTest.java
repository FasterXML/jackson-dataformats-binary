package com.fasterxml.jackson.dataformat.cbor.parse;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParseLongAsciiTextTest extends CBORTestBase
{
    private final CBORFactory CBOR_F = cborFactory();

    @Test
    public void testLongNonChunkedAsciiText() throws Exception {
        // run several times to allow the internal buffers
        // to grow
        for (int x = 0; x < 3 ; x++) {
            try (CBORParser p = CBOR_F.createParser(this.getClass().getResourceAsStream("/data/macbeth-snippet-non-chunked.cbor"))) {
                assertEquals(JsonToken.VALUE_STRING, p.nextToken());
                String expected = new String(readResource("/data/macbeth-snippet.txt"), "UTF-8");
                assertEquals(expected, p.getText());
            }
        }
    }

    @Test
    public void testLongChunkedAsciiText() throws Exception {
        // run several times to allow the internal buffers
        // to grow
        for (int x = 0; x < 3 ; x++) {
            try (CBORParser p = CBOR_F.createParser(this.getClass().getResourceAsStream("/data/macbeth-snippet-chunked.cbor"))) {
                assertEquals(JsonToken.VALUE_STRING, p.nextToken());
                String expected = new String(readResource("/data/macbeth-snippet.txt"), StandardCharsets.UTF_8);
                assertEquals(expected, p.getText());
            }
        }
    }
}
