package com.fasterxml.jackson.dataformat.cbor.parse;


import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParseLongAsciiTextTest extends CBORTestBase {


    @Test
    public void testLongNonChunkedAsciiText() throws IOException {
        CBORParser p = cborFactory().createParser(this.getClass().getResourceAsStream("/data/macbeth-snippet-non-chunked.cbor"));

        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        String expected = new String(readResource("/data/macbeth-snippet.txt"), "UTF-8");
        assertEquals(expected, p.getText());

    }

    @Test
    public void testLongChunkedAsciiText() throws IOException {
        CBORParser p = cborFactory().createParser(this.getClass().getResourceAsStream("/data/macbeth-snippet-chunked.cbor"));

        assertEquals(JsonToken.VALUE_STRING, p.nextToken());
        String expected = new String(readResource("/data/macbeth-snippet.txt"), StandardCharsets.UTF_8);
        assertEquals(expected, p.getText());
    }

}
