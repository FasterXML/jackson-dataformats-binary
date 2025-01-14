package tools.jackson.dataformat.smile.gen;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGeneratorLongStrings extends BaseTestForSmile
{
    final static int DOC_LEN = 2000000; // 2 meg test doc

    @Test
    public void testLongWithMultiBytes() throws Exception
    {
        ArrayList<String> strings = new ArrayList<String>();
        Random rnd = new Random(123);

        ByteArrayOutputStream out = new ByteArrayOutputStream(DOC_LEN);
        JsonGenerator gen = _smileGenerator(out, true);
        gen.writeStartArray();

        // Let's create 1M doc, first using Strings
        while (out.size() < (DOC_LEN - 10000)) {
            String str = generateString(5000, rnd);
            strings.add(str);
            gen.writeString(str);
        }
        gen.writeEndArray();
        gen.close();
        // Written ok; let's try parsing then
        _verifyStrings(out.toByteArray(), strings);

        // Then same with char[]
        out = new ByteArrayOutputStream(DOC_LEN);
        gen = _smileGenerator(out, true);
        gen.writeStartArray();

        // Let's create 1M doc, first using Strings
        for (int i = 0, len = strings.size(); i < len; ++i) {
            char[] ch = strings.get(i).toCharArray();
            gen.writeString(ch, 0, ch.length);
        }
        gen.writeEndArray();
        gen.close();
        _verifyStrings(out.toByteArray(), strings);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    protected String generateString(int length, Random rnd) throws Exception
    {
        StringBuilder sw = new StringBuilder(length+10);
        do {
            // First, add 7 ascii characters
            int num = 4 + (rnd.nextInt() & 7);
            while (--num >= 0) {
                sw.append((char) ('A' + num));
            }
            // Then a unicode char of 2, 3 or 4 bytes long
            switch (rnd.nextInt() % 3) {
            case 0:
                sw.append((char) (256 + rnd.nextInt() & 511));
                break;
            case 1:
                sw.append((char) (2048 + rnd.nextInt() & 4095));
                break;
            default:
                sw.append((char) (65536 + rnd.nextInt() & 0x3FFF));
                break;
            }
        } while (sw.length() < length);
        return sw.toString();
    }

    private void _verifyStrings(byte[] input, List<String> strings) throws IOException
    {
        JsonParser p = _smileParser(input);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        for (int i = 0, len = strings.size(); i < len; ++i) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(strings.get(i), p.getString());
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();

        // Second round, using different accessor
        p = _smileParser(input);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        for (int i = 0, len = strings.size(); i < len; ++i) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            String exp = strings.get(i);
            StringWriter w = new StringWriter();
            int strlen = p.getString(w);
            assertEquals(exp.length(), strlen);
            String str = w.toString();
            assertEquals(exp.length(), str.length());
            assertEquals(exp,str);
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }
}
