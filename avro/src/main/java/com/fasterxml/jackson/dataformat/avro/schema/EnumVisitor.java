package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.ArrayList;
import java.util.Set;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;

import org.apache.avro.Schema;

/**
 * Specific visitor for Java Enum types that are to be exposed as
 * Avro Enums. Used unless Java Enums are to be mapped to Avro Strings.
 *
 * @since 2.18
 */
public class EnumVisitor extends JsonStringFormatVisitor.Base
    implements SchemaBuilder
{
    protected final SerializerProvider _provider;
    protected final JavaType _type;
    protected final DefinedSchemas _schemas;

    protected Set<String> _enums;

    public EnumVisitor(SerializerProvider provider, DefinedSchemas schemas, JavaType t) {
        _schemas = schemas;
        _type = t;
        _provider = provider;
    }

    @Override
    public void enumTypes(Set<String> enums) {
        _enums = enums;
    }

    @Override
    public Schema builtAvroSchema() {
        if (_enums == null) {
            throw new IllegalStateException("Possible enum values cannot be null");
        }

        BeanDescription bean = _provider.getConfig().introspectClassAnnotations(_type);
        Schema schema = AvroSchemaHelper.createEnumSchema(bean, new ArrayList<>(_enums));
        _schemas.addSchema(_type, schema);
        return schema;
    }
}
