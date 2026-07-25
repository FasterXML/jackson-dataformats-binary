package tools.jackson.dataformat.cbor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#700]: SPI file referenced wrong class name
// (`...cbor.databind.CBORMapper` instead of `...cbor.CBORMapper`),
// causing `ServiceConfigurationError` from `ServiceLoader` for classpath
// (non-modular) usage.
//
// NOTE: the SPI file only governs classpath usage; modular usage is covered by
// the `provides` directive in `module-info.java`. Since the test harness runs on
// the module path (where the module's own resources are not reachable), this test
// validates the source SPI file content directly instead of via `ServiceLoader`.
public class ServiceLoader700Test extends CBORTestBase
{
    private final static File SERVICE_FILE = new File(
            "src/main/resources/META-INF/services/tools.jackson.databind.ObjectMapper");

    @Test
    public void testServiceFileClassNamesResolve() throws Exception
    {
        assertTrue(SERVICE_FILE.exists(), "Missing SPI file: " + SERVICE_FILE.getAbsolutePath());
        boolean foundCBORMapper = false;
        List<String> lines = Files.readAllLines(SERVICE_FILE.toPath(), StandardCharsets.UTF_8);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // Class named in SPI file must actually exist and be an `ObjectMapper`...
            Class<?> cls = Class.forName(line);
            assertTrue(ObjectMapper.class.isAssignableFrom(cls),
                    "Class `" + line + "` is not an `ObjectMapper` subtype");
            if (cls == CBORMapper.class) {
                foundCBORMapper = true;
            }
        }
        assertTrue(foundCBORMapper,
                "SPI file should list `" + CBORMapper.class.getName() + "`");
    }
}
