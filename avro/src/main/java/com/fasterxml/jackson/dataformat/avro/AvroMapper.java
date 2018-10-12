package com.fasterxml.jackson.dataformat.avro;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.Version;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.cfg.MapperBuilderState;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;

/**
 * Convenience {@link AvroMapper}, which is mostly similar to simply
 * constructing a mapper with {@link AvroFactory}, but also adds little
 * bit of convenience around {@link AvroSchema} generation.
 */
public class AvroMapper extends ObjectMapper
{
    private static final long serialVersionUID = 3L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * Avro backend.
     *
     * @since 3.0
     */
    public static class Builder extends MapperBuilder<AvroMapper, Builder>
    {
        public Builder(AvroFactory f) {
            super(f);
            addModule(new AvroModule());
        }

        public Builder(StateImpl state) {
            super(state);
            // no need to add module, should come by default
        }
        
        @Override
        public AvroMapper build() {
            return new AvroMapper(this);
        }

        @Override
        protected MapperBuilderState _saveState() {
            // nothing extra, just format features
            return new StateImpl(this);
        }
        
        /*
        /******************************************************************
        /* Format features
        /******************************************************************
         */

        public Builder enable(AvroParser.Feature... features) {
            for (AvroParser.Feature f : features) {
                _formatReadFeatures |= f.getMask();
            }
            return this;
        }

        public Builder disable(AvroParser.Feature... features) {
            for (AvroParser.Feature f : features) {
                _formatReadFeatures &= ~f.getMask();
            }
            return this;
        }

        public Builder configure(AvroParser.Feature feature, boolean state)
        {
            if (state) {
                _formatReadFeatures |= feature.getMask();
            } else {
                _formatReadFeatures &= ~feature.getMask();
            }
            return this;
        }

        public Builder enable(AvroGenerator.Feature... features) {
            for (AvroGenerator.Feature f : features) {
                _formatWriteFeatures |= f.getMask();
            }
            return this;
        }

        public Builder disable(AvroGenerator.Feature... features) {
            for (AvroGenerator.Feature f : features) {
                _formatWriteFeatures &= ~f.getMask();
            }
            return this;
        }

        public Builder configure(AvroGenerator.Feature feature, boolean state)
        {
            if (state) {
                _formatWriteFeatures |= feature.getMask();
            } else {
                _formatWriteFeatures &= ~feature.getMask();
            }
            return this;
        }

        protected static class StateImpl extends MapperBuilderState
            implements java.io.Serializable // important!
        {
            private static final long serialVersionUID = 3L;
    
            public StateImpl(Builder src) {
                super(src);
            }
    
            // We also need actual instance of state as base class can not implement logic
             // for reinstating mapper (via mapper builder) from state.
            @Override
            protected Object readResolve() {
                return new Builder(this).build();
            }
        }
    }

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */
    
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
        this(new Builder(f));
    }

    public AvroMapper(Builder b) {
        super(b);
    }

    public static Builder builder() {
        return new Builder(new AvroFactory());
    }

    public static Builder builder(AvroFactory streamFactory) {
        return new Builder(streamFactory);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder rebuild() {
        return new AvroMapper.Builder((Builder.StateImpl) _savedBuilderState);
    }

    /*
    /**********************************************************************
    /* Basic accessor overrides
    /**********************************************************************
     */
    
    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public AvroFactory tokenStreamFactory() {
        return (AvroFactory) _streamFactory;
    }

    /*
    /**********************************************************************
    /* Schema introspection
    /**********************************************************************
     */

    /**
     * Factory method for constructing {@link AvroSchema} by introspecting given
     * POJO type and building schema that contains specified properties.
     *<p>
     * Resulting schema object does not use separate reader/writer schemas.
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
     */
    public AvroSchema schemaFrom(File schemaFile) throws IOException
    {
        return new AvroSchema(new Schema.Parser().setValidate(true)
                .parse(schemaFile));
    }
}
