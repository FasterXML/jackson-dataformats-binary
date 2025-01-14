package tools.jackson.dataformat.smile.gen;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.fail;

public class GeneratorInvalidCallsTest extends BaseTestForSmile
{
    @Test
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
