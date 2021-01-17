package com.fasterxml.jackson.dataformat.avro;

import java.io.File;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.dataformat.avro.deser.AvroUntypedDeserializer;

/**
 * Module that adds support for handling datatypes specific to the standard
 * Java Avro library:
 *<ul>
 * <li>Support handling of {@link Schema}: remove from Avro lib generated types;
 *     serialize as (JSON) String for others
 *  </li>
 * <li>Add special {@link AnnotationIntrospector} that supports Apache Avro lib
 *    annotations
 *  </li>
 * <li>Support limited polymorphic handling of properties with nominal type
 *    of {@link java.lang.Object}.
 *  </li>
 *</ul>
 */
public class AvroModule extends Module
    implements java.io.Serializable
{
    private static final long serialVersionUID = 3L;

    protected final static AvroAnnotationIntrospector INTR
           = new AvroAnnotationIntrospector();

    protected AnnotationIntrospector _intr = INTR;

    public AvroModule() { }

    @Override
    public String getModuleName() {
        return getClass().getName();
    }

    @Override
    public Version version() { return PackageVersion.VERSION; }

    /*
    /**********************************************************
    /* Configurability
    /**********************************************************
     */
    
    /**
     * Fluent method that configures this module instance 
     */
    public AvroModule withAnnotationIntrospector(AnnotationIntrospector intr) {
        _intr = intr;
        return this;
    }

    /*
    /**********************************************************
    /* Set up methods
    /**********************************************************
     */

    @Override
    public void setupModule(SetupContext context) {
        _addIntrospector(context);
        _addModifiers(context);
        _addDeserializers(context);
        _addSerializers(context);
    }

    protected void _addIntrospector(SetupContext context) {
        if (_intr != null) {
            // insert (instead of append) to have higher precedence
            context.insertAnnotationIntrospector(_intr);
        }
    }

    protected void _addModifiers(SetupContext context) {
        // 08-Mar-2016, tatu: to fix [dataformat-avro#35], need to prune 'schema' property:
        context.addSerializerModifier(new AvroSerializerModifier());
    }

    protected void _addDeserializers(SetupContext context) {
        // Override untyped deserializer to one that checks for type information in the schema before going to default handling
        SimpleDeserializers desers = new SimpleDeserializers();
        desers.addDeserializer(Object.class, AvroUntypedDeserializer.construct(context.typeFactory()));
        context.addDeserializers(desers);
    }

    protected void _addSerializers(SetupContext context) {
        SimpleSerializers sers = new SimpleSerializers();
        sers.addSerializer(new SchemaSerializer());
        // 09-Mar-2017, tatu: As per [dataformats-binary#57], require simple serialization?
        sers.addSerializer(File.class, new ToStringSerializer(File.class));
        context.addSerializers(sers);
    }

    /*
    /**********************************************************
    /* Helper classes (as long as number is small)
    /**********************************************************
     */

    public static class SchemaSerializer extends StdSerializer<Schema>
    {
        public SchemaSerializer() {
            super(Schema.class);
        }

        @Override
        public void serialize(Schema value, JsonGenerator gen, SerializerProvider prov)
            throws JacksonException
        {
            // Let's simply write as String, for now
            gen.writeString(value.toString());
        }
    }
}
