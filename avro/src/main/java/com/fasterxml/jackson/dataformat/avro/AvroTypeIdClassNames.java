package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.databind.util.LRUMap;

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

    /**
     * Type ids for which class was found, mapped to type id with name of that class.
     * Only names are retained (no references to classes), and only for successful
     * lookups: whether class is found may depend on context ClassLoader, so failure
     * must not be remembered beyond a single parser.
     */
    private final static LRUMap<String, String> RESOLVED = new LRUMap<>(80, 800);

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
        // Name to probe is either the whole id, or the first type parameter
        final int ltIndex = typeId.indexOf('<');
        final int start, end;
        if (ltIndex < 0) {
            start = 0;
            end = typeId.length();
        } else {
            start = ltIndex + 1;
            int ix = typeId.indexOf(',', start);
            end = (ix < 0) ? typeId.indexOf('>', start) : ix;
            if (end < 0) { // malformed, leave for databind to report
                return null;
            }
        }
        // Only probe names that could be those of nested classes; and for
        // which variants are worth trying (no real class has name very long
        // or nested very deep)
        final String name = typeId.substring(start, end);
        if ((name.indexOf('.') < 0) || (name.indexOf('<') >= 0) || !_shouldProbe(name)) {
            return null;
        }
        String resolved = RESOLVED.get(typeId);
        if (resolved != null) {
            return resolved;
        }
        final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader moduleLoader = AvroTypeIdClassNames.class.getClassLoader();

        // 23-Aug-2024:[dataformats-binary#167] Based on SpecificData::getClass
        //   from Apache Avro. Initially assume that namespace is a Java package
        final StringBuilder sb = new StringBuilder(name);
        int lastDot = sb.length();
        while (true) {
            if (_classExists(sb.toString(), contextLoader, moduleLoader)) {
                break;
            }
            // Class does not exist - perhaps last dot is actually a nested class
            lastDot = sb.lastIndexOf(".", lastDot);
            if (lastDot < 0) {
                return null;
            }
            sb.setCharAt(lastDot, '$');
        }
        resolved = (ltIndex < 0) ? sb.toString()
                : typeId.substring(0, start) + sb + typeId.substring(end);
        // avoid retaining separate copy of the common case
        if (resolved.equals(typeId)) {
            resolved = typeId;
        }
        RESOLVED.put(typeId, resolved);
        return resolved;
    }

    // NOTE: `LinkageError`s (including `NoClassDefFoundError` for class that exists
    // but cannot be loaded, e.g. due to missing superclass) are intentionally NOT
    // caught: class may well exist, and fallback name would mask the actual problem
    private static boolean _classExists(String className,
            ClassLoader contextLoader, ClassLoader moduleLoader)
    {
        // Only need to know class exists, so must not initialize it
        if (contextLoader != null) {
            try {
                Class.forName(className, false, contextLoader);
                return true;
            } catch (ClassNotFoundException e) { }
            if (contextLoader == moduleLoader) {
                return false;
            }
        }
        try {
            Class.forName(className, false, moduleLoader);
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
