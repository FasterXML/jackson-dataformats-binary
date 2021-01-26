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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.fasterxml.jackson.core.util.SimpleStreamWriteContext;

import com.amazon.ion.IonType;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.Timestamp;

/**
 * Implementation of {@link JsonGenerator} that will use an underlying
 * {@link IonWriter} for actual writing of content.
 */
public class IonGenerator
    extends GeneratorBase
{
    /**
     * Enumeration that defines all toggleable features for Ion generators
     */
    public enum Feature implements FormatFeature // since 2.12
    {
        /**
         * Whether to use Ion native Type Id construct for indicating type (true);
         * or "generic" type property (false) when writing. Former works better for
         * systems that are Ion-centric; latter may be better choice for interoperability,
         * when converting between formats or accepting other formats.
         *<p>
         * Enabled by default for backwards compatibility as that has been the behavior
         * of `jackson-dataformat-ion` since 2.9 (first official public version)
         *
         * @see <a href="https://amzn.github.io/ion-docs/docs/spec.html#annot">The Ion Specification</a>
         *
         * @since 2.12
         */
        USE_NATIVE_TYPE_ID(true),
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
        public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
        @Override
        public int getMask() { return _mask; }
    }

    /*
    /**********************************************************************
    /* Basic configuration
    /**********************************************************************
     */  

    /* This is the textual or binary writer */
    protected final IonWriter _writer;
    /* Indicates whether the IonGenerator is responsible for closing the underlying IonWriter. */
    protected final boolean _ionWriterIsManaged;

    protected final IOContext _ioContext;

    /**
     * Bit flag composed of bits that indicate which
     * {@link IonGenerator.Feature}s
     * are enabled.
     */
    protected int _formatFeatures;

    /**
     * Highest-level output abstraction we can use; either
     * OutputStream or Writer.
     */
    protected final Closeable _destination;

    /**
     * Object that handles pretty-printing (usually additional
     * white space to make results more human-readable) during
     * output. If null, no pretty-printing is done.
     */
    protected PrettyPrinter _cfgPrettyPrinter;

    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */  

    /**
     * Object that keeps track of the current contextual state of the generator.
     */
    protected SimpleStreamWriteContext _streamWriteContext;

    /*
    /**********************************************************************
    /* Instantiation
    /**********************************************************************
     */

    public IonGenerator(ObjectWriteContext writeCtxt, IOContext ioCtxt,
            int streamWriteFeatures, int formatWriteFeatures,
            IonWriter ion, boolean ionWriterIsManaged, Closeable dst)
    {
        super(writeCtxt, streamWriteFeatures);
        _formatFeatures = formatWriteFeatures;
        _writer = ion;
        _ioContext = ioCtxt;
        _ionWriterIsManaged = ionWriterIsManaged;
        _destination = dst;

        final DupDetector dups = StreamWriteFeature.STRICT_DUPLICATE_DETECTION.enabledIn(streamWriteFeatures)
                ? DupDetector.rootDetector(this) : null;
        _streamWriteContext = SimpleStreamWriteContext.createRootContext(dups);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* JsonGenerator implementation: state handling
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
    /* JsonGenerator implementation: low-level I/O
    /**********************************************************************
     */

    @Override
    public int getOutputBuffered() { return -1; }

    @Override
    public Object getOutputTarget() {
        // 25-May-2020, tatu: Tricky one here; should we return `IonWriter` or
        //    actual underlying Writer/OutputStream?
        //    For now, return underlying Writer/OutputStream
        return _writer;
    }
    
    @Override
    public void close()
    {
        if (!_closed) {
            _closed = true;
            try {
                if (_ionWriterIsManaged) {
                    _writer.close();
                }
                if (_ioContext.isResourceManaged()) {
                    _destination.close();
                } else {
                    if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                        if (_destination instanceof Flushable) {
                            ((Flushable) _destination).flush();
                        }
                    }
                }
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
    }

    @Override
    public void flush()
    {
        if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            Object dst = _ioContext.getSourceReference();
            if (dst instanceof Flushable) {
                try {
                    ((Flushable) dst).flush();
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
            }
        }
    }

    @Override
    public boolean isClosed() {
        return _closed;
    }

    /*
    /**********************************************************************
    /* Capability introspection
    /**********************************************************************
     */

    @Override
    public boolean canWriteTypeId() {
        // yes, Ion does support Native Type Ids!
        // 29-Nov-2020, jobarr: Except as per [dataformats-binary#225] might not want to...
        return Feature.USE_NATIVE_TYPE_ID.enabledIn(_formatFeatures);
    }

    @Override
    public boolean canWriteBinaryNatively() { return true; }

    @Override
    public JacksonFeatureSet<StreamWriteCapability> streamWriteCapabilities() {
        return DEFAULT_BINARY_WRITE_CAPABILITIES;
    }

    /*
    /**********************************************************************
    /* JsonGenerator implementation: write numeric values
    /**********************************************************************
     */

    @Override
    public void writeNumber(short v) throws JacksonException {
        writeNumber((int) v);
    }

    @Override
    public void writeNumber(int value) throws JacksonException {
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeInt((long)value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(long value) throws JacksonException {
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeInt(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(BigInteger value) throws JacksonException {
        if (value == null) {
            writeNull();
        } else {
            _verifyValueWrite("write numeric value");
            try {
                _writer.writeInt(value);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
    }

    @Override
    public void writeNumber(double value) throws JacksonException {
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeFloat(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(float value) throws JacksonException {
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeFloat((double) value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNumber(BigDecimal value) throws JacksonException {
        if (value == null) {
            writeNull();
        } else {
            _verifyValueWrite("write numeric value");
            try {
                _writer.writeDecimal(value);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
    }

    @Override
    public void writeNumber(String value) throws JacksonException {
        // will lose its identity... fine
        writeString(value);
    }

    /*
    /**********************************************************************
    /* Ion-specific additional write methods:
    /**********************************************************************
     */

    public void writeSymbol(String value) throws JacksonException {
        _verifyValueWrite("write symbol value");
        try {
            _writer.writeSymbol(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    /**
     * Annotates the next structure or value written -- {@link IonWriter#stepIn(IonType) stepIn()} or one of the
     * {@link IonWriter}s {@code write*()} methods.
     *
     * @param annotation a type annotation
     * 
     * @see com.fasterxml.jackson.dataformat.ion.polymorphism.IonAnnotationTypeSerializer
     */
    public void annotateNextValue(String annotation) {
        // We're not calling _verifyValueWrite because this doesn't actually write anything -- writing happens upon
        // the next _writer.write*() or stepIn().
        _writer.addTypeAnnotation(annotation);
    }

    public void writeDate(Calendar value) throws JacksonException {
        _verifyValueWrite("write date value");
        try {
            _writer.writeTimestamp(Timestamp.forCalendar(value));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    /*
    /*****************************************************************
    /* JsonGenerator implementation: write textual values
    /*****************************************************************
     */

    @Override
    public void writeString(String value) throws JacksonException {
        _verifyValueWrite("write text value");
        try {
            _writer.writeString(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeString(char[] buffer, int offset, int length) throws JacksonException {
        // Ion doesn't have matching optimized method, so:
        writeString(new String(buffer, offset, length));
    }

    @Override
    public void writeUTF8String(byte[] buffer, int offset, int length) throws JacksonException {
        // Ion doesn't have matching optimized method, so:
        writeString(new String(buffer, offset, length, StandardCharsets.UTF_8));
    }

    /*
     *****************************************************************
     * JsonGenerator implementation: write raw JSON; N/A for Ion
     *****************************************************************
     */

    @Override
    public void writeRaw(String value) throws JacksonException {
        _reportNoRaw();
    }

    @Override
    public void writeRaw(char value) throws JacksonException {
        _reportNoRaw();
    }

    @Override
    public void writeRaw(String value, int arg1, int arg2) throws JacksonException {
        _reportNoRaw();
    }

    @Override
    public void writeRaw(char[] value, int arg1, int arg2) throws JacksonException {
        _reportNoRaw();
    }

    @Override
    public void writeRawValue(String value) throws JacksonException {
        _reportNoRaw();
    }

    @Override
    public void writeRawValue(String value, int arg1, int arg2) throws JacksonException {
        _reportNoRaw();
    }

    @Override
    public void writeRawValue(char[] value, int arg1, int arg2) throws JacksonException {
        _reportNoRaw();
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int length) throws JacksonException {
        _reportNoRaw();
    }

    /*
     *****************************************************************
     * JsonGenerator implementation: write other types of values
     *****************************************************************
      */

    @Override
    public void writeBinary(Base64Variant b64v, byte[] data, int offset, int length) throws JacksonException {
        _verifyValueWrite("write binary data");
        // no distinct variants for Ion; should produce plain binary
        try {
            _writer.writeBlob(data, offset, length);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeBoolean(boolean value) throws JacksonException {
        _verifyValueWrite("write boolean");
        try {
            _writer.writeBool(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeNull() throws JacksonException {
        _verifyValueWrite("write null");
        try {
            _writer.writeNull();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    public void writeNull(IonType ionType) throws JacksonException {
        _verifyValueWrite("write null");
        try {
            _writer.writeNull(ionType);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    // 06-Oct-2017, tatu: Base impl from `GeneratorBase` should be sufficient
    /*
    @Override
    public void writeObject(Object pojo) throws JacksonException
    {
        if (pojo == null) {
            // important: call method that does check value write:
            writeNull();
        } else {
            // note: should NOT verify value write is ok; will be done indirectly by codec
            if (_objectCodec == null) {
                throw new IllegalStateException("No ObjectCodec defined for the generator, can not serialize regular Java objects");
            }
            _objectCodec.writeValue(this, pojo);
        }
    }
    */

    public void writeValue(IonValue value) throws JacksonException {
        _verifyValueWrite("write ion value");
        if (value == null) {
            try {
                _writer.writeNull();
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        } else {
            value.writeTo(_writer);
        }
    }

    public void writeValue(Timestamp value) throws JacksonException {
        _verifyValueWrite("write timestamp");
        try {
            if (value == null) {
                _writer.writeNull();
            } else {
                _writer.writeTimestamp(value);
            }
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    /*
    /*****************************************************************
    /* Methods base impl needs
    /*****************************************************************
     */

    @Override
    protected void _releaseBuffers() {
        // nothing to do here...
    }

    @Override
    protected void _verifyValueWrite(String msg) throws JacksonException
    {
        if (!_streamWriteContext.writeValue()) {
            _reportError("Can not "+msg+", expecting a property name");
        }
        // 05-Oct-2017, tatu: I don't think pretty-printing is supported... is it?
        
/*
        // Only additional work needed if we are pretty-printing
        if (_cfgPrettyPrinter != null) {
            // If we have a pretty printer, it knows what to do:
            switch (status) {
            case JsonWriteContext.STATUS_OK_AFTER_COMMA: // array
                _cfgPrettyPrinter.writeArrayValueSeparator(this);
                break;
            case JsonWriteContext.STATUS_OK_AFTER_COLON:
                _cfgPrettyPrinter.writeObjectFieldValueSeparator(this);
                break;
            case JsonWriteContext.STATUS_OK_AFTER_SPACE:
                _cfgPrettyPrinter.writeRootValueSeparator(this);
                break;
            case JsonWriteContext.STATUS_OK_AS_IS:
                // First entry, but of which context?
                if (_outputContext.inArray()) {
                    _cfgPrettyPrinter.beforeArrayValues(this);
                } else if (_outputContext.inObject()) {
                    _cfgPrettyPrinter.beforeObjectEntries(this);
                }
                break;
            default:
                throw new IllegalStateException("Should never occur; status "+status);
            }
        }
        */
    }

    @Override
    public void writeEndArray() throws JacksonException {
        _streamWriteContext = _streamWriteContext.getParent();
        try {
            _writer.stepOut();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeEndObject() throws JacksonException {
        _streamWriteContext = _streamWriteContext.getParent();
        try {
            _writer.stepOut();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeName(String value) throws JacksonException {
        //This call to _outputContext is copied from Jackson's UTF8JsonGenerator.writeFieldName(String)
        if (!_streamWriteContext.writeName(value)) {
            throw _constructWriteException("Can not write a property name, expecting a value");
        }

        _writeFieldName(value);
    }

    @Override
    public void writePropertyId(long id) throws JacksonException {
        // Should not force construction of a String here...
        String idStr = Long.valueOf(id).toString(); // since instances for small values cached
        writeName(idStr);
    }

    protected void _writeFieldName(String value) throws JacksonException {
        //Even though this is a one-liner, putting it into a function "_writeFieldName"
        //to keep this code matching the factoring in Jackson's UTF8JsonGenerator.
        _writer.setFieldName(value);
    }

    @Override
    public void writeStartArray() throws JacksonException {
        _verifyValueWrite("start an array");
        _streamWriteContext = _streamWriteContext.createChildArrayContext(null);
        try {
            _writer.stepIn(IonType.LIST);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeStartArray(Object currValue) throws JacksonException {
        _verifyValueWrite("start an array");
        _streamWriteContext = _streamWriteContext.createChildArrayContext(currValue);
        try {
            _writer.stepIn(IonType.LIST);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeStartObject() throws JacksonException {
        _verifyValueWrite("start an object");
        _streamWriteContext = _streamWriteContext.createChildObjectContext(null);
        try {
            _writer.stepIn(IonType.STRUCT);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void writeStartObject(Object currValue) throws JacksonException {
        _verifyValueWrite("start an object");
        _streamWriteContext = _streamWriteContext.createChildObjectContext(currValue);
        try {
            _writer.stepIn(IonType.STRUCT);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }
    /*
    /*****************************************************************
    /* Support for type ids
    /*****************************************************************
     */

    @Override
    public void writeTypeId(Object rawId) throws JacksonException {
        if (rawId instanceof String[]) {
            String[] ids = (String[]) rawId;
            for (String id : ids) {
                annotateNextValue(id);
            }
        } else {
            annotateNextValue(String.valueOf(rawId));
        }
    }

    // Default impl should work fine here:
    // public WritableTypeId writeTypePrefix(WritableTypeId typeIdDef) throws JacksonException

    // Default impl should work fine here:
    // public WritableTypeId writeTypeSuffix(WritableTypeId typeIdDef) throws JacksonException

    /*
    /*****************************************************************
    /* Standard methods
    /*****************************************************************
     */

    @Override
    public String toString() {
        return "["+getClass().getSimpleName()+", Ion writer: "+_writer+"]";
    }

    /*
    /*****************************************************************
    /* Internal helper methods
    /*****************************************************************
     */

    protected void _reportNoRaw() throws JacksonException {
        throw _constructWriteException("writeRaw() functionality not available with Ion backend");
    }
}
