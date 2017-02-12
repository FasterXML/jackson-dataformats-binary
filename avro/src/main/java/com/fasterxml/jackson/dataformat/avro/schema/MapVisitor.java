package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor;
import org.apache.avro.Schema;
import org.apache.avro.reflect.Stringable;
import org.apache.avro.specific.SpecificData;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MapVisitor extends JsonMapFormatVisitor.Base
    implements SchemaBuilder
{
    protected final Set<Class<?>> _defaultStringableClasses = new HashSet<Class<?>>(Arrays.asList(URI.class,
                                                                                                  URL.class,
                                                                                                  BigInteger.class,
                                                                                                  BigDecimal.class,
                                                                                                  File.class,
                                                                                                  String.class
    ));

    protected final JavaType _type;
    
    protected final DefinedSchemas _schemas;
    
    protected Schema _valueSchema;

    protected String _keyClass;
    
    public MapVisitor(SerializerProvider p, JavaType type, DefinedSchemas schemas)
    {
        super(p);
        _type = type;
        _schemas = schemas;
    }

    @Override
    public Schema builtAvroSchema() {
        // Assumption now is that we are done, so let's assign fields
        if (_valueSchema == null) {
            throw new IllegalStateException("Missing value type for "+_type);
        }
        Schema schema = Schema.createMap(_valueSchema);
        if (_keyClass != null) {
            schema.addProp(SpecificData.KEY_CLASS_PROP, _keyClass);
        }
        return schema;
    }

    /*
    /**********************************************************
    /* JsonMapFormatVisitor implementation
    /**********************************************************
     */
    
    @Override
    public void keyFormat(JsonFormatVisitable handler, JavaType keyType)
        throws JsonMappingException
    {
        // String keys are implicitly handled
        if (keyType.hasRawClass(String.class)) {
            return;
        }
        if (_defaultStringableClasses.contains(keyType.getRawClass()) || keyType.getRawClass().getAnnotation(Stringable.class) != null) {
            _keyClass = keyType.getRawClass().getName();
        } else {
            throw new UnsupportedOperationException("Maps with non-stringable keys are not supported yet: " + keyType);
        }
    }

    @Override
    public void valueFormat(JsonFormatVisitable handler, JavaType valueType)
        throws JsonMappingException
    {
        VisitorFormatWrapperImpl wrapper = new VisitorFormatWrapperImpl(_schemas, getProvider());
        handler.acceptJsonFormatVisitor(wrapper, valueType);
        _valueSchema = wrapper.getAvroSchema();
    }
}
