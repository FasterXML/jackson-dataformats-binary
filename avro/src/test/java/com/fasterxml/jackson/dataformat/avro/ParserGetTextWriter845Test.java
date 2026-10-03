package com.fasterxml.jackson.dataformat.avro;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#845]: getText(Writer) wrote wrong text for numeric tokens
// and failed with NPE for binary values; Apache-backed parser's getText()
// returned stale String value for non-String scalars
public class ParserGetTextWriter845Test extends AvroTestBase
{
    // Generated schema orders fields alphabetically
    public static class Bean {
        public String a = "stale-text-from-previous-string";
        public byte[] b = new byte[] { 1, 2, 3 };
        public double d = 1.25;
        public boolean e = true;
        public float f = 1.25f;
        public int i = 42;
        public long l = -1234567890123L;
        public String s = "abcdefghijklmnopqrstuvwxyz0123456789";
    }

    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testGetTextWriterJacksonImpl() throws Exception
    {
        _testGetTextWriter(new AvroFactory());
    }

    @Test
    public void testGetTextWriterApacheImpl() throws Exception
    {
        _testGetTextWriter(new ApacheAvroFactory());
    }

    private void _testGetTextWriter(AvroFactory f) throws Exception
    {
        AvroSchema schema = MAPPER.schemaFor(Bean.class);
        byte[] doc = MAPPER.writer(schema).writeValueAsBytes(new Bean());

        try (JsonParser p = f.createParser(doc)) {
            p.setSchema(schema);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("{", _getTextWriter(p));
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("a", _getTextWriter(p));
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("stale-text-from-previous-string", _getTextWriter(p));

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("b", _getTextWriter(p));
            // no textual representation for binary: nothing written, and
            // value must still be accessible afterwards
            assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.nextToken());
            StringWriter w = new StringWriter();
            assertEquals(0, p.getText(w));
            assertEquals("", w.toString());
            assertNull(p.getText());
            assertArrayEquals(new byte[] { 1, 2, 3 }, p.getBinaryValue());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("d", _getTextWriter(p));
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals("1.25", _getTextWriter(p));

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("e", _getTextWriter(p));
            assertToken(JsonToken.VALUE_TRUE, p.nextToken());
            assertEquals("true", _getTextWriter(p));

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("f", _getTextWriter(p));
            assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
            assertEquals("1.25", _getTextWriter(p));

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("i", _getTextWriter(p));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals("42", _getTextWriter(p));

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("l", _getTextWriter(p));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals("-1234567890123", _getTextWriter(p));

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("s", _getTextWriter(p));
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("abcdefghijklmnopqrstuvwxyz0123456789", _getTextWriter(p));

            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertEquals("}", _getTextWriter(p));
            assertNull(p.nextToken());
        }
    }

    // Verifies getText(Writer) matches getText()
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
