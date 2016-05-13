package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

public class GeneratorInvalidCallsTest extends CBORTestBase
{
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
