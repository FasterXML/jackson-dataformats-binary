package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufField;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufMessage;

public class ProtobufWriteContext
    extends JsonStreamContext
{
    protected final ProtobufWriteContext _parent;

    /**
     * Definition of the closest Object that this context relates to;
     * either object for the field (for Message/Object types), or its
     * parent (for Array types)
     */
    protected ProtobufMessage _message;

    /**
     * Field within either current object (for Object context); or, parent
     * field (for Array)
     */
    protected ProtobufField _field;

    /**
     * @since 2.5
     */
    protected Object _currentValue;

    /*
    /**********************************************************
    /* Simple instance reuse slots; speed up things
    /* a bit (10-15%) for docs with lots of small
    /* arrays/objects
    /**********************************************************
     */

    protected ProtobufWriteContext _child = null;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    protected ProtobufWriteContext(int type, ProtobufWriteContext parent,
            ProtobufMessage msg)
    {
        super();
        _type = type;
        _parent = parent;
        _message = msg;
    }

    private void reset(int type, ProtobufMessage msg, ProtobufField f) {
        _type = type;
        _message = msg;
        _field = f;
        _currentValue = null;
    }
    
    // // // Factory methods

    public static ProtobufWriteContext createRootContext(ProtobufMessage msg) {
        return new ProtobufWriteContext(TYPE_ROOT, null, msg);
    }

    /**
     * Factory method called to get a placeholder context that is only
     * in place until actual schema is handed.
     */
    public static ProtobufWriteContext createNullContext() {
        return null;
    }
    
    public ProtobufWriteContext createChildArrayContext() {
        ProtobufWriteContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new ProtobufWriteContext(TYPE_ARRAY, this, _message);
            ctxt._field = _field;
            return ctxt;
        }
        ctxt.reset(TYPE_ARRAY, _message, _field);
        return ctxt;
    }

    public ProtobufWriteContext createChildObjectContext(ProtobufMessage type) {
        ProtobufWriteContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new ProtobufWriteContext(TYPE_OBJECT, this, type);
            return ctxt;
        }
        ctxt.reset(TYPE_OBJECT, type, null);
        return ctxt;
    }

    /*
    /**********************************************************
    /* Simple accessors, mutators
    /**********************************************************
     */
    
    @Override
    public final ProtobufWriteContext getParent() { return _parent; }
    
    @Override
    public String getCurrentName() {
        return ((_type == TYPE_OBJECT) && (_field != null)) ? _field.name : null;
    }

    @Override
    public Object getCurrentValue() {
        return _currentValue;
    }

    @Override
    public void setCurrentValue(Object v) {
        _currentValue = v;
    }
    
    public void setField(ProtobufField f) {
        _field = f;
    }

    public ProtobufField getField() {
        return _field;
    }

    public ProtobufMessage getMessageType() {
        return _message;
    }

    public boolean notArray() { return _type != TYPE_ARRAY; }
    
    public StringBuilder appendDesc(StringBuilder sb) {
        if (_parent != null) {
            sb = _parent.appendDesc(sb);
        }
        sb.append('/');
        switch (_type) {
        case TYPE_OBJECT:
            if (_field != null) {
                sb.append(_field.name);
            }
            break;
        case TYPE_ARRAY:
            sb.append(getCurrentIndex());
            break;
        case TYPE_ROOT:
        }
        return sb;
    }
    
    // // // Overridden standard methods
    
    /**
     * Overridden to provide developer JsonPointer representation
     * of the context.
     */
    @Override
    public final String toString() {
        return appendDesc(new StringBuilder(64)).toString();
    }
}
