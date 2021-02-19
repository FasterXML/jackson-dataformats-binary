/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.fasterxml.jackson.dataformat.ion;

import java.io.IOException;
import java.util.Date;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.cfg.MapperBuilderState;
import com.fasterxml.jackson.databind.deser.DeserializationContextExt;
import com.fasterxml.jackson.databind.json.JsonMapper.Builder;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.SerializationContextExt;
import com.fasterxml.jackson.dataformat.ion.ionvalue.IonValueModule;

import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;

/**
 * Specialization of {@link ObjectMapper} that will set underlying
 * factory to be an instance of {@link IonFactory}.
 */
public class IonObjectMapper extends ObjectMapper
{    
    private static final long serialVersionUID = 3L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * ION backend.
     *
     * @since 3.0
     */
    public static class Builder extends MapperBuilder<IonObjectMapper, Builder>
    {
        public Builder(IonFactory f) {
            super(f);

            // 04-Jan-2017, tatu: demoted from `IonValueMapper`
            addModule(new IonValueModule());
            addModule(new EnumAsIonSymbolModule());

            // !!! 04-Jan-2018, tatu: needs to be reworked in future; may remain a module
            // Use native Ion timestamps for dates
            SimpleModule m = new SimpleModule("IonTimestampModule", PackageVersion.VERSION,
                    "IonTimestampModule");
            m.addSerializer(Date.class, new IonTimestampSerializers.IonTimestampJavaDateSerializer());
            m.addSerializer(java.sql.Date.class, new IonTimestampSerializers.IonTimestampSQLDateSerializer());
            m.addDeserializer(Date.class, new IonTimestampDeserializers.IonTimestampJavaDateDeserializer());
            m.addDeserializer(java.sql.Date.class, new IonTimestampDeserializers.IonTimestampSQLDateDeserializer());
            addModule(m);
        }

        public Builder(StateImpl state) {
            super(state);
            // other stuff should have been preserved along with state
        }

        @Override
        public IonObjectMapper build() {
            return new IonObjectMapper(this);
        }

        @Override
        protected MapperBuilderState _saveState() {
            return new StateImpl(this);
        }

        /*
        /******************************************************************
        /* Format features
        /******************************************************************
         */

        public Builder enable(IonParser.Feature... features) {
            for (IonParser.Feature f : features) {
                _formatReadFeatures |= f.getMask();
            }
            return this;
        }

        public Builder disable(IonParser.Feature... features) {
            for (IonParser.Feature f : features) {
                _formatReadFeatures &= ~f.getMask();
            }
            return this;
        }

        public Builder configure(IonParser.Feature feature, boolean state)
        {
            if (state) {
                _formatReadFeatures |= feature.getMask();
            } else {
                _formatReadFeatures &= ~feature.getMask();
            }
            return this;
        }

        public Builder enable(IonGenerator.Feature... features) {
            for (IonGenerator.Feature f : features) {
                _formatWriteFeatures |= f.getMask();
            }
            return this;
        }

        public Builder disable(IonGenerator.Feature... features) {
            for (IonGenerator.Feature f : features) {
                _formatWriteFeatures &= ~f.getMask();
            }
            return this;
        }

        public Builder configure(IonGenerator.Feature feature, boolean state)
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

    public IonObjectMapper() {
        this(new IonFactory());
    }

    public IonObjectMapper(IonFactory f) {
        this(new Builder(f));
    }

    public IonObjectMapper(Builder b) {
        super(b);
    }

    /**
     * A builder for a mapper that will use textual writers by default. Same as
     * {@link #builderForTextualWriters()}.
     */
    public static Builder builder() {
        return builderForTextualWriters();
    }

    /**
     * A builder for a mapper that will use specified {@code streamFactory},
     * pre-configured.
     */
    public static Builder builder(IonFactory streamFactory) {
        return new Builder(streamFactory);
    }

    /**
     * A builder for a mapper that will use textual writers by default and the
     * provided {@link IonSystem}. Same as {@link #builderForTextualWriters(IonSystem)}.
     */
    public static Builder builder(IonSystem ionSystem) {
        return builderForTextualWriters(ionSystem);
    }

    /**
     * A builder for a mapper that will use binary writers by default.
     */
    public static Builder builderForBinaryWriters() {
        return builderForBinaryWriters(IonSystemBuilder.standard().build());
    }

    /**
     * A builder for a mapper that will use binary writers by default and the
     * provided {@link IonSystem}
     */
    public static Builder builderForBinaryWriters(IonSystem ionSystem) {
        return builder(IonFactory.builderForBinaryWriters()
                .ionSystem(ionSystem)
                .build());
    }

