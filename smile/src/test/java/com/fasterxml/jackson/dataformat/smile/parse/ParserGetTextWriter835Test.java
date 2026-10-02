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
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// [dataformats-binary#835]: blocking parser's getText(Writer) wrote
// stale text buffer contents for numeric tokens
public class ParserGetTextWriter835Test extends BaseTestForSmile
{
    private final SmileFactory F = smileFactory(false, true, false);

    @Test
    public void testGetTextWriterFromBytes() throws Exception
    {
        try (JsonParser p = F.createParser(_doc())) {
            _verify(p);
        }
    }

    @Test
    public void testGetTextWriterFromStream() throws Exception
    {
        try (JsonParser p = F.createParser(new ByteArrayInputStream(_doc()))) {
            _verify(p);
        }
    }

    @Test
    public void testGetTextWriterNonBlocking() throws Exception
    {
        byte[] doc = _doc();
        try (JsonParser p = F.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
            feeder.feedInput(doc, 0, doc.length);
            feeder.endOfInput();
            _verify(p);
        }
    }

    private byte[] _doc() throws Exception
    {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bo)) {
            g.writeStartObject();
            g.writeFieldName("values");
            g.writeStartArray();
            g.writeString("abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789");
            g.writeNumber(42);
            g.writeNumber(-1234567890123L);
            g.writeNumber(1.25f);
            g.writeNumber(1.25);
            g.writeNumber(new BigInteger("123456789012345678901234567890"));
            g.writeNumber(new BigDecimal("12345.678901234567890"));
            g.writeBinary(new byte[] { 1, 2, 3 });
            g.writeBoolean(true);
            g.writeNull();
            g.writeEndArray();
            g.writeEndObject();
        }
        return bo.toByteArray();
    }

    private void _verify(JsonParser p) throws Exception
    {
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertEquals("{", _getTextWriter(p));
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("values", _getTextWriter(p));
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789",
                _getTextWriter(p));
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
        // no textual representation for binary: nothing written, and
        // value must still be accessible afterwards
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
        StringWriter w = new StringWriter();
        assertEquals(0, p.getText(w));
        assertEquals("", w.toString());
        assertArrayEquals(new byte[] { 1, 2, 3 }, p.getBinaryValue());
        assertNull(p.getText());
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals("true", _getTextWriter(p));
        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals("null", _getTextWriter(p));
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // Verifies getText(Writer) matches getText(); called first so that
    // the token is still incomplete (not yet decoded)
    private String _getTextWriter(JsonParser p) throws Exception
    {
        StringWriter w = new StringWriter();
        int len = p.getText(w);
        String str = w.toString();
        assertEquals(str.length(), len);
        String text = p.getText();
        assertEquals((text == null) ? "" : text, str);
        return str;
    }
}
