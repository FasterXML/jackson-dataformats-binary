package com.fasterxml.jackson.dataformat.avro.schema;

import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.reflect.AvroMeta;
import org.apache.avro.reflect.AvroSchema;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.dataformat.avro.AvroFixedSize;

public class RecordVisitor
    extends JsonObjectFormatVisitor.Base
    implements SchemaBuilder
{
    protected final JavaType _type;

    protected final DefinedSchemas _schemas;

    /**
     * Tracks if the schema for this record has been overridden (by an annotation or other means), and calls to the {@code property} and
     * {@code optionalProperty} methods should be ignored.
     */
    protected final boolean _overridden;

    protected Schema _avroSchema;
    
    protected List<Schema.Field> _fields = new ArrayList<Schema.Field>();
    
    public RecordVisitor(SerializerProvider p, JavaType type, DefinedSchemas schemas)
    {
        super(p);
        _type = type;
        _schemas = schemas;
        // Check if the schema for this record is overridden
        BeanDescription bean = getProvider().getConfig().introspectDirectClassAnnotations(_type);
        AvroSchema ann = bean.getClassInfo().getAnnotation(AvroSchema.class);
        if (ann != null) {
            _avroSchema = AvroSchemaHelper.parseJsonSchema(ann.value());
            _overridden = true;
        } else {
            _avroSchema = AvroSchemaHelper.initializeRecordSchema(bean);
            _overridden = false;
            AvroMeta meta = bean.getClassInfo().getAnnotation(AvroMeta.class);
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
        /*
        Schema schema = schemaForWriter(writer);        
        _fields.add(_field(writer, schema));
        */
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
        VisitorFormatWrapperImpl wrapper = new VisitorFormatWrapperImpl(_schemas, getProvider());
        handler.acceptJsonFormatVisitor(wrapper, type);
        Schema schema = wrapper.getAvroSchema();
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
        Schema.Field field = new Schema.Field(prop.getName(), writerSchema, prop.getMetadata().getDescription(),
                (Object) null);

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
}
