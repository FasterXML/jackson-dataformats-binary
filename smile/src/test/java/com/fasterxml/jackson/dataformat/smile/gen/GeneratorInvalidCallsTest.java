package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.exc.StreamWriteException;

import com.fasterxml.jackson.dataformat.smile.*;

public class GeneratorInvalidCallsTest extends BaseTestForSmile
{
    public void testInvalidFieldNameInRoot()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = _smileGenerator(out, true);
        try {
            gen.writeStringField("a", "b");
            fail("Should NOT allow writing of FIELD_NAME in root context");
        } catch (StreamWriteException e) {
            verifyException(e, "Can not write a field name");
        }
        gen.close();
    }
}
