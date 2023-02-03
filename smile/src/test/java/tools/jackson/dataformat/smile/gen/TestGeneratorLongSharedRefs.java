package tools.jackson.dataformat.smile.gen;

import java.io.*;

import tools.jackson.core.*;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.BaseTestForSmile;
import tools.jackson.dataformat.smile.SmileGenerator;

public class TestGeneratorLongSharedRefs extends BaseTestForSmile
{
    // [smile#18]: problems encoding long shared-string references
    public void testIssue18EndOfDocByteViaFields() throws Exception
    {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

        // boolean requireHeader, boolean writeHeader, boolean writeEndMarker
        final ObjectMapper mapper = smileMapper(false, true, false);

        JsonGenerator generator =  mapper.createGenerator(byteOut);
        generator.writeStartObject();
        generator.writeName("a");
        generator.writeStartObject();

        final int FIELD_COUNT = 300;

        for (int i=0; i < FIELD_COUNT; i++) {
            generator.writeNumberProperty("f_"+i, i);
            generator.flush();
        }
        generator.writeEndObject();
        generator.writeName("b");
        generator.writeStartObject();
        for (int i=0; i < FIELD_COUNT; i++) {
            generator.writeNumberProperty("f_"+i, i);
            generator.flush();
        }
        generator.writeEndObject();
        generator.writeEndObject();
        generator.close();

        byte[] smile = byteOut.toByteArray();

        // then read it back; make sure to use InputStream to exercise block boundaries
        JsonParser p = _smileParser(new ByteArrayInputStream(smile));
        assertToken(p.nextToken(), JsonToken.START_OBJECT);

        assertToken(p.nextToken(), JsonToken.PROPERTY_NAME);
        assertEquals("a", p.currentName());
        assertToken(p.nextToken(), JsonToken.START_OBJECT);
        for (int i=0; i < FIELD_COUNT; i++) {
            assertToken(p.nextToken(), JsonToken.PROPERTY_NAME);
            assertEquals("f_"+i, p.currentName());
            assertToken(p.nextToken(), JsonToken.VALUE_NUMBER_INT);
            assertEquals(i, p.getIntValue());
        }
        assertToken(p.nextToken(), JsonToken.END_OBJECT);

        assertToken(p.nextToken(), JsonToken.PROPERTY_NAME);
        assertEquals("b", p.currentName());
        assertToken(p.nextToken(), JsonToken.START_OBJECT);
        for (int i=0; i < FIELD_COUNT; i++) {
            assertToken(p.nextToken(), JsonToken.PROPERTY_NAME);
            assertEquals("f_"+i, p.currentName());
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
        ObjectMapper mapper = smileMapper(false, true, false);
        JsonGenerator generator = mapper.writer()
                .with(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
                .createGenerator(byteOut);
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
        JsonParser p = _smileParser(new ByteArrayInputStream(smile));
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
