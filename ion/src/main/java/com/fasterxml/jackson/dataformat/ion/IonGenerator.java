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

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonWriteContext;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.dataformat.ion.polymorphism.IonAnnotationTypeSerializer;

import software.amazon.ion.IonType;
import software.amazon.ion.IonValue;
import software.amazon.ion.IonWriter;
import software.amazon.ion.Timestamp;

/**
 * Implementation of {@link JsonGenerator} that will use an underlying
 * {@link IonWriter} for actual writing of content.
 */
public class IonGenerator
    extends GeneratorBase
{
    /*
     *****************************************************************
     * Basic configuration
     *****************************************************************
      */  

    /* This is the textual or binary writer */
    protected final IonWriter _writer;

    protected final IOContext _ioContext;
    
    /**
     * Highest-level output abstraction we can use; either
     * OutputStream or Writer.
     */
    protected final Closeable _destination;

    /*
     *****************************************************************
     * Instantiation
     *****************************************************************
      */  

    public IonGenerator(int features, ObjectCodec codec,
            IonWriter ion, IOContext ctxt, Closeable dst)
    {
        super(features, codec);
        _writer = ion;
        _ioContext = ctxt;
        _destination = dst;
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
     *****************************************************************
     * JsonGenerator implementation: state handling
     *****************************************************************
      */  

    @Override
    public void close() throws IOException
    {
        if (!_closed) {
            _closed = true;
            // force flush the writer
            _writer.close();
            // either way
            if (_ioContext.isResourceManaged()) {
                _destination.close();
            } else {
                if (_destination instanceof Flushable) {
                    ((Flushable) _destination).flush();
                }
            }
        }
    }

    @Override
    public void flush() throws IOException
    {
        Object dst = _ioContext.getSourceReference();
        if (dst instanceof Flushable) {
            ((Flushable) dst).flush();
        }
    }

    @Override
    public boolean isClosed() {
        return _closed;
    }

    /*
    /*****************************************************************
    /* Capability introspection
    /*****************************************************************
     */  

    @Override
    public boolean canWriteTypeId() { return true; }

    @Override
    public boolean canWriteBinaryNatively() { return true; }

    /*
    /*****************************************************************
    /* JsonGenerator implementation: write numeric values
    /*****************************************************************
     */  

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
     * @see IonAnnotationTypeSerializer
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
        int status = _writeContext.writeValue();
        if (status == JsonWriteContext.STATUS_EXPECT_NAME) {
            _reportError("Can not "+msg+", expecting field name");
        }
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
                if (_writeContext.inArray()) {
                    _cfgPrettyPrinter.beforeArrayValues(this);
                } else if (_writeContext.inObject()) {
                    _cfgPrettyPrinter.beforeObjectEntries(this);
                }
                break;
            default:
                throw new IllegalStateException("Should never occur; status "+status);
            }
        }
    }

    @Override
    public void writeEndArray() throws IOException, JsonGenerationException {
        _writeContext = _writeContext.getParent();  // <-- copied from UTF8JsonGenerator
        _writer.stepOut();
    }

    @Override
    public void writeEndObject() throws IOException, JsonGenerationException {
        _writeContext = _writeContext.getParent();  // <-- copied from UTF8JsonGenerator
        _writer.stepOut();
    }

    @Override
    public void writeFieldName(String value) throws IOException, JsonGenerationException {
        //This call to _writeContext is copied from Jackson's UTF8JsonGenerator.writeFieldName(String)
        int status = _writeContext.writeFieldName(value);
        if (status == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        
        _writeFieldName(value);     
    }
    
    protected void _writeFieldName(String value) throws IOException, JsonGenerationException {
        //Even though this is a one-liner, putting it into a function "_writeFieldName"
        //to keep this code matching the factoring in Jackson's UTF8JsonGenerator.
        _writer.setFieldName(value);   
    }

    @Override
    public void writeStartArray() throws IOException, JsonGenerationException {
        _verifyValueWrite("start an array");                      // <-- copied from UTF8JsonGenerator
        _writeContext = _writeContext.createChildArrayContext();  // <-- copied from UTF8JsonGenerator
        _writer.stepIn(IonType.LIST);
    }

    @Override
    public void writeStartObject() throws IOException, JsonGenerationException {
        _verifyValueWrite("start an object");                      // <-- copied from UTF8JsonGenerator
        _writeContext = _writeContext.createChildObjectContext();  // <-- copied from UTF8JsonGenerator
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
