package tools.jackson.dataformat.avro.schema;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;
import tools.jackson.dataformat.avro.AvroTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Resolving the name of a record, enum or fixed schema must not load (and
// certainly not initialize) the class named by the (possibly untrusted) schema.
// As of 3.1.9 [dataformats-binary#908] no probing for nested classes is done
// for schema names at all: see `NestedClassTypeId908Test` for resolution of
// type ids into nested classes.
public class SchemaNameClassInitTest extends AvroTestBase
{
    static class InitTracker {
        static volatile boolean initialized;
        static volatile boolean enumInitialized;
        static volatile boolean fixedInitialized;
    }

    // Never referenced by test code: only named by the schema below
    static class NotToBeInitialized {
        static {
            InitTracker.initialized = true;
        }
    }

    // Likewise only named by ENUM / FIXED schemas
    enum EnumNotToBeInitialized {
        A, B;

        static {
            InitTracker.enumInitialized = true;
        }
    }

    static class FixedNotToBeInitialized {
        static {
            InitTracker.fixedInitialized = true;
        }
    }

    static class Inner {
        public String x;
    }

    static class Outer {
        public Inner value;
    }

    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testReadDoesNotInitializeClassNamedBySchema() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom("{\"type\":\"record\",\"name\":\"Outer\",\"fields\":["
                +"{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"NotToBeInitialized\","
                +"\"namespace\":\"" + SchemaNameClassInitTest.class.getName() + "\","
                +"\"fields\":[{\"name\":\"x\",\"type\":\"string\"}]}}]}");
        Outer result = MAPPER.readerFor(Outer.class)
                .with(schema)
                .readValue(new byte[] { 2, 'a' });
        assertEquals("a", result.value.x);

        // Name is that of schema, with no probing for nested class...
        assertEquals(SchemaNameClassInitTest.class.getName() + ".NotToBeInitialized",
                AvroSchemaHelper.getFullName(schema.getAvroSchema().getField("value").schema()));
        // ... and without running static initializer of the class
        assertFalse(InitTracker.initialized);
    }

    @Test
    public void testEnumSchemaDoesNotInitializeNamedClass() throws Exception
    {
        Schema schema = Schema.createEnum("EnumNotToBeInitialized", null,
                SchemaNameClassInitTest.class.getName(), Arrays.asList("A", "B"));
        assertEquals(SchemaNameClassInitTest.class.getName() + ".EnumNotToBeInitialized",
                AvroSchemaHelper.getFullName(schema));
        assertFalse(InitTracker.enumInitialized);
    }

    @Test
    public void testFixedSchemaDoesNotInitializeNamedClass() throws Exception
    {
        Schema schema = Schema.createFixed("FixedNotToBeInitialized", null,
                SchemaNameClassInitTest.class.getName(), 4);
        assertEquals(SchemaNameClassInitTest.class.getName() + ".FixedNotToBeInitialized",
                AvroSchemaHelper.getFullName(schema));
        assertFalse(InitTracker.fixedInitialized);
    }

    // Namespace with more dots than any real class name must not be probed
    // for nested classes (one class loading attempt per dot)
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testNoClassProbingForExcessivelyDottedNamespace() throws Exception
    {
        StringBuilder sb = new StringBuilder("a");
        for (int i = 0; i < 20_000; ++i) {
            sb.append(".a");
        }
        final String namespace = sb.toString();
        Schema schema = Schema.createRecord("Rec", null, namespace, false);
        assertEquals(namespace + ".Rec", AvroSchemaHelper.getFullName(schema));
    }

    // Over-long name with few dots: fallback to plain dotted name
    @Test
    public void testNoClassProbingForExcessivelyLongNamespace() throws Exception
    {
        final String namespace = "a.b.c." + _longName(2000);
        Schema schema = Schema.createRecord("Rec", null, namespace, false);
        assertEquals(namespace + ".Rec", AvroSchemaHelper.getFullName(schema));
    }

    private static String _longName(int len) {
        StringBuilder sb = new StringBuilder(len);
        while (sb.length() < len) {
            sb.append('x');
        }
        return sb.toString();
    }

    // Legacy (Avro before 1.9) namespace with trailing dollar sign: name appended as-is
    @Test
    public void testLegacyDollarNamespace() throws Exception
    {
        Schema schema = Schema.createRecord("Inner", null,
                SchemaNameClassInitTest.class.getName() + "$", false);
        assertEquals(SchemaNameClassInitTest.class.getName() + "$Inner",
                AvroSchemaHelper.getFullName(schema));
    }

    // [dataformats-binary#908]: no probing, so dotted name even for existing nested class
    @Test
    public void testNestedClassNameNotProbed() throws Exception
    {
        Schema schema = Schema.createRecord("Inner", null,
                SchemaNameClassInitTest.class.getName(), false);
        assertEquals(SchemaNameClassInitTest.class.getName() + ".Inner",
                AvroSchemaHelper.getFullName(schema));
    }
}
