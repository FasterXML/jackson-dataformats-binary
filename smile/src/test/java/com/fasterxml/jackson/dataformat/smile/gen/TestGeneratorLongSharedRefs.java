package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class TestGeneratorLongSharedRefs extends BaseTestForSmile
{
    // [smile#18]: problems encoding long shared-string references
    public void testIssue18EndOfDocByteViaFields() throws Exception
    {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

        // boolean requireHeader, boolean writeHeader, boolean writeEndMarker
        final SmileFactory f = smileFactory(false, true, false);
        
        SmileGenerator generator =  f.createGenerator(byteOut);
        generator.writeStartObject();
        generator.writeFieldName("a");
        generator.writeStartObject();

        final int FIELD_COUNT = 300;

        for (int i=0; i < FIELD_COUNT; i++) {
            generator.writeNumberField("f_"+i, i);
            generator.flush();
        }
        generator.writeEndObject();
        generator.writeFieldName("b");
        generator.writeStartObject();
        for (int i=0; i < FIELD_COUNT; i++) {
            generator.writeNumberField("f_"+i, i);
            generator.flush();
        }
        generator.writeEndObject();
        generator.writeEndObject();
        generator.close();

        byte[] smile = byteOut.toByteArray();

        // then read it back; make sure to use InputStream to exercise block boundaries
        SmileParser p = f.createParser(new ByteArrayInputStream(smile));
        assertToken(p.nextToken(), JsonToken.START_OBJECT);

        assertToken(p.nextToken(), JsonToken.FIELD_NAME);
        assertEquals("a", p.getCurrentName());
        assertToken(p.nextToken(), JsonToken.START_OBJECT);
        for (int i=0; i < FIELD_COUNT; i++) {
            assertToken(p.nextToken(), JsonToken.FIELD_NAME);
            assertEquals("f_"+i, p.getCurrentName());
            assertToken(p.nextToken(), JsonToken.VALUE_NUMBER_INT);
            assertEquals(i, p.getIntValue());
        }
        assertToken(p.nextToken(), JsonToken.END_OBJECT);

        assertToken(p.nextToken(), JsonToken.FIELD_NAME);
        assertEquals("b", p.getCurrentName());
        assertToken(p.nextToken(), JsonToken.START_OBJECT);
        for (int i=0; i < FIELD_COUNT; i++) {
            assertToken(p.nextToken(), JsonToken.FIELD_NAME);
            assertEquals("f_"+i, p.getCurrentName());
            assertToken(p.nextToken(), JsonToken.VALUE_NUMBER_INT);
            assertEquals(i, p.getIntValue());
        }
        assertToken(p.nextToken(), JsonToken.END_OBJECT);
        
        assertToken(p.nextToken(), JsonToken.END_OBJECT);
        assertNull(p.nextToken());
        p.close();

        // One more thing: verify we don't see the end marker or null anywhere
        
        for (int i = 0, end = smile.length; i < end; ++i) {
            int ch = smile[i] & 0xFF;

            if (ch >= 0xFE) {
                fail("Unexpected 0x"+Integer.toHexString(ch)+" byte at #"+i+" (of "+end+")");
                
            }
        }
    }

    public void testIssue18EndOfDocByteViaStringValues() throws Exception
    {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

        // boolean requireHeader, boolean writeHeader, boolean writeEndMarker
        final SmileFactory f = smileFactory(false, true, false);
        f.enable(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES);
        
        SmileGenerator generator =  f.createGenerator(byteOut);
        generator.writeStartArray();

        final int VALUE_COUNT = 300;

        for (int i=0; i < VALUE_COUNT; i++) {
            generator.writeString("f_"+i);
            generator.flush();
        }
        for (int i=0; i < VALUE_COUNT; i++) {
            generator.writeString("f_"+i);
            generator.flush();
        }
        generator.writeEndArray();
        generator.close();

        byte[] smile = byteOut.toByteArray();

        // then read it back; make sure to use InputStream to exercise block boundaries
        SmileParser p = f.createParser(new ByteArrayInputStream(smile));
        assertToken(p.nextToken(), JsonToken.START_ARRAY);
        for (int i=0; i < VALUE_COUNT; i++) {
            assertToken(p.nextToken(), JsonToken.VALUE_STRING);
            assertEquals("f_"+i, p.getText());
        }
        for (int i=0; i < VALUE_COUNT; i++) {
            assertToken(p.nextToken(), JsonToken.VALUE_STRING);
            assertEquals("f_"+i, p.getText());
        }
        assertToken(p.nextToken(), JsonToken.END_ARRAY);
        assertNull(p.nextToken());
        p.close();

        // One more thing: verify we don't see the end marker or null anywhere
        
        for (int i = 0, end = smile.length; i < end; ++i) {
            int ch = smile[i] & 0xFF;

            if (ch >= 0xFE) {
                fail("Unexpected 0x"+Integer.toHexString(ch)+" byte at #"+i+" (of "+end+")");
                
            }
        }
    }
}
