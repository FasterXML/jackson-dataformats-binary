package tools.jackson.dataformat.protobuf;

import tools.jackson.core.TokenStreamContext;
import tools.jackson.dataformat.protobuf.schema.ProtobufField;
import tools.jackson.dataformat.protobuf.schema.ProtobufMessage;

public class ProtobufWriteContext
    extends TokenStreamContext
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

    protected Object _currentValue;

    /**
     * Whether this (Object-typed) context represents a {@code map<K,V>} field
     * being written: entries are emitted as repeated length-delimited sub-messages
     * rather than as a single sub-message.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    protected boolean _inMap;

    /**
     * For a map context ({@link #_inMap}): whether a map entry sub-message is
     * currently being buffered and still needs its length prefix finalized (which
     * happens when the next key, or the end of the map, is written).
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    protected boolean _entryOpen;

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
        _nestingDepth = parent == null ? 0 : parent._nestingDepth + 1;
        _message = msg;
    }

    private void reset(int type, ProtobufMessage msg, ProtobufField f) {
        _type = type;
        _message = msg;
        _field = f;
        _currentValue = null;
        _inMap = false;
        _entryOpen = false;
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

    /**
     * Factory method for the context of a {@code map<K,V>} field: an Object-typed
     * context that carries the map field itself (so its entry key/value fields stay
     * reachable) and is flagged as a map.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    public ProtobufWriteContext createChildMapContext(ProtobufField mapField) {
        // Carry the synthetic entry message so getMessageType() is correct while
        // entries are written (e.g. after a message-valued entry closes).
        final ProtobufMessage entryType = mapField.getMessageType();
        ProtobufWriteContext ctxt = _child;
        if (ctxt == null) {
            _child = ctxt = new ProtobufWriteContext(TYPE_OBJECT, this, entryType);
        } else {
            ctxt.reset(TYPE_OBJECT, entryType, null);
        }
        ctxt._field = mapField;
        ctxt._inMap = true;
        ctxt._entryOpen = false;
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
    public String currentName() {
        return ((_type == TYPE_OBJECT) && (_field != null)) ? _field.name : null;
    }

    @Override
    public Object currentValue() {
        return _currentValue;
    }

    @Override
    public void assignCurrentValue(Object v) {
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

    /**
     * @return Whether this context represents a {@code map<K,V>} field being written.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    public boolean inMap() { return _inMap; }

    /**
     * @since 2.21.6 [dataformats-binary#712]
     */
    public boolean isEntryOpen() { return _entryOpen; }

    /**
     * @since 2.21.6 [dataformats-binary#712]
     */
    public void setEntryOpen(boolean state) { _entryOpen = state; }

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
