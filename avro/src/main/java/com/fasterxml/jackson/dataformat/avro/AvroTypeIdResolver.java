package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.LRUMap;

/**
 * {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} for Avro type IDs embedded in schemas. Avro generally uses class names,
 * but we want to also support named subtypes so that developers can easily remap the embedded type IDs to a different runtime class.
 *<p>
 * Since 2.24 also handles resolution of names of nested classes: schemas (as of Avro 1.9) use dot as
 * the separator between enclosing and nested class (<code>package.Outer.Inner</code>) so resolver needs to probe
 * to find the actual class (<code>package.Outer$Inner</code>). This is done using the same class lookup as
 * databind uses for loading the class (see [dataformats-binary#908]).
 *
 * @since 2.9
 */
public class AvroTypeIdResolver extends ClassNameIdResolver
{
    private static final long serialVersionUID = 1L;

    // Maximum number of dots in namespace for which nested-class probing is done
    // (moved from {@code AvroSchemaHelper} in 2.24)
    final static int MAX_NAMESPACE_DOTS = 32;

    // Maximum length of full name (namespace, dot, name) for which nested-class
    // probing is done: well above length of any real class name
    final static int MAX_CLASS_NAME_LENGTH = 1024;

    private final Map<String, Class<?>> _idTypes = new HashMap<>();

    /**
     * Type ids for which probing found a class, mapped to the id of that class:
     * for example {@code package.Outer.Inner} to {@code package.Outer$Inner}.
     * Only successful lookups are retained: whether class is found may depend on
     * context ClassLoader at the time, so failure must not be remembered.
     * Per-resolver (and thereby per-mapper), unlike JVM-wide cache used before 2.24.
     *
     * @since 2.24
     */
    private final LRUMap<String, String> _resolvedIds = new LRUMap<>(16, 200);

    public AvroTypeIdResolver(JavaType baseType, TypeFactory typeFactory,
            PolymorphicTypeValidator ptv,
            Collection<NamedType> subTypes)
    {
        super(baseType, typeFactory, subTypes, ptv);
        if (subTypes != null) {
            for (NamedType namedType : subTypes) {
                //registerSubtype(namedType.getType(), namedType.getName());
                final String name = namedType.getName();
                _idTypes.put(name, namedType.getType());
                // [dataformats-binary#908]: type ids from schema use dots for nested classes
                // too: add aliases so that registered subtypes are found without probing
                // (but never overriding exact matches)
                if ((name != null) && name.indexOf('$') >= 0) {
                    _idTypes.putIfAbsent(name.replace('$', '.'), namedType.getType());
                }
            }
        }
    }

    /*// Not used as of Jackson 2.19
    @Override
    public void registerSubtype(Class<?> type, String name) {
        _idTypes.put(name, type);
//        _typeIds.put(type, name);
    }
    */

    @Override
    protected JavaType _typeFromId(String id, DatabindContext ctxt) throws IOException {
        // primitive types don't have subclasses
        if (_baseType.isPrimitive()) {
            return _baseType;
        }
        // check if there's a specific type we should be using for this ID
        Class<?> subType = _idTypes.get(id);
        try {
            if (subType != null) {
                id = _idFrom(null, subType, _typeFactory);
            } else {
                // [dataformats-binary#908]: may need to probe for nested classes
                id = _resolveNestedTypeId(id, ctxt);
            }
            // NOTE: always resolve via super class, for checks it has
            return super._typeFromId(id, ctxt);
        } catch (InvalidTypeIdException | IllegalArgumentException e) {
            // AvroTypeDeserializer expects null if we can't map the type ID to a class; It will throw an appropriate error if we can't
            // find a usable type.
            return null;
        }
    }

    /**
     * @return Id of the class that given type id refers to, if one found by probing
     *    for nested classes; {@code id} itself otherwise
     */
    private String _resolveNestedTypeId(String id, DatabindContext ctxt) throws IOException
    {
        String resolved = _resolvedIds.get(id);
        if (resolved != null) {
            return resolved;
        }
        // If only registered subtypes are allowed, nothing to probe for (all
        // of them have been checked already), and must not load other classes
        if ((ctxt instanceof DeserializationContext)
                && ((DeserializationContext) ctxt).isEnabled(
                        DeserializationFeature.FAIL_ON_SUBTYPE_CLASS_NOT_REGISTERED)) {
            return id;
        }
        resolved = _findNestedTypeId(ctxt, _baseType, id, _subTypeValidator);
        if (resolved == null) {
            return id;
        }
        _resolvedIds.put(id, resolved);
        return resolved;
    }

    /**
     * Method that tries to find class that given type id (name from Avro schema)
     * refers to: first as-is, then considering that dots may be separators
     * between enclosing and nested class, starting with the last one
     * (based on {@code SpecificData.getClass()} of Apache Avro).
     * Lookups use same mechanism (and so same ClassLoaders) as databind does,
     * and are subject to given {@link PolymorphicTypeValidator}.
     *<p>
     * Also handles canonical generic type ids {@code AvroReaderFactory} produces for
     * {@code EnumSet} and {@code EnumMap} (like {@code java.util.EnumSet<package.Outer.Inner>}),
     * for which the first type parameter is name from schema.
     *
     * @return Type id with name of class found, if any (may be {@code id} itself);
     *    {@code null} if no class was found
     *
     * @since 2.24
     */
    static String _findNestedTypeId(DatabindContext ctxt, JavaType baseType, String id,
            PolymorphicTypeValidator ptv)
        throws IOException
    {
        // Name to probe is either the whole id, or the first type parameter
        final int ltIndex = id.indexOf('<');
        final int start, end;
        if (ltIndex < 0) {
            start = 0;
            end = id.length();
        } else {
            start = ltIndex + 1;
            int ix = id.indexOf(',', start);
            end = (ix < 0) ? id.indexOf('>', start) : ix;
            if (end < 0) { // malformed, leave for databind to report
                return null;
            }
        }
        // Only probe names that could be those of nested classes; and for
        // which variants are worth trying (no real class has name very long
        // or nested very deep)
        final String name = id.substring(start, end);
        if ((name.indexOf('.') < 0) || (name.indexOf('<') >= 0) || !_shouldProbe(name)) {
            return null;
        }
        final StringBuilder sb = new StringBuilder(id);
        int lastDot = end;
        while (true) {
            final String candidate = sb.toString();
            JavaType type;
            try {
                // returns `null` if no class found...
                type = ctxt.resolveAndValidateSubType(baseType, candidate, ptv);
            } catch (IllegalArgumentException e) {
                // ... except that for generic types failure to find class (of type
                // parameter) is signaled with exception by `TypeFactory`
                if (ltIndex < 0) {
                    throw e;
                }
                type = null;
            }
            if (type != null) {
                return candidate;
            }
            // Class does not exist - perhaps last dot is actually a nested class
            lastDot = sb.lastIndexOf(".", lastDot);
            if (lastDot < start) {
                return null;
            }
            sb.setCharAt(lastDot, '$');
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
