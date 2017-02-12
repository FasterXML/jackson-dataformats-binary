package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class AvroNativeTypeIdResolver extends ClassNameIdResolver {

    protected Collection<NamedType> _namedSubtypes;

    public AvroNativeTypeIdResolver(JavaType baseType, TypeFactory typeFactory, Collection<NamedType> namedSubtypes) {
        super(baseType, typeFactory);
        _namedSubtypes = namedSubtypes == null ? Collections.<NamedType>emptyList() : namedSubtypes;
    }

    @Override
    protected JavaType _typeFromId(String id, DatabindContext ctxt) throws IOException {
        // check if there is a named type with this ID first
        // allows for class substitution
        for (NamedType namedType : _namedSubtypes) {
            if (id.equals(namedType.getName())) {
                return _typeFactory.constructType(namedType.getType());
            }
        }
        // Handle native java arrays
        if (id.startsWith("[") && id.endsWith(";")) {
            int start = id.indexOf('L') +1;
            String className = id.substring(start, id.length() - 1);
            for (NamedType namedType : _namedSubtypes) {
                if (className.equals(namedType.getName())) {
                    return _typeFactory.constructFromCanonical(id.substring(0,start+1) + namedType.getType().getCanonicalName()+";");
                }
            }
        }
        // At this point, assume it's a class name
        return super._typeFromId(id, ctxt);
    }

}
