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

package com.fasterxml.jackson.dataformat.ion;

import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.json.JsonWriteContext;

/**
 * Extension of JsonWriteContexts that recognizes sexps
 * <p>
 * The JsonWriteContext is used by the pretty printer for handling of the whitespace between tokens,
 * and by the generator for verifying whether it's valid to write a given token. The writeStartSexp
 * method in the IonGenerator will enter a "sexp context", so we need a new state in the write
 * context to track that. Sexp handling is modeled after arrays.
 */
public class IonWriteContext extends JsonWriteContext {
    // Both contstants are in the tens instead of the ones to avoid conflict with the native
    // Jackson ones

    // Ion-specific contexts
    protected final static int TYPE_SEXP = 30;

    // Ion-specific statuses
    public final static int STATUS_OK_AFTER_SEXP_SEPARATOR = 60;

    protected IonWriteContext(int type, IonWriteContext parent, DupDetector dups) {
        super(type, parent, dups);
    }

    public static IonWriteContext createRootContext(DupDetector dd) {
        return new IonWriteContext(TYPE_ROOT, null, dd);
    }

    public IonWriteContext createChildSexpContext() {
        IonWriteContext ctxt = (IonWriteContext) _child;

        if(ctxt == null) {
            // same assignment as in createChildObjectContext, createChildArrayContext
            _child = ctxt = new IonWriteContext(TYPE_SEXP, this, (_dups == null) ? null : _dups.child());
        }

        // reset returns this, OK to cast
        return (IonWriteContext) ctxt.reset(TYPE_SEXP);
    }

    public final boolean inSexp() {
        return _type == TYPE_SEXP;
    }

    // // Overrides

    // We have to override the two createChild*Context methods to return a IonWriteContext
    // instead of a JsonWriteContext so sexps can be arbitrarily embedded in ion. Otherwise we
    // would only be able to create them as top level values.
    // Two methods below are copied from JsonWriteContext

    @Override
    public IonWriteContext createChildArrayContext() {
        IonWriteContext ctxt = (IonWriteContext) _child;

        if (ctxt == null) {
            _child = ctxt = new IonWriteContext(TYPE_ARRAY, this, (_dups == null) ? null : _dups.child());
            return ctxt;
        }

        return (IonWriteContext) ctxt.reset(TYPE_ARRAY);
    }

    @Override
    public IonWriteContext createChildObjectContext() {
        IonWriteContext ctxt = (IonWriteContext) _child;

        if (ctxt == null) {
            _child = ctxt = new IonWriteContext(TYPE_OBJECT, this, (_dups == null) ? null : _dups.child());
            return ctxt;
        }
        return (IonWriteContext) ctxt.reset(TYPE_OBJECT);
    }

    @Override
    public int writeValue() {
        // Add special handling for sexp separator
        if(_type == TYPE_SEXP) {
            int ix = _index;
            ++_index;
            return (ix < 0) ? STATUS_OK_AS_IS : STATUS_OK_AFTER_SEXP_SEPARATOR;
        }

        return super.writeValue();
    }
}
