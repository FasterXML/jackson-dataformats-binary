package tools.jackson.dataformat.avro;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import tools.jackson.databind.exc.InvalidTypeIdException;

import static org.junit.jupiter.api.Assertions.*;

// Limits on probing for nested classes named by (possibly untrusted) schema:
// each dot of name means one more class loading attempt. Originally added
// in [dataformats-binary#906]; probing moved to `AvroParser.getTypeId()`
// in [dataformats-binary#908]
public class NestedClassProbeLimits908Test extends AvroTestBase
{
    static class ObjectBean { public Object value; }

    // ClassLoader that counts attempts to load classes with given name prefix
    static class CountingClassLoader extends ClassLoader {
        private final String _prefix;
        final AtomicInteger count = new AtomicInteger();

        CountingClassLoader(ClassLoader parent, String prefix) {
            super(parent);
            _prefix = prefix;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(_prefix)) {
                count.incrementAndGet();
            }
            return super.loadClass(name, resolve);
        }
    }

    private final AvroMapper MAPPER = newMapper();

    @Test
    public void testDotCountLimitBoundary() throws Exception
    {
        final int max = AvroTypeIdClassNames.MAX_NAMESPACE_DOTS;
        assertEquals(32, max);
        assertTrue(AvroTypeIdClassNames._shouldProbe("Rec"));
        assertTrue(AvroTypeIdClassNames._shouldProbe("abc.Rec"));
        assertTrue(AvroTypeIdClassNames._shouldProbe(_dottedName(max - 1) + ".Rec"));
        assertTrue(AvroTypeIdClassNames._shouldProbe(_dottedName(max) + ".Rec"));
        assertFalse(AvroTypeIdClassNames._shouldProbe(_dottedName(max + 1) + ".Rec"));
        assertFalse(AvroTypeIdClassNames._shouldProbe(_dottedName(max + 100) + ".Rec"));
    }

    // Limit applies to length of full name: namespace, dot, name
    @Test
    public void testNameLengthLimitBoundary() throws Exception
    {
        final int max = AvroTypeIdClassNames.MAX_CLASS_NAME_LENGTH;
        assertEquals(1024, max);
        final String name = ".Rec";
        final int maxNamespace = max - name.length();
        assertTrue(AvroTypeIdClassNames._shouldProbe(_longName(maxNamespace - 1) + name));
        assertTrue(AvroTypeIdClassNames._shouldProbe(_longName(maxNamespace) + name));
        assertFalse(AvroTypeIdClassNames._shouldProbe(_longName(maxNamespace + 1) + name));
        assertFalse(AvroTypeIdClassNames._shouldProbe(_longName(100_000) + name));
    }

    // Sanity check that probing is done (and counted) for names within limits:
    // one attempt per dot, at least
    @Test
    public void testProbingWithinLimits() throws Exception
    {
        final int dots = 10;
        int count = _readAndCountLoads("probe908x." + _dottedName(dots - 1), "probe908x.");
        assertTrue(count >= dots + 1, "Expected at least "+(dots+1)+" attempts, got: "+count);
    }

    // Failure to find class is remembered for schema, so that probing is not
    // repeated for every value (and document)...
    @Test
    public void testFailedProbingNotRepeated() throws Exception
    {
        final int dots = 10;
        final AvroSchema schema = _schema("probe908v." + _dottedName(dots - 1));
        final CountingClassLoader loader = new CountingClassLoader(
                getClass().getClassLoader(), "probe908v.");
        _read(schema, loader);
        final int first = loader.count.get();
        assertTrue(first >= dots + 1, "Expected at least "+(dots+1)+" attempts, got: "+first);

        _read(schema, loader);
        final int second = loader.count.get();
        _assertNotProbed(second - first);
        _read(schema, loader);
        _assertNotProbed(loader.count.get() - second);
    }

    // ... but only for the ClassLoader used: with another one class might be found
    @Test
    public void testFailedProbingRepeatedForDifferentClassLoader() throws Exception
    {
        final int dots = 10;
        final AvroSchema schema = _schema("probe908w." + _dottedName(dots - 1));
        final CountingClassLoader loader1 = new CountingClassLoader(
                getClass().getClassLoader(), "probe908w.");
        _read(schema, loader1);
        final int count1 = loader1.count.get();
        assertTrue(count1 >= dots + 1, "Expected at least "+(dots+1)+" attempts, got: "+count1);

        final CountingClassLoader loader2 = new CountingClassLoader(
                getClass().getClassLoader(), "probe908w.");
        _read(schema, loader2);
        final int count2 = loader2.count.get();
        assertTrue(count2 >= dots + 1, "Expected at least "+(dots+1)+" attempts, got: "+count2);
        assertEquals(count1, loader1.count.get());

        // and outcomes are retained for both, when alternating
        _read(schema, loader1);
        _assertNotProbed(loader1.count.get() - count1);
        _read(schema, loader2);
        _assertNotProbed(loader2.count.get() - count2);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testNoProbingForExcessivelyDottedName() throws Exception
    {
        int count = _readAndCountLoads("probe908y." + _dottedName(20_000), "probe908y.");
        _assertNotProbed(count);
    }

    @Test
    public void testNoProbingForExcessivelyLongName() throws Exception
    {
        int count = _readAndCountLoads("probe908z.a.b.c."
                + _longName(AvroTypeIdClassNames.MAX_CLASS_NAME_LENGTH), "probe908z.");
        _assertNotProbed(count);
    }

    // Without probing only lookups of name as-is are done: type id gets resolved
    // a few times for a value, but number does not depend on number of dots
    private void _assertNotProbed(int count) {
        assertTrue(count <= 5, "Expected at most 5 class loading attempts, got: "+count);
    }

    // Reads `ObjectBean` with value of record type with given namespace
    // (that of non-existing class): returns number of class loading attempts
    // via context ClassLoader, for names with given prefix
    private int _readAndCountLoads(String namespace, String prefix) throws Exception
    {
        final CountingClassLoader loader = new CountingClassLoader(
                getClass().getClassLoader(), prefix);
        _read(_schema(namespace), loader);
        return loader.count.get();
    }

    private AvroSchema _schema(String namespace)
    {
        Schema rec = Schema.createRecord("Rec", null, namespace, false,
                Arrays.asList(new Schema.Field("b", Schema.create(Schema.Type.INT))));
        Schema root = Schema.createRecord("ObjectBean", null, "probe908", false,
                Arrays.asList(new Schema.Field("value", rec)));
        return new AvroSchema(root);
    }

    // Reads `ObjectBean` using given schema and context ClassLoader
    // NOTE: with 3.1 (and 3.2) type id that cannot be resolved into class results
    // in failure instead of binding as "untyped" (`Map`), as per
    // [dataformats-binary#812] (fixed in 3.3)
    private void _read(AvroSchema schema, ClassLoader contextLoader) throws Exception
    {
        final Thread thread = Thread.currentThread();
        final ClassLoader oldLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(contextLoader);
        try {
            // int 42 (zigzag: 84)
            MAPPER.readerFor(ObjectBean.class)
                    .with(schema)
                    .readValue(new byte[] { 84 });
            fail("Should not pass");
        } catch (InvalidTypeIdException e) {
            verifyException(e, "no such class found");
        } finally {
            thread.setContextClassLoader(oldLoader);
        }
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
