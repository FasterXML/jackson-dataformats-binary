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

package tools.jackson.dataformat.ion;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.core.json.DupDetector;

/**
 * Extension of TokenStreamContext that recognizes sexps
 * <p>
 * The TokenStreamContext is used by the pretty printer for handling of the whitespace between tokens,
 * and by the generator for verifying whether it's valid to write a given token. The writeStartSexp
 * method in the IonGenerator will enter a "sexp context", so we need a new state in the write
 * context to track that. Sexp handling is modeled after arrays.
 */
public class IonWriteContext extends TokenStreamContext
{
    // Both constants are in the tens instead of the ones to avoid conflict with the native
    // Jackson ones

    // Ion-specific contexts
    protected final static int TYPE_SEXP = 30;

    // Ion-specific statuses
    public final static int STATUS_OK_AFTER_SEXP_SEPARATOR = 60;
    
    /**
     * Parent context for this context; null for root context.
     */
    protected final IonWriteContext _parent;

    // // // Optional duplicate detection

    protected DupDetector _dups;

    /*
    /**********************************************************************
    /* Simple instance reuse slots; speed up things a bit (10-15%)
    /* for docs with lots of small arrays/objects
    /**********************************************************************
     */

    protected IonWriteContext _child;

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

    protected boolean _gotPropertyId;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected IonWriteContext(int type, IonWriteContext parent, DupDetector dups,
            Object currentValue)
    {
        super();
        _type = type;
        _parent = parent;
        _dups = dups;
        _index = -1;
        _currentValue = currentValue;
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

    public static IonWriteContext createRootContext(DupDetector dd) {
        return new IonWriteContext(TYPE_ROOT, null, dd, null);
    }

    private IonWriteContext reset(int type, Object currentValue) {
        _type = type;
        _index = -1;
        _currentName = null;
        _gotPropertyId = false;
        _currentValue = currentValue;
        if (_dups != null) { _dups.reset(); }
        return this;
    }

    public IonWriteContext createChildSexpContext(Object currentValue) {
        IonWriteContext ctxt = (IonWriteContext) _child;

        if(ctxt == null) {
            // same assignment as in createChildObjectContext, createChildArrayContext
            _child = ctxt = new IonWriteContext(TYPE_SEXP, this,
                    (_dups == null) ? null : _dups.child(), currentValue);
        }

        // reset returns this, OK to cast
        return ctxt.reset(TYPE_SEXP, currentValue);
    }

    // // Overrides

    // We have to override the two createChild*Context methods to return a IonWriteContext
    // instead of a JsonWriteContext so sexps can be arbitrarily embedded in ion. Otherwise we
    // would only be able to create them as top level values.
    // Two methods below are copied from JsonWriteContext

    public IonWriteContext createChildArrayContext(Object currentValue) {
        IonWriteContext ctxt = (IonWriteContext) _child;

        if (ctxt == null) {
            _child = ctxt = new IonWriteContext(TYPE_ARRAY, this,
                    (_dups == null) ? null : _dups.child(),
                            currentValue);
            return ctxt;
        }

        return (IonWriteContext) ctxt.reset(TYPE_ARRAY, currentValue);
    }

    public IonWriteContext createChildObjectContext(Object currentValue) {
        IonWriteContext ctxt = (IonWriteContext) _child;

        if (ctxt == null) {
            _child = ctxt = new IonWriteContext(TYPE_OBJECT, this,
                    (_dups == null) ? null : _dups.child(), currentValue);
            return ctxt;
        }
        return (IonWriteContext) ctxt.reset(TYPE_OBJECT, currentValue);
    }

    @Override
    public final IonWriteContext getParent() { return _parent; }

    @Override
    public final String currentName() {
        return _currentName;
    }

    @Override public boolean hasCurrentName() { return _gotPropertyId; }

    public final boolean inSexp() {
        return _type == TYPE_SEXP;
    }

    /*
    /**********************************************************************
    /* State changing (copied verbatim from `SimpleStreamWriteContext`
    /**********************************************************************
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

    private final void _checkDup(DupDetector dd, String name) throws StreamWriteException {
        if (dd.isDup(name)) {
            Object src = dd.getSource();
            throw new StreamWriteException(((src instanceof JsonGenerator) ? ((JsonGenerator) src) : null),
                    "Duplicate Object property \""+name+"\"");
        }
    }

    public boolean writeValue() {
        // Main limitation is with OBJECTs:
        if (_type == TYPE_OBJECT) {
            if (!_gotPropertyId) {
                return false;
            }
            _gotPropertyId = false;
        }
        // 17-Feb-2021, tatu: Not sure if this is needed (was in 2.12)
        /*
        else if (_type == TYPE_SEXP) {
            int ix = _index;
            ++_index;
            return (ix < 0) ? STATUS_OK_AS_IS : STATUS_OK_AFTER_SEXP_SEPARATOR;
        }
        */
        ++_index;
        return true;
    }

}
