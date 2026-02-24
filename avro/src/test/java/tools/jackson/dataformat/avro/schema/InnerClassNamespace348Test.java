package tools.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.*;

// Tests for [dataformats-binary#348]: inner class schema namespace must not contain '$'
// (actual fix already in via [dataformats-binary#167])
public class InnerClassNamespace348Test extends AvroTestBase
{
    // Outer class matching the issue's "Test" class
    static class Outer348 {
        // Inner class matching the issue's "TestImpl"
        static class Inner348 {
            public int value;
            public String name;
        }
    }

    // Three-level nesting: OuterOuter348 -> Outer348b -> Inner348b
    static class OuterOuter348 {
        static class Outer348b {
            static class Inner348b {
                public int x;
            }
        }
    }

    private final AvroMapper MAPPER = newMapper();
    
    // [dataformats-binary#348]: namespace of inner-class record schema must not contain '$'
    @Test
    public void testInnerClassNamespaceNosDollarSign() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(Outer348.Inner348.class, gen);
        Schema schema = gen.getGeneratedSchema().getAvroSchema();

        final String namespace = schema.getNamespace();
        final String name = schema.getName();

        // Name must be the simple class name
        assertEquals("Inner348", name);

        // Namespace must NOT contain '$'
        assertFalse(namespace.contains("$"),
                "Namespace must not contain '$' but was: " + namespace);

        // Namespace must end with the outer class name, separated by '.'
        assertTrue(namespace.endsWith(".Outer348"),
                "Namespace must end with '.Outer348' but was: " + namespace);
    }

    // [dataformats-binary#348]: deeper nesting must also produce '$'-free namespaces
    @Test
    public void testDeeplyNestedClassNamespaceNoDollarSign() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(OuterOuter348.Outer348b.Inner348b.class, gen);
        Schema schema = gen.getGeneratedSchema().getAvroSchema();

        final String namespace = schema.getNamespace();
        final String name = schema.getName();

        assertEquals("Inner348b", name);

        assertFalse(namespace.contains("$"),
                "Namespace must not contain '$' but was: " + namespace);

        // Namespace must use '.' separators through all nesting levels
        assertTrue(namespace.endsWith(".OuterOuter348.Outer348b"),
                "Namespace must end with '.OuterOuter348.Outer348b' but was: " + namespace);
    }

    // [dataformats-binary#348]: schema with inner class must be usable for roundtrip
    @Test
    public void testInnerClassRoundtrip() throws Exception
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        MAPPER.acceptJsonFormatVisitor(Outer348.Inner348.class, gen);
        AvroSchema schema = gen.getGeneratedSchema();

        // Schema must be usable for write+read without errors
        Outer348.Inner348 input = new Outer348.Inner348();
        input.value = 42;
        input.name = "hello";

        byte[] encoded = MAPPER.writer(schema).writeValueAsBytes(input);
        Outer348.Inner348 decoded = MAPPER.readerFor(Outer348.Inner348.class)
                .with(schema)
                .readValue(encoded);

        assertEquals(input.value, decoded.value);
        assertEquals(input.name, decoded.name);
    }
}
