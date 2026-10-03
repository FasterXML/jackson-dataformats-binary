package tools.jackson.dataformat.avro.constraints;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#824]: `maxStringLength` must be enforced for long String
// values (ones not fully contained in input buffer) regardless of accessor
// used, even if value fits in a single (recycled) text buffer segment
public class LongStringAvroReadTest extends AvroTestBase
{
    static class Value {
        public String value;

        public Value() { }
        public Value(String v) { value = v; }
    }

    private final static int MAX_STRING_LEN = 10;

    private final AvroMapper MAPPER_VANILLA = new AvroMapper();

    private final AvroSchema SCHEMA = MAPPER_VANILLA.schemaFrom(
            "{\"type\":\"record\",\"name\":\"Value\",\"fields\":["
            +"{\"name\":\"value\",\"type\":\"string\"}]}");

    @Test
    public void testLongValueViaStringCharacters() throws Exception
    {
        _verifyFails(p -> "char["+p.getStringCharacters().length+"]");
    }

    @Test
    public void testLongValueViaStringLength() throws Exception
    {
        _verifyFails(p -> "length "+p.getStringLength());
    }

    @Test
    public void testLongValueViaStringWriter() throws Exception
    {
        _verifyFails(p -> "chars written: "+p.getString(new StringWriter()));
    }

    // Values within limit must still be accepted
    @Test
    public void testValueWithinLimit() throws Exception
    {
        final AvroMapper mapper = _constrainedMapper();
        final String value = "abcdeñ";
        try (JsonParser p = mapper.reader().with(SCHEMA)
                .createParser(new ByteArrayInputStream(_doc(value)))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertEquals(value, new String(p.getStringCharacters(),
                    p.getStringOffset(), p.getStringLength()));
            assertToken(JsonToken.END_OBJECT, p.nextToken());
        }
    }

    private void _verifyFails(Function<JsonParser, String> accessor) throws Exception
    {
        final AvroMapper mapper = _constrainedMapper();
        // First: grow text buffer (to be recycled) by reading a value fully
        // contained in input; rejected, but only after buffer was grown
        try (JsonParser p = mapper.reader().with(SCHEMA)
                .createParser(_doc("b".repeat(20_000)))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            p.getString();
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            _verifyStringLengthException(e);
        }

        // Then read value longer than input buffer, from stream: decoded
        // using (recycled) segment big enough to contain all of it
        try (JsonParser p = mapper.reader().with(SCHEMA)
                .createParser(new ByteArrayInputStream(_doc("a".repeat(10_000))))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            String result = accessor.apply(p);
            fail("Should not pass, got "+result);
        } catch (StreamConstraintsException e) {
            _verifyStringLengthException(e);
        }
    }

    private void _verifyStringLengthException(StreamConstraintsException e) {
        assertTrue(e.getMessage().startsWith("String value length"),
                "Unexpected message: "+e.getMessage());
    }

    // New mapper (and factory) for each test, to have separate buffer recycler pool
    private AvroMapper _constrainedMapper() {
        return new AvroMapper(AvroFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(MAX_STRING_LEN)
                        .build())
                .build());
    }

    private byte[] _doc(String value) throws Exception {
        return MAPPER_VANILLA.writerFor(Value.class).with(SCHEMA)
                .writeValueAsBytes(new Value(value));
    }
}
