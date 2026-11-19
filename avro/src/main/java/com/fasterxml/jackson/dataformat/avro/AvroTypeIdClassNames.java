package com.fasterxml.jackson.dataformat.avro;

/**
 * Helper class for translating native type ids into names of Java classes:
 * needed since names of record, enum and fixed schemas (as of Avro 1.9) use
 * dot as the separator between enclosing and nested class
 * ({@code package.Outer.Inner}), so need to probe to find the actual class
 * ({@code package.Outer$Inner}).
 *<p>
 * Probing is done (as per [dataformats-binary#908]) the way databind looks for
 * classes: first using context ClassLoader (if any), then ClassLoader of this
 * module; in both cases without initializing class found (name comes from
 * possibly untrusted schema).
 *
 * @since 2.24
 */
final class AvroTypeIdClassNames
{
    // Maximum number of dots in namespace for which nested-class probing is done
    final static int MAX_NAMESPACE_DOTS = 32;

    // Maximum length of full name (namespace, dot, name) for which nested-class
    // probing is done: well above length of any real class name
    final static int MAX_CLASS_NAME_LENGTH = 1024;

    private AvroTypeIdClassNames() { }

    /**
     * @param typeId Type id from schema: class name (possibly using dots for nested
     *   classes), or canonical generic type {@code AvroReaderFactory} produces for
     *   {@code EnumSet} and {@code EnumMap} (like {@code java.util.EnumSet<package.Outer.Inner>}),
     *   for which the first type parameter is name from schema.
     *
     * @return Type id with name of class found, if any (may be {@code typeId} itself);
     *    {@code null} if no class was found
     */
    static String resolve(String typeId)
    {
        final int start = typeId.indexOf('<') + 1;
        if (start == 0) {
            return _findClassName(typeId);
        }
        int end = typeId.indexOf(',', start);
        if (end < 0) {
            end = typeId.indexOf('>', start);
            if (end < 0) { // malformed, leave for databind to report
                return null;
            }
        }
        final String className = _findClassName(typeId.substring(start, end));
        return (className == null) ? null
                : typeId.substring(0, start) + className + typeId.substring(end);
    }

    private static String _findClassName(String name)
    {
        // Nothing to probe without namespace; and no real class has name
        // very long or nested very deep
        if ((name.indexOf('.') < 0) || !_shouldProbe(name)) {
            return null;
        }
        // 23-Aug-2024:[dataformats-binary#167] Based on SpecificData::getClass
        //   from Apache Avro. Initially assume that namespace is a Java package
        final StringBuilder sb = new StringBuilder(name);
        int lastDot = sb.length();
        while (!_classExists(sb.toString())) {
            // Class does not exist - perhaps last dot is actually a nested class
            lastDot = sb.lastIndexOf(".", lastDot);
            if (lastDot < 0) {
                return null;
            }
            sb.setCharAt(lastDot, '$');
        }
        return sb.toString();
    }

    /**
     * @return ClassLoader that determines outcome of {@link #resolve}, for the
     *   current thread: context ClassLoader, if any (ClassLoader of this module
     *   is used as fallback, but is always the same); otherwise that of this module.
     */
    static ClassLoader lookupClassLoader()
    {
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        return (contextLoader != null) ? contextLoader
                : AvroTypeIdClassNames.class.getClassLoader();
    }

    // Same lookup order as databind has: context ClassLoader (if any) first
    private static boolean _classExists(String className)
    {
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader moduleLoader = AvroTypeIdClassNames.class.getClassLoader();
        return ((contextLoader != null) && (contextLoader != moduleLoader)
                    && _classExists(className, contextLoader))
                || _classExists(className, moduleLoader);
    }

    // NOTE: `LinkageError`s (including `NoClassDefFoundError` for class that exists
    // but cannot be loaded, e.g. due to missing superclass) are intentionally NOT
    // caught: class may well exist, and fallback name would mask the actual problem
    private static boolean _classExists(String className, ClassLoader loader)
    {
        try {
            // Only need to know class exists, so must not initialize it
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Whether to probe for nested classes for given (possibly untrusted) schema
    // name: each dot means one more class loading attempt, so limit both number
    // of dots and length.
    // Package-visible for tests
    static boolean _shouldProbe(String className) {
        if (className.length() > MAX_CLASS_NAME_LENGTH) {
            return false;
        }
        // One more dot than in namespace: separator between namespace and name
        final int maxDots = MAX_NAMESPACE_DOTS + 1;
        int count = 0;
        for (int ix = className.indexOf('.'); ix >= 0; ix = className.indexOf('.', ix + 1)) {
            if (++count > maxDots) {
                return false;
            }
        }
        return true;
    }
}
