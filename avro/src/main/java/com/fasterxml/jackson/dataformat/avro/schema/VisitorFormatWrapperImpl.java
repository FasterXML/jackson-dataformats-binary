package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.JsonGenerator;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.jsonFormatVisitors.*;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

public class VisitorFormatWrapperImpl
    implements JsonFormatVisitorWrapper
{
    protected SerializerProvider _provider;
    
    protected final DefinedSchemas _schemas;

    /**
     * Visitor used for resolving actual Schema, if structured type
     * (or one with complex configuration)
     */
    protected SchemaBuilder _builder;

    /**
     * Schema for simple types that do not need a visitor.
     */
    protected Schema _valueSchema;
    
    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    public VisitorFormatWrapperImpl(DefinedSchemas schemas, SerializerProvider p) {
        _schemas = schemas;
        _provider = p;
    }
    
    @Override
    public SerializerProvider getProvider() {
        return _provider;
    }

    @Override
    public void setProvider(SerializerProvider provider) {
        _schemas.setProvider(provider);
        _provider = provider;
    }

    /*
    /**********************************************************************
    /* Extended API
    /**********************************************************************
     */

    public Schema getAvroSchema() {
        if (_valueSchema != null) {
            return _valueSchema;
        }
        if (_builder == null) {
            throw new IllegalStateException("No visit methods called on "+getClass().getName()
                    +": no schema generated");
        }
        return _builder.builtAvroSchema();
    }
    
    /*
    /**********************************************************************
    /* Callbacks
    /**********************************************************************
     */

    public void expectAvroFormat(AvroSchema schema) {
        _valueSchema = schema.getAvroSchema();
    }

    @Override
    public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {

        Schema s = _schemas.findSchema(type);
        if (s != null) {
            _valueSchema = s;
            return null;
        }
        RecordVisitor v = new RecordVisitor(_provider, type, _schemas);
        _builder = v;
        return v;
    }

    @Override
    public JsonMapFormatVisitor expectMapFormat(JavaType mapType) {
        MapVisitor v = new MapVisitor(_provider, mapType, _schemas);
        _builder = v;
        return v;
    }
    
    @Override
    public JsonArrayFormatVisitor expectArrayFormat(final JavaType convertedType) {
        // 22-Mar-2016, tatu: Actually we can detect byte[] quite easily here can't we?
        if (convertedType.isArrayType()) {
            JavaType vt = convertedType.getContentType();
            if (vt.hasRawClass(Byte.TYPE)) {
                _builder = new SchemaBuilder() {
                    @Override
                    public Schema builtAvroSchema() {
                        return AvroSchemaHelper.typedSchema(Schema.Type.BYTES, convertedType);
                    }
                };
                return null;
            }
        }
        ArrayVisitor v = new ArrayVisitor(_provider, convertedType, _schemas);
        _builder = v;
        return v;
    }

    @Override
    public JsonStringFormatVisitor expectStringFormat(JavaType type)
    {
        // may be getting ref to Enum type:
        Schema s = _schemas.findSchema(type);
        if (s != null) {
            _valueSchema = s;
            return null;
        }
        StringVisitor v = new StringVisitor(_provider, _schemas, type);
        _builder = v;
        return v;
    }

    @Override
    public JsonNumberFormatVisitor expectNumberFormat(JavaType convertedType) {
        DoubleVisitor v = new DoubleVisitor(convertedType);
        _builder = v;
        return v;
    }

    @Override
    public JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) {
        // possible we might be getting Enum type, using indexes:
        // may be getting ref to Enum type:
        Schema s = _schemas.findSchema(type);
        if (s != null) {
            _valueSchema = s;
            return null;
        }
        IntegerVisitor v = new IntegerVisitor(type);
        _builder = v;
        return v;
    }

    @Override
    public JsonBooleanFormatVisitor expectBooleanFormat(JavaType convertedType) {
        _valueSchema = Schema.create(Schema.Type.BOOLEAN);
        // We don't really need anything from there so:
        return null;
    }

    @Override
    public JsonNullFormatVisitor expectNullFormat(JavaType convertedType) {
        _valueSchema = Schema.create(Schema.Type.NULL);
        // no info on null type that we care about so:
        return null;
    }

    @Override
    public JsonAnyFormatVisitor expectAnyFormat(JavaType convertedType) throws JsonMappingException {
        // could theoretically create union of all possible types but...
        final String msg = "\"Any\" type (usually for `java.lang.Object`) not supported: `expectAnyFormat` called with type "+convertedType;
        throw InvalidDefinitionException.from((JsonGenerator) null, msg, convertedType);
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected <T> T _throwUnsupported() {
        return _throwUnsupported("Format variation not supported");
    }
    protected <T> T _throwUnsupported(String msg) {
        throw new UnsupportedOperationException(msg);
    }
}
