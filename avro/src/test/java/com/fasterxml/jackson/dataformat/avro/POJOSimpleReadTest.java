package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.dataformat.avro.testsupport.ThrottledInputStream;

public class POJOSimpleReadTest extends AvroTestBase
{
    private final AvroMapper MAPPER = new AvroMapper();

    public void testSimplePojoViaMapper() throws Exception
    {
        Employee empl = _simpleEmployee();
        byte[] avro = toAvro(empl);
        Employee result = getMapper().readerFor(Employee.class)
                .with(getEmployeeSchema())
                .readValue(avro);
        assertEquals(empl.name, result.name);
        assertEquals(empl.age, result.age);
        assertEquals(empl.emails.length, result.emails.length);

        assertNotNull(empl.boss);
        assertEquals(empl.boss.name, result.boss.name);
        assertEquals(empl.boss.age, result.boss.age);
        assertEquals(empl.boss.emails.length, result.boss.emails.length);
        assertNull(empl.boss.boss);
    }

    public void testSimplePojoViaParser() throws Exception
    {
        Employee empl = _simpleEmployee();
        byte[] avro = toAvro(empl);
        _testSimplePojoViaParser(empl, avro, false);
        _testSimplePojoViaParser(empl, avro, true);
    }

    private void _testSimplePojoViaParser(Employee empl, byte[] avro,
            boolean smallReads) throws Exception
    {
//        System.out.println("Bytes -> "+avro.length);
        InputStream in = new ByteArrayInputStream(avro);
        JsonParser p = MAPPER.getFactory().createParser(smallReads
                ? ThrottledInputStream.wrap(in, 9) : in);
        p.setSchema(getEmployeeSchema());
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        if (!smallReads) {
            assertSame(in, p.getInputSource());
        }

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertFalse(p.hasTextCharacters());
        assertEquals("name", p.getCurrentName());
        assertEquals("name", p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(empl.name, p.getText());

        StringWriter sw  = new StringWriter();
        assertEquals(empl.name.length(), p.getText(sw));
        assertEquals(empl.name, sw.toString());

        assertTrue(p.hasTextCharacters());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("age", p.getCurrentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(Integer.valueOf(empl.age), p.getNumberValue());
        assertEquals(empl.age, p.getIntValue());
        assertFalse(p.isNaN());
        assertEquals((double) empl.age, p.getDoubleValue());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());

        assertEquals("emails", p.getCurrentName());
        sw  = new StringWriter();
        assertEquals(6, p.getText(sw));
        assertEquals("emails", sw.toString());

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(empl.emails[0], p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(empl.emails[1], p.getText());
        assertToken(JsonToken.END_ARRAY, p.nextToken());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("boss", p.getCurrentName());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("name", p.getCurrentName());
        // and then skip various bits and pieces
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertTrue(p.nextFieldName(new SerializedString("age")));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        String n = p.nextFieldName();
        assertEquals("emails", n);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertNull(p.nextFieldName());
        assertToken(JsonToken.VALUE_STRING, p.currentToken());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertFalse(p.nextFieldName(new SerializedString("bossy")));
        assertToken(JsonToken.FIELD_NAME, p.currentToken());
        assertEquals("boss", p.getCurrentName());
        assertToken(JsonToken.VALUE_NULL, p.nextToken());

        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        // should have consumed it all, but let's verify
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        assertEquals(0, p.releaseBuffered(b));
        b.close();
        // also...
        sw = new StringWriter();
        assertEquals(-1, p.releaseBuffered(sw));

        p.close();
    }

    public void testMissingSchema() throws Exception
    {
        Employee empl = _simpleEmployee();
        byte[] avro = toAvro(empl);
        JsonParser p = MAPPER.getFactory().createParser(avro);

        try {
            p.nextToken();
            fail("Should not pass");
        } catch (JsonParseException e) {
            verifyException(e, "No AvroSchema set");
        }
        p.close();
    }

    private Employee _simpleEmployee() {
        Employee boss = new Employee();
        boss.name = "Pointy";
        boss.age = 25;
        boss.emails = new String[] { "phb@oracle.com" };
        boss.boss = null;

        Employee empl = new Employee();
        empl.name = "Bob";
        empl.age = 39;
        empl.emails = new String[] { "bob@aol.com", "Bob@gmail.com" };

        // NOTE: currently problematic (gives us a union...)
        empl.boss = boss;
        return empl;
    }
}
