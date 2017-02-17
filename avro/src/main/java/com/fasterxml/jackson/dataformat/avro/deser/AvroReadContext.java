package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;

/**
 * We need to use a custom context to be able to carry along
 * Object and array records.
 */
public abstract class AvroReadContext extends JsonStreamContext
{
    protected final AvroReadContext _parent;
    protected final Schema          _schema;

    /*
    /**********************************************************************
    /* Instance construction
    /**********************************************************************
     */

    public AvroReadContext(AvroReadContext parent, Schema schema)
    {
        super();
        _parent = parent;
        _schema = schema;
    }

    public abstract JsonToken nextToken() throws IOException;

    public abstract String nextFieldName() throws IOException;

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

    public Schema getSchema() { return _schema; }

    public Object getTypeId() {
        switch (getSchema().getType()) {
        case ENUM:
        case RECORD:
        case FIXED:
            return SpecificData.getClassName(getSchema());
        }
        return getSchema().getProp(SpecificData.CLASS_PROP);
    }
    
    protected abstract void appendDesc(StringBuilder sb);

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
