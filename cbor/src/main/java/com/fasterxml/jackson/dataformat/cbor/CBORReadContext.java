package com.fasterxml.jackson.dataformat.cbor;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.json.DupDetector;

/**
 * Replacement of {@link com.fasterxml.jackson.core.json.JsonReadContext}
 * to support features needed by CBOR format.
 */
public final class CBORReadContext
    extends JsonStreamContext
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
    
    /*
    /**********************************************************
    /* Simple instance reuse slots
    /**********************************************************
     */

    protected CBORReadContext _child = null;

    /*
    /**********************************************************
    /* Instance construction, reuse
    /**********************************************************
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
        if (_dups != null) {
            _dups.reset();
        }
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
    /**********************************************************
    /* Abstract method implementation
    /**********************************************************
     */

    @Override
    public String getCurrentName() { return _currentName; }

    @Override
    public CBORReadContext getParent() { return _parent; }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    public boolean hasExpectedLength() { return (_expEntryCount >= 0); }
    public int getExpectedLength() { return _expEntryCount; }

    public boolean acceptsBreakMarker() {
        return (_expEntryCount < 0) && _type != TYPE_ROOT;
    }
    
    /**
     * Method called to see if a new value is expected for this
     * Array or Object. Checks against expected length, if one known,
     * updating count of current entries if limit not yet reached.
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
    public JsonLocation getStartLocation(Object srcRef) {
        // not much we can tell
        return new JsonLocation(srcRef, 1L, -1, -1);
    }

    /*
    /**********************************************************
    /* State changes
    /**********************************************************
     */

    public void setCurrentName(String name) throws JsonProcessingException
    {
        _currentName = name;
        if (_dups != null) {
            _checkDup(_dups, name);
        }
    }

    private void _checkDup(DupDetector dd, String name) throws JsonProcessingException
    {
        if (dd.isDup(name)) {
            throw new JsonParseException("Duplicate field '"+name+"'", dd.findLocation());
        }
    }
    
    /*
    /**********************************************************
    /* Overridden standard methods
    /**********************************************************
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
