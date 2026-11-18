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

    /*
    /**********************************************************************
    /* Test methods, ClassLoader handling
    /**********************************************************************
     */

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
            Object result = _roundTrip(loader, loader);
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
            Object result = _roundTrip(loader, null);
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
            assertTrue(_roundTrip(loader1, null) instanceof Map<?,?>);

            Object result1 = _roundTrip(loader1, loader1);
            assertSame(loader1, result1.getClass().getClassLoader());

            Object result2 = _roundTrip(loader2, loader2);
            assertSame(loader2, result2.getClass().getClassLoader());

            // and failing lookup still fails
            assertTrue(_roundTrip(loader1, null) instanceof Map<?,?>);
        }
    }

    // Writes `Bean` with `ImplB` value, reads it back (with new mapper) using
    // given context ClassLoader (if any); returns value of `Bean.value`
    private Object _roundTrip(ClassLoader beanLoader, ClassLoader contextLoader) throws Exception
    {
        final Class<?> beanClass = beanLoader.loadClass(NS + "$Bean");
        final Object bean = beanClass.getDeclaredConstructor().newInstance();
        final Object implB = beanLoader.loadClass(NS + "$ImplB").getDeclaredConstructor().newInstance();
        implB.getClass().getField("b").set(implB, 42);
        beanClass.getField("value").set(bean, implB);

        final AvroMapper mapper = newMapper();
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

    /*
    /**********************************************************************
    /* Test methods, probing limits
    /**********************************************************************
     */

    @Test
    public void testDotCountLimitBoundary() throws Exception
    {
        final int max = AvroTypeIdResolver.MAX_NAMESPACE_DOTS;
        assertEquals(32, max);
        assertTrue(AvroTypeIdResolver._shouldProbe("Rec"));
        assertTrue(AvroTypeIdResolver._shouldProbe("abc.Rec"));
        assertTrue(AvroTypeIdResolver._shouldProbe(_dottedName(max - 1) + ".Rec"));
        assertTrue(AvroTypeIdResolver._shouldProbe(_dottedName(max) + ".Rec"));
        assertFalse(AvroTypeIdResolver._shouldProbe(_dottedName(max + 1) + ".Rec"));
        assertFalse(AvroTypeIdResolver._shouldProbe(_dottedName(max + 100) + ".Rec"));
    }

    // Limit applies to length of full name: namespace, dot, name
    @Test
    public void testNameLengthLimitBoundary() throws Exception
    {
        final int max = AvroTypeIdResolver.MAX_CLASS_NAME_LENGTH;
        assertEquals(1024, max);
        final String name = ".Rec";
        final int maxNamespace = max - name.length();
        assertTrue(AvroTypeIdResolver._shouldProbe(_longName(maxNamespace - 1) + name));
        assertTrue(AvroTypeIdResolver._shouldProbe(_longName(maxNamespace) + name));
        assertFalse(AvroTypeIdResolver._shouldProbe(_longName(maxNamespace + 1) + name));
        assertFalse(AvroTypeIdResolver._shouldProbe(_longName(100_000) + name));
    }

    private static String _longName(int len) {
        StringBuilder sb = new StringBuilder(len);
        while (sb.length() < len) {
            sb.append('x');
        }
        return sb.toString();
    }

    // Name with given number of dots
    private static String _dottedName(int dots) {
        StringBuilder sb = new StringBuilder("a");
        for (int i = 0; i < dots; ++i) {
            sb.append(".a");
        }
        return sb.toString();
    }
}
