package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

/**
 * Base class for handlers for Avro structured types (or, in case of
 * root values, wrapped scalar values).
 */
public abstract class AvroStructureReader
    extends AvroReadContext
{
    protected JsonToken _currToken;

    protected AvroStructureReader(AvroReadContext parent, int type) {
        super(parent);
        _type = type;
    }
    
    /*
    /**********************************************************************
    /* Reader API
    /**********************************************************************
     */

    /**
     * Method for creating actual instance to use for reading (initial
     * instance constructed is so-called blue print).
     */
    public abstract AvroStructureReader newReader(AvroReadContext parent,
            AvroParserImpl parser, BinaryDecoder decoder);

    @Override
    public abstract JsonToken nextToken() throws IOException;

    @Override
    public final JsonToken getCurrentToken() {
        return _currToken;
    }
    
    protected void throwIllegalState(int state) {
        throw new IllegalStateException("Illegal state for reader of type "
                +getClass().getName()+": "+state);
    }

    protected <T> T _throwUnsupported() {
        throw new IllegalStateException("Can not call on "+getClass().getName());
    }
}
