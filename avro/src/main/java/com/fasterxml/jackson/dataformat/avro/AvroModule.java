package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Module that adds support for handling datatypes specific to the standard
 * Java Avro library; most specifically {@link Schema}
 *
 * @since 2.5
 */
public class AvroModule extends SimpleModule
{
    private static final long serialVersionUID = 1L;

    public AvroModule()
    {
        super(PackageVersion.VERSION);
        addSerializer(new SchemaSerializer());
        // 08-Mar-2016, tatu: to fix [dataformat-avro#35], need to prune 'schema' property:
        setSerializerModifier(new AvroSerializerModifier());
    }

    /**
     * @deprecated since 2.7
     */
    @Deprecated // 08-Mar-2016, tatu: How on earth did this end up here?!?
    public Schema schema;

    /*
    /**********************************************************
    /* Helper classes (as long as number is small)
    /**********************************************************
     */

    public static class SchemaSerializer extends StdSerializer<Schema>
    {
        private static final long serialVersionUID = 1L;

        public SchemaSerializer() {
            super(Schema.class);
        }

        @Override
        public void serialize(Schema value, JsonGenerator gen, SerializerProvider prov)
                throws IOException {
            // Let's simply write as String, for now
            gen.writeString(value.toString());
        }
    }
}
