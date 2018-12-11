package com.fasterxml.jackson.dataformat.cbor.filter;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.cbor.*;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.fasterxml.jackson.dataformat.cbor.util.PrefixInputDecorator;
import com.fasterxml.jackson.dataformat.cbor.util.PrefixOutputDecorator;

public class StreamingDecoratorsTest extends CBORTestBase
{
    public void testInputDecorators() throws Exception
    {
        final byte[] DOC = cborDoc("42   37");
        final CBORFactory streamF = CBORFactory.builder()
                .inputDecorator(new PrefixInputDecorator(DOC))
                .build();
        final CBORMapper mapper = CBORMapper.builder(streamF).build();
        JsonParser p = mapper.createParser(new byte[0], 0, 0);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(42, p.getIntValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(37, p.getIntValue());
        assertNull(p.nextToken());
        p.close();
    }

    public void testOutputDecorators() throws Exception
    {
        final byte[] DOC = cborDoc(" 137");

        final CBORFactory streamF = CBORFactory.builder()
                .outputDecorator(new PrefixOutputDecorator(DOC))
                .build();
        final CBORMapper mapper = CBORMapper.builder(streamF).build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        JsonGenerator g = mapper.createGenerator(bytes);
        g.writeString("foo");
        g.close();

        JsonParser p = mapper.createParser(bytes.toByteArray());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(137, p.getIntValue());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("foo", p.getText());
        assertNull(p.nextToken());
        p.close();
    }
}
