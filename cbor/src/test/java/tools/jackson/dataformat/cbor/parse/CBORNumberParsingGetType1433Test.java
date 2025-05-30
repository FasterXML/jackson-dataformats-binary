package tools.jackson.dataformat.cbor.parse;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CBORNumberParsingGetType1433Test
    extends CBORTestBase
{
    private final CBORFactory JSON_F = cborFactory();

    @Test
    void getNumberType() throws Exception
    {
       JsonParser p;

        p = _createParser(jsonFactory(), " 123 ");
        _verifyGetNumberTypeFail(p, "null");
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonParser.NumberType.INT, p.getNumberType());
        assertNull(p.nextToken());
        _verifyGetNumberTypeFail(p, "null");
        p.close();
        _verifyGetNumberTypeFail(p, "null");

        p = _createParser(jsonFactory(), " -9 false ");
        _verifyGetNumberTypeFail(p, "null");
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonParser.NumberType.INT, p.getNumberType());
        assertToken(JsonToken.VALUE_FALSE, p.nextToken());
        _verifyGetNumberTypeFail(p, "VALUE_FALSE");
        assertNull(p.nextToken());
        _verifyGetNumberTypeFail(p, "null");
        p.close();
        _verifyGetNumberTypeFail(p, "null");

        p = _createParser(jsonFactory(), "[123, true]");
        _verifyGetNumberTypeFail(p, "null");
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        _verifyGetNumberTypeFail(p, "START_ARRAY");
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(JsonParser.NumberType.INT, p.getNumberType());
        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        _verifyGetNumberTypeFail(p, "VALUE_TRUE");
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        _verifyGetNumberTypeFail(p, "END_ARRAY");
        assertNull(p.nextToken());
        _verifyGetNumberTypeFail(p, "null");
        p.close();
        _verifyGetNumberTypeFail(p, "null");
    }

    private void _verifyGetNumberTypeFail(JsonParser p, String token) throws Exception
    {
        // In 2.x got exception; in 3.x null
        assertNull(p.getNumberType());
    }

    private CBORFactory jsonFactory() {
        return JSON_F;
    }

    private JsonParser _createParser(CBORFactory f, String text) throws Exception {
        return f.createParser(ObjectReadContext.empty(), cborDoc(text));
    }
}
