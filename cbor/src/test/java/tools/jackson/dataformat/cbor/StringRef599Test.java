package tools.jackson.dataformat.cbor;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringRef599Test extends CBORTestBase
{
    private final ObjectMapper VANILLA_MAPPER = cborMapper();
    private final ObjectMapper REF_MAPPER = cborMapper(cborFactoryBuilder()
            .enable(CBORWriteFeature.STRINGREF)
            .build());

    // [dataformats-binary#599]
    @Test
    public void testDupsNoStringRef() throws Exception
    {
        _testStringRef(VANILLA_MAPPER);
    }

    // [dataformats-binary#599]
    @Test
    public void testDupsWithStringRef() throws Exception
    {
        _testStringRef(REF_MAPPER);
    }

    // [dataformats-binary#669]: STRINGREF enabled via mapper builder (not factory builder)
    @Test
    public void testDupsWithStringRefViaMapperBuilder() throws Exception
    {
        ObjectMapper mapper = CBORMapper.builder()
                .enable(CBORWriteFeature.STRINGREF)
                .build();
        List<?> original = Arrays.asList("foo", "foo");
        byte[] cbor = mapper.writeValueAsBytes(original);
        List<?> deserialized = mapper.readValue(cbor, List.class);
        assertEquals(original, deserialized);
    }

    private void _testStringRef(ObjectMapper mapper) throws Exception
    {
        byte[] cbor = mapper.writeValueAsBytes(Arrays.asList("foo", "foo"));
        try (CBORParser p = cborParser(cbor)) {
             assertToken(JsonToken.START_ARRAY, p.nextToken());
             assertToken(JsonToken.VALUE_STRING, p.nextToken());
             // important! Skip String value
             assertToken(JsonToken.VALUE_STRING, p.nextToken());
             // equally important; try to check second instance
             assertEquals("foo", p.getString());
             assertToken(JsonToken.END_ARRAY, p.nextToken());
        }
    }
}
