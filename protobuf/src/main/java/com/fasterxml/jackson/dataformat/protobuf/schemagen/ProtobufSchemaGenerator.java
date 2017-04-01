package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.util.*;

import com.squareup.protoparser.TypeElement;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.jsonFormatVisitors.*;

import com.fasterxml.jackson.dataformat.protobuf.schema.NativeProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

/**
 * Class that can generate a {@link ProtobufSchema} for a given Java POJO, using
 * definitions Jackson would use for serialization. An instance is typically
 * given to
 * {@link com.fasterxml.jackson.databind.ObjectMapper#acceptJsonFormatVisitor}
 * which will invoke necessary callbacks.
 */
public class ProtobufSchemaGenerator extends ProtoBufSchemaVisitor
{
    protected HashSet<JavaType> _generated;

    protected JavaType _rootType;

    public ProtobufSchemaGenerator() {
        super();
    }

    public ProtobufSchema getGeneratedSchema() throws JsonMappingException {
        return getGeneratedSchema(true);
    }

    public ProtobufSchema getGeneratedSchema(boolean appendDependencies) throws JsonMappingException {
        if (_rootType == null || _builder == null) {
            throw new IllegalStateException(
                    "No visit methods called on " + getClass().getName() + ": no schema generated");
        }

        Collection<TypeElement> types;
        if (appendDependencies) {
            types = this.buildWithDependencies();
        } else {
            types = new LinkedList<>();
            types.add(build());
        }

        return NativeProtobufSchema.construct(_rootType.getRawClass().getName(),
                types).forFirstType();
    }

    /*
    /**********************************************************************
    /* Callbacks, structured types
    /**********************************************************************
     */
	
    @Override
    public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
        _rootType = type;
        return super.expectObjectFormat(type);
    }

    @Override
    public JsonMapFormatVisitor expectMapFormat(JavaType mapType) {
        return _throwUnsupported("'Map' type not supported as root type by protobuf");
    }

    @Override
    public JsonArrayFormatVisitor expectArrayFormat(JavaType convertedType) {
        return _throwUnsupported("'Array' type not supported as root type by protobuf");
    }

    /*
    /**********************************************************************
    /* Callbacks, scalar types
    /**********************************************************************
     */

    @Override
    public JsonStringFormatVisitor expectStringFormat(JavaType type) {
        if (type.isEnumType()) {
            return super.expectStringFormat(type);
        }
        return _throwUnsupported("'String' type not supported as root type by protobuf");
    }

    @Override
    public JsonNumberFormatVisitor expectNumberFormat(JavaType convertedType) {
        return _throwUnsupported("'Number' type not supported as root type by protobuf");
    }

    @Override
    public JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) {
        return _throwUnsupported("'Integer' type not supported as root type by protobuf");
    }

    @Override
    public JsonBooleanFormatVisitor expectBooleanFormat(JavaType convertedType) {
        return _throwUnsupported("'Boolean' type not supported as root type by protobuf");
    }
}
