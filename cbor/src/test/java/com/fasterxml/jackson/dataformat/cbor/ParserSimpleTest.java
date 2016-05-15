package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.JsonParser.NumberType;

/**
 * Unit tests for simple value types.
 */
public class ParserSimpleTest extends CBORTestBase
{
    /**
     * Test for verifying handling of 'true', 'false' and 'null' literals
     */
    public void testSimpleLiterals() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(out);
        gen.writeBoolean(true);
        assertEquals("/", gen.getOutputContext().toString());
        gen.close();
        JsonParser p = cborParser(out);
        assertEquals(JsonToken.VALUE_TRUE, p.nextToken());
        assertNull(p.nextToken());
        p.close();

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeBoolean(false);
        gen.close();
        p = cborParser(out);
        assertEquals(JsonToken.VALUE_FALSE, p.nextToken());
        assertEquals("/", p.getParsingContext().toString());
        
        assertNull(p.nextToken());
        p.close();

        out = new ByteArrayOutputStream();
        gen = cborGenerator(out);
        gen.writeNull();
        gen.close();
        p = cborParser(out);
        assertEquals(JsonToken.VALUE_NULL, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }
    
    public void testIntValues() throws Exception
    {
        // first, single-byte
        CBORFactory f = cborFactory();
        // single byte
        _verifyInt(f, 13);
        _verifyInt(f, -19);
        // two bytes
        _verifyInt(f, 255);
        _verifyInt(f, -127);
        // three
        _verifyInt(f, 256);
        _verifyInt(f, 0xFFFF);
        _verifyInt(f, -300);
        _verifyInt(f, -0xFFFF);
        // and all 4 bytes
        _verifyInt(f, 0x7FFFFFFF);
        _verifyInt(f, 0x70000000 << 1);
    }

    private void _verifyInt(CBORFactory f, int value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(value, p.getIntValue());
        assertEquals((double) value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }

    public void testLongValues() throws Exception
    {
        CBORFactory f = cborFactory();
        _verifyLong(f, 1L + Integer.MAX_VALUE);
        _verifyLong(f, -1L + Integer.MIN_VALUE);
    }

    private void _verifyLong(CBORFactory f, long value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber(value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(value, p.getLongValue());
        assertEquals(NumberType.LONG, p.getNumberType());
        assertEquals((double) value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }
    
    public void testFloatValues() throws Exception
    {
        // first, single-byte
        CBORFactory f = cborFactory();
        // single byte
        _verifyFloat(f, 0.25);
        _verifyFloat(f, 20.5);

        // But then, oddity: 16-bit mini-float
        // Examples from [https://en.wikipedia.org/wiki/Half_precision_floating-point_format]
        _verifyHalfFloat(f, 0, 0.0);
        _verifyHalfFloat(f, 0x3C00, 1.0);
        _verifyHalfFloat(f, 0xC000, -2.0);
        _verifyHalfFloat(f, 0x7BFF, 65504.0);
        _verifyHalfFloat(f, 0x7C00, Double.POSITIVE_INFINITY);
        _verifyHalfFloat(f, 0xFC00, Double.NEGATIVE_INFINITY);

        // ... can add more, but need bit looser comparison if so
    }

    private void _verifyFloat(CBORFactory f, double value) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = cborGenerator(f, out);
        gen.writeNumber((float) value);
        gen.close();
        JsonParser p = cborParser(f, out.toByteArray());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        if (NumberType.FLOAT != p.getNumberType()) {
            fail("Expected `NumberType.FLOAT`, got "+p.getNumberType()+": "+p.getText());
        }
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }

    private void _verifyHalfFloat(JsonFactory f, int i16, double value) throws IOException
    {
        JsonParser p = f.createParser(new byte[] {
                (byte) (CBORConstants.PREFIX_TYPE_MISC + 25),
                (byte) (i16 >> 8), (byte) i16
        });
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(NumberType.FLOAT, p.getNumberType());
        assertEquals(value, p.getDoubleValue());
        assertNull(p.nextToken());
        p.close();
    }

    public void testMediumText() throws Exception
    {
        _testMedium(1100);
        _testMedium(1300);
        _testMedium(1900);
        _testMedium(2300);
        _testMedium(3900);
    }
    
    private void _testMedium(int len) throws Exception
    {
        // First, use size that should fit in output buffer, but
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        final String MEDIUM = generateUnicodeString(len);
        gen.writeString(MEDIUM);
        gen.close();

        final byte[] b = out.toByteArray();

        // verify that it is indeed non-chunked still...
        assertEquals((byte) (CBORConstants.PREFIX_TYPE_TEXT + 25), b[0]);
        
        JsonParser p = cborParser(b);
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(MEDIUM, p.getText());
        assertNull(p.nextToken());
        p.close();
    }

    public void testCurrentLocationByteOffset() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeString("1234567890");
        gen.writeString("1234567890");
        gen.close();

        final byte[] b = out.toByteArray();

        JsonParser p = cborParser(b);

        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(1, p.getCurrentLocation().getByteOffset());
        p.getText(); // fully read token.
        assertEquals(11, p.getCurrentLocation().getByteOffset());

        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(12, p.getCurrentLocation().getByteOffset());
        p.getText();
        assertEquals(22, p.getCurrentLocation().getByteOffset());

        assertNull(p.nextToken());
        assertEquals(22, p.getCurrentLocation().getByteOffset());

        p.close();
        assertEquals(22, p.getCurrentLocation().getByteOffset());
    }

    public void testLongNonChunkedText() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        final String LONG = generateUnicodeString(37000);
        final byte[] LONG_B = LONG.getBytes("UTF-8");
        final int BYTE_LEN = LONG_B.length;
        out.write(CBORConstants.BYTE_ARRAY_INDEFINITE);
        out.write((byte) (CBORConstants.PREFIX_TYPE_TEXT + 25));
        out.write((byte) (BYTE_LEN >> 8));
        out.write((byte) BYTE_LEN);
        out.write(LONG.getBytes("UTF-8"));
        out.write(CBORConstants.BYTE_BREAK);

        final byte[] b = out.toByteArray();
        assertEquals(BYTE_LEN + 5, b.length);

        // Important! Need to construct a stream, to force boundary conditions
        JsonParser p = cborParser(new ByteArrayInputStream(b));
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        String actual = p.getText();

        final int end = Math.min(LONG.length(), actual.length());
        for (int i = 0; i < end; ++i) {
            if (LONG.charAt(i) != actual.charAt(i)) {
                fail("Character #"+i+" (of "+end+") differs; expected 0x"+Integer.toHexString(LONG.charAt(i))
                        +" found 0x"+Integer.toHexString(actual.charAt(i)));
            }
        }
        
        assertEquals(LONG.length(), actual.length());
        
        assertEquals(LONG, p.getText());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }
    
