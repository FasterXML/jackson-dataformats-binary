package tools.jackson.dataformat.cbor.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// [dataformats-binary#844]: getString(Writer) wrote stale text buffer contents
// for numeric tokens, and failed with NPE for binary values
public class ParserGetTextWriter844Test extends CBORTestBase
{
    private final static String LONG_TEXT =
            "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789";

    @Test
    public void testGetTextWriterFromBytes() throws Exception
    {
        try (JsonParser p = cborParser(_doc(false))) {
            _verify(p);
        }
    }

    @Test
    public void testGetTextWriterFromStream() throws Exception
    {
        try (JsonParser p = cborParser(new ByteArrayInputStream(_doc(false)))) {
            _verify(p);
        }
    }

    // String references (shared Strings) are handled separately
    @Test
    public void testGetTextWriterWithStringRefs() throws Exception
    {
        try (JsonParser p = cborParser(_doc(true))) {
            _verify(p);
        }
    }

    private byte[] _doc(boolean stringRefs) throws Exception
    {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (JsonGenerator g = stringRefs ? stringrefCborGenerator(bo) : cborGenerator(bo)) {
            g.writeStartObject();
            g.writeName("values");
            g.writeStartArray();
            g.writeString(LONG_TEXT);
            // second time as a back-reference, if String refs enabled
            g.writeString(LONG_TEXT);
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
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("values", _getTextWriter(p));
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(LONG_TEXT, _getTextWriter(p));
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(LONG_TEXT, _getTextWriter(p));
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
        assertEquals(0, p.getString(w));
        assertEquals("", w.toString());
        assertNull(p.getString());
        assertNull(p.getStringCharacters());
        assertEquals(0, p.getStringLength());
        assertArrayEquals(new byte[] { 1, 2, 3 }, p.getBinaryValue());
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals("true", _getTextWriter(p));
        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertEquals("null", _getTextWriter(p));
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    // Verifies getString(Writer) matches getString(); called first so that
    // the token is still incomplete (not yet decoded)
    private String _getTextWriter(JsonParser p) throws Exception
    {
        StringWriter w = new StringWriter();
        int len = p.getString(w);
        String str = w.toString();
        assertEquals(str.length(), len);
        String text = p.getString();
        assertEquals((text == null) ? "" : text, str);
        return str;
    }
}
