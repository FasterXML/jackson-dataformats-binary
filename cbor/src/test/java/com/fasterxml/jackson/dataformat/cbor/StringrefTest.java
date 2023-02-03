package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;

import static org.junit.Assert.assertArrayEquals;

/**
 * Basic testing for string reference generation added in 2.15.
 */
public class StringrefTest extends CBORTestBase
{
    public void testSimpleObject() throws Exception {
        CBORParser parser = cborParser(_simpleObjectBytes);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        String rankStr = parser.getCurrentName();
        assertEquals("rank", rankStr);
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        String countStr = parser.getCurrentName();
        assertEquals("count", countStr);
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(417, parser.getValueAsInt());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        String nameStr = parser.getCurrentName();
        assertEquals("name", nameStr);
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Cocktail", parser.getText());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertSame(nameStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Bath", parser.getText());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertSame(countStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(312, parser.getValueAsInt());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertSame(rankStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertSame(nameStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Food", parser.getText());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertSame(countStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(691, parser.getValueAsInt());
        assertToken(JsonToken.FIELD_NAME, parser.nextToken());
        assertSame(rankStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.END_ARRAY, parser.nextToken());
    }

    public void testSimpleObjectSerializedStrings() throws Exception {
        CBORParser parser = cborParser(_simpleObjectBytes);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextFieldName(new SerializedString("rank")));
        String rankStr = parser.getCurrentName();
        assertEquals("rank", rankStr);
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertTrue(parser.nextFieldName(new SerializedString("count")));
        String countStr = parser.getCurrentName();
        assertEquals("count", countStr);
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(417, parser.getValueAsInt());
        assertTrue(parser.nextFieldName(new SerializedString("name")));
        String nameStr = parser.getCurrentName();
        assertEquals("name", nameStr);
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Cocktail", parser.getText());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextFieldName(new SerializedString("name")));
        assertSame(nameStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Bath", parser.getText());
        assertTrue(parser.nextFieldName(new SerializedString("count")));
        assertSame(countStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(312, parser.getValueAsInt());
        assertTrue(parser.nextFieldName(new SerializedString("rank")));
        assertSame(rankStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextFieldName(new SerializedString("name")));
        assertSame(nameStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Food", parser.getText());
        assertTrue(parser.nextFieldName(new SerializedString("count")));
        assertSame(countStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(691, parser.getValueAsInt());
        assertTrue(parser.nextFieldName(new SerializedString("rank")));
        assertSame(rankStr, parser.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.END_ARRAY, parser.nextToken());
    }

    public void testStringArray() throws Exception {
        verifyStringArray(_stringArrayBytes);
    }

    public void testStringArrayNextTextValue() throws Exception {
        // nextTextValue() takes a separate code path. Use the expected encoded bytes since there's
        // no special overload we want to test for encoding.
        CBORParser parser = cborParser(_stringArrayBytes);

        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        verifyNextTextValue("1", parser);
        verifyNextTextValue("222", parser);
        String str333 = verifyNextTextValue("333", parser);
        verifyNextTextValue("4", parser);
        verifyNextTextValue("555", parser);
        verifyNextTextValue("666", parser);
        verifyNextTextValue("777", parser);
        verifyNextTextValue("888", parser);
        verifyNextTextValue("999", parser);
        verifyNextTextValue("aaa", parser);
        verifyNextTextValue("bbb", parser);
        verifyNextTextValue("ccc", parser);
        verifyNextTextValue("ddd", parser);
        verifyNextTextValue("eee", parser);
        verifyNextTextValue("fff", parser);
        verifyNextTextValue("ggg", parser);
        verifyNextTextValue("hhh", parser);
        verifyNextTextValue("iii", parser);
        verifyNextTextValue("jjj", parser);
        verifyNextTextValue("kkk", parser);
        verifyNextTextValue("lll", parser);
        verifyNextTextValue("mmm", parser);
        verifyNextTextValue("nnn", parser);
        verifyNextTextValue("ooo", parser);
        verifyNextTextValue("ppp", parser);
        String qqqStr = verifyNextTextValue("qqq", parser);
        String rrrStr = verifyNextTextValue("rrr", parser);
        verifyNextTextValueRef(str333, parser);
        String ssssStr = verifyNextTextValue("ssss", parser);
        verifyNextTextValueRef(qqqStr, parser);
        verifyNextTextValueNotRef(rrrStr, parser);
        verifyNextTextValueRef(ssssStr, parser);
    }

    public void testBinaryStringArray() throws Exception {
        CBORParser parser = cborParser(_binaryStringArrayBytes);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        verifyNextTokenBinary("1", parser);
        verifyNextTokenBinary("222", parser);
        byte[] str333 = verifyNextTokenBinary("333", parser);
        verifyNextTokenBinary("4", parser);
        verifyNextTokenBinary("555", parser);
        verifyNextTokenBinary("666", parser);
        verifyNextTokenBinary("777", parser);
        verifyNextTokenBinary("888", parser);
        verifyNextTokenBinary("999", parser);
        verifyNextTokenBinary("aaa", parser);
        verifyNextTokenBinary("bbb", parser);
        verifyNextTokenBinary("ccc", parser);
        verifyNextTokenBinary("ddd", parser);
        verifyNextTokenBinary("eee", parser);
        verifyNextTokenBinary("fff", parser);
        verifyNextTokenBinary("ggg", parser);
        verifyNextTokenBinary("hhh", parser);
        verifyNextTokenBinary("iii", parser);
        verifyNextTokenBinary("jjj", parser);
        verifyNextTokenBinary("kkk", parser);
        verifyNextTokenBinary("lll", parser);
        verifyNextTokenBinary("mmm", parser);
        verifyNextTokenBinary("nnn", parser);
        verifyNextTokenBinary("ooo", parser);
        verifyNextTokenBinary("ppp", parser);
        byte[] qqqStr = verifyNextTokenBinary("qqq", parser);
        byte[] rrrStr = verifyNextTokenBinary("rrr", parser);
        verifyNextTokenBinaryRef(str333, parser);
        byte[] ssssStr = verifyNextTokenBinary("ssss", parser);
        verifyNextTokenBinaryRef(qqqStr, parser);
        verifyNextTokenBinaryNotRef(rrrStr, parser);
        verifyNextTokenBinaryRef(ssssStr, parser);
    }

    public void testNestedNamespaces() throws Exception {
        byte[] nestedNamespaceBytes = new byte[]{
                (byte) 0xD9, 0x01, 0x00, (byte) 0x85, 0x63, 0x61, 0x61, 0x61, (byte) 0xD8, 0x19,
                0x00, (byte) 0xD9, 0x01, 0x00, (byte) 0x83, 0x63, 0x62, 0x62, 0x62, 0x63, 0x61,
                0x61, 0x61, (byte) 0xD8, 0x19, 0x01, (byte) 0xD9, 0x01, 0x00, (byte) 0x82, 0x63,
                0x63, 0x63, 0x63, (byte) 0xD8, 0x19, 0x00, (byte) 0xD8, 0x19, 0x00
        };
        CBORParser parser = cborParser(nestedNamespaceBytes);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        String aaaStrOuter = verifyNextTokenString("aaa", parser);
        verifyNextTokenStringRef(aaaStrOuter, parser);

        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        verifyNextTokenString("bbb", parser);
        String aaaStrInner = verifyNextTokenString("aaa", parser);
        assertNotSame(aaaStrOuter, aaaStrInner);
        verifyNextTokenStringRef(aaaStrInner, parser);
        assertToken(JsonToken.END_ARRAY, parser.nextToken());

        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        String cccStrInner = verifyNextTokenString("ccc", parser);
        verifyNextTokenStringRef(cccStrInner, parser);
        assertToken(JsonToken.END_ARRAY, parser.nextToken());

        verifyNextTokenStringRef(aaaStrOuter, parser);
        assertToken(JsonToken.END_ARRAY, parser.nextToken());
    }

    public void testNestedTags() throws Exception {
        byte[] nestedTagBytes = new byte[]{
                (byte) 0xD9, (byte) 0xD9, (byte) 0xF7, (byte) 0xD9, 0x01, 0x00, (byte) 0x9F,
                (byte) 0xC2, 0x45, 0x12, 0x34, 0x56, 0x78, (byte) 0x90, (byte) 0xC2, 0x46, 0x00,
                (byte) 0x98, 0x76, 0x54, 0x32, 0x10, (byte) 0xC2, (byte) 0xD8, 0x19, 0x00,
                (byte) 0xFF
        };
        CBORParser parser = cborParser(nestedTagBytes);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        assertTrue(parser.getCurrentTags().contains(CBORConstants.TAG_ID_SELF_DESCRIBE));
        assertTrue(parser.getCurrentTags().contains(CBORConstants.TAG_ID_STRINGREF_NAMESPACE));
        assertEquals(CBORConstants.TAG_ID_SELF_DESCRIBE, parser.getCurrentTag());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(new BigInteger("1234567890", 16), parser.getBigIntegerValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(new BigInteger("9876543210", 16), parser.getBigIntegerValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(new BigInteger("1234567890", 16), parser.getBigIntegerValue());
        assertToken(JsonToken.END_ARRAY, parser.nextToken());
    }

    private void verifyStringArray(byte[] encoded) throws IOException {
        assertArrayEquals(_stringArrayBytes, encoded);

        CBORParser parser = cborParser(encoded);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        verifyNextTokenString("1", parser);
        verifyNextTokenString("222", parser);
        String str333 = verifyNextTokenString("333", parser);
        verifyNextTokenString("4", parser);
        verifyNextTokenString("555", parser);
        verifyNextTokenString("666", parser);
        verifyNextTokenString("777", parser);
        verifyNextTokenString("888", parser);
        verifyNextTokenString("999", parser);
        verifyNextTokenString("aaa", parser);
        verifyNextTokenString("bbb", parser);
        verifyNextTokenString("ccc", parser);
        verifyNextTokenString("ddd", parser);
        verifyNextTokenString("eee", parser);
        verifyNextTokenString("fff", parser);
        verifyNextTokenString("ggg", parser);
        verifyNextTokenString("hhh", parser);
        verifyNextTokenString("iii", parser);
        verifyNextTokenString("jjj", parser);
        verifyNextTokenString("kkk", parser);
        verifyNextTokenString("lll", parser);
        verifyNextTokenString("mmm", parser);
        verifyNextTokenString("nnn", parser);
        verifyNextTokenString("ooo", parser);
        verifyNextTokenString("ppp", parser);
        String qqqStr = verifyNextTokenString("qqq", parser);
        String rrrStr = verifyNextTokenString("rrr", parser);
        verifyNextTokenStringRef(str333, parser);
        String ssssStr = verifyNextTokenString("ssss", parser);
        verifyNextTokenStringRef(qqqStr, parser);
        verifyNextTokenStringNotRef(rrrStr, parser);
        verifyNextTokenStringRef(ssssStr, parser);
    }

    private String verifyNextTokenString(String expected, CBORParser parser) throws IOException {
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals(expected, parser.getText());
        return parser.getText();
    }

    private void verifyNextTokenStringRef(String expected, CBORParser parser) throws IOException {
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertSame(expected, parser.getText());
    }

    private void verifyNextTokenStringNotRef(String expected, CBORParser parser) throws IOException {
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals(expected, parser.getText());
        assertNotSame(expected, parser.getText());
    }

    private String verifyNextTextValue(String expected, CBORParser parser) throws IOException {
        assertEquals(expected, parser.nextTextValue());
        return parser.getText();
    }

    private void verifyNextTextValueRef(String expected, CBORParser parser) throws IOException {
        assertSame(expected, parser.nextTextValue());
    }

    private void verifyNextTextValueNotRef(String expected, CBORParser parser) throws IOException {
        assertEquals(expected, parser.nextTextValue());
        assertNotSame(expected, parser.getText());
    }

    private byte[] verifyNextTokenBinary(String expected, CBORParser parser) throws IOException {
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, parser.nextToken());
        byte[] binary = parser.getBinaryValue();
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), binary);
        return binary;
    }

    private void verifyNextTokenBinaryRef(byte[] expected, CBORParser parser) throws IOException {
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, parser.nextToken());
        assertSame(expected, parser.getBinaryValue());
    }

    private void verifyNextTokenBinaryNotRef(byte[] expected, CBORParser parser) throws IOException {
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, parser.nextToken());
        assertArrayEquals(expected, parser.getBinaryValue());
        assertNotSame(expected, parser.getBinaryValue());
    }

    private static final byte[] _simpleObjectBytes = new byte[]{
            (byte) 0xD9, 0x01, 0x00, (byte) 0x9F, (byte) 0xBF, 0x64, 0x72, 0x61, 0x6E, 0x6B,
            0x04, 0x65, 0x63, 0x6F, 0x75, 0x6E, 0x74, 0x19, 0x01, (byte) 0xA1, 0x64, 0x6E, 0x61,
            0x6D, 0x65, 0x68, 0x43, 0x6F, 0x63, 0x6B, 0x74, 0x61, 0x69, 0x6C, (byte) 0xFF,
            (byte) 0xBF, (byte) 0xD8, 0x19, 0x02, 0x64, 0x42, 0x61, 0x74, 0x68, (byte) 0xD8,
            0x19, 0x01, 0x19, 0x01, 0x38, (byte) 0xD8, 0x19, 0x00, 0x04, (byte) 0xFF,
            (byte) 0xBF, (byte) 0xD8, 0x19, 0x02, 0x64, 0x46, 0x6F, 0x6F, 0x64, (byte) 0xD8,
            0x19, 0x01, 0x19, 0x02, (byte) 0xB3, (byte) 0xD8, 0x19, 0x00, 0x04, (byte) 0xFF,
            (byte) 0xFF
    };

    private static final byte[] _stringArrayBytes = new byte[]{
            (byte) 0xD9, 0x01, 0x00, (byte) 0x9F, 0x61, 0x31, 0x63, 0x32, 0x32, 0x32, 0x63, 0x33,
            0x33, 0x33, 0x61, 0x34, 0x63, 0x35, 0x35, 0x35, 0x63, 0x36, 0x36, 0x36, 0x63, 0x37,
            0x37, 0x37, 0x63, 0x38, 0x38, 0x38, 0x63, 0x39, 0x39, 0x39, 0x63, 0x61, 0x61, 0x61,
            0x63, 0x62, 0x62, 0x62, 0x63, 0x63, 0x63, 0x63, 0x63, 0x64, 0x64, 0x64, 0x63, 0x65,
            0x65, 0x65, 0x63, 0x66, 0x66, 0x66, 0x63, 0x67, 0x67, 0x67, 0x63, 0x68, 0x68, 0x68,
            0x63, 0x69, 0x69, 0x69, 0x63, 0x6A, 0x6A, 0x6A, 0x63, 0x6B, 0x6B, 0x6B, 0x63, 0x6C,
            0x6C, 0x6C, 0x63, 0x6D, 0x6D, 0x6D, 0x63, 0x6E, 0x6E, 0x6E, 0x63, 0x6F, 0x6F, 0x6F,
            0x63, 0x70, 0x70, 0x70, 0x63, 0x71, 0x71, 0x71, 0x63, 0x72, 0x72, 0x72, (byte) 0xD8,
            0x19, 0x01, 0x64, 0x73, 0x73, 0x73, 0x73, (byte) 0xD8, 0x19, 0x17, 0x63, 0x72, 0x72,
            0x72, (byte) 0xD8, 0x19, 0x18, 0x18, (byte) 0xFF
    };

    private static final byte[] _binaryStringArrayBytes = new byte[]{
            (byte) 0xD9, 0x01, 0x00, (byte) 0x9F, 0x41, 0x31, 0x43, 0x32, 0x32, 0x32, 0x43, 0x33,
            0x33, 0x33, 0x41, 0x34, 0x43, 0x35, 0x35, 0x35, 0x43, 0x36, 0x36, 0x36, 0x43, 0x37,
            0x37, 0x37, 0x43, 0x38, 0x38, 0x38, 0x43, 0x39, 0x39, 0x39, 0x43, 0x61, 0x61, 0x61,
            0x43, 0x62, 0x62, 0x62, 0x43, 0x63, 0x63, 0x63, 0x43, 0x64, 0x64, 0x64, 0x43, 0x65,
            0x65, 0x65, 0x43, 0x66, 0x66, 0x66, 0x43, 0x67, 0x67, 0x67, 0x43, 0x68, 0x68, 0x68,
            0x43, 0x69, 0x69, 0x69, 0x43, 0x6A, 0x6A, 0x6A, 0x43, 0x6B, 0x6B, 0x6B, 0x43, 0x6C,
            0x6C, 0x6C, 0x43, 0x6D, 0x6D, 0x6D, 0x43, 0x6E, 0x6E, 0x6E, 0x43, 0x6F, 0x6F, 0x6F,
            0x43, 0x70, 0x70, 0x70, 0x43, 0x71, 0x71, 0x71, 0x43, 0x72, 0x72, 0x72, (byte) 0xD8,
            0x19, 0x01, 0x44, 0x73, 0x73, 0x73, 0x73, (byte) 0xD8, 0x19, 0x17, 0x43, 0x72, 0x72,
            0x72, (byte) 0xD8, 0x19, 0x18, 0x18, (byte) 0xFF
    };
}