    public void testLongChunkedText() throws Exception
    {
        // First, try with ASCII content
        StringBuilder sb = new StringBuilder(21000);
        for (int i = 0; i < 21000; ++i) {
            sb.append('Z');
        }
        _testLongChunkedText(sb.toString());        
        // Second, with actual variable byte-length Unicode
        _testLongChunkedText(generateUnicodeString(21000));
    }
        
    public void _testLongChunkedText(String input) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        gen.writeString(input);
        gen.close();

        final int textByteCount = input.getBytes("UTF-8").length;
        final byte[] b = out.toByteArray();
        assertEquals((byte) (CBORConstants.PREFIX_TYPE_TEXT + 0x1F), b[0]);
        assertEquals(CBORConstants.BYTE_BREAK, b[b.length-1]);

        // First, verify validity by scanning
        int i = 1;
        int total = 0;

        for (int end = b.length-1; i < end; ) {
            int type = b[i++] & 0xFF;
            int len = type - CBORConstants.PREFIX_TYPE_TEXT;

            if (len < 24) { // tiny, fine
                ;
            } else if (len == 24) { // 1-byte
                len = (b[i++] & 0xFF);
            } else if (len == 25) { // 2-byte
                len = ((b[i++] & 0xFF) << 8) + (b[i++] & 0xFF);
            }
            i += len;
            total += len;
        }
        assertEquals(b.length-1, i);
        assertEquals(textByteCount, total);

        JsonParser p;

        // then skipping
        p = cborParser(new ByteArrayInputStream(b));
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertNull(p.nextToken());
        p.close();
        
        // and then with actual full parsing/access
        p = cborParser(new ByteArrayInputStream(b));
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        String actual = p.getText();
        assertNull(p.nextToken());
        assertEquals(input.length(), actual.length());
        if (!input.equals(actual)) {
            i = 0;
            while (i < input.length() && input.charAt(i) == actual.charAt(i)) { ++i; }
            fail("Strings differ at #"+i+" (length "+input.length()+"); expected 0x"
                    +Integer.toHexString(input.charAt(i))+", got 0x"
                    +Integer.toHexString(actual.charAt(i)));
        }
        assertEquals(input, actual);
        p.close();
    }

    public void testFloatNumberType() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeStartObject();
        generator.writeFieldName("foo");
        generator.writeNumber(3f);
        generator.writeEndObject();
        generator.close();

        CBORParser parser = cborParser(out.toByteArray());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
        assertEquals(NumberType.FLOAT, parser.getNumberType()); // fails with expected <FLOAT> but was <DOUBLE>
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    public void testStringField() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeStartObject();
        generator.writeStringField("a", "b");
        generator.writeEndObject();
        generator.close();

        CBORParser parser = cborParser(out.toByteArray());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("b", parser.getText());
        assertEquals(1, parser.getTextLength());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        parser.close();
    }

    public void testBufferRelease() throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator generator = cborGenerator(out);
        generator.writeStartObject();
        generator.writeStringField("a", "1");
        generator.writeEndObject();
        generator.flush();
        // add stuff that is NOT part of the Object
        out.write(new byte[] { 1, 2, 3 });
        generator.close();

        CBORParser parser = cborParser(out.toByteArray());
        assertEquals(JsonToken.START_OBJECT, parser.nextToken());
        assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
        assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());

        // Fine; but now should be able to retrieve 3 bytes that are (likely)
        // to have been  buffered

        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        assertEquals(3, parser.releaseBuffered(extra));
        byte[] extraBytes = extra.toByteArray();
        assertEquals((byte) 1, extraBytes[0]);
        assertEquals((byte) 2, extraBytes[1]);
        assertEquals((byte) 3, extraBytes[2]);
        
        parser.close();
    }
}
