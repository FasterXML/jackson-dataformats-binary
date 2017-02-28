package com.fasterxml.jackson.dataformat.avro;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;

/**
 * Convenience {@link AvroMapper}, which is mostly similar to simply
 * constructing a mapper with {@link AvroFactory}, but also adds little
 * bit of convenience around {@link AvroSchema} generation.
 * 
 * @since 2.5
 */
public class AvroMapper extends ObjectMapper
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructor that will construct mapper with standard {@link AvroFactory}
     * as codec, and will also register {@link AvroModule}.
     */
    public AvroMapper() {
        this(new AvroFactory());
    }

    /**
     * Constructor that will construct mapper with given {@link AvroFactory},
     * as well as register standard {@link AvroModule} (with default settings).
     */
    public AvroMapper(AvroFactory f) {
        super(f);
        registerModule(new AvroModule());
    }

    /**
     * Constructor that will construct mapper with standard {@link AvroFactory}
     * as codec, and register given modules but nothing else (that is, will
     * only register {@link AvroModule} if it's included as argument.
     */
    public AvroMapper(Module... modules) {
        super(new AvroFactory());
        registerModules(modules);
    }

    /**
     * Constructor that will construct mapper with specified {@link AvroFactory}
     * as codec, and register given modules but nothing else (that is, will
     * only register {@link AvroModule} if it's included as argument.
     */
    public AvroMapper(AvroFactory f, Module... modules) {
        super(f);
        registerModules(modules);
    }

    protected AvroMapper(ObjectMapper src) {
        super(src);
    }

    @Override
    public AvroMapper copy()
    {
        _checkInvalidCopy(AvroMapper.class);
        return new AvroMapper(this);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public AvroFactory getFactory() {
        return (AvroFactory) _jsonFactory;
    }
    
    /**
     * Factory method for constructing {@link AvroSchema} by introspecting given
     * POJO type and building schema that contains specified properties.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.5
     */
    public AvroSchema schemaFor(Class<?> type) throws JsonMappingException
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        acceptJsonFormatVisitor(type, gen);
        return gen.getGeneratedSchema();
    }

    /**
     * Factory method for constructing {@link AvroSchema} by introspecting given
     * POJO type and building schema that contains specified properties.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.5
     */
    public AvroSchema schemaFor(JavaType type) throws JsonMappingException
    {
        AvroSchemaGenerator gen = new AvroSchemaGenerator();
        acceptJsonFormatVisitor(type, gen);
        return gen.getGeneratedSchema();
    }

    /**
     * Method for reading an Avro Schema from given {@link InputStream},
     * and once done (successfully or not), closing the stream.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.6
     */
    public AvroSchema schemaFrom(InputStream in) throws IOException
    {
        try {
            return new AvroSchema(new Schema.Parser().setValidate(true)
                    .parse(in));
        } finally {
            in.close();
        }
    }

    /**
     * Convenience method for reading {@link AvroSchema} from given
     * encoded JSON representation.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.6
     */
    public AvroSchema schemaFrom(String schemaAsString) throws IOException
    {
        return new AvroSchema(new Schema.Parser().setValidate(true)
                .parse(schemaAsString));
    }

    /**
     * Convenience method for reading {@link AvroSchema} from given
     * encoded JSON representation.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
     *
     * @since 2.6
     */
    public AvroSchema schemaFrom(File schemaFile) throws IOException
    {
        return new AvroSchema(new Schema.Parser().setValidate(true)
                .parse(schemaFile));
    }
}
