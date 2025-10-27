package com.fasterxml.jackson.dataformat.cbor;

import java.util.Arrays;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StringRef599Test extends CBORTestBase
{
    private final ObjectMapper VANILLA_MAPPER = cborMapper();
    private final ObjectMapper REF_MAPPER = cborMapper(cborFactoryBuilder()
            .enable(CBORGenerator.Feature.STRINGREF)
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

    private void _testStringRef(ObjectMapper mapper) throws Exception
    {
        byte[] cbor = mapper.writeValueAsBytes(Arrays.asList("foo", "foo"));
        try (CBORParser p = cborParser(cbor)) {
             assertToken(JsonToken.START_ARRAY, p.nextToken());
             assertToken(JsonToken.VALUE_STRING, p.nextToken());
             // important! Skip String value
             assertToken(JsonToken.VALUE_STRING, p.nextToken());
             // equally important; try to check second instance
             assertEquals("foo", p.getText());
             assertToken(JsonToken.END_ARRAY, p.nextToken());
        }
    }
}
