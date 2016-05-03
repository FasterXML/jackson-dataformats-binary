package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import com.fasterxml.jackson.dataformat.protobuf.schema.NativeProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.squareup.protoparser.TypeElement;

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
		// NOTE: null is fine here, as provider links itself after construction
		super(null);
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
			types = new ArrayList<>();
			types.add(this.build());
		}

		return NativeProtobufSchema.construct(_rootType.getRawClass().getName(), types).forFirstType();
	}

	@Override
	public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
		_rootType = type;
		return super.expectObjectFormat(type);
	}

	@Override
	public JsonStringFormatVisitor expectStringFormat(JavaType type) {
		return _throwUnsupported("'String' type not supported as root type by protobuf");
	}
}
