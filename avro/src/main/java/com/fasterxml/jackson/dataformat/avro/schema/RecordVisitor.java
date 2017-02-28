package com.fasterxml.jackson.dataformat.avro.schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.dataformat.avro.AvroFixedSize;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

public class RecordVisitor
    extends JsonObjectFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;

    protected final DefinedSchemas _schemas;

    protected Schema _avroSchema;
    
    protected List<Schema.Field> _fields = new ArrayList<Schema.Field>();
    
    public RecordVisitor(SerializerProvider p, JavaType type, DefinedSchemas schemas)
    {
        super(p);
        _type = type;
        _schemas = schemas;
        _avroSchema = Schema.createRecord(AvroSchemaHelper.getName(type),
                "Schema for "+type.toCanonical(),
                AvroSchemaHelper.getNamespace(type), false);
        schemas.addSchema(type, _avroSchema);
    }
    
    @Override
    public Schema builtAvroSchema() {
        // Assumption now is that we are done, so let's assign fields
        _avroSchema.setFields(_fields);
        return _avroSchema;
    }

    /*
    /**********************************************************
    /* JsonObjectFormatVisitor implementation
    /**********************************************************
     */
    
    @Override
    public void property(BeanProperty writer) throws JsonMappingException
    {
        Schema schema = schemaForWriter(writer);
        JsonNode defaultValue = parseJson(getProvider().getAnnotationIntrospector().findPropertyDefaultValue(writer.getMember()));
        schema = reorderUnionToMatchDefaultType(schema, defaultValue);
        _fields.add(new Schema.Field(writer.getName(), schema, null, defaultValue));
    }

    @Override
    public void property(String name, JsonFormatVisitable handler,
            JavaType type) throws JsonMappingException
    {
        VisitorFormatWrapperImpl wrapper = new VisitorFormatWrapperImpl(_schemas, getProvider());
        handler.acceptJsonFormatVisitor(wrapper, type);
        Schema schema = wrapper.getAvroSchema();
        _fields.add(new Schema.Field(name, schema, null, null));
    }

    @Override
    public void optionalProperty(BeanProperty writer) throws JsonMappingException {
        Schema schema = schemaForWriter(writer);
        /* 23-Nov-2012, tatu: Actually let's also assume that primitive type values
         *   are required, as Jackson does not distinguish whether optional has been
         *   defined, or is merely the default setting.
         */
        if (!writer.getType().isPrimitive()) {
            schema = AvroSchemaHelper.unionWithNull(schema);
        }
        JsonNode defaultValue = parseJson(getProvider().getAnnotationIntrospector().findPropertyDefaultValue(writer.getMember()));
        schema = reorderUnionToMatchDefaultType(schema, defaultValue);
        _fields.add(new Schema.Field(writer.getName(), schema, null, defaultValue));
    }

    @Override
    public void optionalProperty(String name, JsonFormatVisitable handler,
            JavaType type) throws JsonMappingException
    {
        VisitorFormatWrapperImpl wrapper = new VisitorFormatWrapperImpl(_schemas, getProvider());
        handler.acceptJsonFormatVisitor(wrapper, type);
        Schema schema = wrapper.getAvroSchema();
        if (!type.isPrimitive()) {
            schema = AvroSchemaHelper.unionWithNull(schema);
        }
        _fields.add(new Schema.Field(name, schema, null, null));
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */
    
    protected Schema schemaForWriter(BeanProperty prop) throws JsonMappingException
    {
        AvroFixedSize fixedSize = prop.getAnnotation(AvroFixedSize.class);
        if (fixedSize != null) {
            return Schema.createFixed(fixedSize.typeName(), null, fixedSize.typeNamespace(), fixedSize.size());
        }

        JsonSerializer<?> ser = null;

        // 23-Nov-2012, tatu: Ideally shouldn't need to do this but...
        if (prop instanceof BeanPropertyWriter) {
            BeanPropertyWriter bpw = (BeanPropertyWriter) prop;
            ser = bpw.getSerializer();
        }
        final SerializerProvider prov = getProvider();
        if (ser == null) {
            if (prov == null) {
                throw JsonMappingException.from(prov, "SerializerProvider missing for RecordVisitor");
            }
            ser = prov.findValueSerializer(prop.getType(), prop);
        }
        VisitorFormatWrapperImpl visitor = new VisitorFormatWrapperImpl(_schemas, prov);
        ser.acceptJsonFormatVisitor(visitor, prop.getType());
        return visitor.getAvroSchema();
    }

    /**
     * Parses a JSON-encoded string for use as the default value of a field
     *
     * @param defaultValue
     *     Default value as a JSON-encoded string
     *
     * @return Jackson V1 {@link JsonNode} for use as the default value in a {@link Schema.Field}
     *
     * @throws JsonMappingException
     *     if {@code defaultValue} is not valid JSON
     */
    protected JsonNode parseJson(String defaultValue) throws JsonMappingException {
        if (defaultValue == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readTree(defaultValue);
        } catch (IOException e) {
            throw JsonMappingException.from(getProvider(), "Unable to parse default value as JSON: " + defaultValue, e);
        }
    }

    /**
     * A union schema with a default value must always have the schema branch corresponding to the default value first, or Avro will print a
     * warning complaining that the default value is not compatible. If {@code schema} is a {@link Schema.Type#UNION UNION} schema and
     * {@code defaultValue} is non-{@code null}, this finds the appropriate branch in the union and reorders the union so that it is first.
     *
     * @param schema
     *     Schema to reorder; If {@code null} or not a {@code UNION}, then it is returned unmodified.
     * @param defaultValue
     *     Default value to match with the union
     *
     * @return A schema modified so the first branch matches the type of {@code defaultValue}; otherwise, {@code schema} is returned
     * unmodified.
     */
    protected Schema reorderUnionToMatchDefaultType(Schema schema, JsonNode defaultValue) {
        if (schema == null || defaultValue == null || schema.getType() != Type.UNION) {
            return schema;
        }
        List<Schema> types = new ArrayList<>(schema.getTypes());
        Integer matchingIndex = null;
        if (defaultValue.isArray()) {
            matchingIndex = schema.getIndexNamed(Type.ARRAY.getName());
        } else if (defaultValue.isObject()) {
            matchingIndex = schema.getIndexNamed(Type.MAP.getName());
            if (matchingIndex == null) {
                // search for a record
                for (int i = 0; i < types.size(); i++) {
                    if (types.get(i).getType() == Type.RECORD) {
                        matchingIndex = i;
                        break;
                    }
                }
            }
        } else if (defaultValue.isBoolean()) {
            matchingIndex = schema.getIndexNamed(Type.BOOLEAN.getName());
        } else if (defaultValue.isNull()) {
            matchingIndex = schema.getIndexNamed(Type.NULL.getName());
        } else if (defaultValue.isBinary()) {
            matchingIndex = schema.getIndexNamed(Type.BYTES.getName());
        } else if (defaultValue.isFloatingPointNumber()) {
            matchingIndex = schema.getIndexNamed(Type.DOUBLE.getName());
            if (matchingIndex == null) {
                matchingIndex = schema.getIndexNamed(Type.FLOAT.getName());
            }
        } else if (defaultValue.isIntegralNumber()) {
            matchingIndex = schema.getIndexNamed(Type.LONG.getName());
            if (matchingIndex == null) {
                matchingIndex = schema.getIndexNamed(Type.INT.getName());
            }
        } else if (defaultValue.isTextual()) {
            matchingIndex = schema.getIndexNamed(Type.STRING.getName());
            if (matchingIndex == null) {
                // search for an enum
                for (int i = 0; i < types.size(); i++) {
                    if (types.get(i).getType() == Type.ENUM) {
                        matchingIndex = i;
                        break;
                    }
                }
            }
        }
        if (matchingIndex != null) {
            types.add(0, types.remove((int)matchingIndex));
            Map<String, JsonNode> jsonProps = schema.getJsonProps();
            schema = Schema.createUnion(types);
            // copy any properties over
            for (String property : jsonProps.keySet()) {
                schema.addProp(property, jsonProps.get(property));
            }
        }
        return schema;
    }
}
