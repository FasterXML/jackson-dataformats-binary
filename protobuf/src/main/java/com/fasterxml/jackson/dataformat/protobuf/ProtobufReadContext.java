package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufField;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufMessage;

/**
 * Replacement of {@link com.fasterxml.jackson.core.json.JsonReadContext}
 * to support features needed to decode nested Protobuf messages.
 */
public final class ProtobufReadContext
    extends JsonStreamContext
{
    /**
     * Parent context for this context; null for root context.
     */
    protected final ProtobufReadContext _parent;

    /**
     * Type of current context.
     */
    protected ProtobufMessage _messageType;

    /**
     * For array contexts: field that defines type of array values.
     */
    protected ProtobufField _field;

    protected String _currentName;

    /**
     * @since 2.9
     */
    protected Object _currentValue;

    /**
     * Offset within input buffer where the message represented
     * by this context (if message context) ends.
     */
    protected int _endOffset;
    
    /*
    /**********************************************************
    /* Simple instance reuse slots
    /**********************************************************
     */

    protected ProtobufReadContext _child = null;

    /*
    /**********************************************************
    /* Instance construction, reuse
    /**********************************************************
     */

    public ProtobufReadContext(ProtobufReadContext parent,
            ProtobufMessage messageType, int type, int endOffset)
    {
        super();
        _parent = parent;
        _messageType = messageType;
        _type = type;
        _endOffset = endOffset;
        _index = -1;
    }

    protected void reset(ProtobufMessage messageType, int type, int endOffset)
    {
        _messageType = messageType;
        _type = type;
        _index = -1;
        _currentName = null;
        _currentValue = null;
        _endOffset = endOffset;
    }

    @Override
    public Object getCurrentValue() {
        return _currentValue;
    }

    @Override
    public void setCurrentValue(Object v) {
        _currentValue = v;
    }
    
    // // // Factory methods

    public static ProtobufReadContext createRootContext() {
        return new ProtobufReadContext(null, null, TYPE_ROOT, Integer.MAX_VALUE);
    }

    public ProtobufReadContext createChildArrayContext(ProtobufField f)
    {
        _field = f;
        ProtobufReadContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new ProtobufReadContext(this, _messageType,
                    TYPE_ARRAY, _endOffset);
        } else {
            ctxt.reset(_messageType, TYPE_ARRAY, _endOffset);
        }
        return ctxt;
    }

    public ProtobufReadContext createChildArrayContext(ProtobufField f, int endOffset)
    {
        _field = f;
        ProtobufReadContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new ProtobufReadContext(this, _messageType,
                    TYPE_ARRAY, 0);
        } else {
            ctxt.reset(_messageType, TYPE_ARRAY, endOffset);
        }
        ctxt._field = f;
        return ctxt;
    }
    
    public ProtobufReadContext createChildObjectContext(ProtobufMessage messageType,
            ProtobufField f, int endOffset)
    {
        _field = f;
        ProtobufReadContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new ProtobufReadContext(this, messageType,
                    TYPE_OBJECT, endOffset);
            return ctxt;
        }
        ctxt.reset(messageType, TYPE_OBJECT, endOffset);
        return ctxt;
    }

    /*
    /**********************************************************
    /* Abstract method implementations
    /**********************************************************
     */

    @Override
    public String getCurrentName() { return _currentName; }

    @Override
    public ProtobufReadContext getParent() { return _parent; }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    /**
     * Method called when loading more input, or moving existing data;
     * this requires adjusting relative end offset as well, except for
     * root context.
     */
    public int adjustEnd(int bytesConsumed) {
        if (_type == TYPE_ROOT) {
            return _endOffset;
        }
        int newOffset = _endOffset - bytesConsumed;

        _endOffset = newOffset;

        for (ProtobufReadContext ctxt = _parent; ctxt != null; ctxt = ctxt.getParent()) {
            ctxt._adjustEnd(bytesConsumed);
        }

        // could do sanity check here; but caller should catch it
        return newOffset;
    }

    private void _adjustEnd(int bytesConsumed) {
        if (_type != TYPE_ROOT) {
            _endOffset -= bytesConsumed;
        }
    }
    
    public int getEndOffset() { return _endOffset; }

    public ProtobufMessage getMessageType() { return _messageType; }

    public ProtobufField getField() { return _field; }
    
    public void setMessageType(ProtobufMessage mt) { _messageType = mt; }
    
    /**
     * @return Location pointing to the point where the context
     *   start marker was found
     */
    public JsonLocation getStartLocation(Object srcRef, long byteOffset) {
        // not much we can tell
        return new JsonLocation(srcRef, byteOffset, -1, -1);
    }

    /*
    /**********************************************************
    /* State changes
    /**********************************************************
     */

    public void setCurrentName(String name) {
        _currentName = name;
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
