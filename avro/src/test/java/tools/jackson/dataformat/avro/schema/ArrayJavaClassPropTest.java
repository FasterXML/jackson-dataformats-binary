package tools.jackson.dataformat.avro.schema;

import java.util.*;

import org.apache.avro.Schema;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JavaType;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test for [dataformats-binary#xxx]: generated {@code array} schemas must carry the
 * {@code java-class} property naming the collection type -- including for plain
 * {@link java.util.List}, which used to be excluded.
 *<p>
 * Without it Apache's {@code ReflectDatumReader.newArray()} falls through to the
 * primitive-specialized array that {@code GenericData} builds for {@code int} elements,
 * which cannot hold the {@code Byte} / {@code Character} / {@code Short} values that the
 * element-level {@code java-class} calls for.
 */
public class ArrayJavaClassPropTest extends AvroTestBase
{
    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testListSchemaHasJavaClassProp() throws Exception
    {
        for (Class<?> elementType : new Class<?>[] {
                Character.class, Byte.class, Short.class, Integer.class, String.class }) {
            Schema schema = _arraySchema(List.class, elementType);
            assertEquals(Schema.Type.ARRAY, schema.getType());
            assertEquals("java.util.List",
                    schema.getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS),
                    "Wrong `java-class` for List<"+elementType.getSimpleName()+">");
        }
    }

    @Test
    public void testOtherCollectionTypesKeepOwnJavaClassProp() throws Exception
    {
        assertEquals("java.util.Set",
                _arraySchema(Set.class, String.class).getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS));
        assertEquals("java.util.ArrayList",
                _arraySchema(ArrayList.class, String.class).getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS));
    }

    @Test
    public void testElementJavaClassStillEmitted() throws Exception
    {
        // element-level hint is what tells Avro to convert `int` back to `Character`
        Schema schema = _arraySchema(List.class, Character.class);
        assertEquals("java.lang.Character",
                schema.getElementType().getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS));
    }

    private Schema _arraySchema(Class<?> collectionType, Class<?> elementType) throws Exception
    {
        JavaType type = MAPPER.getTypeFactory()
                .constructCollectionType(uncheckedCollection(collectionType), elementType);
        Schema schema = MAPPER.schemaFor(type).getAvroSchema();
        assertNotNull(schema);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Collection<?>> uncheckedCollection(Class<?> raw) {
        return (Class<? extends Collection<?>>) raw;
    }
}
