package com.fasterxml.jackson.dataformat.protobuf;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.io.ContentReference;
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

    /**
     * Whether this (Object-typed) context represents a {@code map<K,V>} field:
     * its entries are surfaced as key/value pairs of a JSON Object.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    protected boolean _inMap;

    /**
     * For a map context ({@link #_inMap}): the {@code map} field itself. Kept apart from
     * {@link #_field}, which is overwritten while a message-valued entry is streamed.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    protected ProtobufField _mapField;

    /**
     * For a map context ({@link #_inMap}): whether an entry sub-message is currently
     * being decoded. A map entry is <b>not</b> a JSON nesting level of its own -- its
     * key/value pair is one member of the map Object -- so it gets no context, and its
     * bound is tracked here instead.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    protected boolean _entryOpen;

    /**
     * Offset within input buffer where the {@code map} entry currently being decoded
     * ends; only meaningful while {@link #_entryOpen}. Rebased on buffer reload along
     * with {@link #_endOffset}.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    protected int _entryEndOffset;

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
        _nestingDepth = parent == null ? 0 : parent._nestingDepth + 1;
    }

    protected void reset(ProtobufMessage messageType, int type, int endOffset)
    {
        _messageType = messageType;
        _type = type;
        _index = -1;
        _currentName = null;
        _currentValue = null;
        _endOffset = endOffset;
        _field = null;
        _inMap = false;
        _mapField = null;
        _entryOpen = false;
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
                    TYPE_ARRAY, endOffset);
        } else {
            ctxt.reset(_messageType, TYPE_ARRAY, endOffset);
        }
        return ctxt;
    }

    /**
     * Factory for the context of a {@code map<K,V>} field: an Object-typed context
     * (carrying the synthetic entry message and the map field itself) whose entries
     * are iterated much like an unpacked array, so it inherits the enclosing message's
     * end offset.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    public ProtobufReadContext createChildMapContext(ProtobufField mapField, int endOffset)
    {
        // Enclosing context remembers the map field, so it can be replayed once the
        // map ends (mirrors createChildArrayContext)
        _field = mapField;
        final ProtobufMessage entryType = mapField.getMessageType();
        ProtobufReadContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new ProtobufReadContext(this, entryType, TYPE_OBJECT, endOffset);
        } else {
            ctxt.reset(entryType, TYPE_OBJECT, endOffset);
        }
        ctxt._field = mapField;
        ctxt._mapField = mapField;
        ctxt._inMap = true;
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
        _endOffset -= bytesConsumed;
        if (_entryOpen) {
            _entryEndOffset -= bytesConsumed;
        }

        for (ProtobufReadContext ctxt = _parent; ctxt != null; ctxt = ctxt.getParent()) {
            ctxt._adjustEnd(bytesConsumed);
        }

        // could do sanity check here; but caller should catch it
        return getEndOffset();
    }

    private void _adjustEnd(int bytesConsumed) {
        if (_type != TYPE_ROOT) {
            _endOffset -= bytesConsumed;
            if (_entryOpen) {
                _entryEndOffset -= bytesConsumed;
            }
        }
    }

    /**
     * @return Offset at which the content currently being decoded ends: for a map context
     *    with an entry open that is the entry's end, otherwise this context's own end.
     */
    public int getEndOffset() { return _entryOpen ? _entryEndOffset : _endOffset; }

    /**
     * @since 2.21.6 [dataformats-binary#712]
     */
    public boolean inMap() { return _inMap; }

    /**
     * Marks a {@code map} entry as being decoded, bounded by given offset. No child
     * context is created: an entry is one member of the map Object, not a nesting level.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    public void startMapEntry(int endOffset) {
        _entryOpen = true;
        _entryEndOffset = endOffset;
    }

    /**
     * @since 2.21.6 [dataformats-binary#712]
     */
    public void closeMapEntry() { _entryOpen = false; }

    /**
     * @since 2.21.6 [dataformats-binary#712]
     */
    public boolean isEntryOpen() { return _entryOpen; }

    /**
     * @return The {@code map} field this (map) context was created for; unlike
     *    {@link #getField()} this survives a message-valued entry being streamed.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    public ProtobufField getMapField() { return _mapField; }

    public ProtobufMessage getMessageType() { return _messageType; }

    public ProtobufField getField() { return _field; }

    public void setMessageType(ProtobufMessage mt) { _messageType = mt; }

    /**
     * @return Location pointing to the point where the context
     *   start marker was found
     */
    public JsonLocation startLocation(ContentReference srcRef, long byteOffset) {
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
