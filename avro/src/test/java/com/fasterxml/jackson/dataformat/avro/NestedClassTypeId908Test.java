package com.fasterxml.jackson.dataformat.avro;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.avro.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.dataformat.avro.ser.AvroWriteContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// [dataformats-binary#908]: nested classes named by schema (with dot as separator
// between enclosing and nested class) must be found using same ClassLoader(s)
// databind uses for loading classes, not just one that loaded Avro module itself.
public class NestedClassTypeId908Test extends AvroTestBase
{
    // Classes that are NOT visible via ClassLoader of the module (or tests):
    // compiled on the fly, loaded via separate ClassLoader(s)
    private final static String ISOLATED_SOURCE =
            "package isolated908;\n"
            +"public class Outer {\n"
            +"  public static class Bean { public Object value; }\n"
            +"  public static class ImplA { public String a; }\n"
            +"  public static class ImplB { public int b; }\n"
            +"}\n";

    private final static String NS = "isolated908.Outer";

    private final static String SCHEMA_JSON = "{\"type\":\"record\",\"name\":\"Bean\",\"namespace\":\"" + NS + "\","
            +"\"fields\":[{\"name\":\"value\",\"type\":["
            +"{\"type\":\"record\",\"name\":\"ImplA\",\"fields\":[{\"name\":\"a\",\"type\":\"string\"}]},"
            +"{\"type\":\"record\",\"name\":\"ImplB\",\"fields\":[{\"name\":\"b\",\"type\":\"int\"}]}"
            +"]}]}";

    @TempDir
    static Path TEMP_DIR;

    private static URL CLASSES_URL;

    @BeforeAll
    static void compileIsolatedClasses() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "No Java compiler available (running on JRE?)");

        Path srcDir = TEMP_DIR.resolve("src").resolve("isolated908");
        Files.createDirectories(srcDir);
        Path src = srcDir.resolve("Outer.java");
        Files.write(src, ISOLATED_SOURCE.getBytes(StandardCharsets.UTF_8));
        File classesDir = TEMP_DIR.resolve("classes").toFile();
        assertTrue(classesDir.mkdirs());
        int status = compiler.run(null, null, null,
                "-d", classesDir.getAbsolutePath(), src.toString());
        assertEquals(0, status, "Compilation of isolated classes failed");
        CLASSES_URL = classesDir.toURI().toURL();
    }

    private URLClassLoader isolatedLoader() {
        return new URLClassLoader(new URL[] { CLASSES_URL },
                NestedClassTypeId908Test.class.getClassLoader());
    }

    // Sanity check for test setup itself
    @Test
    public void testIsolatedClassesNotVisibleToModule() throws Exception
    {
        try {
            Class.forName(NS + "$ImplB", false, AvroMapper.class.getClassLoader());
            fail("Should not find isolated class via module's ClassLoader");
        } catch (ClassNotFoundException e) {
            // expected
        }
    }

    // Write side: union branch chosen by comparing names, no class loading
    @Test
    public void testWriteResolvesUnionForNestedClass() throws Exception
    {
        try (URLClassLoader loader = isolatedLoader()) {
            Object implA = loader.loadClass(NS + "$ImplA").getDeclaredConstructor().newInstance();
            Object implB = loader.loadClass(NS + "$ImplB").getDeclaredConstructor().newInstance();
            Schema union = new Schema.Parser().parse(SCHEMA_JSON).getField("value").schema();

            // note: no context ClassLoader set
            assertEquals(0, AvroWriteContext.resolveUnionIndex(union, implA));
            assertEquals(1, AvroWriteContext.resolveUnionIndex(union, implB));
            assertEquals("ImplA", AvroWriteContext.resolveUnionType(union, implA).getName());
            assertEquals("ImplB", AvroWriteContext.resolveUnionType(union, implB).getName());
        }
    }

    @Test
    public void testRoundTripWithContextClassLoader() throws Exception
    {
        try (URLClassLoader loader = isolatedLoader()) {
            Object result = _roundTrip(newMapper(), loader, loader);
            assertEquals(NS + "$ImplB", result.getClass().getName());
            assertSame(loader, result.getClass().getClassLoader());
            assertEquals(42, result.getClass().getField("b").get(result));
        }
    }

    // Without access to ClassLoader, name does not resolve to a class: bound as "untyped"
    @Test
    public void testReadWithoutContextClassLoader() throws Exception
    {
        try (URLClassLoader loader = isolatedLoader()) {
            Object result = _roundTrip(newMapper(), loader, null);
            assertTrue(result instanceof Map<?,?>, "Expected Map, got: "+result.getClass().getName());
            assertEquals(42, ((Map<?,?>) result).get("b"));
        }
    }

    // Outcome with one ClassLoader must not leak to usage with another (as it
    // did with JVM-wide name cache), regardless of order
    @Test
    public void testResolutionIsNotSharedAcrossClassLoaders() throws Exception
    {
        try (URLClassLoader loader1 = isolatedLoader();
                URLClassLoader loader2 = isolatedLoader()) {
            // first: lookup that fails
            assertTrue(_roundTrip(newMapper(), loader1, null) instanceof Map<?,?>);

            Object result1 = _roundTrip(newMapper(), loader1, loader1);
            assertSame(loader1, result1.getClass().getClassLoader());

            Object result2 = _roundTrip(newMapper(), loader2, loader2);
            assertSame(loader2, result2.getClass().getClassLoader());

            // and failing lookup still fails
            assertTrue(_roundTrip(newMapper(), loader1, null) instanceof Map<?,?>);
        }
    }

    // Failure to find class (here: due to context ClassLoader not being set) must
    // not be remembered by mapper: lookup may succeed later on
    @Test
    public void testFailedLookupNotRetainedByMapper() throws Exception
    {
        final AvroMapper mapper = newMapper();
        try (URLClassLoader loader = isolatedLoader()) {
            assertTrue(_roundTrip(mapper, loader, null) instanceof Map<?,?>);
            assertTrue(_roundTrip(mapper, loader, null) instanceof Map<?,?>);

            Object result = _roundTrip(mapper, loader, loader);
            assertEquals(NS + "$ImplB", result.getClass().getName());
            assertSame(loader, result.getClass().getClassLoader());
        }
    }

    // Writes `Bean` with `ImplB` value, reads it back using given context
    // ClassLoader (if any); returns value of `Bean.value`
    private Object _roundTrip(AvroMapper mapper,
            ClassLoader beanLoader, ClassLoader contextLoader) throws Exception
    {
        final Class<?> beanClass = beanLoader.loadClass(NS + "$Bean");
        final Object bean = beanClass.getDeclaredConstructor().newInstance();
        final Object implB = beanLoader.loadClass(NS + "$ImplB").getDeclaredConstructor().newInstance();
        implB.getClass().getField("b").set(implB, 42);
        beanClass.getField("value").set(bean, implB);

        final AvroSchema schema = mapper.schemaFrom(SCHEMA_JSON);
        final byte[] avro = mapper.writer(schema).writeValueAsBytes(bean);
        // union index 1 (zigzag: 2), then int 42 (zigzag: 84)
        assertArrayEquals(new byte[] { 2, 84 }, avro);

        final Thread thread = Thread.currentThread();
        final ClassLoader oldLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(contextLoader);
        try {
            Object result = mapper.readerFor(beanClass).with(schema).readValue(avro);
            assertSame(beanClass, result.getClass());
            return beanClass.getField("value").get(result);
        } finally {
            thread.setContextClassLoader(oldLoader);
        }
    }
}
