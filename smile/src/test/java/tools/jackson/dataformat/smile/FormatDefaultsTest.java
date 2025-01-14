package tools.jackson.dataformat.smile;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadCapability;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FormatDefaultsTest extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    @Test
    public void testParserDefaults() throws Exception
    {
        try (JsonParser p = MAPPER.createParser(new byte[4])) {
            // 15-Jan-2021, tatu: 2.14 added this setting, not enabled in
            //    default set
            assertTrue(p.streamReadCapabilities().isEnabled(StreamReadCapability.EXACT_FLOATS));
        }
    }
}
