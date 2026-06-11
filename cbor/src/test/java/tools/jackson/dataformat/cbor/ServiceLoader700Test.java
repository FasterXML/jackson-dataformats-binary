package tools.jackson.dataformat.cbor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#700]: SPI file referenced wrong class name
// (`...cbor.databind.CBORMapper` instead of `...cbor.CBORMapper`),
// causing `ServiceConfigurationError` from `ServiceLoader`.
public class ServiceLoader700Test extends CBORTestBase
{
    private final static String SERVICE_FILE =
            "META-INF/services/tools.jackson.databind.ObjectMapper";

    @Test
    public void testServiceFileClassNamesResolve() throws Exception
    {
        boolean foundCBORMapper = false;
        // Enumerate every copy of the SPI file on the path (cbor, smile, etc.)...
        Enumeration<URL> resources = getClass().getClassLoader().getResources(SERVICE_FILE);
        assertTrue(resources.hasMoreElements(),
                "Should find at least one `" + SERVICE_FILE + "` resource");
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            System.err.println("DEBUG url=" + url);
            try (InputStream in = url.openStream()) {
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    System.err.println("DEBUG line=" + line);
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    // Every class named in any SPI file must resolve to an `ObjectMapper`...
                    Class<?> cls = Class.forName(line);
                    assertTrue(ObjectMapper.class.isAssignableFrom(cls),
                            "Class `" + line + "` (from " + url + ") is not an `ObjectMapper` subtype");
                    if (cls == CBORMapper.class) {
                        foundCBORMapper = true;
                    }
                }
            }
        }
        assertTrue(foundCBORMapper,
                "SPI file should list `" + CBORMapper.class.getName() + "`");
    }
}
