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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.SimpleTokenReadContext;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.amazon.ion.*;

/**
 * Implementation of {@link JsonParser} that will use an underlying
 * {@link IonReader} as actual parser, and camouflage it as json parser.
 * Will not expose all Ion info (specifically, annotations)
 */
public class IonParser
    extends ParserMinimalBase
{
    private static final BigInteger LONG_MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger INT_MIN_VALUE = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX_VALUE = BigInteger.valueOf(Integer.MAX_VALUE);
    
    /*
    /**********************************************************************
    /* Basic configuration
    /**********************************************************************
     */  

    protected final IonReader _reader;
    
    /**
     * Some information about source is passed here, including underlying
     * stream
     */
    protected final IOContext _ioContext;

    private final IonSystem _system;

    /*
    /**********************************************************************
    /* State
    /**********************************************************************
     */  

    /**
     * Whether this logical parser has been closed or not
     */
    protected boolean _closed;

    /**
     * Information about context in structure hierarchy
     */
    protected SimpleTokenReadContext _parsingContext;

    /**
     * Type of value token we have; used to temporarily hold information
     * when pointing to field name
     */
    protected JsonToken _valueToken;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */  

    public IonParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            int parserFeatures, IonReader r, IonSystem system)
    {
        super(readCtxt, parserFeatures);
        _reader = r;
        _ioContext = ioCtxt;
        // No DupDetector in use (yet?)
        _parsingContext = SimpleTokenReadContext.createRootContext(-1, -1, null);
        _system = system;
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* Capability, config introspection
    /**********************************************************************
     */

    @Override
    public JacksonFeatureSet<StreamReadCapability> getReadCapabilities() {
        // Defaults are fine
        return DEFAULT_READ_CAPABILITIES;
    }

    /*
    /*****************************************************************
    /* JsonParser implementation: state handling
    /*****************************************************************
     */  
 
    @Override
    public boolean isClosed() {
        return _closed;
    }
    
    @Override
    public void close() throws IOException {
        if (!_closed) {
            // should only close if manage the resource
            if (_ioContext.isResourceManaged()) {
                Object src = _ioContext.getSourceReference();
                if (src instanceof Closeable) {
                    ((Closeable) src).close();
                }
            }
            _closed = true;
        }
    }

    @Override
    public IonReader getInputSource() {
        return _reader;
    }

    /*
    /*****************************************************************
    /* JsonParser implementation: Text value access
    /*****************************************************************
     */

    @Override
    public boolean hasTextCharacters() {
        //This is always false because getText() is more efficient than getTextCharacters().
        // See the javadoc for JsonParser.hasTextCharacters().
        return false;
    }

    @Override
    public String getText() throws IOException
    {
         if (_currToken != null) { // null only before/after document
            switch (_currToken) {
            case FIELD_NAME:
                return currentName();
            case VALUE_STRING:
                return _reader.stringValue();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                Number n = getNumberValue();
                return (n == null) ? null : n.toString();
            // Some special cases here:
            case VALUE_EMBEDDED_OBJECT:
                if (_reader.getType() == IonType.TIMESTAMP) {
                    Timestamp ts = _reader.timestampValue();
                    if (ts != null) return ts.toString();
                }
                // How about CLOB?
                break;
            default:
            }
            return _currToken.asString();
        }
        return null;
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        String str = getText();
        return (str == null) ? null : str.toCharArray();
    }

    @Override
    public int getTextLength() throws IOException {
        return getText().length();
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }
    
    /*
    /*****************************************************************
    /* JsonParser implementation: Numeric value access
    /*****************************************************************
     */  

    @Override
    public BigInteger getBigIntegerValue() throws IOException {
        return _reader.bigIntegerValue();
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {
        return _reader.bigDecimalValue();
    }

    @Override
    public double getDoubleValue() throws IOException {
        return _reader.doubleValue();
    }

    @Override
    public float getFloatValue() throws IOException {
        return (float) _reader.doubleValue();
    }

    @Override
    public int getIntValue() throws IOException {
        return _reader.intValue();
    }

    @Override
    public long getLongValue() throws IOException {
        return _reader.longValue();
    }

    @Override
    public NumberType getNumberType() throws IOException
    {
        IonType type = _reader.getType();
        if (type != null) {
            // Hmmh. Looks like Ion gives little bit looser definition here;
            // harder to pin down exact type. But let's try some checks still.
            switch (type) {
            case DECIMAL:
                //Ion decimals can be arbitrary precision, need to read as big decimal
                return NumberType.BIG_DECIMAL;
            case INT:
                IntegerSize size = _reader.getIntegerSize();
                switch (size) {
                case INT:
                    return NumberType.INT;
                case LONG:
                    return NumberType.LONG;
                default:
                    return NumberType.BIG_INTEGER;
                }
            case FLOAT:
                return NumberType.DOUBLE;
            default:
            }
        }
        return null;
    }

    @Override
    public Number getNumberValue() throws IOException {
        NumberType nt = getNumberType();
        if (nt != null) {
            switch (nt) {
            case INT:
                return _reader.intValue();
            case LONG:
                return _reader.longValue();
            case FLOAT:
                return (float) _reader.doubleValue();
            case DOUBLE:
                return _reader.doubleValue();
            case BIG_DECIMAL:
                return _reader.bigDecimalValue();
            case BIG_INTEGER:
                return getBigIntegerValue();
            }
        }
        return null;
    }

    // @TODO -- 27-Jun-2020, tatu: 3.0 requires parser implementations to define
    //  and I _think_ this should be implemented, assuming Ion allows some Not-a-Number
    //  values for floating-point types?
    @Override
    public boolean isNaN() throws IOException {
        return false;
    }

    /*
    /****************************************************************
    /* JsonParser implementation: Access to other (non-text/number) values
    /*****************************************************************
     */  
    
    @Override
    public byte[] getBinaryValue(Base64Variant arg0) throws IOException
    {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT) {
            switch (_reader.getType()) {
            case BLOB:
            case CLOB: // looks like CLOBs are much like BLOBs...
                return _reader.newBytes();
            default:
            }
        }
        // 19-Jan-2010, tatus: how about base64 encoded Strings? Should we allow
        //    automatic decoding here?
        return null;
    }

    @SuppressWarnings("resource")
    private IonValue getIonValue() throws IOException {
        if (_system == null) {
            throw new IllegalStateException("This "+getClass().getSimpleName()+" instance cannot be used for IonValue mapping");
        }
        _currToken = JsonToken.VALUE_EMBEDDED_OBJECT;
        IonList l = _system.newEmptyList();
        IonWriter writer = _system.newWriter(l);
        writer.writeValue(_reader);
        IonValue v = l.get(0);
        v.removeFromContainer();
        return v;
    }

    @Override
    public Object getEmbeddedObject() throws IOException {
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT) {
            switch (_reader.getType()) {
            case TIMESTAMP:
                return _reader.timestampValue();
            case BLOB:
            case CLOB:
                return _reader.newBytes();
            // What about CLOB?
            default:
            }
        }
        return getIonValue();
    }

    /*
    /*****************************************************************
    /* JsonParser implementation: traversal
    /*****************************************************************
     */  

    @Override
    public JsonLocation getCurrentLocation() {
        return JsonLocation.NA;
    }

    @Override
    public String currentName() throws IOException {
        return _parsingContext.currentName();
    }

    @Override public TokenStreamContext getParsingContext() {  return _parsingContext; }
    @Override public void setCurrentValue(Object v) { _parsingContext.setCurrentValue(v); }
    @Override public Object getCurrentValue() { return _parsingContext.getCurrentValue(); }

    @Override
    public JsonLocation getTokenLocation() {
        return JsonLocation.NA;
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        // special case: if we return field name, we know value type, return it:
        if (_currToken == JsonToken.FIELD_NAME) {
            return (_currToken = _valueToken);
        }
        // also, when starting array/object, need to create new context
        if (_currToken == JsonToken.START_OBJECT) {
            _parsingContext = _parsingContext.createChildObjectContext(-1, -1);
            _reader.stepIn();
        } else if (_currToken == JsonToken.START_ARRAY) {
            _parsingContext = _parsingContext.createChildArrayContext(-1, -1);
            _reader.stepIn();
        }

        // any more tokens in this scope?
        IonType type = _reader.next();
        if (type == null) {
            if (_parsingContext.inRoot()) { // EOF?
                close();
                _currToken = null;
            } else {
                _parsingContext = _parsingContext.getParent();
                _currToken = _reader.isInStruct() ? JsonToken.END_OBJECT : JsonToken.END_ARRAY;
                _reader.stepOut();
            }
            return _currToken;
        }
        // Structs have field names; need to keep track:
        boolean inStruct = !_parsingContext.inRoot() && _reader.isInStruct();
        // (isInStruct can return true for the first value read if the reader
        // was created from an IonValue that has a parent container)
        _parsingContext.setCurrentName(inStruct ? _reader.getFieldName() : null);
        JsonToken t = _tokenFromType(type);
        // and return either field name first
        if (inStruct) {
            _valueToken = t;
            return (_currToken = JsonToken.FIELD_NAME);
        }
        // or just the value (for lists, root value)
        return (_currToken = t);
    }

    /**
     * @see com.fasterxml.jackson.dataformat.ion.polymorphism.IonAnnotationTypeDeserializer
     */
    public String[] getTypeAnnotations() {
        // Per its spec, will not return null
        return _reader.getTypeAnnotations();
    }

    @Override
    public JsonParser skipChildren() throws IOException
    {
       if (_currToken != JsonToken.START_OBJECT
            && _currToken != JsonToken.START_ARRAY) {
            return this;
        }
        int open = 1;

        /* Since proper matching of start/end markers is handled
         * by nextToken(), we'll just count nesting levels here
         */
        while (true) {
            JsonToken t = nextToken();
            if (t == null) {
                _handleEOF(); // won't return in this case... 
                return this;
            }
            switch (t) {
            case START_OBJECT:
            case START_ARRAY:
                ++open;
                break;
            case END_OBJECT:
            case END_ARRAY:
                if (--open == 0) {
                    return this;
                }
                break;
            default:
            }
        }
    }

    /*
     *****************************************************************
     * Internal helper methods
     *****************************************************************
      */  
    
    protected JsonToken _tokenFromType(IonType type)
    {
        // One twist: Ion exposes nulls as typed ones... so:
        if (_reader.isNullValue()) {
            return JsonToken.VALUE_NULL;
        }
        
        switch (type) {
        case BOOL:
            return _reader.booleanValue() ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
        case DECIMAL:
        case FLOAT:
            return JsonToken.VALUE_NUMBER_FLOAT;
        case INT:
            return JsonToken.VALUE_NUMBER_INT;
        case STRING:
        case SYMBOL:
            return JsonToken.VALUE_STRING;
            /* This is bit trickier: Ion has precise understanding of what it is... but
             * to retain that knowledge, let's consider it an embedded object for now
             */
        case NULL: // I guess this must then be "untyped" null?
            return JsonToken.VALUE_NULL;
        case LIST:
        case SEXP:
            return JsonToken.START_ARRAY;
        case STRUCT:
            return JsonToken.START_OBJECT;
        case TIMESTAMP:
            return JsonToken.VALUE_EMBEDDED_OBJECT;
        default:
        }
        // and actually everything also as sort of alien thing...
        // (BLOB, CLOB)
        return JsonToken.VALUE_EMBEDDED_OBJECT;
    }
    
    /**
     * Method called when an EOF is encountered between tokens.
     */
    @Override
    protected void _handleEOF() throws JsonParseException
    {
        if (!_parsingContext.inRoot()) {
            _reportError(": expected close marker for "+_parsingContext.typeDesc()+" (from "+_parsingContext.getStartLocation(_ioContext.getSourceReference())+")");
        }
    }
}
