package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.util.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;

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
        int fuzz = 0;
        while (out.size() < (DOC_LEN - 10000)) {
            String str = generateUnicodeString(5000, rnd);
            strings.add(str);
            // let's mix in alternative representaton as well
            if ((++fuzz % 7) == 1) {
                gen.writeString(new SerializedString(str));
            } else {
                gen.writeString(str);
            }
        }
        gen.writeEndArray();
        gen.close();
        // Written ok; let's try parsing then
        _verifyStrings(f, out.toByteArray(), strings);

        // Then same with char[] 
        out = new ByteArrayOutputStream(DOC_LEN);
        gen = f.createGenerator(out);
        gen.writeStartArray();
        
        for (int i = 0, len = strings.size(); i < len; ++i) {
            String str = strings.get(i);
            // let's mix in alternative representaton as well
            if ((++fuzz % 7) == 1) {
                byte[] b = str.getBytes("UTF-8");
                gen.writeRawUTF8String(b, 0, b.length);
            } else {
                char[] ch = str.toCharArray();
                gen.writeString(ch, 0, ch.length);
            }
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
        JsonParser p = f.createParser(input);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        for (int i = 0, len = strings.size(); i < len; ++i) {
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            if ((i % 3) == 0) { // just for fun, try calling finish every now and then
                p.finishToken();
            }
            assertEquals(strings.get(i), p.getText());
        }
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        p.close();
    }
}
