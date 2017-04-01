package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.*;

import com.squareup.protoparser.DataType;
import com.squareup.protoparser.TypeElement;
import com.squareup.protoparser.DataType.ScalarType;

public class ProtoBufSchemaVisitor extends JsonFormatVisitorWrapper.Base
    implements TypeElementBuilder
{
    protected final DefinedTypeElementBuilders _definedTypeElementBuilders;

    /**
     * When visiting Object (Record) types, Enums, Arrays, we get
     * this type builder.
     */
    protected TypeElementBuilder _builder;

    /**
     * When visiting simple scalar types, we'll get this assigned
     */
    protected DataType _simpleType;

    protected boolean _isNested;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
	*/

    // Called by sub-classes only
    protected ProtoBufSchemaVisitor() {
        this(null, new DefinedTypeElementBuilders(), false);
    }

    public ProtoBufSchemaVisitor(SerializerProvider provider, DefinedTypeElementBuilders defBuilders,
            boolean isNested)
    {
        super(provider);
        _definedTypeElementBuilders = defBuilders;
        _isNested = isNested;
    }

    /*
    /**********************************************************************
    /* Extended API
    /**********************************************************************
	*/

    @Override
    public TypeElement build() {
        return _builder.build();
    }

    public DataType getSimpleType() {
        return _simpleType;
    }

    public Set<TypeElement> buildWithDependencies() {
        Set<TypeElement> allTypeElements = new LinkedHashSet<>();
        allTypeElements.add(build());

        for (TypeElementBuilder builder : _definedTypeElementBuilders.getDependencyBuilders()) {
            allTypeElements.add(builder.build());
        }
        return allTypeElements;
    }

    /*
    /*********************************************************************
    /* Callbacks, structured types
    /*********************************************************************
     */

	@Override
	public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
		MessageElementVisitor visitor = new MessageElementVisitor(_provider, type, _definedTypeElementBuilders,
				_isNested);
		_builder = visitor;
		_definedTypeElementBuilders.addTypeElement(type, visitor, _isNested);
		return visitor;
	}

	@Override
	public JsonMapFormatVisitor expectMapFormat(JavaType mapType) {
         // 31-Mar-2017, tatu: I don't think protobuf v2 really supports map types natively,
	    //   and we can't quite assume anything specific can we?
		return _throwUnsupported("'Map' type not supported as type by protobuf module");
	}

	@Override
	public JsonArrayFormatVisitor expectArrayFormat(JavaType type) {
         // 31-Mar-2017, tatu: This is bit messy, may get Base64 encoded or int array so
         if (ProtobufSchemaHelper.isBinaryType(type)) {
              _simpleType = ScalarType.BYTES;
              return null;
         }
         
         // !!! TODO: surely we should support array types, right?
         
         return _throwUnsupported("'Map' type not supported as type by protobuf module");
	}

    /*
    /*********************************************************************
    /* Callbacks, scalar types
    /*********************************************************************
     */

    @Override
    public JsonStringFormatVisitor expectStringFormat(JavaType type) {
        if (type.isEnumType()) {
            EnumElementVisitor visitor = new EnumElementVisitor(_provider, type, _definedTypeElementBuilders, _isNested);
            _builder = visitor;
            _definedTypeElementBuilders.addTypeElement(type, visitor, _isNested);
            return visitor;
        }
        // 31-Mar-2017, tatu: This is bit messy, may get Base64 encoded or int array so
        if (ProtobufSchemaHelper.isBinaryType(type)) {
            _simpleType = ScalarType.BYTES;
        } else {
            _simpleType = ScalarType.STRING;
        }
        return null;
    }

    @Override
    public JsonNumberFormatVisitor expectNumberFormat(JavaType type)
    {
        // default to some sane value
        _simpleType = DataType.ScalarType.DOUBLE;
        return new JsonNumberFormatVisitor.Base() {
            @Override
            public void numberType(NumberType nt) {
                switch (nt) {
                // should only get decimal types
                case FLOAT:
                    _simpleType = ScalarType.FLOAT;
                    break;
                case BIG_DECIMAL:
                case DOUBLE:
                    _simpleType = ScalarType.DOUBLE;
                    break;
                default:
                }
            }
         };
	}

	@Override
	public JsonIntegerFormatVisitor expectIntegerFormat(JavaType type)
	{
	    // default to some sane value
         _simpleType = DataType.ScalarType.INT64;
         return new JsonIntegerFormatVisitor.Base() {
            @Override
            public void numberType(NumberType nt) {
                switch (nt) {
                // should only get integer types
                case INT:
                    _simpleType = ScalarType.INT32;
                    break;
                case LONG:
                case BIG_INTEGER:
                    _simpleType = ScalarType.INT64;
                    break;
                default:
                }
            }
         };
	}

	@Override
	public JsonBooleanFormatVisitor expectBooleanFormat(JavaType convertedType) {
	    _simpleType = ScalarType.BOOL;
         return null;
	}

	@Override
	public JsonNullFormatVisitor expectNullFormat(JavaType convertedType) {
         return _throwUnsupported("'Null type' not supported as a type by protobuf");
	}

	@Override
     public JsonAnyFormatVisitor expectAnyFormat(JavaType convertedType) {
          return _throwUnsupported("'Any' type not supported as a type by protobuf");
     }

     /*
	/*********************************************************************
	/* Internal methods
	/*********************************************************************
	 */

	protected <T> T _throwUnsupported() {
		return _throwUnsupported("Format variation not supported");
	}

	protected <T> T _throwUnsupported(String msg) {
		throw new UnsupportedOperationException(msg);
	}
}
