package tools.jackson.dataformat.cbor;

import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadCapability;
import tools.jackson.databind.ObjectMapper;

public class FormatDefaultsTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testParserDefaults() throws Exception
    {
        try (JsonParser p = MAPPER.createParser(new byte[4])) {
            // 15-Jan-2021, tatu: 2.14 added this setting, not enabled in
            //    default set
            assertTrue(p.streamReadCapabilities().isEnabled(StreamReadCapability.EXACT_FLOATS));
        }
    }
}
