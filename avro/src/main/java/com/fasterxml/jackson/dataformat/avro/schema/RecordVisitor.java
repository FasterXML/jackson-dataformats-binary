package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.*;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.reflect.AvroMeta;
import org.apache.avro.reflect.AvroSchema;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.dataformat.avro.AvroFixedSize;
import com.fasterxml.jackson.dataformat.avro.annotation.AvroDecimal;
import com.fasterxml.jackson.dataformat.avro.ser.CustomEncodingSerializer;

public class RecordVisitor
    extends JsonObjectFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;

    protected final VisitorFormatWrapperImpl _visitorWrapper;

    /**
     * Tracks if the schema for this record has been overridden (by an annotation or other means), and calls to the {@code property} and
     * {@code optionalProperty} methods should be ignored.
     */
    protected final boolean _overridden;

    protected Schema _avroSchema;

    protected List<Schema.Field> _fields = new ArrayList<>();

    public RecordVisitor(SerializerProvider p, JavaType type, VisitorFormatWrapperImpl visitorWrapper)
    {
        super(p);
        _type = type;
        _visitorWrapper = visitorWrapper;
        // Check if the schema for this record is overridden
        BeanDescription bean = getProvider().getConfig().introspectDirectClassAnnotations(_type);
        List<NamedType> subTypes = getProvider().getAnnotationIntrospector().findSubtypes(bean.getClassInfo());
        AvroSchema ann = bean.getClassInfo().getAnnotation(AvroSchema.class);
        if (ann != null) {
            _avroSchema = AvroSchemaHelper.parseJsonSchema(ann.value());
            _overridden = true;
        } else if (subTypes != null && !subTypes.isEmpty()) {
            List<Schema> unionSchemas = new ArrayList<>();
            try {
                for (NamedType subType : subTypes) {
                    JsonSerializer<?> ser = getProvider().findValueSerializer(subType.getType());
                    VisitorFormatWrapperImpl visitor = _visitorWrapper.createChildWrapper();
                    ser.acceptJsonFormatVisitor(visitor, getProvider().getTypeFactory().constructType(subType.getType()));
                    unionSchemas.add(visitor.getAvroSchema());
                }
                _avroSchema = Schema.createUnion(unionSchemas);
                _overridden = true;
            } catch (JsonMappingException jme) {
                throw new RuntimeException("Failed to build schema", jme);
            }
        } else {
            _avroSchema = AvroSchemaHelper.initializeRecordSchema(bean);
            _overridden = false;
            AvroMeta meta = bean.getClassInfo().getAnnotation(AvroMeta.class);
            if (meta != null) {
                _avroSchema.addProp(meta.key(), meta.value());
            }
        }
        _visitorWrapper.getSchemas().addSchema(type, _avroSchema);
    }

    @Override
    public Schema builtAvroSchema() {
        if (!_overridden) {
            // Assumption now is that we are done, so let's assign fields
            _avroSchema.setFields(_fields);
        }
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
        if (_overridden) {
            return;
        }
        _fields.add(schemaFieldForWriter(writer, false));
    }

    @Override
    public void property(String name, JsonFormatVisitable handler,
            JavaType type) throws JsonMappingException
    {
        if (_overridden) {
            return;
        }
        VisitorFormatWrapperImpl visitorWrapper = _visitorWrapper.createChildWrapper();
        handler.acceptJsonFormatVisitor(visitorWrapper, type);
        Schema schema = visitorWrapper.getAvroSchema();
        _fields.add(new Schema.Field(name, schema, null, (Object) null));
    }

    @Override
    public void optionalProperty(BeanProperty writer) throws JsonMappingException {
        if (_overridden) {
            return;
        }
        _fields.add(schemaFieldForWriter(writer, true));
    }

    @Override
    public void optionalProperty(String name, JsonFormatVisitable handler,
            JavaType type) throws JsonMappingException
    {
        if (_overridden) {
            return;
        }
        VisitorFormatWrapperImpl visitorWrapper = _visitorWrapper.createChildWrapper();
        handler.acceptJsonFormatVisitor(visitorWrapper, type);
        Schema schema = visitorWrapper.getAvroSchema();
        if (!type.isPrimitive()) {
            schema = AvroSchemaHelper.unionWithNull(schema);
        }
        _fields.add(new Schema.Field(name, schema, null, (Object) null));
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected Schema.Field schemaFieldForWriter(BeanProperty prop, boolean optional) throws JsonMappingException
    {
        Schema writerSchema = null;
        // Check if schema for property is overridden
        AvroSchema schemaOverride = prop.getAnnotation(AvroSchema.class);
        if (schemaOverride != null) {
            Schema.Parser parser = new Schema.Parser();
            writerSchema = parser.parse(schemaOverride.value());
        } else {
            AvroFixedSize fixedSize = prop.getAnnotation(AvroFixedSize.class);
            if (fixedSize != null) {
                writerSchema = Schema.createFixed(fixedSize.typeName(), null, fixedSize.typeNamespace(), fixedSize.size());
            }
            if (_visitorWrapper.isLogicalTypesEnabled()) {
                AvroDecimal avroDecimal = prop.getAnnotation(AvroDecimal.class);
                if (avroDecimal != null) {
                    if (writerSchema == null) {
                        writerSchema = Schema.create(Type.BYTES);
                    }
                    writerSchema = LogicalTypes.decimal(avroDecimal.precision(), avroDecimal.scale())
                            .addToSchema(writerSchema);
                }
            }
            if (writerSchema == null) {
                JsonSerializer<?> ser = null;

                // 23-Nov-2012, tatu: Ideally shouldn't need to do this but...
                if (prop instanceof BeanPropertyWriter) {
                    BeanPropertyWriter bpw = (BeanPropertyWriter) prop;
                    ser = bpw.getSerializer();
                    // 2-Mar-2017, bryan: AvroEncode annotation expects to have the schema used directly
                    optional = optional && !(ser instanceof CustomEncodingSerializer); // Don't modify schema
                }
                final SerializerProvider prov = getProvider();
                if (ser == null) {
                    if (prov == null) {
                        throw JsonMappingException.from(prov, "SerializerProvider missing for RecordVisitor");
                    }
                    ser = prov.findValueSerializer(prop.getType(), prop);
                }
                VisitorFormatWrapperImpl visitorWrapper = _visitorWrapper.createChildWrapper();
                ser.acceptJsonFormatVisitor(visitorWrapper, prop.getType());
                writerSchema = visitorWrapper.getAvroSchema();
            }

            /* 23-Nov-2012, tatu: Actually let's also assume that primitive type values
             *   are required, as Jackson does not distinguish whether optional has been
             *   defined, or is merely the default setting.
             */
            if (optional && !prop.getType().isPrimitive()) {
                writerSchema = AvroSchemaHelper.unionWithNull(writerSchema);
            }
        }
        JsonNode defaultValue = AvroSchemaHelper.parseDefaultValue(prop.getMetadata().getDefaultValue());
        writerSchema = reorderUnionToMatchDefaultType(writerSchema, defaultValue);
        Schema.Field field = new Schema.Field(prop.getName(), writerSchema, prop.getMetadata().getDescription(),
                AvroSchemaHelper.jsonNodeToObject(defaultValue));

        AvroMeta meta = prop.getAnnotation(AvroMeta.class);
        if (meta != null) {
            field.addProp(meta.key(), meta.value());
        }
        List<PropertyName> aliases = prop.findAliases(getProvider().getConfig());
        if (!aliases.isEmpty()) {
            for (PropertyName pn : aliases) {
                field.addAlias(pn.getSimpleName());
            }
        }

        return field;
    }

    /**
     * A union schema with a default value must always have the schema branch corresponding to the default value first, or Avro will print a
     * warning complaining that the default value is not compatible. If {@code schema} is a {@link Type#UNION UNION} schema and
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
            Map<String, Object> jsonProps = schema.getObjectProps();
            schema = Schema.createUnion(types);
            // copy any properties over
            for (String property : jsonProps.keySet()) {
                schema.addProp(property, jsonProps.get(property));
            }
        }
        return schema;
    }
}
