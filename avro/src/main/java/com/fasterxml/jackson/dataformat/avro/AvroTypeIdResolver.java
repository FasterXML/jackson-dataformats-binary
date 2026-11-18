package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * {@link com.fasterxml.jackson.databind.jsontype.TypeIdResolver} for Avro type IDs embedded in schemas. Avro generally uses class names,
 * but we want to also support named subtypes so that developers can easily remap the embedded type IDs to a different runtime class.
 *
 * @since 2.9
 */
public class AvroTypeIdResolver extends ClassNameIdResolver
{
    private static final long serialVersionUID = 1L;

    private final Map<String, Class<?>> _idTypes = new HashMap<>();

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
}
