package com.fasterxml.jackson.dataformat.smile.failing;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;

public class SymbolTable312Test extends BaseTestForSmile
{
    private final ObjectMapper MAPPER = smileMapper();

    public void testNullHandling1Quad() throws Exception
    {
        _testNullHandling(1);
        _testNullHandling(2);
    }

    public void testNullHandling2Quads() throws Exception
    {
        _testNullHandling(5);
        _testNullHandling(6);
    }
    
    public void testNullHandling3Quads() throws Exception
    {
        _testNullHandling(9);
        _testNullHandling(10);
    }

    public void testNullHandlingNQuads() throws Exception
    {
        _testNullHandling(13);
        _testNullHandling(14);
        _testNullHandling(17);
        _testNullHandling(18);
        _testNullHandling(21);
    }

    public void _testNullHandling(int minNulls) throws Exception
    {
        final String FIELD1 = _nulls(minNulls);
        final String FIELD2 = _nulls(minNulls+1);
        final String FIELD3 = _nulls(minNulls+2);
        final String FIELD4 = _nulls(minNulls+3);
        final String FIELD5 = _nulls(minNulls+4);

        final String SRC = a2q(String.format("{'%s':'a','%s':'b','%s':'c','%s':'d','%s':'e'}",
                _quotedNulls(minNulls),
                _quotedNulls(minNulls+1),
                _quotedNulls(minNulls+2),
                _quotedNulls(minNulls+3),
                _quotedNulls(minNulls+4)));
        byte[] DOC = _smileDoc(SRC);

        try (JsonParser p = MAPPER.createParser(DOC)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _assertNullStrings(FIELD1, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("a", p.getText());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _assertNullStrings(FIELD2, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("b", p.getText());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _assertNullStrings(FIELD3, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("c", p.getText());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _assertNullStrings(FIELD4, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("d", p.getText());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            _assertNullStrings(FIELD5, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals("e", p.getText());

            assertToken(JsonToken.END_OBJECT, p.nextToken());
        }
    }

    private String _nulls(int len) {
        return new String(new byte[len], StandardCharsets.US_ASCII);
    }

    private String _quotedNulls(int len) {
        StringBuilder sb = new StringBuilder();
        while (--len >= 0) {
            sb.append("\\u0000");
        }
        return sb.toString();
    }

    private void _assertNullStrings(String exp, String actual) {
        if (exp.length() != actual.length()) {
            fail("Expected name with "+exp.length()+" null chars, got "+actual.length());
        }
        assertEquals(exp, actual);
    }
}
