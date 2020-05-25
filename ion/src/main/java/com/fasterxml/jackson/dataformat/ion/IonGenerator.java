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
import java.util.Calendar;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.core.util.SimpleTokenWriteContext;
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

    protected final IOContext _ioContext;
    
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
    protected SimpleTokenWriteContext _tokenWriteContext;

    /*
    /**********************************************************************
    /* Instantiation
    /**********************************************************************
     */

    public IonGenerator(ObjectWriteContext writeCtxt, IOContext ioCtxt,
            int streamWriteFeatures,
            IonWriter ion, boolean ionWriterIsManaged, Closeable dst)
    {
        super(writeCtxt, streamWriteFeatures);
        _writer = ion;
        _ioContext = ioCtxt;
        _ionWriterIsManaged = ionWriterIsManaged;
        _destination = dst;

        final DupDetector dups = StreamWriteFeature.STRICT_DUPLICATE_DETECTION.enabledIn(streamWriteFeatures)
                ? DupDetector.rootDetector(this) : null;
        _tokenWriteContext = SimpleTokenWriteContext.createRootContext(dups);
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
    public void close() throws IOException
    {
        if (!_closed) {
            _closed = true;
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
    }

    @Override
    public void flush() throws IOException
    {
        if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            Object dst = _ioContext.getSourceReference();
            if (dst instanceof Flushable) {
                ((Flushable) dst).flush();
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
    public boolean canWriteTypeId() { return true; }

    @Override
    public boolean canWriteBinaryNatively() { return true; }

    /*
    /**********************************************************************
    /* JsonGenerator implementation: write numeric values
    /**********************************************************************
     */  

    @Override
    public void writeNumber(short v) throws IOException {
        writeNumber((int) v);
    }

    @Override
    public void writeNumber(int value) throws IOException, JsonGenerationException {
        _verifyValueWrite("write numeric value");
        _writer.writeInt((long)value);
    }

    @Override
    public void writeNumber(long value) throws IOException, JsonGenerationException {
        _verifyValueWrite("write numeric value");
        _writer.writeInt(value);
    }

    @Override
    public void writeNumber(BigInteger value) throws IOException, JsonGenerationException {
        if (value == null) {
            writeNull();
        } else {
            _verifyValueWrite("write numeric value");
            _writer.writeInt(value);
        }
    }

    @Override
    public void writeNumber(double value) throws IOException, JsonGenerationException {
        _verifyValueWrite("write numeric value");
        _writer.writeFloat(value);
    }

    @Override
    public void writeNumber(float value) throws IOException, JsonGenerationException {
        _verifyValueWrite("write numeric value");
        _writer.writeFloat((double) value);
    }

    @Override
    public void writeNumber(BigDecimal value) throws IOException, JsonGenerationException {
        if (value == null) {
            writeNull();
        } else {
            _verifyValueWrite("write numeric value");
            _writer.writeDecimal(value);
        }
    }

    @Override
    public void writeNumber(String value) throws IOException, JsonGenerationException, UnsupportedOperationException {
        // will lose its identity... fine
        writeString(value);
    }

    public void writeSymbol(String value) throws JsonGenerationException, IOException {
        _verifyValueWrite("write symbol value");
        _writer.writeSymbol(value);
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
    
    /* Ion Exentions
     * 
     */
    
    public void writeDate(Calendar value) throws JsonGenerationException, IOException {
        _verifyValueWrite("write date value");
        _writer.writeTimestamp(Timestamp.forCalendar(value));
    }
    /*
     *****************************************************************
     * JsonGenerator implementation: write textual values
     *****************************************************************
      */  

    @Override
    public void writeString(String value) throws IOException, JsonGenerationException {
        _verifyValueWrite("write text value");
        _writer.writeString(value);
    }

    @Override
    public void writeString(char[] buffer, int offset, int length) throws IOException, JsonGenerationException {
        // Ion doesn't have matching optimized method, so:
        writeString(new String(buffer, offset, length));
    }

    @Override
    public void writeUTF8String(byte[] buffer, int offset, int length) throws IOException, JsonGenerationException {
        // Ion doesn't have matching optimized method, so:
        writeString(new String(buffer, offset, length, "UTF-8"));
    }
    
    /*
     *****************************************************************
     * JsonGenerator implementation: write raw JSON; N/A for Ion
     *****************************************************************
     */  
    
    @Override
    public void writeRaw(String value) throws IOException, JsonGenerationException {
        _reportNoRaw();
    }

    @Override
    public void writeRaw(char value) throws IOException, JsonGenerationException {
        _reportNoRaw();
    }

    @Override
    public void writeRaw(String value, int arg1, int arg2) throws IOException, JsonGenerationException {
        _reportNoRaw();
    }

    @Override
    public void writeRaw(char[] value, int arg1, int arg2) throws IOException, JsonGenerationException {
        _reportNoRaw();
    }

    @Override
    public void writeRawValue(String value) throws IOException, JsonGenerationException {
        _reportNoRaw();
    }

    @Override
    public void writeRawValue(String value, int arg1, int arg2) throws IOException, JsonGenerationException {
        _reportNoRaw();
    }

    @Override
    public void writeRawValue(char[] value, int arg1, int arg2) throws IOException, JsonGenerationException {
        _reportNoRaw();
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int length) throws IOException, JsonGenerationException {
        _reportNoRaw();
    }
    
    /*
     *****************************************************************
     * JsonGenerator implementation: write other types of values
     *****************************************************************
      */  
    
    @Override
    public void writeBinary(Base64Variant b64v, byte[] data, int offset, int length) throws IOException, JsonGenerationException {
        _verifyValueWrite("write binary data");
        // no distinct variants for Ion; should produce plain binary
        _writer.writeBlob(data, offset, length);
    }

    @Override
    public void writeBoolean(boolean value) throws IOException, JsonGenerationException {
        _verifyValueWrite("write boolean");
        _writer.writeBool(value);
    }

    @Override
    public void writeNull() throws IOException, JsonGenerationException {
        _verifyValueWrite("write null");
        _writer.writeNull();
    }
    
    public void writeNull(IonType ionType) throws IOException, JsonGenerationException {
        _verifyValueWrite("write null");
        _writer.writeNull(ionType);    
    }

    // 06-Oct-2017, tatu: Base impl from `GeneratorBase` should be sufficient
    /*
    @Override
    public void writeObject(Object pojo) throws IOException, JsonProcessingException
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

    public void writeValue(IonValue value) throws IOException {
        _verifyValueWrite("write ion value");
        if (value == null) {
            _writer.writeNull();
        } else {
            value.writeTo(_writer);
        }
    }

    public void writeValue(Timestamp value) throws IOException {
        _verifyValueWrite("write timestamp");
        if (value == null) {
            _writer.writeNull();
        } else {
            _writer.writeTimestamp(value);
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
    protected void _verifyValueWrite(String msg) throws IOException, JsonGenerationException
    {
        if (!_tokenWriteContext.writeValue()) {
            _reportError("Can not "+msg+", expecting field name");
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
    public void writeEndArray() throws IOException, JsonGenerationException {
        _tokenWriteContext = _tokenWriteContext.getParent();
        _writer.stepOut();
    }

    @Override
    public void writeEndObject() throws IOException, JsonGenerationException {
        _tokenWriteContext = _tokenWriteContext.getParent();
        _writer.stepOut();
    }

    @Override
    public void writeFieldName(String value) throws IOException, JsonGenerationException {
        //This call to _outputContext is copied from Jackson's UTF8JsonGenerator.writeFieldName(String)
        if (!_tokenWriteContext.writeFieldName(value)) {
            _reportError("Can not write a field name, expecting a value");
        }
        
        _writeFieldName(value);     
    }

    @Override
    public void writeFieldId(long id) throws IOException {
        // Should not force construction of a String here...
        String idStr = Long.valueOf(id).toString(); // since instances for small values cached
        writeFieldName(idStr);
    }

    protected void _writeFieldName(String value) throws IOException {
        //Even though this is a one-liner, putting it into a function "_writeFieldName"
        //to keep this code matching the factoring in Jackson's UTF8JsonGenerator.
        _writer.setFieldName(value);   
    }

    @Override
    public void writeStartArray() throws IOException {
        _verifyValueWrite("start an array");
        _tokenWriteContext = _tokenWriteContext.createChildArrayContext(null);
        _writer.stepIn(IonType.LIST);
    }

    @Override
    public void writeStartArray(Object currValue) throws IOException {
        _verifyValueWrite("start an array");
        _tokenWriteContext = _tokenWriteContext.createChildArrayContext(currValue);
        _writer.stepIn(IonType.LIST);
    }

    @Override
    public void writeStartObject() throws IOException {
        _verifyValueWrite("start an object");
        _tokenWriteContext = _tokenWriteContext.createChildObjectContext(null);
        _writer.stepIn(IonType.STRUCT);
    }

    @Override
    public void writeStartObject(Object currValue) throws IOException {
        _verifyValueWrite("start an object");
        _tokenWriteContext = _tokenWriteContext.createChildObjectContext(currValue);
        _writer.stepIn(IonType.STRUCT);
    }

    /*
    /*****************************************************************
    /* Support for type ids
    /*****************************************************************
     */  

    @Override
    public void writeTypeId(Object rawId) throws IOException {
        if (rawId instanceof String[]) {
            String[] ids = (String[]) rawId;
            for (String id : ids) {
                annotateNextValue(id);
            }
        } else {
            annotateNextValue(String.valueOf(rawId));
        }
    }

    // default might actually work, but let's straighten it out a bit
    @Override
    public WritableTypeId writeTypePrefix(WritableTypeId typeIdDef) throws IOException
    {
        final JsonToken valueShape = typeIdDef.valueShape;
        typeIdDef.wrapperWritten = false;
        writeTypeId(typeIdDef.id);

        // plus start marker for value, if/as necessary
        if (valueShape == JsonToken.START_OBJECT) {
            writeStartObject(typeIdDef.forValue);
        } else if (valueShape == JsonToken.START_ARRAY) {
            // should we now set the current object?
            writeStartArray();
        }
        return typeIdDef;
    }

    // Default impl should work fine here:
    // public WritableTypeId writeTypeSuffix(WritableTypeId typeIdDef) throws IOException

    /*
    /*****************************************************************
    /* Standard methods
    *****************************************************************
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

    protected void _reportNoRaw() throws IOException {
        throw new IOException("writeRaw() functionality not available with Ion backend");
    }
}
