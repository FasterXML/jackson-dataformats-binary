package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;

/**
 * Base class for handlers for Avro structured types (or in some cases,
 * scalar types that need to be exposed in unified way similar to
 * structured types).
 */
public abstract class AvroStructureReader
    extends AvroReadContext
{
    protected JsonToken _currToken;

    protected AvroStructureReader(AvroReadContext parent, int type, String typeId) {
        super(parent, typeId);
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
    public abstract AvroStructureReader newReader(AvroReadContext parent, AvroParserImpl parser);

    @Override
    public abstract JsonToken nextToken() throws IOException;

    /**
     * Alternative to {@link #nextToken} which will simply skip the full
     * value.
     */
    @Override
    public abstract void skipValue(AvroParserImpl parser) throws IOException;

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
