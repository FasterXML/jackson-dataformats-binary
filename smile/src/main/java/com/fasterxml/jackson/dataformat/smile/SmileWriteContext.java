package com.fasterxml.jackson.dataformat.smile;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.json.DupDetector;

/**
 * Replacement for {@code JsonWriteContext}; while not strictly
 * needed is more streamlined, optimal since there's no need to
 * keep track of separators etc.
 *
 * @since 2.10
 */
public final class SmileWriteContext extends JsonStreamContext
{
    /**
     * Parent context for this context; null for root context.
     */
    protected final SmileWriteContext _parent;

    // // // Optional duplicate detection

    protected DupDetector _dups;

    /*
    /**********************************************************
    /* Simple instance reuse slots; speed up things a bit (10-15%)
    /* for docs with lots of small arrays/objects
    /**********************************************************
     */

    protected SmileWriteContext _childToRecycle;

    /*
    /**********************************************************
    /* Location/state information (minus source reference)
    /**********************************************************
     */

    /**
     * Name of the field of which value is to be written; only
     * used for OBJECT contexts
     */
    protected String _currentName;

    protected Object _currentValue;

    /**
     * Marker used to indicate that we just wrote a field name (or Map name / id)
     * and now expect a value to write
     */
    protected boolean _gotFieldId;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    protected SmileWriteContext(int type, SmileWriteContext parent, DupDetector dups,
            Object currentValue) {
        super();
        _type = type;
        _parent = parent;
        _dups = dups;
        _index = -1;
        _currentValue = currentValue;
    }

    private SmileWriteContext reset(int type, Object currentValue) {
        _type = type;
        _index = -1;
        // as long as _gotFieldId false, current name/id can be left as-is
        _gotFieldId = false;
        _currentValue = currentValue;
        if (_dups != null) { _dups.reset(); }
        return this;
    }

    public SmileWriteContext withDupDetector(DupDetector dups) {
        _dups = dups;
        return this;
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
    /**********************************************************
    /* Factory methods
    /**********************************************************
     */

    public static SmileWriteContext createRootContext(DupDetector dd) {
        return new SmileWriteContext(TYPE_ROOT, null, dd, null);
    }

    public SmileWriteContext createChildArrayContext(Object currentValue) {
        SmileWriteContext ctxt = _childToRecycle;
        if (ctxt == null) {
            _childToRecycle = ctxt = new SmileWriteContext(TYPE_ARRAY, this,
                    (_dups == null) ? null : _dups.child(), currentValue);
            return ctxt;
        }
        return ctxt.reset(TYPE_ARRAY, currentValue);
    }

    public SmileWriteContext createChildObjectContext(Object currentValue) {
        SmileWriteContext ctxt = _childToRecycle;
        if (ctxt == null) {
            _childToRecycle = ctxt = new SmileWriteContext(TYPE_OBJECT, this,
                    (_dups == null) ? null : _dups.child(), currentValue);
            return ctxt;
        }
        return ctxt.reset(TYPE_OBJECT, currentValue);
    }

    @Override public final SmileWriteContext getParent() { return _parent; }
    @Override public final String getCurrentName() {
        if (_gotFieldId) {
            return _currentName;
        }
        return null;
    }

    @Override public boolean hasCurrentName() { return _gotFieldId; }

    /**
     * Method that can be used to both clear the accumulated references
     * (specifically value set with {@link #setCurrentValue(Object)})
     * that should not be retained, and returns parent (as would
     * {@link #getParent()} do). Typically called when closing the active
     * context when encountering {@link JsonToken#END_ARRAY} or
     * {@link JsonToken#END_OBJECT}.
     */
    public SmileWriteContext clearAndGetParent() {
        _currentValue = null;
        // could also clear the current name, but seems cheap enough to leave?
        return _parent;
    }

    public DupDetector getDupDetector() {
        return _dups;
    }

    /**
     * Method that writer is to call before it writes a field name.
     *
     * @return Ok if name writing should proceed
     */
    public boolean writeFieldName(String name) throws JsonProcessingException {
        if ((_type != TYPE_OBJECT) || _gotFieldId) {
            return false;
        }
        _gotFieldId = true;
        _currentName = name;
        if (_dups != null) { _checkDup(_dups, name); }
        return true;
    }

    private final void _checkDup(DupDetector dd, String name) throws JsonProcessingException {
        if (dd.isDup(name)) {
            Object src = dd.getSource();
            throw new JsonGenerationException("Duplicate field '"+name+"'",
                    ((src instanceof JsonGenerator) ? ((JsonGenerator) src) : null));
        }
    }

    public boolean writeValue() {
        // Only limitation is with OBJECTs:
        if (_type == TYPE_OBJECT) {
            if (!_gotFieldId) {
                return false;
            }
            _gotFieldId = false;
        }
        ++_index;
        return true;
    }
}
