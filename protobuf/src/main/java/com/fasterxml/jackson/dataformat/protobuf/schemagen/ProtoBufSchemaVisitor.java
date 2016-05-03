package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonAnyFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import com.squareup.protoparser.TypeElement;

public class ProtoBufSchemaVisitor extends JsonFormatVisitorWrapper.Base
    implements TypeElementBuilder
{
	protected DefinedTypeElementBuilders _definedTypeElementBuilders;

	protected TypeElementBuilder _builder;

	protected boolean _isNested;

	/*
	 * /**********************************************************************
	 * /* Construction
	 * /**********************************************************************
	 */

	public ProtoBufSchemaVisitor(SerializerProvider provider) {
		this(provider, null, false);
	}

	public ProtoBufSchemaVisitor(SerializerProvider provider, DefinedTypeElementBuilders definedTypeElementBuilders,
			boolean isNested) {
		super(provider);

		_definedTypeElementBuilders = (definedTypeElementBuilders != null) ? definedTypeElementBuilders
				: new DefinedTypeElementBuilders();

		_isNested = isNested;
	}

	/*
	 * /**********************************************************************
	 * /* Extended API
	 * /**********************************************************************
	 */

	@Override
	public TypeElement build() {
		return _builder.build();
	}

	public Set<TypeElement> buildWithDependencies() {
		Set<TypeElement> allTypeElements = new LinkedHashSet<>();
		allTypeElements.add(build());
		
		for(TypeElementBuilder builder : _definedTypeElementBuilders.getDependencyBuilders()) {
			allTypeElements.add(builder.build());
		}
		return allTypeElements;
	}

	/*
	/**********************************************************************
	/* Callbacks
	/**********************************************************************
	 */

	@Override
	public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
		MessageElementVisitor visitor = new MessageElementVisitor(_provider, type, _definedTypeElementBuilders,
				_isNested);
		_builder = visitor;
		return visitor;
	}

	@Override
	public JsonMapFormatVisitor expectMapFormat(JavaType mapType) {
		return _throwUnsupported("'Map' type not supported as root type by protobuf");
	}

	@Override
	public JsonArrayFormatVisitor expectArrayFormat(JavaType convertedType) {
		return _throwUnsupported("'Array' type not supported as root type by protobuf");
	}

	@Override
	public JsonStringFormatVisitor expectStringFormat(JavaType type) {
		if (!type.isEnumType()) {
			return _throwUnsupported("'String' type not supported as root type by protobuf");
		}

		EnumElementVisitor visitor = new EnumElementVisitor(_provider, type, _definedTypeElementBuilders, _isNested);
		_builder = visitor;
		return visitor;
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

	@Override
	public JsonNullFormatVisitor expectNullFormat(JavaType convertedType) {
		return _throwUnsupported("'Null' type not supported as root type by protobuf");
	}

	@Override
	public JsonAnyFormatVisitor expectAnyFormat(JavaType convertedType) {
		return _throwUnsupported("'Any' type not supported as root type by protobuf");
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
