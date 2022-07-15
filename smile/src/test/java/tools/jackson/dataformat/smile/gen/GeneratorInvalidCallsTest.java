package tools.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.dataformat.smile.*;

public class GeneratorInvalidCallsTest extends BaseTestForSmile
{
    public void testInvalidFieldNameInRoot()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator gen = _smileGenerator(out, true);
        try {
            gen.writeStringProperty("a", "b");
            fail("Should NOT allow writing of PROPERTY_NAME in root context");
        } catch (StreamWriteException e) {
            verifyException(e, "Cannot write a property name");
        }
        gen.close();
    }
}
