package tools.jackson.dataformat.cbor.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

public class MapperSimpleReadTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();
    
    public void testSimpleArray() throws Exception
    {
        byte[] b = MAPPER.writeValueAsBytes(new int[] { 1, 2, 3, 4});
        int[] output = MAPPER.readValue(b, int[].class);
        assertEquals(4, output.length);
        for (int i = 1; i <= output.length; ++i) {
            assertEquals(i, output[i-1]);
        }
    }

    public void testSimpleObject() throws Exception
    {
        Map<String,Object> input = new LinkedHashMap<String,Object>();
        input.put("a", 1);
        input.put("bar", "foo");
        final String NON_ASCII_NAME = "Y\\u00F6";
        input.put(NON_ASCII_NAME, -3.25);
        input.put("", "");
        byte[] b = MAPPER.writeValueAsBytes(input);

        // First, using streaming API
        JsonParser p = cborParser(b);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("a", p.currentName());
        assertEquals("{\"a\"}", p.streamReadContext().toString());

        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(1, p.getIntValue());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("bar", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("foo", p.getText());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals(NON_ASCII_NAME, p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken());
        assertEquals(-3.25, p.getDoubleValue());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("", p.getText());
        
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        p.close();
        
        Map<?,?> output = MAPPER.readValue(b, Map.class);
        assertEquals(4, output.size());
        assertEquals(Integer.valueOf(1), output.get("a"));
        assertEquals("foo", output.get("bar"));
        assertEquals(Double.valueOf(-3.25), output.get(NON_ASCII_NAME));
        assertEquals("", output.get(""));
    }

}
