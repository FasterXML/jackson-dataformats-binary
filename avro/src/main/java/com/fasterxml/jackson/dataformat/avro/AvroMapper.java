package com.fasterxml.jackson.dataformat.avro;

import java.io.*;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.util.ClassUtil;
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
    private static final long serialVersionUID = 2L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * Avro backend.
     *
     * @since 2.10
     */
    public static class Builder extends MapperBuilder<AvroMapper, Builder>
    {
        protected final AvroFactory _streamFactory; // since 2.10

        public Builder(AvroMapper m) {
            super(m);
            _streamFactory = m.getFactory();
        }

        /*
        /******************************************************************
        /* Format features
        /******************************************************************
         */

        public Builder enable(AvroParser.Feature... features) {
            for (AvroParser.Feature f : features) {
                _streamFactory.enable(f);
            }
            return this;
        }

        public Builder disable(AvroParser.Feature... features) {
            for (AvroParser.Feature f : features) {
                _streamFactory.disable(f);
            }
            return this;
        }

        public Builder configure(AvroParser.Feature f, boolean state)
        {
            if (state) {
                _streamFactory.enable(f);
            } else {
                _streamFactory.disable(f);
            }
            return this;
        }

        public Builder enable(AvroGenerator.Feature... features) {
            for (AvroGenerator.Feature f : features) {
                _streamFactory.enable(f);
            }
            return this;
        }

        public Builder disable(AvroGenerator.Feature... features) {
            for (AvroGenerator.Feature f : features) {
                _streamFactory.disable(f);
            }
            return this;
        }

        public Builder configure(AvroGenerator.Feature f, boolean state)
        {
            if (state) {
                _streamFactory.enable(f);
            } else {
                _streamFactory.disable(f);
            }
            return this;
        }
    }

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

    /**
     * @since 2.10
     */
    public static AvroMapper.Builder xmlBuilder() {
        return new AvroMapper.Builder(new AvroMapper());
    }

    /**
     * @since 2.10
     */
    public static AvroMapper.Builder builder() {
        return new AvroMapper.Builder(new AvroMapper());
    }

    public static Builder builder(AvroFactory streamFactory) {
        return new AvroMapper.Builder(new AvroMapper(streamFactory));
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
        try {
            acceptJsonFormatVisitor(type, gen);
            return gen.getGeneratedSchema();
        } catch (RuntimeException e0) {
            throw _invalidSchemaDefinition(constructType(type), e0);
        }
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
        try {
            acceptJsonFormatVisitor(type, gen);
            return gen.getGeneratedSchema();
        } catch (RuntimeException e0) {
            throw _invalidSchemaDefinition(type, e0);
        }
    }

    // @since 2.13
    protected JsonMappingException _invalidSchemaDefinition(JavaType type,
            Exception e0)
    {
        String msg = String.format(
"Failed to generate `AvroSchema` for %s, problem: (%s) %s",
                ClassUtil.getTypeDescription(type),
                e0.getClass().getName(), e0.getMessage()
                );
        return InvalidDefinitionException.from((JsonGenerator) null, msg, type)
            .withCause(e0);
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
