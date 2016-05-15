package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonGenerationException;

import com.fasterxml.jackson.dataformat.smile.*;

public class GeneratorInvalidCallsTest extends BaseTestForSmile
{
    final SmileFactory SMILE_F = new SmileFactory();

    public void testInvalidFieldNameInRoot() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileGenerator gen = SMILE_F.createGenerator(out);
        try {
            gen.writeStringField("a", "b");
            fail("Should NOT allow writing of FIELD_NAME in root context");
        } catch (JsonGenerationException e) {
            verifyException(e, "Can not write a field name");
        }
        gen.close();
    }
}
