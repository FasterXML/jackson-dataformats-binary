package tools.jackson.dataformat.protobuf;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Round-trip (write-then-read) coverage for a simple proto3 message with the
// scalar types (int64 / string / bool), including proto3 `option` declarations
// which must be tolerated by the schema loader.
public class Proto3UserRoundtripTest extends ProtobufTestBase
{
    static class User {
        public long id;
        public String name;
        public String email;
        public String language;
        public boolean active;

        protected User() { }

        public User(long id, String name, String email, String language, boolean active) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.language = language;
            this.active = active;
        }
    }

    private final static String SCHEMA_STR =
            "syntax = \"proto3\";\n"
            + "package jackson3;\n"
            + "option java_multiple_files = true;\n"
            + "option java_package = \"tools.jackson.proto3\";\n"
            + "\n"
            + "message User {\n"
            + "  int64 id = 1;\n"
            + "  string name = 2;\n"
            + "  string email = 3;\n"
            + "  string language = 4;\n"
            + "  bool active = 5;\n"
            + "}\n";

    private final ProtobufMapper MAPPER = newObjectMapper();

    @Test
    public void testUserRoundtrip() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR, "User");
        final ObjectWriter w = MAPPER.writer(schema);
        final ObjectReader r = MAPPER.readerFor(User.class).with(schema);

        User input = new User(123456789012L, "Bob Smith",
                "bob@example.com", "en", true);

        byte[] bytes = w.writeValueAsBytes(input);
        assertTrue(bytes.length > 0);

        User result = r.readValue(bytes);
        assertEquals(input.id, result.id);
        assertEquals(input.name, result.name);
        assertEquals(input.email, result.email);
        assertEquals(input.language, result.language);
        assertEquals(input.active, result.active);
    }

    // proto3 omits fields left at their default value on the wire; make sure such
    // a value round-trips back to the default rather than getting lost/corrupted.
    @Test
    public void testUserWithDefaultsRoundtrip() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(SCHEMA_STR, "User");
        final ObjectWriter w = MAPPER.writer(schema);
        final ObjectReader r = MAPPER.readerFor(User.class).with(schema);

        // id == 0, active == false, empty strings: all proto3 defaults
        User input = new User(0L, "", "", "", false);

        byte[] bytes = w.writeValueAsBytes(input);
        User result = r.readValue(bytes);

        assertEquals(0L, result.id);
        assertEquals("", result.name);
        assertEquals("", result.email);
        assertEquals("", result.language);
        assertEquals(false, result.active);
    }
}
