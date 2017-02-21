package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.dataformat.avro.AvroFixedSize;

import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroMeta;
import org.apache.avro.reflect.AvroSchema;

public class RecordVisitor
    extends JsonObjectFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;

    protected final DefinedSchemas _schemas;

    protected Schema _avroSchema;

    protected boolean _overridden;
    
    protected List<Schema.Field> _fields = new ArrayList<Schema.Field>();
    
    public RecordVisitor(SerializerProvider p, JavaType type, DefinedSchemas schemas)
    {
        super(p);
        _type = type;
        _schemas = schemas;
        // Check if the schema for this record is overridden
        AnnotatedClass ac = getProvider().getConfig().introspectDirectClassAnnotations(_type).getClassInfo();
        AvroSchema ann = ac.getAnnotation(AvroSchema.class);
        if (ann != null) {
            Schema.Parser parser = new Schema.Parser();
            _avroSchema = parser.parse(ann.value());
            _overridden = true;
        } else {
            String description = getProvider().getAnnotationIntrospector().findClassDescription(ac);
            _avroSchema = Schema.createRecord(AvroSchemaHelper.getName(type), description, AvroSchemaHelper.getNamespace(type), false);
            _overridden = false;
            AvroMeta meta = ac.getAnnotation(AvroMeta.class);
            if (meta != null) {
                _avroSchema.addProp(meta.key(), meta.value());
            }
        }
        schemas.addSchema(type, _avroSchema);
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
        VisitorFormatWrapperImpl wrapper = new VisitorFormatWrapperImpl(_schemas, getProvider());
        handler.acceptJsonFormatVisitor(wrapper, type);
        Schema schema = wrapper.getAvroSchema();
        _fields.add(new Schema.Field(name, schema, null, null));
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
    
    protected Schema.Field schemaFieldForWriter(BeanProperty prop, boolean optional) throws JsonMappingException
    {
        Schema writerSchema;
        // Check if schema for property is overridden
        AvroSchema schemaOverride = prop.getAnnotation(AvroSchema.class);
        if (schemaOverride != null) {
            Schema.Parser parser = new Schema.Parser();
            writerSchema = parser.parse(schemaOverride.value());
        } else {
            AvroFixedSize fixedSize = prop.getAnnotation(AvroFixedSize.class);
            if (fixedSize != null) {
                writerSchema = Schema.createFixed(fixedSize.typeName(), null, fixedSize.typeNamespace(), fixedSize.size());
            } else {
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
                writerSchema = visitor.getAvroSchema();
            }

            /* 23-Nov-2012, tatu: Actually let's also assume that primitive type values
             *   are required, as Jackson does not distinguish whether optional has been
             *   defined, or is merely the default setting.
             */
            if (optional && !prop.getType().isPrimitive()) {
                writerSchema = AvroSchemaHelper.unionWithNull(writerSchema);
            }
        }
        String description = getProvider().getAnnotationIntrospector().findPropertyDescription(prop.getMember());
        Schema.Field field = new Schema.Field(prop.getName(), writerSchema, description, null);

        AvroMeta meta = prop.getAnnotation(AvroMeta.class);
        if (meta != null) {
            field.addProp(meta.key(), meta.value());
        }

        return field;
    }
}
