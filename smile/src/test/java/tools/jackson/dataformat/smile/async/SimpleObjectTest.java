package tools.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser.NumberType;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

public class SimpleObjectTest extends AsyncTestBase
{
    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    /*
    @JsonPropertyOrder(alphabetic=true)
    static class BooleanBean {
        public boolean a, b, ac, abcde, e;
    }
     */

    private final static String UNICODE_SHORT_NAME = "Unicode"+UNICODE_3BYTES+"RlzOk";

    public void testBooleans() throws IOException
    {
        byte[] data = _smileDoc(aposToQuotes("{ 'a':true, 'b':false, 'acdc':true, '"+UNICODE_SHORT_NAME+"':true, 'a1234567':false }"), true);
        // first, no offsets
        _testBooleans(data, 0, 100);
        _testBooleans(data, 0, 3);
        _testBooleans(data, 0, 1);

        // then with some
        _testBooleans(data, 1, 100);
        _testBooleans(data, 1, 3);
        _testBooleans(data, 1, 1);
    }

    private void _testBooleans(byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(_smileReader(true), readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_OBJECT, r.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("a", r.currentText());
        // by default no cheap access to char[] version:
        assertFalse(r.parser().hasTextCharacters());
        // but... 
        char[] ch = r.parser().getTextCharacters();
        assertEquals(0, r.parser().getTextOffset());
        assertEquals(1, r.parser().getTextLength());
        assertEquals("a", new String(ch, 0, 1));

        // 04-Nov-2019, tatu: Changed in 3.0 (remove use of namecopybuffer)
        assertFalse(r.parser().hasTextCharacters());
        
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("b", r.currentText());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("acdc", r.currentText());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals(UNICODE_SHORT_NAME, r.currentText());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("a1234567", r.currentText());
        assertToken(JsonToken.VALUE_FALSE, r.nextToken());

        // and for fun let's verify can't access this as number or binary
        try {
            r.getDoubleValue();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Current token (VALUE_FALSE) not numeric");
        }
        try {
            r.parser().getBinaryValue();
            fail("Should not pass");
        } catch (StreamReadException e) {
            verifyException(e, "Current token (VALUE_FALSE) not");
            verifyException(e, "can not access as binary");
        }

        assertToken(JsonToken.END_OBJECT, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    /*
    static class  {
        public int i1;
        public double doubley;
        public BigDecimal biggieDecimal;
    }
    */

    private final int NUMBER_EXP_I = -123456789;
    private final double NUMBER_EXP_D = 1024798.125;
    private final BigDecimal NUMBER_EXP_BD = new BigDecimal("1243565768679065.1247305834");

    public void testNumbers() throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        JsonGenerator g = _smileGenerator(bytes, true);
        g.writeStartObject();
        g.writeNumberProperty("i1", NUMBER_EXP_I);
        g.writeNumberProperty("doubley", NUMBER_EXP_D);
        g.writeNumberProperty("biggieDecimal", NUMBER_EXP_BD);
        g.writeEndObject();
        g.close();
        byte[] data = bytes.toByteArray();

        // first, no offsets
        _testNumbers(data, 0, 100);
        _testNumbers(data, 0, 3);
        _testNumbers(data, 0, 1);

        // then with some
        _testNumbers(data, 1, 100);
        _testNumbers(data, 1, 3);
        _testNumbers(data, 1, 1);
    }

    private void _testNumbers(byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(_smileReader(true), readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_OBJECT, r.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("i1", r.currentText());
        assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
        assertEquals(NumberType.INT, r.getNumberType());
        assertEquals(NUMBER_EXP_I, r.getIntValue());
        assertEquals((double)NUMBER_EXP_I, r.getDoubleValue());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("doubley", r.currentText());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, r.nextToken());
        assertEquals(NumberType.DOUBLE, r.getNumberType());
        assertEquals(NUMBER_EXP_D, r.getDoubleValue());
        assertEquals((long) NUMBER_EXP_D, r.getLongValue());

        assertToken(JsonToken.PROPERTY_NAME, r.nextToken());
        assertEquals("biggieDecimal", r.currentText());
        assertToken(JsonToken.VALUE_NUMBER_FLOAT, r.nextToken());
        assertEquals(NumberType.BIG_DECIMAL, r.getNumberType());
        assertEquals(NUMBER_EXP_BD, r.getBigDecimalValue());
        assertEquals(""+NUMBER_EXP_BD, r.currentText());

        assertToken(JsonToken.END_OBJECT, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }
}
