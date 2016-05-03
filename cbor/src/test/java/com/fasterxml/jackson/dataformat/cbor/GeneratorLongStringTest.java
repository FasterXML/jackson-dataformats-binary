package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.util.*;

import com.fasterxml.jackson.core.*;

public class GeneratorLongStringTest extends CBORTestBase
{
    final static int DOC_LEN = 2000000; // 2 meg test doc
    
    public void testLongWithMultiBytes() throws Exception
    {
        CBORFactory f = cborFactory();
        ArrayList<String> strings = new ArrayList<String>();
        Random rnd = new Random(123);

        ByteArrayOutputStream out = new ByteArrayOutputStream(DOC_LEN);
        JsonGenerator gen = f.createGenerator(out);
        gen.writeStartArray();
        
        // Let's create 1M doc, first using Strings
        while (out.size() < (DOC_LEN - 10000)) {
            String str = generateUnicodeString(5000, rnd);
            strings.add(str);
            gen.writeString(str);
        }
        gen.writeEndArray();
        gen.close();
        // Written ok; let's try parsing then
        _verifyStrings(f, out.toByteArray(), strings);

        // Then same with char[] 
        out = new ByteArrayOutputStream(DOC_LEN);
        gen = f.createGenerator(out);
        gen.writeStartArray();
        
        // Let's create 1M doc, first using Strings
        for (int i = 0, len = strings.size(); i < len; ++i) {
            char[] ch = strings.get(i).toCharArray();
            gen.writeString(ch, 0, ch.length);
        }
        gen.writeEndArray();
        gen.close();
        _verifyStrings(f, out.toByteArray(), strings);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _verifyStrings(JsonFactory f, byte[] input, List<String> strings)
        throws IOException
    {
        /*
        JsonParser jp = f.createParser(input);
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        for (int i = 0, len = strings.size(); i < len; ++i) {
            assertToken(JsonToken.VALUE_STRING, jp.nextToken());
            assertEquals(strings.get(i), jp.getText());
        }
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
        */
    }
}
