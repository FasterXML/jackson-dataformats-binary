package com.fasterxml.jackson.dataformat.smile.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [dataformats-binary#835]: blocking parser's getText(Writer) wrote
// stale text buffer contents for numeric tokens
public class ParserGetTextWriter835Test extends BaseTestForSmile
{
    private final SmileFactory F = new SmileFactory();

    @Test
    public void testGetTextWriterForNumbersFromBytes() throws Exception
    {
        byte[] doc = _doc();
        try (JsonParser p = F.createParser(doc)) {
            _verify(p);
        }
    }

    @Test
    public void testGetTextWriterForNumbersFromStream() throws Exception
    {
        byte[] doc = _doc();
        try (JsonParser p = F.createParser(new ByteArrayInputStream(doc))) {
            _verify(p);
        }
    }

    private byte[] _doc() throws Exception
    {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bo)) {
            g.writeStartArray();
            g.writeString("abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789");
            g.writeNumber(42);
            g.writeNumber(-1234567890123L);
            g.writeNumber(1.25f);
            g.writeNumber(1.25);
            g.writeNumber(new BigInteger("123456789012345678901234567890"));
            g.writeNumber(new BigDecimal("12345.678901234567890"));
            g.writeBoolean(true);
            g.writeEndArray();
        }
        return bo.toByteArray();
    }

    private void _verify(JsonParser p) throws Exception
    {
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        _getTextWriter(p);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals("42", _getTextWriter(p));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals("-1234567890123", _getTextWriter(p));
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals("1.25", _getTextWriter(p));
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals("1.25", _getTextWriter(p));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals("123456789012345678901234567890", _getTextWriter(p));
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals("12345.678901234567890", _getTextWriter(p));
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        _getTextWriter(p);
        assertToken(JsonToken.END_ARRAY, p.nextToken());
    }

    // Verifies getText(Writer) matches getText(); called first so that
    // the token is still incomplete (not yet decoded)
    private String _getTextWriter(JsonParser p) throws Exception
    {
        StringWriter w = new StringWriter();
        int len = p.getText(w);
        String str = w.toString();
        assertEquals(str.length(), len);
        assertEquals(p.getText(), str);
        return str;
    }
}
