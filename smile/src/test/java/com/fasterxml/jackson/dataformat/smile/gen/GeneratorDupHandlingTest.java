package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class GeneratorDupHandlingTest extends BaseTestForSmile
{
    public void testSimpleDupsEagerlyBytes() {
        _testSimpleDups(false, new SmileFactory());
    }

    // NOTE: with 3.0, no more "lazy" option for enabling duplicate detection
    
    @SuppressWarnings("resource")
    protected void _testSimpleDups(boolean lazySetting, TokenStreamFactory f)
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
            g1.configure(StreamWriteFeature.STRICT_DUPLICATE_DETECTION, true);
        } else {
            f = f.rebuild().enable(StreamWriteFeature.STRICT_DUPLICATE_DETECTION).build();
            g1 = _generator(f);            
        }
        try {
            _writeSimple0(g1, "a");
            fail("Should have gotten exception");
        } catch (StreamWriteException e) {
            verifyException(e, "duplicate Object property \"a\"");
        }

        JsonGenerator g2;
        if (lazySetting) {
            g2 = _generator(f);            
            g2.configure(StreamWriteFeature.STRICT_DUPLICATE_DETECTION, true);
        } else {
            g2 = _generator(f);            
        }
        try {
            _writeSimple1(g2, "x");
            fail("Should have gotten exception");
        } catch (StreamWriteException e) {
            verifyException(e, "duplicate Object property \"x\"");
        }
    }

    protected JsonGenerator _generator(TokenStreamFactory f)
    {
        return f.createGenerator(ObjectWriteContext.empty(), new ByteArrayOutputStream());
    }

    protected void _writeSimple0(JsonGenerator g, String name)
    {
        g.writeStartObject();
        g.writeNumberProperty(name, 1);
        g.writeNumberProperty(name, 2);
        g.writeEndObject();
        g.close();
    }

    protected void _writeSimple1(JsonGenerator g, String name)
    {
        g.writeStartArray();
        g.writeNumber(3);
        g.writeStartObject();
        g.writeNumberProperty("foo", 1);
        g.writeNumberProperty("bar", 1);
        g.writeNumberProperty(name, 1);
        g.writeNumberProperty("bar2", 1);
        g.writeNumberProperty(name, 2);
        g.writeEndObject();
        g.writeEndArray();
        g.close();
    }
}
