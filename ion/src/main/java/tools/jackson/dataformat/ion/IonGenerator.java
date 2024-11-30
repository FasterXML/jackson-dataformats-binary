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

package tools.jackson.dataformat.ion;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

import tools.jackson.core.*;
import tools.jackson.core.base.GeneratorBase;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.json.DupDetector;
import tools.jackson.core.util.JacksonFeatureSet;

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
    /*
    /**********************************************************************
    /* Basic configuration
    /**********************************************************************
     */

    /* This is the textual or binary writer */
    protected final IonWriter _writer;
    /* Indicates whether the IonGenerator is responsible for closing the underlying IonWriter. */
    protected final boolean _ionWriterIsManaged;

    /**
     * Bit flag composed of bits that indicate which
     * {@link IonWriteFeature}s
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
    protected IonWriteContext _streamWriteContext;

    /*
    /**********************************************************************
    /* Instantiation
    /**********************************************************************
     */

    public IonGenerator(ObjectWriteContext writeCtxt, IOContext ioCtxt,
            int streamWriteFeatures, int formatWriteFeatures,
            IonWriter ion, boolean ionWriterIsManaged, Closeable dst)
    {
        super(writeCtxt, ioCtxt, streamWriteFeatures);
        _formatFeatures = formatWriteFeatures;
        _writer = ion;
        _ionWriterIsManaged = ionWriterIsManaged;
        _destination = dst;

        final DupDetector dups = StreamWriteFeature.STRICT_DUPLICATE_DETECTION.enabledIn(streamWriteFeatures)
                ? DupDetector.rootDetector(this) : null;
                //  Overwrite the writecontext with our own implementation
        _streamWriteContext = IonWriteContext.createRootContext(dups);
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
    public int streamWriteOutputBuffered() { return -1; }

    @Override
    public Object streamWriteOutputTarget() {
        // 25-May-2020, tatu: Tricky one here; should we return `IonWriter` or
        //    actual underlying Writer/OutputStream?
        //    For now, return underlying Writer/OutputStream
        return _writer;
    }

    @Override
    protected void _closeInput() throws IOException
    {
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
    }

    @Override
    public void flush()
    {
        if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            Object dst = _ioContext.contentReference().getRawContent();
            if (dst instanceof Flushable) {
                try {
                    ((Flushable) dst).flush();
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
            }
        }
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
        return IonWriteFeature.USE_NATIVE_TYPE_ID.enabledIn(_formatFeatures);
    }

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
    public JsonGenerator writeNumber(short v) throws JacksonException {
        return writeNumber((int) v);
    }

    @Override
    public JsonGenerator writeNumber(int value) throws JacksonException {
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeInt((long)value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(long value) throws JacksonException {
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeInt(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(BigInteger value) throws JacksonException {
        if (value == null) {
            return writeNull();
        }
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeInt(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(double value) throws JacksonException {
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeFloat(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(float value) throws JacksonException {
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeFloat((double) value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(BigDecimal value) throws JacksonException {
        if (value == null) {
            return writeNull();
        }
        _verifyValueWrite("write numeric value");
        try {
            _writer.writeDecimal(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNumber(String value) throws JacksonException {
        // will lose its identity... fine
        return writeString(value);
    }

    /*
    /**********************************************************************
    /* Ion-specific additional write methods:
    /**********************************************************************
     */

    public JsonGenerator writeSymbol(String value) throws JacksonException {
        _verifyValueWrite("write symbol value");
        try {
            _writer.writeSymbol(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    /**
     * Annotates the next structure or value written -- {@link IonWriter#stepIn(IonType) stepIn()} or one of the
     * {@link IonWriter}s {@code write*()} methods.
     *
     * @param annotation a type annotation
     *
     * @see tools.jackson.dataformat.ion.polymorphism.IonAnnotationTypeSerializer
     */
    public JsonGenerator annotateNextValue(String annotation) {
        // We're not calling _verifyValueWrite because this doesn't actually write anything -- writing happens upon
        // the next _writer.write*() or stepIn().
        _writer.addTypeAnnotation(annotation);
        return this;
    }

    // // // Ion Extensions

    public JsonGenerator writeDate(Calendar value) throws JacksonException {
        _verifyValueWrite("write date value");
        try {
            _writer.writeTimestamp(Timestamp.forCalendar(value));
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    /*
    /**********************************************************************
    /* JsonGenerator implementation: write textual values
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeString(String value) throws JacksonException {
        _verifyValueWrite("write text value");
        try {
            _writer.writeString(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeString(char[] buffer, int offset, int length) throws JacksonException {
        // Ion doesn't have matching optimized method, so:
        return writeString(new String(buffer, offset, length));
    }

    @Override
    public JsonGenerator writeUTF8String(byte[] buffer, int offset, int length) throws JacksonException {
        // Ion doesn't have matching optimized method, so:
        return writeString(new String(buffer, offset, length, StandardCharsets.UTF_8));
    }

    /*
    /**********************************************************************
    /* JsonGenerator implementation: write raw JSON; N/A for Ion
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeRaw(String value) throws JacksonException {
        return _reportNoRaw();
    }

    @Override
    public JsonGenerator writeRaw(char value) throws JacksonException {
        return _reportNoRaw();
    }

    @Override
    public JsonGenerator writeRaw(String value, int arg1, int arg2) throws JacksonException {
        return _reportNoRaw();
    }

    @Override
    public JsonGenerator writeRaw(char[] value, int arg1, int arg2) throws JacksonException {
        return _reportNoRaw();
    }

    @Override
    public JsonGenerator writeRawValue(String value) throws JacksonException {
        return _reportNoRaw();
    }

    @Override
    public JsonGenerator writeRawValue(String value, int arg1, int arg2) throws JacksonException {
        return _reportNoRaw();
    }

    @Override
    public JsonGenerator writeRawValue(char[] value, int arg1, int arg2) throws JacksonException {
        return _reportNoRaw();
    }

    @Override
    public JsonGenerator writeRawUTF8String(byte[] text, int offset, int length) throws JacksonException {
        return _reportNoRaw();
    }

    /*
    /**********************************************************************
    /* JsonGenerator implementation: write other types of values
    /**********************************************************************
      */

    @Override
    public JsonGenerator writeBinary(Base64Variant b64v, byte[] data, int offset, int length) throws JacksonException {
        _verifyValueWrite("write binary data");
        // no distinct variants for Ion; should produce plain binary
        try {
            _writer.writeBlob(data, offset, length);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeBoolean(boolean value) throws JacksonException {
        _verifyValueWrite("write boolean");
        try {
            _writer.writeBool(value);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNull() throws JacksonException {
        _verifyValueWrite("write null");
        try {
            _writer.writeNull();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    public JsonGenerator writeNull(IonType ionType) throws JacksonException {
        _verifyValueWrite("write null");
        try {
            _writer.writeNull(ionType);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    // 06-Oct-2017, tatu: Base impl from `GeneratorBase` should be sufficient
    /*
    @Override
    public JsonGenerator writeObject(Object pojo) throws JacksonException
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
        return this;
    }
    */

    public JsonGenerator writeValue(IonValue value) throws JacksonException {
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
        return this;
    }

    public JsonGenerator writeValue(Timestamp value) throws JacksonException {
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
        return this;
    }

    /*
    /**********************************************************************
    /* Methods base impl needs
    /**********************************************************************
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
        // 18-Feb-2021, tatu: as per [dataformats-binary#247], this does not work
        //   (Ion impl must do pretty-printing), so
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
            case IonWriteContext.STATUS_OK_AFTER_SEXP_SEPARATOR:
                // Special handling of sexp value separators can be added later. Root value
                // separator will be whitespace which is sufficient to separate sexp values
                _cfgPrettyPrinter.writeRootValueSeparator(this);
                break;
            case JsonWriteContext.STATUS_OK_AS_IS:
                // First entry, but of which context?
                if (_outputContext.inArray()) {
                    _cfgPrettyPrinter.beforeArrayValues(this);
                } else if (_outputContext.inObject()) {
                    _cfgPrettyPrinter.beforeObjectEntries(this);
                } else if(((IonWriteContext) _writeContext).inSexp()) {
                    // Format sexps like arrays
                    _cfgPrettyPrinter.beforeArrayValues(this);
                }
                break;
            default:
                throw new IllegalStateException("Should never occur; status "+status);
            }
        }
        */
    }

    @Override
    public JsonGenerator writeEndArray() throws JacksonException {
        _streamWriteContext = _streamWriteContext.getParent();
        try {
            _writer.stepOut();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeEndObject() throws JacksonException {
        _streamWriteContext = _streamWriteContext.getParent();
        try {
            _writer.stepOut();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    /**
     * @since 2.12.2
     */
    public JsonGenerator writeEndSexp() throws JacksonException {
        _streamWriteContext = _streamWriteContext.getParent();
        try {
            _writer.stepOut();
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeName(String value) throws JacksonException {
        //This call to _outputContext is copied from Jackson's UTF8JsonGenerator.writeName(String)
        if (!_streamWriteContext.writeName(value)) {
            throw _constructWriteException("Can not write a property name, expecting a value");
        }
        _writeName(value);
        return this;
    }

    @Override
    public JsonGenerator writePropertyId(long id) throws JacksonException {
        // Should not force construction of a String here...
        String idStr = Long.valueOf(id).toString(); // since instances for small values cached
        return writeName(idStr);
    }

    protected void _writeName(String value) throws JacksonException {
        //Even though this is a one-liner, putting it into a function "_writeName"
        //to keep this code matching the factoring in Jackson's UTF8JsonGenerator.
        _writer.setFieldName(value);
    }

    @Override
    public JsonGenerator writeStartArray() throws JacksonException {
        _verifyValueWrite("start an array");
        _streamWriteContext = _streamWriteContext.createChildArrayContext(null);
        streamWriteConstraints().validateNestingDepth(_streamWriteContext.getNestingDepth());
        try {
            _writer.stepIn(IonType.LIST);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(Object currValue) throws JacksonException {
        _verifyValueWrite("start an array");
        _streamWriteContext = _streamWriteContext.createChildArrayContext(currValue);
        streamWriteConstraints().validateNestingDepth(_streamWriteContext.getNestingDepth());
        try {
            _writer.stepIn(IonType.LIST);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeStartObject() throws JacksonException {
        _verifyValueWrite("start an object");
        _streamWriteContext = _streamWriteContext.createChildObjectContext(null);
        streamWriteConstraints().validateNestingDepth(_streamWriteContext.getNestingDepth());
        try {
            _writer.stepIn(IonType.STRUCT);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(Object currValue) throws JacksonException {
        _verifyValueWrite("start an object");
        _streamWriteContext = _streamWriteContext.createChildObjectContext(currValue);
        streamWriteConstraints().validateNestingDepth(_streamWriteContext.getNestingDepth());
        try {
            _writer.stepIn(IonType.STRUCT);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
    }

    /**
     * @since 2.12.2
     */
    public JsonGenerator writeStartSexp() throws JacksonException {
        _verifyValueWrite("start a sexp");
        _streamWriteContext = _streamWriteContext.createChildSexpContext(null);
        streamWriteConstraints().validateNestingDepth(_streamWriteContext.getNestingDepth());
        try {
            _writer.stepIn(IonType.SEXP);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        return this;
   }

    /*
    /**********************************************************************
    /* Support for type ids
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeTypeId(Object rawId) throws JacksonException {
        if (rawId instanceof String[]) {
            String[] ids = (String[]) rawId;
            for (String id : ids) {
                annotateNextValue(id);
            }
        } else {
            annotateNextValue(String.valueOf(rawId));
        }
        return this;
    }

    // Default impl should work fine here:
    // public WritableTypeId writeTypePrefix(WritableTypeId typeIdDef) throws JacksonException

    // Default impl should work fine here:
    // public WritableTypeId writeTypeSuffix(WritableTypeId typeIdDef) throws JacksonException

    /*
    /**********************************************************************
    /* Standard methods
    /**********************************************************************
     */

    @Override
    public String toString() {
        return "["+getClass().getSimpleName()+", Ion writer: "+_writer+"]";
    }

    /*
    /**********************************************************************
    /* Internal helper methods
    /**********************************************************************
     */

    protected <T> T _reportNoRaw() throws JacksonException {
        throw _constructWriteException("writeRaw() functionality not available with Ion backend");
    }
}
