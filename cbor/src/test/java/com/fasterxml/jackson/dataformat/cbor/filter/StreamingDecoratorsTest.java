package com.fasterxml.jackson.dataformat.cbor.filter;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.testutil.PrefixInputDecorator;
import com.fasterxml.jackson.dataformat.cbor.testutil.PrefixOutputDecorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StreamingDecoratorsTest extends CBORTestBase
{
    @Test
    public void testInputDecorators() throws Exception
    {
        final byte[] DOC = cborDoc("42   37");
        final CBORFactory streamF = cborFactoryBuilder()
                .inputDecorator(new PrefixInputDecorator(DOC))
                .build();
        JsonParser p = streamF.createParser(new byte[0], 0, 0);
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(42, p.getIntValue());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(37, p.getIntValue());
        assertNull(p.nextToken());
        p.close();
    }

    @Test
    public void testOutputDecorators() throws Exception
    {
        final byte[] DOC = cborDoc(" 137");
        final CBORFactory streamF = cborFactoryBuilder()
                .outputDecorator(new PrefixOutputDecorator(DOC))
                .build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        JsonGenerator g = streamF.createGenerator(bytes);
        g.writeString("foo");
        g.close();

        JsonParser p = streamF.createParser(bytes.toByteArray());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(137, p.getIntValue());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("foo", p.getText());
        assertNull(p.nextToken());
        p.close();
    }
}
