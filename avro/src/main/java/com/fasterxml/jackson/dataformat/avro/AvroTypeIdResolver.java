package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.LRUMap;

/**
 * {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} for Avro type IDs embedded in schemas. Avro generally uses class names,
 * but we want to also support named subtypes so that developers can easily remap the embedded type IDs to a different runtime class.
 *
 * @since 2.9
 */
public class AvroTypeIdResolver extends ClassNameIdResolver
{
    private static final long serialVersionUID = 1L;

    // Maximum number of dots in type id for which nested-class probing is done:
    // each one means one more class loading attempt for (possibly untrusted) id
    final static int MAX_PROBE_DOTS = 32;

    // Maximum length of type id for which nested-class probing is done:
    // well above length of any real class name
    final static int MAX_PROBE_LENGTH = 1024;

    private final Map<String, Class<?>> _idTypes = new HashMap<>();

    /**
     * Cache of type ids (full names from Avro schema, with dots for nested classes) resolved
     * into class names; ids for which no class is found are mapped to themselves.
     * Since resolver is constructed for a mapper, so is this cache.
     *
     * @since 2.24
     */
    private final LRUMap<String, String> _resolvedIds = new LRUMap<>(16, 100);

    // 04-Jan-2021, tatu: Not sure why this is being kept around, not used as of
    //    2.12; commenting out may be removed from 2.13
//    private final Map<Class<?>, String> _typeIds = new HashMap<>();

    public AvroTypeIdResolver(JavaType baseType, TypeFactory typeFactory,
            PolymorphicTypeValidator ptv,
            Collection<NamedType> subTypes)
    {
        super(baseType, typeFactory, subTypes, ptv);
        if (subTypes != null) {
            for (NamedType namedType : subTypes) {
                //registerSubtype(namedType.getType(), namedType.getName());
                _idTypes.put(namedType.getName(), namedType.getType());
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
        if (subType == null) {
            // [dataformats-binary#908]: id from schema uses dots for nested classes too
            id = _resolveNestedClassName(id, ctxt);
            subType = _idTypes.get(id);
        }
        if (subType != null) {
            id = _idFrom(null, subType, _typeFactory);
        }
        try {
            return super._typeFromId(id, ctxt);
        } catch (InvalidTypeIdException | IllegalArgumentException e) {
            // AvroTypeDeserializer expects null if we can't map the type ID to a class; It will throw an appropriate error if we can't
            // find a usable type.
            return null;
        }
    }

    /**
     * Type ids for records, enums and fixed types are full names from Avro schema, in which
     * (since Apache Avro 1.9) nested classes are separated by dots, not dollar signs: this
     * method finds the name of the actual class, trying given id first, then replacing dots
     * with dollar signs, one by one, starting from the last one.
     * Classes are looked up same way as when resolving type itself (no class initialization,
     * same {@link ClassLoader}s, subtype validation), unlike with earlier probing by
     * {@code AvroSchemaHelper}.
     *
     * @return Name of the class to use, if one found; {@code id} if not
     *
     * @since 2.24
     */
    protected String _resolveNestedClassName(String id, DatabindContext ctxt)
    {
        if (!_shouldProbe(id)) {
            return id;
        }
        String resolved = _resolvedIds.get(id);
        if (resolved == null) {
            // Canonical id of generic type (see `AvroReaderFactory`, for `EnumSet`)?
            // Type parameters may be full names from schema as well
            if (id.indexOf('<') > 0) {
                resolved = _resolveGenericClassNames(id, ctxt);
            } else {
                resolved = _resolveClassName(id, _baseType, ctxt);
            }
            _resolvedIds.put(id, resolved);
        }
        return resolved;
    }

    private String _resolveGenericClassNames(String id, DatabindContext ctxt)
    {
        final JavaType paramBaseType = ctxt.constructType(Object.class);
        final StringBuilder sb = new StringBuilder(id.length());
        boolean first = true;
        int start = 0;
        for (int i = 0, len = id.length(); i < len; ++i) {
            final char c = id.charAt(i);
            if (c == '<' || c == '>' || c == ',') {
                if (i > start) {
                    sb.append(_resolveClassName(id.substring(start, i),
                            first ? _baseType : paramBaseType, ctxt));
                    first = false;
                }
                sb.append(c);
                start = i + 1;
            }
        }
        return sb.append(id, start, id.length()).toString();
    }

    private String _resolveClassName(String id, JavaType baseType, DatabindContext ctxt)
    {
        final StringBuilder sb = new StringBuilder(id);
        int lastDot = sb.length();
        String candidate = id;
        while (true) {
            // Registered subtypes are known without class lookups
            if (_idTypes.containsKey(candidate)) {
                return candidate;
            }
            try {
                if (ctxt.resolveAndValidateSubType(baseType, candidate, _subTypeValidator) != null) {
                    return candidate;
                }
            } catch (JsonMappingException e) {
                // Class exists but is not valid (not allowed, not a subtype): no point in
                // trying others; caller gets to handle the problem
                return candidate;
            }
            lastDot = sb.lastIndexOf(".", lastDot);
            if (lastDot == -1) { // no more dots, nothing found
                return id;
            }
            sb.setCharAt(lastDot, '$');
            candidate = sb.toString();
        }
    }

    // Package-visible for tests
    static boolean _shouldProbe(String id) {
        if (id.length() > MAX_PROBE_LENGTH) {
            return false;
        }
        int count = 0;
        for (int ix = id.indexOf('.'); ix >= 0; ix = id.indexOf('.', ix + 1)) {
            if (++count > MAX_PROBE_DOTS) {
                return false;
            }
        }
        // No dots, no nested-class alternatives
        return (count > 0);
    }
}
