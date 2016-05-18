package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class WriteStringsTest extends ProtobufTestBase
{

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = new ObjectMapper(new ProtobufFactory());

    private final ProtobufSchema NAME_SCHEMA;
    { 
        try {
            NAME_SCHEMA = ProtobufSchemaLoader.std.parse(PROTOC_NAME);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void testSimpleShort() throws Exception
    {
        final ObjectWriter w = MAPPER.writer(NAME_SCHEMA);
        byte[] bytes = w.writeValueAsBytes(new Name("Bob", "Burger"));
        assertEquals(13, bytes.length);

        // at main level just seq of fields; first one 1 byte tag, 1 byte len, 3 chars -> 5
        // and second similarly 1 + 1 + 6 -> 8
        assertEquals(13, bytes.length);
        assertEquals(0x12, bytes[0]); // length-prefixed (2), field 2
        assertEquals(3, bytes[1]); // length for array
        assertEquals((byte) 'B', bytes[2]);
        assertEquals((byte) 'o', bytes[3]);
        assertEquals((byte) 'b', bytes[4]);
    
        assertEquals(0x3A, bytes[5]); // length-prefixed (2), field 7
        assertEquals(6, bytes[6]); // length for array
        assertEquals((byte) 'B', bytes[7]);
        assertEquals((byte) 'u', bytes[8]);
        assertEquals((byte) 'r', bytes[9]);
        assertEquals((byte) 'g', bytes[10]);
        assertEquals((byte) 'e', bytes[11]);
        assertEquals((byte) 'r', bytes[12]);
    }

    public void testSimpleLongAscii() throws Exception
    {
        _testSimpleLong(129, "Bob");
        _testSimpleLong(2007, "Bill");
        _testSimpleLong(9000, "Emily");
    }

    public void testSimpleLongTwoByteUTF8() throws Exception
    {
        _testSimpleLong(90, "\u00A8a\u00F3");
        _testSimpleLong(129, "\u00A8a\u00F3");
        _testSimpleLong(2007, "\u00E8\u00EC");
        _testSimpleLong(7000, "\u00A8xy");
    }

    public void testSimpleLongThreeByteUTF8() throws Exception
    {
        _testSimpleLong(90, "\u2009\u3333");
        _testSimpleLong(129, "\u2009\u3333");
        _testSimpleLong(2007, "abc\u3333");
        _testSimpleLong(5000, "\u2009b\u3333a");
    }
    
    private void _testSimpleLong(int clen, String part) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        do {
            sb.append(part);
        } while (sb.length() < clen);
        final String longName = sb.toString();
        _testSimpleLongMapper(longName);
        _testSimpleLongManual(longName);
    }

    private void _testSimpleLongManual(String longName) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator g = MAPPER.getFactory().createGenerator(bytes);
        g.enable(JsonGenerator.Feature.IGNORE_UNKNOWN);
        g.setSchema(NAME_SCHEMA);

        g.writeStartObject();
        g.writeStringField("first", null);
        g.writeFieldName("last");
        int nameLen = longName.length();
        char[] ch = new char[nameLen + 10];
        longName.getChars(0, nameLen, ch, 5);
        g.writeString(ch, 5, nameLen);
        g.writeEndObject();
        g.close();

        JsonParser p = MAPPER.getFactory().createParser(new ByteArrayInputStream(bytes.toByteArray()));
        p.setSchema(NAME_SCHEMA);
        
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());

        // Note: nulls are never explicitly written, but simple lead to omission of the field...
        assertEquals("last", p.getText());
        StringWriter w = new StringWriter();
        assertEquals(4, p.getText(w));
        assertEquals("last", w.toString());
        
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        ch = p.getTextCharacters();
        String str = new String(ch, p.getTextOffset(), p.getTextLength());
        assertEquals(longName, str);

        w = new StringWriter();
        assertEquals(longName.length(), p.getText(w));
        assertEquals(longName, w.toString());
        
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
    }
    
    private void _testSimpleLongMapper(String longName) throws Exception
    {
        final ObjectWriter w = MAPPER.writer(NAME_SCHEMA);
        final byte[] LONG_BYTES = longName.getBytes("UTF-8");
        
        final int longLen = LONG_BYTES.length;
        
        byte[] bytes = w.writeValueAsBytes(new Name("Bill", longName));
        // 4 or 5 bytes for fields (tag, length), 4 for first name, N for second
        int expLen = 8 + longLen;
        if (longLen > 127) {
            expLen += 1;
        }
        assertEquals(expLen, bytes.length);

        // at main level just seq of fields; first one 1 byte tag, 1 byte len, 3 chars -> 5
        // and second similarly 1 + 1 + 6 -> 8
        assertEquals(0x12, bytes[0]);
        assertEquals(4, bytes[1]); // length for array
        assertEquals((byte) 'B', bytes[2]);
        assertEquals((byte) 'i', bytes[3]);
        assertEquals((byte) 'l', bytes[4]);
        assertEquals((byte) 'l', bytes[5]);
        assertEquals(0x3A, bytes[6]); // length-prefixed (2), field 7
    
        int offset = 7;

        if (longLen <= 0x7F) {
            assertEquals((longLen & 0x7F), bytes[offset++] & 0xFF); // sign set for non-last length bytes
        } else {
            assertEquals(128 + (longLen & 0x7F), bytes[offset++] & 0xFF); // sign set for non-last length bytes
            assertEquals(longLen >> 7, bytes[offset++]); // no sign bit set
        }
        for (int i = 0; i < longLen; ++i) {
            assertEquals((byte) LONG_BYTES[i], bytes[offset+i]);
        }
    }
}
