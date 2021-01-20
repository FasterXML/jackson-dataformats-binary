package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.avro.io.BinaryEncoder;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheCodecRecycler;
import com.fasterxml.jackson.dataformat.avro.ser.AvroWriteContext;
import com.fasterxml.jackson.dataformat.avro.ser.EncodedDatum;

public class AvroGenerator extends GeneratorBase
{
    /**
     * Enumeration that defines all togglable features for Avro generators
     */
    public enum Feature
        implements FormatFeature
    {
        /**
         * Feature that can be disabled to prevent Avro from buffering any more
         * data then absolutely necessary.
         * This affects buffering by underlying codec.
         * Note that disabling buffer is likely to reduce performance if the underlying
         * input/output is unbuffered.
         *<p>
         * Enabled by default to preserve the existing behavior.
         */
        AVRO_BUFFERING(true),

        /**
         * Feature that tells Avro to write data in file format (i.e. including the schema with the data)
         * rather than the RPC format which is otherwise default
         *<p>
         * NOTE: reader-side will have to be aware of distinction as well, since possible inclusion
         * of this header is not 100% reliably auto-detectable (while header has distinct marker,
         * "raw" Avro content has no limitations and could theoretically have same pre-amble from data).
         */
        AVRO_FILE_OUTPUT(false),

        /**
         * Feature that enables addition of {@code null} as default value in generated schema
         * when no real default value is defined and {@code null} is legal value for type
         * (union type with {@code null} included).
         *<p>
         * Disabled by default.
         *
         * @since 3.0
         * 
         */
        ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA(false)
        ;

        protected final boolean _defaultState;
        protected final int _mask;
        
        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }
        
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }

        @Override
        public boolean enabledByDefault() { return _defaultState; }

        @Override
        public int getMask() { return _mask; }

        @Override
        public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
    }
    
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    final protected IOContext _ioContext;

    /**
     * Bit flag composed of bits that indicate which
     * {@link AvroGenerator.Feature}s
     * are enabled.
     */
    protected int _formatWriteFeatures;

    final protected AvroSchema _rootSchema;

    /*
    /**********************************************************************
    /* Output state
    /**********************************************************************
     */

    final protected OutputStream _output;

    /**
     * Reference to the root context since that is needed for serialization
     */
    protected AvroWriteContext _rootContext;

    /**
     * Current context
     */
    protected AvroWriteContext _tokenWriteContext;

    /**
     * Lazily constructed encoder; reused in case of writing root-value sequences.
     */
    protected BinaryEncoder _encoder;

    /**
     * Flag that is set when the whole content is complete, can
     * be output.
     */
    protected boolean _complete;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public AvroGenerator(ObjectWriteContext writeCtxt, IOContext ctxt,
            int jsonFeatures, int avroFeatures,
            OutputStream output,
            AvroSchema schema)
        throws JacksonException
    {
        super(writeCtxt, jsonFeatures);
        _ioContext = ctxt;
        _formatWriteFeatures = avroFeatures;
        _output = output;
        _tokenWriteContext = AvroWriteContext.nullContext();
        _encoder = ApacheCodecRecycler.encoder(_output, isEnabled(Feature.AVRO_BUFFERING));
        _rootSchema = Objects.requireNonNull(schema, "Can not pass `null` 'schema'");
        // start with temporary root...
        _tokenWriteContext = _rootContext = AvroWriteContext.createRootContext(this,
                schema.getAvroSchema(), _encoder);
    }

    /*                                                                                       
    /**********************************************************************
    /* Versioned                                                                             
    /**********************************************************************
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* Output state handling
    /**********************************************************************
     */
    
    @Override
    public TokenStreamContext getOutputContext() { return _tokenWriteContext; }

    @Override
    public Object getCurrentValue() {
        return _tokenWriteContext.getCurrentValue();
    }

    @Override
    public void setCurrentValue(Object v) {
        _tokenWriteContext.setCurrentValue(v);
    }

    /*
    /**********************************************************************
    /* Overridden methods, configuration
    /**********************************************************************
     */

    @Override
    public Object getOutputTarget() {
        return _output;
    }

    /**
     * Unfortunately we have no visibility into buffering Avro codec does;
     * and need to return <code>-1</code> to reflect that lack of knowledge.
     */
    @Override
    public int getOutputBuffered() {
        return -1;
    }

    @Override public AvroSchema getSchema() {
        return _rootSchema;
    }

    /*
    /**********************************************************************
    /* Public API, capability introspection methods
    /**********************************************************************
     */

    // 26-Nov-2019, tatu: [dataformats-binary#179] needed this; could
    //   only add in 2.11
    @Override // since 2.11
    public boolean canWriteBinaryNatively() { return true; }

    @Override // @since 2.12
    public JacksonFeatureSet<StreamWriteCapability> getWriteCapabilities() {
        return DEFAULT_BINARY_WRITE_CAPABILITIES;
    }

    /*
    /**********************************************************************
    /* Extended API, configuration
    /**********************************************************************
     */

    public AvroGenerator enable(Feature f) {
        _formatWriteFeatures |= f.getMask();
        return this;
    }

    public AvroGenerator disable(Feature f) {
        _formatWriteFeatures &= ~f.getMask();
        return this;
    }

    public final boolean isEnabled(Feature f) {
        return (_formatWriteFeatures & f.getMask()) != 0;
    }

    public AvroGenerator configure(Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /*
    /**********************************************************************
    /* Overridden methods; writing field names
    /**********************************************************************
     */
    
    /* And then methods overridden to make final, streamline some
     * aspects...
     */

    @Override
    public final void writeFieldName(String name) throws JacksonException
    {
        try {
            _tokenWriteContext.writeFieldName(name);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public final void writeFieldName(SerializableString name)
        throws JacksonException
    {
        try {
            _tokenWriteContext.writeFieldName(name.getValue());
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeFieldId(long id) throws JacksonException {
        try {
            // TODO: Should not force construction of a String here...
            String idStr = Long.valueOf(id).toString(); // since instances for small values cached
            _tokenWriteContext.writeFieldName(idStr);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    /*
    /**********************************************************************
    /* Public API: low-level I/O
    /**********************************************************************
     */

    @Override
    public final void flush() {
        if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            try {
                _output.flush();
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
    }

    @Override
    public void close()
    {
        super.close();
        if (isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT)) {
            AvroWriteContext ctxt;
            while ((ctxt = _tokenWriteContext) != null) {
                if (ctxt.inArray()) {
                    writeEndArray();
                } else if (ctxt.inObject()) {
                    writeEndObject();
                } else {
                    break;
                }
            }
        }
        // May need to finalize...
        /* 18-Nov-2014, tatu: Since this method is (a) often called as a result of an exception,
         *   and (b) quite likely to cause an exception of its own, need to work around
         *   combination of problems; one part being to catch non-IOExceptions; something that
         *   is usually NOT done. Partly this is because Avro codec is leaking low-level exceptions
         *   such as NPE.
         */
        if (!_complete) {
            try {
                _complete();
            } catch (Exception e) {
                throw new JsonGenerationException("Failed to close AvroGenerator: ("
                        +e.getClass().getName()+"): "+e.getMessage(), e, this);
            }
        }
        if (_output != null) {
            try {
                if (_ioContext.isResourceManaged() || isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET)) {
                    _output.close();
                } else  if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                    // If we can't close it, we should at least flush
                    _output.flush();
                }
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
        // Internal buffer(s) generator has can now be released as well
        _releaseBuffers();
    }

    /*
    /**********************************************************************
    /* Public API: structural output
    /**********************************************************************
     */

    @Override
    public final void writeStartArray() throws JacksonException {
        _tokenWriteContext = _tokenWriteContext.createChildArrayContext(null);
        _complete = false;
    }

    @Override
    public final void writeStartArray(Object currValue) throws JacksonException {
        _tokenWriteContext = _tokenWriteContext.createChildArrayContext(currValue);
        _complete = false;
    }

    @Override
    public final void writeStartArray(Object currValue, int len) throws JacksonException {
        _tokenWriteContext = _tokenWriteContext.createChildArrayContext(currValue);
        _complete = false;
    }
    
    @Override
    public final void writeEndArray() throws JacksonException
    {
        if (!_tokenWriteContext.inArray()) {
            _reportError("Current context not Array but "+_tokenWriteContext.typeDesc());
        }
        _tokenWriteContext = _tokenWriteContext.getParent();
        if (_tokenWriteContext.inRoot() && !_complete) {
            _complete();
        }
    }

    @Override
    public final void writeStartObject() throws JacksonException {
        _tokenWriteContext = _tokenWriteContext.createChildObjectContext(null);
        _complete = false;
    }

    @Override
    public void writeStartObject(Object forValue) throws JacksonException {
        _tokenWriteContext = _tokenWriteContext.createChildObjectContext(forValue);
        _complete = false;
    }

    @Override
    public final void writeEndObject() throws JacksonException
    {
        if (!_tokenWriteContext.inObject()) {
            _reportError("Current context not Object but "+_tokenWriteContext.typeDesc());
        }
        if (!_tokenWriteContext.canClose()) {
            _reportError("Can not write END_OBJECT after writing FIELD_NAME but not value");
        }
        _tokenWriteContext = _tokenWriteContext.getParent();

        if (_tokenWriteContext.inRoot() && !_complete) {
            _complete();
        }
    }

    /*
    /**********************************************************************
    /* Output method implementations, textual
    /**********************************************************************
     */

    @Override
    public void writeString(String text) throws JacksonException
    {
        if (text == null) {
            writeNull();
            return;
        }
        try {
            _tokenWriteContext.writeString(text);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeString(char[] text, int offset, int len) throws JacksonException {
        writeString(new String(text, offset, len));
    }

    @Override
    public final void writeString(SerializableString sstr) throws JacksonException {
        writeString(sstr.toString());
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public final void writeUTF8String(byte[] text, int offset, int len) throws JacksonException {
        writeString(new String(text, offset, len,  StandardCharsets.UTF_8));
    }

    /*
    /**********************************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************************
     */

    @Override
    public void writeEmbeddedObject(Object object) throws JacksonException {
        if (object instanceof EncodedDatum) {
            try {
                _tokenWriteContext.writeValue(object);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            return;
        }
        super.writeEmbeddedObject(object);
    }

    @Override
    public void writeRaw(String text) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char c) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    /*
    /**********************************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************************
     */
    
    @Override
    public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws JacksonException
    {
        if (data == null) {
            writeNull();
            return;
        }
        try {
            _tokenWriteContext.writeBinary(data, offset, len);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    /*
    /**********************************************************************
    /* Output method implementations, primitive
    /**********************************************************************
     */

    @Override
    public void writeBoolean(boolean state) throws JacksonException {
        try {
            _tokenWriteContext.writeValue(state ? Boolean.TRUE : Boolean.FALSE);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNull() throws JacksonException {
        try {
            _tokenWriteContext.writeNull();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(short v) throws JacksonException {
        try {
            _tokenWriteContext.writeValue(Short.valueOf(v));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(int v) throws JacksonException {
        try {
            _tokenWriteContext.writeValue(Integer.valueOf(v));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(long v) throws JacksonException {
        try {
            _tokenWriteContext.writeValue(Long.valueOf(v));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(BigInteger v) throws JacksonException
    {
        if (v == null) {
            writeNull();
            return;
        }
        try {
            _tokenWriteContext.writeValue(v);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }
    
    @Override
    public void writeNumber(double d) throws JacksonException {
        try {
            _tokenWriteContext.writeValue(Double.valueOf(d));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }    

    @Override
    public void writeNumber(float f) throws JacksonException {
        try {
            _tokenWriteContext.writeValue(Float.valueOf(f));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(BigDecimal dec) throws JacksonException
    {
        try {
            if (dec == null) {
                writeNull();
                return;
            }
            _tokenWriteContext.writeValue(dec);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(String encodedValue) throws JacksonException {
        /* 08-Mar-2016, tatu: Looks like this may need to be supported, eventually,
         *   for things like floating-point (Decimal) types. But, for now,
         *   let's at least handle null.
         */
        if (encodedValue == null) {
            writeNull();
            return;
        }
        throw new UnsupportedOperationException("Can not write 'untyped' numbers");
    }

    /*
    /**********************************************************************
    /* Implementations for methods from base class
    /**********************************************************************
     */
    
    @Override
    protected final void _verifyValueWrite(String typeMsg) throws JacksonException {
        _throwInternal();
    }

    @Override
    protected void _releaseBuffers() {
        // no super implementation to call
        BinaryEncoder e = _encoder;
        if (e != null) {
            _encoder = null;
            ApacheCodecRecycler.release(e);
        }
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    protected void _complete() throws JacksonException
    {
        _complete = true;

        // add defensive coding here but only because this often gets triggered due
        // to forced closure resulting from another exception; so, we typically
        // do not want to hide the original problem...
        // First one sanity check, for a (relatively?) common case
        if (_rootContext != null) {
            try {
                _rootContext.complete();
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
    }
}
