package tools.jackson.dataformat.avro;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;

import tools.jackson.core.*;
import tools.jackson.core.base.GeneratorBase;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.util.JacksonFeatureSet;
import tools.jackson.dataformat.avro.apacheimpl.ApacheCodecRecycler;
import tools.jackson.dataformat.avro.ser.AvroWriteContext;
import tools.jackson.dataformat.avro.ser.EncodedDatum;

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

    /**
     * @since 2.16
     */
    protected final static EncoderFactory ENCODER_FACTORY = EncoderFactory.get();

    /**
     * Bit flag composed of bits that indicate which
     * {@link AvroGenerator.Feature}s
     * are enabled.
     */
    protected int _formatWriteFeatures;

    protected final AvroSchema _rootSchema;

    /*
    /**********************************************************************
    /* Output state
    /**********************************************************************
     */

    protected final OutputStream _output;

    /**
     * Reference to the root context since that is needed for serialization
     */
    protected AvroWriteContext _rootContext;

    /**
     * Current context
     */
    protected AvroWriteContext _streamWriteContext;

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

    public AvroGenerator(ObjectWriteContext writeCtxt, IOContext ioCtxt,
            int streamWriteFeatures, int avroFeatures,
            OutputStream output,
            AvroSchema schema)
        throws JacksonException
    {
        super(writeCtxt, ioCtxt, streamWriteFeatures);
        _formatWriteFeatures = avroFeatures;
        _output = output;
        _streamWriteContext = AvroWriteContext.nullContext();
        final boolean buffering = isEnabled(Feature.AVRO_BUFFERING);
        BinaryEncoder encoderToReuse = ApacheCodecRecycler.acquireEncoder();
        _encoder = buffering
                ? ENCODER_FACTORY.binaryEncoder(output, encoderToReuse)
                : ENCODER_FACTORY.directBinaryEncoder(output, encoderToReuse);
        _rootSchema = Objects.requireNonNull(schema, "Can not pass `null` 'schema'");
        // start with temporary root...
        _streamWriteContext = _rootContext = AvroWriteContext.createRootContext(this,
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
    public TokenStreamContext streamWriteContext() { return _streamWriteContext; }

    @Override
    public Object currentValue() {
        return _streamWriteContext.currentValue();
    }

    @Override
    public void assignCurrentValue(Object v) {
        _streamWriteContext.assignCurrentValue(v);
    }

    /*
    /**********************************************************************
    /* Overridden methods, configuration
    /**********************************************************************
     */

    @Override
    public Object streamWriteOutputTarget() {
        return _output;
    }

    /**
     * Unfortunately we have no visibility into buffering Avro codec does;
     * and need to return <code>-1</code> to reflect that lack of knowledge.
     */
    @Override
    public int streamWriteOutputBuffered() {
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

    @Override // @since 2.12
    public JacksonFeatureSet<StreamWriteCapability> streamWriteCapabilities() {
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
    public JsonGenerator writeName(String name) throws JacksonException
    {
        try {
            _streamWriteContext.writeName(name);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeName(SerializableString name)
        throws JacksonException
    {
        try {
            _streamWriteContext.writeName(name.getValue());
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writePropertyId(long id) throws JacksonException {
        try {
            // TODO: Should not force construction of a String here...
            String idStr = Long.valueOf(id).toString(); // since instances for small values cached
            _streamWriteContext.writeName(idStr);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
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
    protected void _closeInput() throws IOException
    {
        if (isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT)) {
            AvroWriteContext ctxt;
            while ((ctxt = _streamWriteContext) != null) {
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
                throw _constructWriteException(
"Failed to close AvroGenerator: ("+e.getClass().getName()+"): "+e.getMessage(),
                        e);
            }
        }
        if (_output != null) {
            if (_ioContext.isResourceManaged() || isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET)) {
                _output.close();
            } else  if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                // If we can't close it, we should at least flush
                _output.flush();
            }
        }
    }

    /*
    /**********************************************************************
    /* Public API: structural output
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeStartArray() throws JacksonException {
        _streamWriteContext = _streamWriteContext.createChildArrayContext(null);
        _complete = false;
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(Object currValue) throws JacksonException {
        _streamWriteContext = _streamWriteContext.createChildArrayContext(currValue);
        _complete = false;
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(Object currValue, int len) throws JacksonException {
        _streamWriteContext = _streamWriteContext.createChildArrayContext(currValue);
        _complete = false;
        return this;
    }
    
    @Override
    public JsonGenerator writeEndArray() throws JacksonException
    {
        if (!_streamWriteContext.inArray()) {
            _reportError("Current context not Array but "+_streamWriteContext.typeDesc());
        }
        _streamWriteContext = _streamWriteContext.getParent();
        if (_streamWriteContext.inRoot() && !_complete) {
            _complete();
        }
        return this;
    }

    @Override
    public JsonGenerator writeStartObject() throws JacksonException {
        _streamWriteContext = _streamWriteContext.createChildObjectContext(null);
        _complete = false;
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(Object forValue) throws JacksonException {
        _streamWriteContext = _streamWriteContext.createChildObjectContext(forValue);
        _complete = false;
        return this;
    }

    @Override
    public JsonGenerator writeEndObject() throws JacksonException
    {
        if (!_streamWriteContext.inObject()) {
            _reportError("Current context not Object but "+_streamWriteContext.typeDesc());
        }
        if (!_streamWriteContext.canClose()) {
            _reportError("Can not write END_OBJECT after writing FIELD_NAME but not value");
        }
        _streamWriteContext = _streamWriteContext.getParent();

        if (_streamWriteContext.inRoot() && !_complete) {
            _complete();
        }
        return this;
    }

    /*
    /**********************************************************************
    /* Output method implementations, textual
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeString(String text) throws JacksonException
    {
        if (text == null) {
            return writeNull();
        }
        try {
            _streamWriteContext.writeString(text);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeString(char[] text, int offset, int len) throws JacksonException {
        return writeString(new String(text, offset, len));
    }

    @Override
    public JsonGenerator writeString(SerializableString sstr) throws JacksonException {
        return writeString(sstr.toString());
    }

    @Override
    public JsonGenerator writeRawUTF8String(byte[] text, int offset, int len) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeUTF8String(byte[] text, int offset, int len) throws JacksonException {
        return writeString(new String(text, offset, len,  StandardCharsets.UTF_8));
    }

    /*
    /**********************************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeEmbeddedObject(Object object) throws JacksonException {
        if (object instanceof EncodedDatum) {
            try {
                _streamWriteContext.writeValue(object);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            return this;
        }
        return super.writeEmbeddedObject(object);
    }

    @Override
    public JsonGenerator writeRaw(String text) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRaw(String text, int offset, int len) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRaw(char[] text, int offset, int len) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRaw(char c) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRawValue(String text) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRawValue(String text, int offset, int len) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRawValue(char[] text, int offset, int len) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    /*
    /**********************************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************************
     */
    
    @Override
    public JsonGenerator writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws JacksonException
    {
        if (data == null) {
            return writeNull();
        }
        try {
            _streamWriteContext.writeBinary(data, offset, len);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    /*
    /**********************************************************************
    /* Output method implementations, primitive
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeBoolean(boolean state) throws JacksonException {
        try {
            _streamWriteContext.writeValue(state ? Boolean.TRUE : Boolean.FALSE);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNull() throws JacksonException {
        try {
            _streamWriteContext.writeNull();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(short v) throws JacksonException {
        try {
            _streamWriteContext.writeValue(Short.valueOf(v));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(int v) throws JacksonException {
        try {
            _streamWriteContext.writeValue(Integer.valueOf(v));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(long v) throws JacksonException {
        try {
            _streamWriteContext.writeValue(Long.valueOf(v));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(BigInteger v) throws JacksonException
    {
        if (v == null) {
            return writeNull();
        }
        try {
            _streamWriteContext.writeValue(v);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }
    
    @Override
    public JsonGenerator writeNumber(double d) throws JacksonException {
        try {
            _streamWriteContext.writeValue(Double.valueOf(d));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }    

    @Override
    public JsonGenerator writeNumber(float f) throws JacksonException {
        try {
            _streamWriteContext.writeValue(Float.valueOf(f));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(BigDecimal dec) throws JacksonException
    {
        try {
            if (dec == null) {
                return writeNull();
            }
            _streamWriteContext.writeValue(dec);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(String encodedValue) throws JacksonException {
        /* 08-Mar-2016, tatu: Looks like this may need to be supported, eventually,
         *   for things like floating-point (Decimal) types. But, for now,
         *   let's at least handle null.
         */
        if (encodedValue == null) {
            return writeNull();
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
