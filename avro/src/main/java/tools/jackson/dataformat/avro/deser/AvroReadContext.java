package tools.jackson.dataformat.avro.deser;

import java.io.IOException;

import tools.jackson.core.TokenStreamContext;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.JsonToken;

/**
 * We need to use a custom context to be able to carry along
 * Object and array records.
 */
public abstract class AvroReadContext extends TokenStreamContext
{
    protected final AvroReadContext _parent;

    protected final String _typeId;

    protected JsonToken _currToken;

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
        _nestingDepth = parent == null ? 0 : parent._nestingDepth + 1;
    }

    /*
    /**********************************************************************
    /* Traversal
    /**********************************************************************
     */

    public abstract JsonToken nextToken() throws IOException;

    public abstract String nextName() throws IOException;

    // @since 3.0
    public abstract int nextNameMatch(PropertyNameMatcher matcher) throws IOException;

    public abstract void skipValue(AvroParserImpl parser) throws IOException;

    public long getRemainingElements() {
        return -1L;
    }

    @Override
    public Object currentValue() {
        return _currentValue;
    }

    @Override
    public void assignCurrentValue(Object v) {
        _currentValue = v;
    }

    /*
    /**********************************************************************
    /* Accessors
    /**********************************************************************
     */

    @Override
    public String currentName() { return null; }

    public final JsonToken currentToken() {
        return _currToken;
    }
    
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
