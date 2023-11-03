package tools.jackson.dataformat.avro.schema;

import tools.jackson.core.JsonGenerator;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.jsonFormatVisitors.*;
import tools.jackson.dataformat.avro.AvroSchema;

import org.apache.avro.Schema;

import java.time.temporal.Temporal;

public class VisitorFormatWrapperImpl
    implements JsonFormatVisitorWrapper
{
    protected SerializerProvider _provider;

    protected final DefinedSchemas _schemas;

    /**
     * @since 2.13
     */
    protected boolean _logicalTypesEnabled = false;

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


    protected VisitorFormatWrapperImpl(VisitorFormatWrapperImpl src) {
        this._schemas = src._schemas;
        this._provider = src._provider;
        this._logicalTypesEnabled = src._logicalTypesEnabled;
    }

    /**
     * Creates new {@link VisitorFormatWrapperImpl} instance with shared schemas,
     * serialization provider and same configuration.
     *
     * @return new instance with shared properties and configuration.
     */
    protected VisitorFormatWrapperImpl createChildWrapper() {
        return new VisitorFormatWrapperImpl(this);
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

    protected DefinedSchemas getSchemas() {
        return _schemas;
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

    /**
     * Enables Avro schema with Logical Types generation.
     */
    public VisitorFormatWrapperImpl enableLogicalTypes() {
        _logicalTypesEnabled = true;
        return this;
    }

    /**
     * Disables Avro schema with Logical Types generation.
     */
    public VisitorFormatWrapperImpl disableLogicalTypes() {
        _logicalTypesEnabled = false;
        return this;
    }

    public boolean isLogicalTypesEnabled() {
        return _logicalTypesEnabled;
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
        RecordVisitor v = new RecordVisitor(_provider, type, this);
        _builder = v;
        return v;
    }

    @Override
    public JsonMapFormatVisitor expectMapFormat(JavaType mapType) {
        MapVisitor v = new MapVisitor(_provider, mapType, this);
        _builder = v;
        return v;
    }

    @Override
    public JsonArrayFormatVisitor expectArrayFormat(final JavaType convertedType) {
        // 22-Mar-2016, tatu: Actually we can detect byte[] quite easily here can't we?
        if (convertedType.isArrayType()) {
            JavaType vt = convertedType.getContentType();
            if (vt.hasRawClass(Byte.TYPE)) {
                _builder = () -> AvroSchemaHelper.typedSchema(Schema.Type.BYTES, convertedType);
                return null;
            }
        }
        ArrayVisitor v = new ArrayVisitor(_provider, convertedType, this);
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

        if (isLogicalTypesEnabled() && _isDateTimeType(type)) {
            DateTimeVisitor v = new DateTimeVisitor(type);
            _builder = v;
            return v;
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
    public JsonAnyFormatVisitor expectAnyFormat(JavaType convertedType) {
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

    private boolean _isDateTimeType(JavaType type) {
        return Temporal.class.isAssignableFrom(type.getRawClass());
    }
}
