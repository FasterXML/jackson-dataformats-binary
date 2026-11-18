package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.Arrays;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// [dataformats-binary#906], [dataformats-binary#908]: resolving full name (type id)
// of a record, enum or fixed schema must not load, let alone initialize, the class
// named by the (possibly untrusted) schema; matching against a known class works
// without class loading
public class SchemaFullNameTest extends AvroTestBase
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

        static class Deeper { }
    }

    static class Outer {
        public Inner value;
    }

    private final static String TEST_CLASS_NAME = SchemaFullNameTest.class.getName();

    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testReadDoesNotInitializeClassNamedBySchema() throws Exception
    {
        AvroSchema schema = MAPPER.schemaFrom("{\"type\":\"record\",\"name\":\"Outer\",\"fields\":["
                +"{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"NotToBeInitialized\","
                +"\"namespace\":\"" + TEST_CLASS_NAME + "\","
                +"\"fields\":[{\"name\":\"x\",\"type\":\"string\"}]}}]}");
        Outer result = MAPPER.readerFor(Outer.class)
                .with(schema)
                .readValue(new byte[] { 2, 'a' });
        assertEquals("a", result.value.x);

        // Name is not resolved to that of nested class
        assertEquals(TEST_CLASS_NAME + ".NotToBeInitialized",
                AvroSchemaHelper.getFullName(schema.getAvroSchema().getField("value").schema()));
        assertFalse(InitTracker.initialized);
    }

    @Test
    public void testEnumSchemaDoesNotInitializeNamedClass() throws Exception
    {
        Schema schema = Schema.createEnum("EnumNotToBeInitialized", null,
                TEST_CLASS_NAME, Arrays.asList("A", "B"));
        assertEquals(TEST_CLASS_NAME + ".EnumNotToBeInitialized",
                AvroSchemaHelper.getFullName(schema));
        assertFalse(InitTracker.enumInitialized);
    }

    @Test
    public void testFixedSchemaDoesNotInitializeNamedClass() throws Exception
    {
        Schema schema = Schema.createFixed("FixedNotToBeInitialized", null,
                TEST_CLASS_NAME, 4);
        assertEquals(TEST_CLASS_NAME + ".FixedNotToBeInitialized",
                AvroSchemaHelper.getFullName(schema));
        assertFalse(InitTracker.fixedInitialized);
    }

    // Namespace that uses dollar sign for nested classes (Avro before 1.9)
    @Test
    public void testFullNameWithDollarNamespace() throws Exception
    {
        Schema schema = Schema.createRecord("Inner", null, TEST_CLASS_NAME + "$", false);
        assertEquals(TEST_CLASS_NAME + "$Inner", AvroSchemaHelper.getFullName(schema));

        assertEquals("Inner",
                AvroSchemaHelper.getFullName(Schema.createRecord("Inner", null, null, false)));
    }

    @Test
    public void testTypeIdMatchesForNestedClasses() throws Exception
    {
        // Dots for nested classes (Avro 1.9+)
        assertTrue(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("Inner", null, TEST_CLASS_NAME, false)));
        assertTrue(AvroSchemaHelper.typeIdMatches(Inner.Deeper.class,
                Schema.createRecord("Deeper", null, TEST_CLASS_NAME + ".Inner", false)));
        // Dollar signs (older Avro), fully or partially
        assertTrue(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("Inner", null, TEST_CLASS_NAME + "$", false)));
        assertTrue(AvroSchemaHelper.typeIdMatches(Inner.Deeper.class,
                Schema.createRecord("Deeper", null, TEST_CLASS_NAME + "$Inner$", false)));
        assertTrue(AvroSchemaHelper.typeIdMatches(Inner.Deeper.class,
                Schema.createRecord("Deeper", null, TEST_CLASS_NAME + "$Inner", false)));
        // Enum, fixed too
        assertTrue(AvroSchemaHelper.typeIdMatches(EnumNotToBeInitialized.class,
                Schema.createEnum("EnumNotToBeInitialized", null,
                        TEST_CLASS_NAME, Arrays.asList("A", "B"))));
        assertFalse(InitTracker.enumInitialized);
    }

    @Test
    public void testTypeIdMatchesForTopLevelClasses() throws Exception
    {
        final String pkg = SchemaFullNameTest.class.getPackage().getName();
        assertTrue(AvroSchemaHelper.typeIdMatches(SchemaFullNameTest.class,
                Schema.createRecord("SchemaFullNameTest", null, pkg, false)));
        // class in default package would have no namespace
        assertFalse(AvroSchemaHelper.typeIdMatches(SchemaFullNameTest.class,
                Schema.createRecord("SchemaFullNameTest", null, null, false)));
    }

    @Test
    public void testTypeIdMatchesMismatches() throws Exception
    {
        final String pkg = SchemaFullNameTest.class.getPackage().getName();
        // wrong name, same length
        assertFalse(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("Outer", null, TEST_CLASS_NAME, false)));
        // wrong namespace, same length
        assertFalse(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("Inner", null,
                        pkg + ".SchemaFullNameTesX", false)));
        // name is suffix only
        assertFalse(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("ner", null, TEST_CLASS_NAME + ".In", false)));
        // namespace separator where class name has none
        assertFalse(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("Inner", null,
                        pkg + ".SchemaFullName.est", false)));
        // dollar sign in namespace does not match dot (package separator) in class name
        assertFalse(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("Inner", null,
                        pkg.replace('.', '$') + ".SchemaFullNameTest", false)));
        // shorter / longer
        assertFalse(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("Inner", null, pkg, false)));
        assertFalse(AvroSchemaHelper.typeIdMatches(Inner.class,
                Schema.createRecord("Inner", null, TEST_CLASS_NAME + ".Inner", false)));
    }

    // Non-named types match by "java-class" property
    @Test
    public void testTypeIdMatchesForNonNamedTypes() throws Exception
    {
        Schema schema = Schema.create(Schema.Type.STRING);
        assertFalse(AvroSchemaHelper.typeIdMatches(String.class, schema));
        schema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS, String.class.getName());
        assertTrue(AvroSchemaHelper.typeIdMatches(String.class, schema));
        assertFalse(AvroSchemaHelper.typeIdMatches(Integer.class, schema));

        // primitives use name of wrapper
        Schema intSchema = Schema.create(Schema.Type.INT);
        intSchema.addProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS, Integer.class.getName());
        assertTrue(AvroSchemaHelper.typeIdMatches(int.class, intSchema));
    }

    // Excessively long / dotted names are not a problem as nothing gets probed
    @Test
    public void testFullNameForExcessivelyDottedNamespace() throws Exception
    {
        StringBuilder sb = new StringBuilder("a");
        for (int i = 0; i < 20_000; ++i) {
            sb.append(".a");
        }
        final String namespace = sb.toString();
        Schema schema = Schema.createRecord("Rec", null, namespace, false);
        assertEquals(namespace + ".Rec", AvroSchemaHelper.getFullName(schema));
        assertFalse(AvroSchemaHelper.typeIdMatches(Inner.class, schema));
    }
}
