package tools.jackson.dataformat.smile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#700]: SPI file referenced wrong class name
// (`...smile.databind.SmileMapper` instead of `...smile.SmileMapper`),
// causing `ServiceConfigurationError` from `ServiceLoader`.
public class ServiceLoader700Test extends BaseTestForSmile
{
    private final static String SERVICE_FILE =
            "META-INF/services/tools.jackson.databind.ObjectMapper";

    @Test
    public void testServiceFileClassNamesResolve() throws Exception
    {
        boolean foundSmileMapper = false;
        try (InputStream in = getClass().getModule().getResourceAsStream(SERVICE_FILE)) {
            assertNotNull(in, "Missing SPI resource: " + SERVICE_FILE);
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // Class named in SPI file must actually exist and be an `ObjectMapper`...
                Class<?> cls = Class.forName(line);
                assertTrue(ObjectMapper.class.isAssignableFrom(cls),
                        "Class `" + line + "` is not an `ObjectMapper` subtype");
                if (cls == SmileMapper.class) {
                    foundSmileMapper = true;
                }
            }
        }
        assertTrue(foundSmileMapper,
                "SPI file should list `" + SmileMapper.class.getName() + "`");
    }
}
