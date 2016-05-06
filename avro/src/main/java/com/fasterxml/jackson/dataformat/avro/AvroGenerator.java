package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.avro.ser.AvroWriteContext;

import org.apache.avro.io.BinaryEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public class AvroGenerator extends GeneratorBase
{
    /**
     * Enumeration that defines all togglable features for Avro generators
     */
    public enum Feature
        implements FormatFeature // since 2.7
    {
        // !!! TODO: remove from 2.8
        /**
         * Feature that can be enabled to quietly ignore serialization of properties
         * that can not be mapped to output schema: if enabled, trying to output
         * properties that do not map result in such output calls being discarded;
         * if disabled, an exception is thrown.
         *<p>
         * Feature is disabled by default.
         * 
         * @since 2.4
         * 
         * @deprecated Since 2.5 replaced by {@link com.fasterxml.jackson.core.JsonGenerator.Feature#IGNORE_UNKNOWN}
         *   which should be used instead
         */
        @Deprecated
        IGNORE_UNKWNOWN(false),

        /**
         * Feature that can be disabled to prevent Avro from buffering any more
         * data then absolutely necessary.
         * This affects buffering by underlying codec.
         *<p>
         * Enabled by default to preserve the existing behavior.
         *
         * @since 2.7
         */
        AVRO_BUFFERING(true)
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
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    final protected IOContext _ioContext;

    /**
     * Bit flag composed of bits that indicate which
     * {@link AvroGenerator.Feature}s
     * are enabled.
     */
    protected int _formatFeatures;

    protected AvroSchema _rootSchema;
    
    /*
    /**********************************************************
    /* Output state
    /**********************************************************
     */

    final protected OutputStream _output;
    
    /**
     * Reference to the root context since that is needed for serialization
     */
    protected AvroWriteContext _rootContext;

    /**
     * Current context
     */
    protected AvroWriteContext _avroContext;

    /**
     * Flag that is set when the whole content is complete, can
     * be output.
     */
    protected boolean _complete;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public AvroGenerator(IOContext ctxt, int jsonFeatures, int avroFeatures,
            ObjectCodec codec, OutputStream output)
        throws IOException
    {
        super(jsonFeatures, codec);
        _ioContext = ctxt;
        _formatFeatures = avroFeatures;
        _output = output;
        _avroContext = AvroWriteContext.createNullContext();
    }

    public void setSchema(AvroSchema schema)
    {
        if (_rootSchema == schema) {
            return;
        }
        _rootSchema = schema;
        // start with temporary root...
        _avroContext = _rootContext = AvroWriteContext.createRootContext(this, schema.getAvroSchema());
    }

    /*                                                                                       
    /**********************************************************                              
    /* Versioned                                                                             
    /**********************************************************                              
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************
    /* Overridden methods, configuration
    /**********************************************************
     */

    /**
     * Not sure what to do here; could reset indentation to some value maybe?
     */
    @Override
    public AvroGenerator useDefaultPrettyPrinter()
    {
        return this;
    }

    /**
     * Not relevant, as binary formats typically have no indentation.
     */
    @Override
    public AvroGenerator setPrettyPrinter(PrettyPrinter pp) {
        return this;
    }

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

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return (schema instanceof AvroSchema);
    }

    @Override public AvroSchema getSchema() {
        return _rootSchema;
    }

    @Override
    public void setSchema(FormatSchema schema)
    {
        if (!(schema instanceof AvroSchema)) {
            throw new IllegalArgumentException("Can not use FormatSchema of type "
                    +schema.getClass().getName());
        }
        setSchema((AvroSchema) schema);
    }

    /*
    /**********************************************************
    /* Extended API, configuration
    /**********************************************************
     */

    public AvroGenerator enable(Feature f) {
        _formatFeatures |= f.getMask();
        return this;
    }

    public AvroGenerator disable(Feature f) {
        _formatFeatures &= ~f.getMask();
        return this;
    }

    public final boolean isEnabled(Feature f) {
        return (_formatFeatures & f.getMask()) != 0;
    }

    public AvroGenerator configure(Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    @Override
    public JsonGenerator overrideFormatFeatures(int values, int mask) {
        int oldF = _formatFeatures;
        int newF = (_formatFeatures & ~mask) | (values & mask);

        if (oldF != newF) {
            _formatFeatures = newF;
            // 22-Oct-2015, tatu: Actually, not way to change buffering details at
            //   this point. If change needs to be dynamic have to change it
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
    public final void writeFieldName(String name) throws IOException
    {
        _avroContext.writeFieldName(name);
    }

    @Override
    public final void writeFieldName(SerializableString name)
        throws IOException
    {
        _avroContext.writeFieldName(name.getValue());
    }

    @Override
    public final void writeStringField(String fieldName, String value)
        throws IOException
    {
        _avroContext.writeFieldName(fieldName);
        writeString(value);
    }

    /*
    /**********************************************************
    /* Public API: low-level I/O
    /**********************************************************
     */

    @Override
    public final void flush() throws IOException {
        _output.flush();
    }
    
    @Override
    public void close() throws IOException
    {
        super.close();
        if (isEnabled(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT)) {
            AvroWriteContext ctxt;
            while ((ctxt = _avroContext) != null) {
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
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new JsonGenerationException("Failed to close AvroGenerator: ("
                        +e.getClass().getName()+"): "+e.getMessage(), e);
            }
        }
        if (_output != null) {
            if (_ioContext.isResourceManaged() || isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET)) {
                _output.close();
            } else  if (isEnabled(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM)) {
                // If we can't close it, we should at least flush
                _output.flush();
            }
        }
        // Internal buffer(s) generator has can now be released as well
        _releaseBuffers();
    }

    /*
    /**********************************************************
    /* Public API: structural output
    /**********************************************************
     */
    
    @Override
    public final void writeStartArray() throws IOException {
        _avroContext = _avroContext.createChildArrayContext();
    }
    
    @Override
    public final void writeEndArray() throws IOException
    {
        if (!_avroContext.inArray()) {
            _reportError("Current context not an ARRAY but "+_avroContext.getTypeDesc());
        }
        _avroContext = _avroContext.getParent();
        if (_avroContext.inRoot() && !_complete) {
            _complete();
        }
    }

    @Override
    public final void writeStartObject() throws IOException {
        _avroContext = _avroContext.createChildObjectContext();
    }

    @Override
    public final void writeEndObject() throws IOException
    {
        if (!_avroContext.inObject()) {
            _reportError("Current context not an object but "+_avroContext.getTypeDesc());
        }
        if (!_avroContext.canClose()) {
            _reportError("Can not write END_OBJECT after writing FIELD_NAME but not value");
        }
        _avroContext = _avroContext.getParent();

        if (_avroContext.inRoot() && !_complete) {
            _complete();
        }
    }

    /*
    /**********************************************************
    /* Output method implementations, textual
    /**********************************************************
     */

    @Override
    public void writeString(String text) throws IOException
    {
        if (text == null) {
            writeNull();
            return;
        }
        _avroContext.writeString(text);
    }

    @Override
    public void writeString(char[] text, int offset, int len) throws IOException {
        writeString(new String(text, offset, len));
    }

    @Override
    public final void writeString(SerializableString sstr) throws IOException {
        writeString(sstr.toString());
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public final void writeUTF8String(byte[] text, int offset, int len) throws IOException {
        writeString(new String(text, offset, len, "UTF-8"));
    }

    /*
    /**********************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************
     */

    @Override
    public void writeRaw(String text) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char c) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    /*
    /**********************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************
     */
    
    @Override
    public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws IOException
    {
        if (data == null) {
            writeNull();
            return;
        }
        _avroContext.writeValue(ByteBuffer.wrap(data, offset, len));
    }

    /*
    /**********************************************************
    /* Output method implementations, primitive
    /**********************************************************
     */

    @Override
    public void writeBoolean(boolean state) throws IOException {
        _avroContext.writeValue(state ? Boolean.TRUE : Boolean.FALSE);
    }

    @Override
    public void writeNull() throws IOException {
        _avroContext.writeValue(null);
    }

    @Override
    public void writeNumber(int i) throws IOException {
        _avroContext.writeValue(Integer.valueOf(i));
    }

    @Override
    public void writeNumber(long l) throws IOException {
        _avroContext.writeValue(Long.valueOf(l));
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException
    {
        if (v == null) {
            writeNull();
            return;
        }
        _avroContext.writeValue(v);
    }
    
    @Override
    public void writeNumber(double d) throws IOException {
        _avroContext.writeValue(Double.valueOf(d));
    }    

    @Override
    public void writeNumber(float f) throws IOException {
        _avroContext.writeValue(Float.valueOf(f));
    }

    @Override
    public void writeNumber(BigDecimal dec) throws IOException
    {
        if (dec == null) {
            writeNull();
            return;
        }
        _avroContext.writeValue(dec);
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException {
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
    /**********************************************************
    /* Implementations for methods from base class
    /**********************************************************
     */
    
    @Override
    protected final void _verifyValueWrite(String typeMsg) throws IOException {
        _throwInternal();
    }

    @Override
    protected void _releaseBuffers() {
        // nothing special to do...
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    protected void _complete() throws IOException
    {
        _complete = true;
        
        // add defensive coding here but only because this often gets triggered due
        // to forced closure resulting from another exception; so, we typically
        // do not want to hide the original problem...
    	// First one sanity check, for a (relatively?) common case
        if (_rootContext == null) {
        	return;
        }
        BinaryEncoder encoder = AvroSchema.encoder(
            _output, isEnabled(Feature.AVRO_BUFFERING)
        );
        _rootContext.complete(encoder);
        encoder.flush();
    }
}
