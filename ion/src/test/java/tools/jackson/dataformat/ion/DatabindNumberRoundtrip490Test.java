package tools.jackson.dataformat.ion;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

// for [dataformats-binary#490]
public class DatabindNumberRoundtrip490Test
{
    private final IonObjectMapper BINARY_MAPPER = IonObjectMapper.builderForBinaryWriters()
            .build();
    private final IonObjectMapper TEXT_MAPPER = IonObjectMapper.builderForTextualWriters()
            .build();

    @Test
    public void testBinaryFloats() throws Exception {
        _floatRoundtrip(BINARY_MAPPER);
    }

    @Test
    public void testBinaryIntegers() throws Exception {
        _integerRoundtrip490(BINARY_MAPPER);
    }

    @Test
    public void testTextualFloats() throws Exception {
        _floatRoundtrip(TEXT_MAPPER);
    }

    @Test
    public void testTextualIntegers() throws Exception {
        _integerRoundtrip490(TEXT_MAPPER);
    }

    private void _floatRoundtrip(ObjectMapper mapper) throws Exception
    {
        final double d = 42.25d;
        final float f = 42.75f;

        _roundtrip490(mapper, d, d);

        // Ion oddity: "float"s get upgraded to "double"s, so...
        _roundtrip490(mapper, f, (double) f);
    }

    private void _integerRoundtrip490(ObjectMapper mapper) throws Exception
    {
        _roundtrip490(mapper, Integer.MAX_VALUE, Integer.MAX_VALUE);
        _roundtrip490(mapper, Long.MAX_VALUE, Long.MAX_VALUE);
    }
    
    private void _roundtrip490(ObjectMapper mapper,
            Object input, Object result)
        throws Exception
    {
        byte[] serialized = mapper.writeValueAsBytes(Collections.singletonMap("k", input));

        Map<?,?> deserialized = mapper.readValue(serialized, Map.class);
        assertEquals(result, deserialized.get("k"));
    }
}
