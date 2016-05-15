package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class GeneratorDupHandlingTest extends BaseTestForSmile
{
    public void testSimpleDupsEagerlyBytes() throws Exception {
        _testSimpleDups(false, new JsonFactory());
    }

    // Testing ability to enable checking after construction of
    // generator, not just via JsonFactory
    public void testSimpleDupsLazilyBytes() throws Exception {
        final JsonFactory f = new JsonFactory();
        assertFalse(f.isEnabled(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION));
        _testSimpleDups(true, f);
    }

    @SuppressWarnings("resource")
    protected void _testSimpleDups(boolean lazySetting, JsonFactory f)
        throws Exception
    {
        // First: fine, when not checking
        if (!lazySetting) {
            _writeSimple0(_generator(f), "a");
            _writeSimple1(_generator(f), "b");
        }

        // but not when checking
        JsonGenerator g1;

        if (lazySetting) {
            g1 = _generator(f);            
            g1.enable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
        } else {
            f.enable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
            g1 = _generator(f);            
        }
        try {
            _writeSimple0(g1, "a");
            fail("Should have gotten exception");
        } catch (JsonGenerationException e) {
            verifyException(e, "duplicate field 'a'");
        }

        JsonGenerator g2;
        if (lazySetting) {
            g2 = _generator(f);            
            g2.enable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
        } else {
            g2 = _generator(f);            
        }
        try {
            _writeSimple1(g2, "x");
            fail("Should have gotten exception");
        } catch (JsonGenerationException e) {
            verifyException(e, "duplicate field 'x'");
        }
    }

    protected JsonGenerator _generator(JsonFactory f) throws IOException
    {
        return f.createGenerator(new ByteArrayOutputStream());
    }

    protected void _writeSimple0(JsonGenerator g, String name) throws IOException
    {
        g.writeStartObject();
        g.writeNumberField(name, 1);
        g.writeNumberField(name, 2);
        g.writeEndObject();
        g.close();
    }

    protected void _writeSimple1(JsonGenerator g, String name) throws IOException
    {
        g.writeStartArray();
        g.writeNumber(3);
        g.writeStartObject();
        g.writeNumberField("foo", 1);
        g.writeNumberField("bar", 1);
        g.writeNumberField(name, 1);
        g.writeNumberField("bar2", 1);
        g.writeNumberField(name, 2);
        g.writeEndObject();
        g.writeEndArray();
        g.close();
    }
}
