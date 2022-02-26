package com.fasterxml.jackson.dataformat.cbor.failing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class SymbolTable312Test extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#312]: null handling
    public void testNullHandling() throws Exception
    {
        final String FIELD1 = "\u0000";
        final String FIELD2 = FIELD1 + FIELD1;
        final String FIELD3 = FIELD2 + FIELD1;

        final String QUOTED_NULL = "\\u0000";

        final String SRC = a2q(String.format("{'%s':'a','%s':'b','%s':'c'}",
                QUOTED_NULL, QUOTED_NULL+QUOTED_NULL, QUOTED_NULL+QUOTED_NULL+QUOTED_NULL));
        byte[] DOC = cborDoc(SRC);

        try (JsonParser p = MAPPER.createParser(DOC)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            _assertNullStrings(FIELD1, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("a", p.getText());

            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            _assertNullStrings(FIELD2, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("b", p.getText());

            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            _assertNullStrings(FIELD3, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("c", p.getText());

            assertToken(JsonToken.END_OBJECT, p.nextToken());
        }
    }

    private void _assertNullStrings(String exp, String actual) {
        if (exp.length() != actual.length()) {
            fail("Expected "+exp.length()+" nulls, got "+actual.length());
        }
        assertEquals(exp, actual);
    }
}
