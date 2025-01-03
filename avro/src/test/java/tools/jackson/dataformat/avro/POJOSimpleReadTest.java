package tools.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonParser.NumberType;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.SerializedString;

import tools.jackson.dataformat.avro.testsupport.ThrottledInputStream;

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
        JsonParser p = MAPPER.reader()
                .with(getEmployeeSchema())
                .createParser(smallReads ? ThrottledInputStream.wrap(in, 9) : in);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        if (!smallReads) {
            assertSame(in, p.streamReadInputSource());
        }

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertFalse(p.hasStringCharacters());
        assertEquals("name", p.currentName());
        assertEquals("name", p.getString());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(empl.name, p.getString());

        StringWriter sw  = new StringWriter();
        assertEquals(empl.name.length(), p.getString(sw));
        assertEquals(empl.name, sw.toString());

        assertTrue(p.hasStringCharacters());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("age", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals(Integer.valueOf(empl.age), p.getNumberValue());
        assertEquals(empl.age, p.getIntValue());
        assertFalse(p.isNaN());
        assertEquals((double) empl.age, p.getDoubleValue());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());

        assertEquals("emails", p.currentName());
        sw  = new StringWriter();
        assertEquals(6, p.getString(sw));
        assertEquals("emails", sw.toString());

        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(empl.emails[0], p.getString());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(empl.emails[1], p.getString());
        assertToken(JsonToken.END_ARRAY, p.nextToken());

        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("boss", p.currentName());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals("name", p.currentName());
        // and then skip various bits and pieces
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertTrue(p.nextName(new SerializedString("age")));
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        String n = p.nextName();
        assertEquals("emails", n);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertNull(p.nextName());
        assertToken(JsonToken.VALUE_STRING, p.currentToken());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertFalse(p.nextName(new SerializedString("bossy")));
        assertToken(JsonToken.PROPERTY_NAME, p.currentToken());
        assertEquals("boss", p.currentName());
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
        JsonParser p = MAPPER.createParser(avro);

        try {
            p.nextToken();
            fail("Should not pass");
        } catch (StreamReadException e) {
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
