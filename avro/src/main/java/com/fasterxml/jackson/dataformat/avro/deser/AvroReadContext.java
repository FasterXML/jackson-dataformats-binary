package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;

/**
 * We need to use a custom context to be able to carry along
 * Object and array records.
 */
public abstract class AvroReadContext extends JsonStreamContext
{
    protected final AvroReadContext _parent;

    protected final String _typeId;

    /**
     * @since 2.9
     */
    protected Object _currentValue;

    /*
    /**********************************************************************
    /* Instance construction
    /**********************************************************************
     */

    public AvroReadContext(AvroReadContext parent, String typeId)
    {
        super();
        _parent = parent;
        _typeId = typeId;
    }

    /*
    /**********************************************************************
    /* Traversal
    /**********************************************************************
     */

    public abstract JsonToken nextToken() throws IOException;

    public abstract String nextFieldName() throws IOException;

    public abstract void skipValue(AvroParserImpl parser) throws IOException;

    public long getRemainingElements() {
        return -1L;
    }

    @Override
    public Object getCurrentValue() {
        return _currentValue;
    }

    @Override
    public void setCurrentValue(Object v) {
        _currentValue = v;
    }

    /*
    /**********************************************************************
    /* Accessors
    /**********************************************************************
     */

    @Override
    public String getCurrentName() { return null; }

    public abstract JsonToken getCurrentToken();
    
    @Override
    public final AvroReadContext getParent() { return _parent; }
    
    protected abstract void appendDesc(StringBuilder sb);

    public String getTypeId() {
        return _typeId;
    }

    // !!! TODO: implement from here
    /**
     * @since 2.8.7
    public abstract boolean isEnd() { }
    */

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    protected void _reportError() {
        throw new IllegalStateException("Can not read Avro input without specifying Schema");
    }

    /*
    /**********************************************************************
    /* Overridden standard methods
    /**********************************************************************
     */

    /**
     * Overridden to provide developer writeable "JsonPath" representation
     * of the context.
     */
    @Override
    public final String toString()
    {
        StringBuilder sb = new StringBuilder(64);
        appendDesc(sb);
        return sb.toString();
    }
}
