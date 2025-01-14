package com.fasterxml.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.fail;

public class GeneratorInvalidCallsTest extends BaseTestForSmile
{
    private final SmileFactory SMILE_F = new SmileFactory();

    @Test
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
