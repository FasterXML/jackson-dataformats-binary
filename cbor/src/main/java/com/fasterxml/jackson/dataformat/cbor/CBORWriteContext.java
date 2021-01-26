package com.fasterxml.jackson.dataformat.cbor;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.core.json.DupDetector;

/**
 * Replacement for {@code JsonWriteContext}, needed to support alternative
 * numeric field id for Integer-valued Maps that CBOR allows.
 */
public final class CBORWriteContext extends TokenStreamContext
{
    /**
     * Parent context for this context; null for root context.
     */
    protected final CBORWriteContext _parent;

    // // // Optional duplicate detection

    protected DupDetector _dups;

    /*
    /**********************************************************************
    /* Simple instance reuse slots; speed up things a bit (10-15%)
    /* for docs with lots of small arrays/objects
    /**********************************************************************
     */

    protected CBORWriteContext _childToRecycle;

    /*
    /**********************************************************************
    /* Location/state information (minus source reference)
    /**********************************************************************
     */

    /**
     * Name of the Object property of which value is to be written; only
     * used for OBJECT contexts
     */
    protected String _currentName;

    protected Object _currentValue;

    /**
     * Alternative to {@code _currentName} used for integer/long-valued Maps.
     */
    protected long _currentPropertyId;

    /**
     * Marker used to indicate that we just wrote a property name (or Map name / id)
     * and now expect a value to write
     */
    protected boolean _gotPropertyId;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected CBORWriteContext(int type, CBORWriteContext parent, DupDetector dups,
            Object currentValue) {
        super();
        _type = type;
        _parent = parent;
        _dups = dups;
        _index = -1;
        _currentValue = currentValue;
    }

    private CBORWriteContext reset(int type, Object currentValue) {
        _type = type;
        _index = -1;
        // as long as _gotFieldId false, current name/id can be left as-is
        _gotPropertyId = false;
        _currentValue = currentValue;
        if (_dups != null) { _dups.reset(); }
        return this;
    }

    public CBORWriteContext withDupDetector(DupDetector dups) {
        _dups = dups;
        return this;
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
    /* Factory methods
    /**********************************************************************
     */

    public static CBORWriteContext createRootContext(DupDetector dd) {
        return new CBORWriteContext(TYPE_ROOT, null, dd, null);
    }

    public CBORWriteContext createChildArrayContext(Object currentValue) {
        CBORWriteContext ctxt = _childToRecycle;
        if (ctxt == null) {
            _childToRecycle = ctxt = new CBORWriteContext(TYPE_ARRAY, this,
                    (_dups == null) ? null : _dups.child(), currentValue);
            return ctxt;
        }
        return ctxt.reset(TYPE_ARRAY, currentValue);
    }

    public CBORWriteContext createChildObjectContext(Object currentValue) {
        CBORWriteContext ctxt = _childToRecycle;
        if (ctxt == null) {
            _childToRecycle = ctxt = new CBORWriteContext(TYPE_OBJECT, this,
                    (_dups == null) ? null : _dups.child(), currentValue);
            return ctxt;
        }
        return ctxt.reset(TYPE_OBJECT, currentValue);
    }

    @Override public final CBORWriteContext getParent() { return _parent; }

    @Override public final String currentName() {
        if (_gotPropertyId) {
            if (_currentName != null) {
                return _currentName;
            }
            return String.valueOf(_currentPropertyId);
        }
        return null;
    }

    @Override public boolean hasCurrentName() { return _gotPropertyId; }

    /**
     * Method that can be used to both clear the accumulated references
     * (specifically value set with {@link #assignCurrentValue(Object)})
     * that should not be retained, and returns parent (as would
     * {@link #getParent()} do). Typically called when closing the active
     * context when encountering {@link JsonToken#END_ARRAY} or
     * {@link JsonToken#END_OBJECT}.
     */
    public CBORWriteContext clearAndGetParent() {
        _currentValue = null;
        // could also clear the current name, but seems cheap enough to leave?
        return _parent;
    }

    public DupDetector getDupDetector() {
        return _dups;
    }

    /**
     * Method that writer is to call before it writes an Object property name.
     *
     * @return Ok if name writing should proceed
     */
    public boolean writeName(String name) throws StreamWriteException {
        if ((_type != TYPE_OBJECT) || _gotPropertyId) {
            return false;
        }
        _gotPropertyId = true;
        _currentName = name;
        if (_dups != null) { _checkDup(_dups, name); }
        return true;
    }

    public boolean writePropertyId(long propertyId) throws StreamWriteException {
        if ((_type != TYPE_OBJECT) || _gotPropertyId) {
            return false;
        }
        _gotPropertyId = true;
        _currentPropertyId = propertyId;
        // 14-Aug-2019, tatu: No dup deps for non-String keys, for now at least
        return true;
    }

    private final void _checkDup(DupDetector dd, String name) throws StreamWriteException {
        if (dd.isDup(name)) {
            Object src = dd.getSource();
            throw new StreamWriteException(((src instanceof JsonGenerator) ? ((JsonGenerator) src) : null),
                    "Duplicate Object property \""+name+"\"");
        }
    }

    public boolean writeValue() {
        // Only limitation is with OBJECTs:
        if (_type == TYPE_OBJECT) {
            if (!_gotPropertyId) {
                return false;
            }
            _gotPropertyId = false;
        }
        ++_index;
        return true;
    }
}
