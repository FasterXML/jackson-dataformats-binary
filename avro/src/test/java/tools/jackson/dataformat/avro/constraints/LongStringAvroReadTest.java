package tools.jackson.dataformat.avro.constraints;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#824]: `maxStringLength` must be enforced for long String
// values (ones not fully contained in input buffer) even if value fits in a
// single (recycled) text buffer segment: otherwise accessors that do not
// validate length, like `getString(Writer)`, would expose all of it
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

    // NOTE: Avro parser decodes String values eagerly, when advancing to
    // VALUE_STRING token, so that is where failure is expected (and not on
    // accessing contents)
    @Test
    public void testLongValue() throws Exception
    {
        final AvroMapper mapper = _constrainedMapper();
        // First: grow text buffer (to be recycled) by reading a value fully
        // contained in input; rejected, but only after buffer was grown
        _verifyFails(mapper, _doc("b".repeat(20_000)));

        // Then read value longer than input buffer, from stream: decoded
        // using (recycled) segment big enough to contain all of it
        _verifyFails(mapper, new ByteArrayInputStream(_doc("a".repeat(10_000))));
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

    private void _verifyFails(AvroMapper mapper, Object input) throws Exception
    {
        try (JsonParser p = (input instanceof byte[])
                ? mapper.reader().with(SCHEMA).createParser((byte[]) input)
                : mapper.reader().with(SCHEMA).createParser((InputStream) input)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            JsonToken t = p.nextToken();
            // (note: cannot use content accessors here, most of which validate length)
            fail("Should not pass, got "+t);
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
