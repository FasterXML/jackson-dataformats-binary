package tools.jackson.dataformat.cbor;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.CharTypes;
import tools.jackson.core.io.ContentReference;
import tools.jackson.core.json.DupDetector;

/**
 * Extension of {@link tools.jackson.core.TokenStreamContext}
 * to support features needed by CBOR format (mainly limits of expected
 * entries to output).
 */
public final class CBORReadContext
    extends TokenStreamContext
{
    /**
     * Parent context for this context; null for root context.
     */
    protected final CBORReadContext _parent;
    
    // // // Optional duplicate detection

    protected final DupDetector _dups;

    /**
     * For fixed-size Arrays, Objects, this indicates expected number of entries.
     */
    protected int _expEntryCount;
    
    // // // Location information (minus source reference)

    protected String _currentName;

    protected Object _currentValue;
    
    /*
    /**********************************************************************
    /* Simple instance reuse slots
    /**********************************************************************
     */

    protected CBORReadContext _child = null;

    /*
    /**********************************************************************
    /* Instance construction, reuse
    /**********************************************************************
     */

    public CBORReadContext(CBORReadContext parent, DupDetector dups,
            int type, int expEntryCount)
    {
        super();
        _parent = parent;
        _dups = dups;
        _type = type;
        _expEntryCount = expEntryCount;
        _index = -1;
    }

    protected void reset(int type, int expEntryCount)
    {
        _type = type;
        _expEntryCount = expEntryCount;
        _index = -1;
        _currentName = null;
        _currentValue = null;
        if (_dups != null) {
            _dups.reset();
        }
    }

    @Override
    public Object currentValue() {
        return _currentValue;
    }

    @Override
    public void assignCurrentValue(Object v) {
        _currentValue = v;
    }
    
    // // // Factory methods

    public static CBORReadContext createRootContext(DupDetector dups) {
        return new CBORReadContext(null, dups, TYPE_ROOT, -1);
    }

    public CBORReadContext createChildArrayContext(int expEntryCount)
    {
        CBORReadContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new CBORReadContext(this,
                    (_dups == null) ? null : _dups.child(),
                            TYPE_ARRAY, expEntryCount);
        } else {
            ctxt.reset(TYPE_ARRAY, expEntryCount);
        }
        return ctxt;
    }

    public CBORReadContext createChildObjectContext(int expEntryCount)
    {
        CBORReadContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new CBORReadContext(this,
                    (_dups == null) ? null : _dups.child(),
                    TYPE_OBJECT, expEntryCount);
            return ctxt;
        }
        ctxt.reset(TYPE_OBJECT, expEntryCount);
        return ctxt;
    }

    /*
    /**********************************************************************
    /* Abstract method implementation
    /**********************************************************************
     */

    @Override
    public String currentName() { return _currentName; }

    @Override
    public CBORReadContext getParent() { return _parent; }

    /*
    /**********************************************************************
    /* Extended API
    /**********************************************************************
     */

    public boolean hasExpectedLength() { return (_expEntryCount >= 0); }
    public int getExpectedLength() { return _expEntryCount; }

    // @since 2.13
    public int getRemainingExpectedLength() {
        int diff = _expEntryCount - _index;
        // Negative values would occur when expected count is -1
        return Math.max(0, diff);
    }

    public boolean acceptsBreakMarker() {
        return (_expEntryCount < 0) && _type != TYPE_ROOT;
    }

    /**
     * Method called to increment the current entry count (Object property, Array
     * element or Root value) for this context level
     * and then see if more entries are accepted.
     * The only case where more entries are NOT expected is for fixed-count
     * Objects and Arrays that just reached the entry count.
     *<p>
     * Note that since the entry count is updated this is a state-changing method.
     */
    public boolean expectMoreValues() {
        if (++_index == _expEntryCount) {
            return false;
        }
        return true;
    }

    /**
     * @return Location pointing to the point where the context
     *   start marker was found
     */
    @Override
    public JsonLocation startLocation(ContentReference srcRef) {
        return new JsonLocation(srcRef, 1L, -1, -1);
    }

    /*
    /**********************************************************************
    /* State changes
    /**********************************************************************
     */

    public void setCurrentName(String name) throws StreamReadException
    {
        _currentName = name;
        if (_dups != null) {
            _checkDup(_dups, name);
        }
    }

    private void _checkDup(DupDetector dd, String name) throws StreamReadException
    {
        if (dd.isDup(name)) {
            Object src = dd.getSource();
            throw new StreamReadException(((src instanceof JsonParser) ? ((JsonParser) src) : null),
                    "Duplicate Object property \""+name+"\"");
        }
    }

    /*
    /**********************************************************************
    /* Overridden standard methods
    /**********************************************************************
     */

    /**
     * Overridden to provide developer readable "JsonPath" representation
     * of the context.
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(64);
        switch (_type) {
        case TYPE_ROOT:
            sb.append("/");
            break;
        case TYPE_ARRAY:
            sb.append('[');
            sb.append(getCurrentIndex());
            sb.append(']');
            break;
        case TYPE_OBJECT:
            sb.append('{');
            if (_currentName != null) {
                sb.append('"');
                CharTypes.appendQuoted(sb, _currentName);
                sb.append('"');
            } else {
                sb.append('?');
            }
            sb.append('}');
            break;
        }
        return sb.toString();
    }
}
