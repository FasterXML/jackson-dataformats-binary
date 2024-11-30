package tools.jackson.dataformat.cbor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import tools.jackson.core.*;
import tools.jackson.core.io.SerializedString;

import static org.junit.Assert.assertArrayEquals;

/**
 * Basic testing for string reference generation added in 2.15.
 */
public class StringrefTest extends CBORTestBase
{
    public void testSimpleObject() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = stringrefCborGenerator(bytes);
        assertTrue(gen.isEnabled(CBORWriteFeature.STRINGREF));

        gen.writeStartArray();
        gen.writeStartObject();
        gen.writeNumberProperty("rank", 4);
        gen.writeNumberProperty("count", 417);
        gen.writeStringProperty("name", "Cocktail");
        gen.writeEndObject();
        gen.writeStartObject();
        gen.writeStringProperty("name", "Bath");
        gen.writeNumberProperty("count", 312);
        gen.writeNumberProperty("rank", 4);
        gen.writeEndObject();
        gen.writeStartObject();
        gen.writeStringProperty("name", "Food");
        gen.writeNumberProperty("count", 691);
        gen.writeNumberProperty("rank", 4);
        gen.writeEndObject();
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();
        assertArrayEquals(_simpleObjectBytes, encoded);

        CBORParser parser = cborParser(encoded);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        String rankStr = parser.currentName();
        assertEquals("rank", rankStr);
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        String countStr = parser.currentName();
        assertEquals("count", countStr);
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(417, parser.getValueAsInt());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        String nameStr = parser.currentName();
        assertEquals("name", nameStr);
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Cocktail", parser.getText());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertSame(nameStr, parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Bath", parser.getText());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertSame(countStr, parser.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(312, parser.getValueAsInt());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertSame(rankStr, parser.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertSame(nameStr, parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Food", parser.getText());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertSame(countStr, parser.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(691, parser.getValueAsInt());
        assertToken(JsonToken.PROPERTY_NAME, parser.nextToken());
        assertSame(rankStr, parser.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.END_ARRAY, parser.nextToken());
    }

    public void testSimpleObjectSerializedStrings() throws Exception {
        // SerializableString interface takes different code paths.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = stringrefCborGenerator(bytes);
        assertTrue(gen.isEnabled(CBORWriteFeature.STRINGREF));

        gen.writeStartArray();
        gen.writeStartObject();
        gen.writeName(new SerializedString("rank"));
        gen.writeNumber(4);
        gen.writeName(new SerializedString("count"));
        gen.writeNumber(417);
        gen.writeName(new SerializedString("name"));
        gen.writeString(new SerializedString("Cocktail"));
        gen.writeEndObject();
        gen.writeStartObject();
        gen.writeName(new SerializedString("name"));
        gen.writeString(new SerializedString("Bath"));
        gen.writeName(new SerializedString("count"));
        gen.writeNumber(312);
        gen.writeName(new SerializedString("rank"));
        gen.writeNumber(4);
        gen.writeEndObject();
        gen.writeStartObject();
        gen.writeStringProperty("name", "Food");
        gen.writeName(new SerializedString("count"));
        gen.writeNumber(691);
        gen.writeName(new SerializedString("rank"));
        gen.writeNumber(4);
        gen.writeEndObject();
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();
        assertArrayEquals(_simpleObjectBytes, encoded);

        CBORParser parser = cborParser(encoded);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextName(new SerializedString("rank")));
        String rankStr = parser.currentName();
        assertEquals("rank", rankStr);
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertTrue(parser.nextName(new SerializedString("count")));
        String countStr = parser.currentName();
        assertEquals("count", countStr);
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(417, parser.getValueAsInt());
        assertTrue(parser.nextName(new SerializedString("name")));
        String nameStr = parser.currentName();
        assertEquals("name", nameStr);
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Cocktail", parser.getText());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextName(new SerializedString("name")));
        assertSame(nameStr, parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Bath", parser.getText());
        assertTrue(parser.nextName(new SerializedString("count")));
        assertSame(countStr, parser.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(312, parser.getValueAsInt());
        assertTrue(parser.nextName(new SerializedString("rank")));
        assertSame(rankStr, parser.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.START_OBJECT, parser.nextToken());
        assertTrue(parser.nextName(new SerializedString("name")));
        assertSame(nameStr, parser.currentName());
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals("Food", parser.getText());
        assertTrue(parser.nextName(new SerializedString("count")));
        assertSame(countStr, parser.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(691, parser.getValueAsInt());
        assertTrue(parser.nextName(new SerializedString("rank")));
        assertSame(rankStr, parser.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
        assertEquals(4, parser.getValueAsInt());
        assertToken(JsonToken.END_OBJECT, parser.nextToken());

        assertToken(JsonToken.END_ARRAY, parser.nextToken());
    }

    public void testStringArray() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = stringrefCborGenerator(bytes);
        assertTrue(gen.isEnabled(CBORWriteFeature.STRINGREF));

        gen.writeStartArray();
        gen.writeString("1");
        gen.writeString("222");
        gen.writeString("333");
        gen.writeString("4");
        gen.writeString("555");
        gen.writeString("666");
        gen.writeString("777");
        gen.writeString("888");
        gen.writeString("999");
        gen.writeString("aaa");
        gen.writeString("bbb");
        gen.writeString("ccc");
        gen.writeString("ddd");
        gen.writeString("eee");
        gen.writeString("fff");
        gen.writeString("ggg");
        gen.writeString("hhh");
        gen.writeString("iii");
        gen.writeString("jjj");
        gen.writeString("kkk");
        gen.writeString("lll");
        gen.writeString("mmm");
        gen.writeString("nnn");
        gen.writeString("ooo");
        gen.writeString("ppp");
        gen.writeString("qqq");
        gen.writeString("rrr");
        gen.writeString("333");
        gen.writeString("ssss");
        gen.writeString("qqq");
        gen.writeString("rrr");
        gen.writeString("ssss");
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();
        verifyStringArray(encoded);
    }

    public void testStringArrayFromChars() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = stringrefCborGenerator(bytes);
        assertTrue(gen.isEnabled(CBORWriteFeature.STRINGREF));

        gen.writeStartArray();
        writeStringAsCharArray(gen, "1");
        writeStringAsCharArray(gen, "222");
        writeStringAsCharArray(gen, "333");
        writeStringAsCharArray(gen, "4");
        writeStringAsCharArray(gen, "555");
        writeStringAsCharArray(gen, "666");
        writeStringAsCharArray(gen, "777");
        writeStringAsCharArray(gen, "888");
        writeStringAsCharArray(gen, "999");
        writeStringAsCharArray(gen, "aaa");
        writeStringAsCharArray(gen, "bbb");
        writeStringAsCharArray(gen, "ccc");
        writeStringAsCharArray(gen, "ddd");
        writeStringAsCharArray(gen, "eee");
        writeStringAsCharArray(gen, "fff");
        writeStringAsCharArray(gen, "ggg");
        writeStringAsCharArray(gen, "hhh");
        writeStringAsCharArray(gen, "iii");
        writeStringAsCharArray(gen, "jjj");
        writeStringAsCharArray(gen, "kkk");
        writeStringAsCharArray(gen, "lll");
        writeStringAsCharArray(gen, "mmm");
        writeStringAsCharArray(gen, "nnn");
        writeStringAsCharArray(gen, "ooo");
        writeStringAsCharArray(gen, "ppp");
        writeStringAsCharArray(gen, "qqq");
        writeStringAsCharArray(gen, "rrr");
        writeStringAsCharArray(gen, "333");
        writeStringAsCharArray(gen, "ssss");
        writeStringAsCharArray(gen, "qqq");
        writeStringAsCharArray(gen, "rrr");
        writeStringAsCharArray(gen, "ssss");
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();
        verifyStringArray(encoded);
    }

    public void testStringArraySerializedString() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = stringrefCborGenerator(bytes);
        assertTrue(gen.isEnabled(CBORWriteFeature.STRINGREF));

        gen.writeStartArray();
        gen.writeString(new SerializedString("1"));
        gen.writeString(new SerializedString("222"));
        gen.writeString(new SerializedString("333"));
        gen.writeString(new SerializedString("4"));
        gen.writeString(new SerializedString("555"));
        gen.writeString(new SerializedString("666"));
        gen.writeString(new SerializedString("777"));
        gen.writeString(new SerializedString("888"));
        gen.writeString(new SerializedString("999"));
        gen.writeString(new SerializedString("aaa"));
        gen.writeString(new SerializedString("bbb"));
        gen.writeString(new SerializedString("ccc"));
        gen.writeString(new SerializedString("ddd"));
        gen.writeString(new SerializedString("eee"));
        gen.writeString(new SerializedString("fff"));
        gen.writeString(new SerializedString("ggg"));
        gen.writeString(new SerializedString("hhh"));
        gen.writeString(new SerializedString("iii"));
        gen.writeString(new SerializedString("jjj"));
        gen.writeString(new SerializedString("kkk"));
        gen.writeString(new SerializedString("lll"));
        gen.writeString(new SerializedString("mmm"));
        gen.writeString(new SerializedString("nnn"));
        gen.writeString(new SerializedString("ooo"));
        gen.writeString(new SerializedString("ppp"));
        gen.writeString(new SerializedString("qqq"));
        gen.writeString(new SerializedString("rrr"));
        gen.writeString(new SerializedString("333"));
        gen.writeString(new SerializedString("ssss"));
        gen.writeString(new SerializedString("qqq"));
        gen.writeString(new SerializedString("rrr"));
        gen.writeString(new SerializedString("ssss"));
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();
        verifyStringArray(encoded);
    }

    public void testStringArrayUTF8() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = stringrefCborGenerator(bytes);
        assertTrue(gen.isEnabled(CBORWriteFeature.STRINGREF));

        gen.writeStartArray();
        writeStringAsUTF8(gen, "1");
        writeStringAsUTF8(gen, "222");
        writeStringAsUTF8(gen, "333");
        writeStringAsUTF8(gen, "4");
        writeStringAsUTF8(gen, "555");
        writeStringAsUTF8(gen, "666");
        writeStringAsUTF8(gen, "777");
        writeStringAsUTF8(gen, "888");
        writeStringAsUTF8(gen, "999");
        writeStringAsUTF8(gen, "aaa");
        writeStringAsUTF8(gen, "bbb");
        writeStringAsUTF8(gen, "ccc");
        writeStringAsUTF8(gen, "ddd");
        writeStringAsUTF8(gen, "eee");
        writeStringAsUTF8(gen, "fff");
        writeStringAsUTF8(gen, "ggg");
        writeStringAsUTF8(gen, "hhh");
        writeStringAsUTF8(gen, "iii");
        writeStringAsUTF8(gen, "jjj");
        writeStringAsUTF8(gen, "kkk");
        writeStringAsUTF8(gen, "lll");
        writeStringAsUTF8(gen, "mmm");
        writeStringAsUTF8(gen, "nnn");
        writeStringAsUTF8(gen, "ooo");
        writeStringAsUTF8(gen, "ppp");
        writeStringAsUTF8(gen, "qqq");
        writeStringAsUTF8(gen, "rrr");
        writeStringAsUTF8(gen, "333");
        writeStringAsUTF8(gen, "ssss");
        writeStringAsUTF8(gen, "qqq");
        writeStringAsUTF8(gen, "rrr");
        writeStringAsUTF8(gen, "ssss");
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();
        verifyStringArray(encoded);
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = stringrefCborGenerator(bytes);
        assertTrue(gen.isEnabled(CBORWriteFeature.STRINGREF));

        gen.writeStartArray();
        writeStringAsBinary(gen, "1");
        writeStringAsBinary(gen, "222");
        writeStringAsBinary(gen, "333");
        writeStringAsBinary(gen, "4");
        writeStringAsBinary(gen, "555");
        writeStringAsBinary(gen, "666");
        writeStringAsBinary(gen, "777");
        writeStringAsBinary(gen, "888");
        writeStringAsBinary(gen, "999");
        writeStringAsBinary(gen, "aaa");
        writeStringAsBinary(gen, "bbb");
        writeStringAsBinary(gen, "ccc");
        writeStringAsBinary(gen, "ddd");
        writeStringAsBinary(gen, "eee");
        writeStringAsBinary(gen, "fff");
        writeStringAsBinary(gen, "ggg");
        writeStringAsBinary(gen, "hhh");
        writeStringAsBinary(gen, "iii");
        writeStringAsBinary(gen, "jjj");
        writeStringAsBinary(gen, "kkk");
        writeStringAsBinary(gen, "lll");
        writeStringAsBinary(gen, "mmm");
        writeStringAsBinary(gen, "nnn");
        writeStringAsBinary(gen, "ooo");
        writeStringAsBinary(gen, "ppp");
        writeStringAsBinary(gen, "qqq");
        writeStringAsBinary(gen, "rrr");
        writeStringAsBinary(gen, "333");
        writeStringAsBinary(gen, "ssss");
        writeStringAsBinary(gen, "qqq");
        writeStringAsBinary(gen, "rrr");
        writeStringAsBinary(gen, "ssss");
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();
        assertArrayEquals(_binaryStringArrayBytes, encoded);

        CBORParser parser = cborParser(encoded);
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

    public void testBinaryStringArrayStream() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = stringrefCborGenerator(bytes);
        assertTrue(gen.isEnabled(CBORWriteFeature.STRINGREF));

        gen.writeStartArray();
        writeStringAsBinaryStream(gen, "1");
        writeStringAsBinaryStream(gen, "222");
        writeStringAsBinaryStream(gen, "333");
        writeStringAsBinaryStream(gen, "4");
        writeStringAsBinaryStream(gen, "555");
        writeStringAsBinaryStream(gen, "666");
        writeStringAsBinaryStream(gen, "777");
        writeStringAsBinaryStream(gen, "888");
        writeStringAsBinaryStream(gen, "999");
        writeStringAsBinaryStream(gen, "aaa");
        writeStringAsBinaryStream(gen, "bbb");
        writeStringAsBinaryStream(gen, "ccc");
        writeStringAsBinaryStream(gen, "ddd");
        writeStringAsBinaryStream(gen, "eee");
        writeStringAsBinaryStream(gen, "fff");
        writeStringAsBinaryStream(gen, "ggg");
        writeStringAsBinaryStream(gen, "hhh");
        writeStringAsBinaryStream(gen, "iii");
        writeStringAsBinaryStream(gen, "jjj");
        writeStringAsBinaryStream(gen, "kkk");
        writeStringAsBinaryStream(gen, "lll");
        writeStringAsBinaryStream(gen, "mmm");
        writeStringAsBinaryStream(gen, "nnn");
        writeStringAsBinaryStream(gen, "ooo");
        writeStringAsBinaryStream(gen, "ppp");
        writeStringAsBinaryStream(gen, "qqq");
        writeStringAsBinaryStream(gen, "rrr");
        writeStringAsBinaryStream(gen, "333");
        writeStringAsBinaryStream(gen, "ssss");
        writeStringAsBinaryStream(gen, "qqq");
        writeStringAsBinaryStream(gen, "rrr");
        writeStringAsBinaryStream(gen, "ssss");
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();
        assertArrayEquals(_binaryStringArrayBytes, encoded);

        CBORParser parser = cborParser(encoded);
        assertToken(JsonToken.START_ARRAY, parser.nextToken());
        verifyNextTokenBinaryStream("1", parser);
        verifyNextTokenBinaryStream("222", parser);
        verifyNextTokenBinaryStream("333", parser);
        verifyNextTokenBinaryStream("4", parser);
        verifyNextTokenBinaryStream("555", parser);
        verifyNextTokenBinaryStream("666", parser);
        verifyNextTokenBinaryStream("777", parser);
        verifyNextTokenBinaryStream("888", parser);
        verifyNextTokenBinaryStream("999", parser);
        verifyNextTokenBinaryStream("aaa", parser);
        verifyNextTokenBinaryStream("bbb", parser);
        verifyNextTokenBinaryStream("ccc", parser);
        verifyNextTokenBinaryStream("ddd", parser);
        verifyNextTokenBinaryStream("eee", parser);
        verifyNextTokenBinaryStream("fff", parser);
        verifyNextTokenBinaryStream("ggg", parser);
        verifyNextTokenBinaryStream("hhh", parser);
        verifyNextTokenBinaryStream("iii", parser);
        verifyNextTokenBinaryStream("jjj", parser);
        verifyNextTokenBinaryStream("kkk", parser);
        verifyNextTokenBinaryStream("lll", parser);
        verifyNextTokenBinaryStream("mmm", parser);
        verifyNextTokenBinaryStream("nnn", parser);
        verifyNextTokenBinaryStream("ooo", parser);
        verifyNextTokenBinaryStream("ppp", parser);
        verifyNextTokenBinaryStream("qqq", parser);
        verifyNextTokenBinaryStream("rrr", parser);
        verifyNextTokenBinaryStream("333", parser);
        verifyNextTokenBinaryStream("ssss", parser);
        verifyNextTokenBinaryStream("qqq", parser);
        verifyNextTokenBinaryStream("rrr", parser);
        verifyNextTokenBinaryStream("ssss", parser);
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
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = (CBORGenerator) CBORFactory.builder()
                .enable(CBORWriteFeature.WRITE_TYPE_HEADER)
                .enable(CBORWriteFeature.STRINGREF)
                .build()
                .createGenerator(ObjectWriteContext.empty(), bytes);

        gen.writeStartArray();
        gen.writeNumber(new BigInteger("1234567890", 16));
        gen.writeNumber(new BigInteger("9876543210", 16));
        gen.writeNumber(new BigInteger("1234567890", 16));
        gen.writeEndArray();
        gen.close();

        byte[] encoded = bytes.toByteArray();

        assertArrayEquals(_nestedTagBytes, encoded);

        CBORParser parser = cborParser(encoded);
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

    public void testNestedTagsRounddTrip() throws Exception {
        CBORParser parser = cborParser(_nestedTagBytes);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(bytes);
        parser.nextToken();
        gen.copyCurrentStructure(parser);
        gen.close();

        byte[] expectedExpandedBytes = new byte[]{
                (byte) 0x9F, (byte) 0xC2, 0x45, 0x12, 0x34, 0x56, 0x78, (byte) 0x90, (byte) 0xC2,
                0x46, 0x00, (byte) 0x98, 0x76, 0x54, 0x32, 0x10, (byte) 0xC2, 0x45, 0x12, 0x34,
                0x56, 0x78, (byte) 0x90, (byte) 0xFF
        };
        byte[] encoded = bytes.toByteArray();
        assertArrayEquals(expectedExpandedBytes, encoded);

        bytes.reset();
        parser = cborParser(encoded);
        gen = (CBORGenerator) CBORFactory.builder()
                .enable(CBORWriteFeature.WRITE_TYPE_HEADER)
                .enable(CBORWriteFeature.STRINGREF)
                .build()
                .createGenerator(ObjectWriteContext.empty(), bytes);
        parser.nextToken();
        gen.copyCurrentStructure(parser);
        gen.close();

        assertArrayEquals(_nestedTagBytes, bytes.toByteArray());
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

    private void writeStringAsCharArray(CBORGenerator gen, String str) throws IOException {
        char[] chars = str.toCharArray();
        gen.writeString(chars, 0, chars.length);
    }

    private void writeStringAsUTF8(CBORGenerator gen, String str) throws IOException {
        byte[] encoded = str.getBytes(StandardCharsets.UTF_8);
        gen.writeUTF8String(encoded, 0, encoded.length);
    }

    private void writeStringAsBinary(CBORGenerator gen, String str) throws IOException {
        gen.writeBinary(str.getBytes(StandardCharsets.UTF_8));
    }

    private void writeStringAsBinaryStream(CBORGenerator gen, String str) throws IOException {
        byte[] encoded = str.getBytes(StandardCharsets.UTF_8);
        gen.writeBinary(new ByteArrayInputStream(encoded), encoded.length);
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

    private void verifyNextTokenBinaryStream(String expected, CBORParser parser) throws IOException {
        assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, parser.nextToken());
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        parser.readBinaryValue(stream);
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), stream.toByteArray());
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

    private static final byte[] _nestedTagBytes = new byte[]{
            (byte) 0xD9, (byte) 0xD9, (byte) 0xF7, (byte) 0xD9, 0x01, 0x00, (byte) 0x9F,
            (byte) 0xC2, 0x45, 0x12, 0x34, 0x56, 0x78, (byte) 0x90, (byte) 0xC2, 0x46, 0x00,
            (byte) 0x98, 0x76, 0x54, 0x32, 0x10, (byte) 0xC2, (byte) 0xD8, 0x19, 0x00,
            (byte) 0xFF
    };
}