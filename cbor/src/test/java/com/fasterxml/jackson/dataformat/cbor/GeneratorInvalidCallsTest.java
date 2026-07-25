package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerationException;

import static org.junit.jupiter.api.Assertions.fail;

public class GeneratorInvalidCallsTest extends CBORTestBase
{
    @Test
    public void testInvalidFieldNameInRoot() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CBORGenerator gen = cborGenerator(out);
        try {
            gen.writeStringField("a", "b");
            fail("Should NOT allow writing of FIELD_NAME in root context");
        } catch (JsonGenerationException e) {
            verifyException(e, "Can not write a field name");
        }
        gen.close();
    }
}