    /**
     * A builder for a mapper that will use textual writers by default.
     */
    public static Builder builderForTextualWriters() {
        return builderForTextualWriters(IonSystemBuilder.standard().build());
    }

    /**
     * A builder for a mapper that will use textual writers by default and the
     * provided {@link IonSystem}.
     */
    public static Builder builderForTextualWriters(IonSystem ionSystem) {
        return builder(IonFactory.builderForTextualWriters()
                .ionSystem(ionSystem)
                .build());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder rebuild() {
        return new Builder((Builder.StateImpl) _savedBuilderState);
    }

    /*
    /**********************************************************************
    /* Life-cycle, shared "vanilla" (default configuration) instance
    /**********************************************************************
     */

    /**
     * Accessor method for getting globally shared "default" {@link IonObjectMapper}
     * instance: one that has default configuration, no modules registered, no
     * config overrides. Usable mostly when dealing "untyped" or Tree-style
     * content reading and writing.
     */
    public static IonObjectMapper shared() {
        return SharedWrapper.wrapped();
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
    public IonFactory tokenStreamFactory() {
        return (IonFactory) _streamFactory;
    }

    /*
    /************************************************************************
    /* Additional convenience factory methods for parsers, generators
    /************************************************************************
     */

    /**
     * @since 3.0
     */
    public IonParser createParser(IonReader src) {
        DeserializationContextExt ctxt = _deserializationContext();
        return (IonParser) ctxt.assignAndReturnParser(tokenStreamFactory().createParser(ctxt, src));
    }

    /**
     * @since 3.0
     */
    public IonParser createParser(IonValue value) {
        DeserializationContextExt ctxt = _deserializationContext();
        return (IonParser) ctxt.assignAndReturnParser(tokenStreamFactory().createParser(ctxt, value));
    }

    /**
     * @since 3.0
     */
    public IonGenerator createGenerator(IonWriter out) {
        return (IonGenerator) tokenStreamFactory().createGenerator(_serializerProvider(), out);
    }

    /*
    /************************************************************************
    /* Convenience read/write methods for IonReader, IonWriter, and IonValue,
    /* by analogy with the existing convenience methods of ObjectMapper
    /************************************************************************
     */

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonReader r, Class<T> valueType) throws IOException {
        DeserializationContextExt ctxt = _deserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, r),
                _typeFactory.constructType(valueType));
    }

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T readValue(IonReader r, TypeReference valueTypeRef) throws IOException {
        DeserializationContextExt ctxt = _deserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, r),
                _typeFactory.constructType(valueTypeRef));
    }

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonReader r, JavaType valueType) throws IOException {
        DeserializationContextExt ctxt = _deserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, r), valueType);
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonValue value, Class<T> valueType) throws IOException {
        if (value == null) {
            return null;
        }
        DeserializationContextExt ctxt = _deserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, value),
                _typeFactory.constructType(valueType));
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T readValue(IonValue value, TypeReference valueTypeRef) throws IOException {
        if (value == null) {
            return null;
        }
        DeserializationContextExt ctxt = _deserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, value),
                _typeFactory.constructType(valueTypeRef));
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonValue value, JavaType valueType) throws IOException  {
        if (value == null) {
            return null;
        }
        DeserializationContextExt ctxt = _deserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, value), valueType);
    }

    /**
     * Method that can be used to serialize any Java value as
     * Ion output, using IonWriter provided.
     *<p>
     * Note: method does not close the underlying writer explicitly
     */
    public void writeValue(IonWriter w, Object value) throws IOException {
        SerializationContextExt prov = _serializerProvider();
        _configAndWriteValue(prov,
                tokenStreamFactory().createGenerator(prov, w), value);
    }

    /**
     * Method that can be used to map any Java value to an IonValue.
     */
    public IonValue writeValueAsIonValue(Object value) throws IOException
    {
        // 04-Jan-2017, tatu: Bit of incompatiblity wrt 2.x handling: should this result in
        //   Java `null`, or Ion null marker? For now, choose latter
/*        
        if (value == null) {
            return null;
        }
        */

        IonFactory f = tokenStreamFactory();
        IonDatagram container = f._system.newDatagram();
        try (IonWriter writer = f._system.newWriter(container)) {
            writeValue(writer, value);
            IonValue result = container.get(0);
            result.removeFromContainer();
            return result;
        }
    }

    /*
    /**********************************************************
    /* Helper class(es)
    /**********************************************************
     */

    /**
     * Helper class to contain dynamically constructed "shared" instance of
     * mapper, should one be needed via {@link #shared}.
     */
    private final static class SharedWrapper {
        private final static IonObjectMapper MAPPER = IonObjectMapper.builder().build();

        public static IonObjectMapper wrapped() { return MAPPER; }
    }
}
