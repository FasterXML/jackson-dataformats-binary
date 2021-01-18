package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Objects;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;

import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.fasterxml.jackson.dataformat.protobuf.schema.FieldType;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufField;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufMessage;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.WireType;

public class ProtobufGenerator extends GeneratorBase
{
    /*
    /**********************************************************************
    /* Constants
    /**********************************************************************
     */
    /**
     * This instance is used as a placeholder for cases where we do not know
     * actual field and want to simply skip over any values that caller tries
     * to write for it.
     */
    protected final static ProtobufField UNKNOWN_FIELD = ProtobufField.unknownField();

    /**
     * This is used as a placeholder for case where we don't have an actual message
     * to use, but know (from context) that one is expected.
     */
    protected final static ProtobufMessage UNKNOWN_MESSAGE = ProtobufMessage.bogusMessage("<unknown>");

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    final protected IOContext _ioContext;

    final protected ProtobufSchema _schema;

    /*
    /**********************************************************************
    /* Output state
    /**********************************************************************
     */

    /**
     * Reference to the root context since that is needed for serialization
     */
    protected ProtobufWriteContext _rootContext;
    
    protected boolean _inObject;

    /**
     * Flag that indicates whether values should be written with tag or not;
     * false for packed arrays, true for others.
     */
    protected boolean _writeTag;

    /**
     * Flag that is set when the whole content is complete, can
     * be output.
     */
    protected boolean _complete;

    /**
     * Type of protobuf message that is currently being output: usually
     * matches write context, but for arrays may indicate "parent" of array.
     */
    protected ProtobufMessage _currMessage;

    /**
     * Field to be output next; set when {@link JsonToken#FIELD_NAME} is written,
     * cleared once value has been written
     */
    protected ProtobufField _currField;

    /*
    /**********************************************************************
    /* Output buffering
    /**********************************************************************
     */

    /**
     * Ultimate destination
     */
    final protected OutputStream _output;

    /**
     * Object used in cases where we need to buffer content to calculate length-prefix.
     */
    protected ByteAccumulator _buffered;

    /**
     * Current context, in form we can use it.
     */
    protected ProtobufWriteContext _tokenWriteContext;

    /**
     * Currently active output buffer, place where appends occur.
     */
    protected byte[] _currBuffer;

    // TODO: remove work around in 2.8?
    /**
     * The first allocated (or recycled) buffer instance; needed to avoid
     * issue [dataformat-protobuf#14].
     */
    protected byte[] _origCurrBuffer;
    
    protected int _currStart;

    protected int _currPtr;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public ProtobufGenerator(ObjectWriteContext writeCtxt, IOContext ctxt,
            int streamWriteFeatures, ProtobufSchema schema,
            OutputStream output)
    {
        super(writeCtxt, streamWriteFeatures);
        _ioContext = ctxt;
        _output = output;
        _tokenWriteContext = _rootContext = ProtobufWriteContext.createNullContext();
        _currBuffer = _origCurrBuffer = ctxt.allocWriteEncodingBuffer();
        _schema = Objects.requireNonNull(schema, "Can not pass `null` 'schema'");
        // start with temporary root...
//        _currentContext = _rootContext = ProtobufWriteContext.createRootContext(this, schema);
        _tokenWriteContext = _rootContext = ProtobufWriteContext.createRootContext(schema.getRootType());
    }

    @Override
    public final Object getCurrentValue() {
        return _tokenWriteContext.getCurrentValue();
    }

    @Override
    public final void setCurrentValue(Object v) {
        _tokenWriteContext.setCurrentValue(v);
    }

    @Override
    public final TokenStreamContext getOutputContext() { return _tokenWriteContext; }

    /*
    /**********************************************************************
    /* Versioned                                                                             
    /**********************************************************************
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* Capability introspection
    /**********************************************************************
     */

    @Override
    public boolean canWriteBinaryNatively() {
        return true;
    }

    @Override
    public JacksonFeatureSet<StreamWriteCapability> getWriteCapabilities() {
        return DEFAULT_BINARY_WRITE_CAPABILITIES;
    }

    /*
    /**********************************************************************
    /* Overridden methods, configuration
    /**********************************************************************
     */

    @Override
    public Object getOutputTarget() {
        return _output;
    }

    /**
     * Calculating actual amount of buffering is somewhat complicated, and can not
     * necessarily give 100% accurate answer due to presence of VInt encoding for
     * length indicators. So, for now, we'll respond "don't know": this may be
     * improved if and as needed.
     */
    @Override
    public int getOutputBuffered() {
        return -1;
    }

    @Override public ProtobufSchema getSchema() {
        return _schema;
    }

    /*
    /**********************************************************************
    /* Overridden methods; writing field names
    /**********************************************************************
     */

    /* And then methods overridden to make final, streamline some
     * aspects...
     */

    @Override
    public final void writeFieldName(String name) throws JacksonException {
        if (!_inObject) {
            _reportError("Can not write field name: current context not Object but "+_tokenWriteContext.typeDesc());
        }
        ProtobufField f = _currField;
        // important: use current field only if NOT repeated field; repeated
        // field means an array until START_OBJECT
        if (f != null && _tokenWriteContext.notArray()) {
            f = f.nextIf(name);
            if (f == null) {
                f = _currMessage.field(name);
            }
        } else  {
            f = _currMessage.firstIf(name);
        }
        if (f == null) {
            // May be ok, if we have said so
            if ((_currMessage == UNKNOWN_MESSAGE)
                    || isEnabled(StreamWriteFeature.IGNORE_UNKNOWN)) {
                f = UNKNOWN_FIELD;
            } else {
                _reportError("Unrecognized field '"+name+"' (in Message of type "+_currMessage.getName()
                        +"); known fields are: "+_currMessage.fieldsAsString());
                        
            }
        }
        _tokenWriteContext.setField(f);
        _currField = f;
    }

    @Override
    public final void writeFieldName(SerializableString sstr) throws JacksonException {
        if (!_inObject) {
            _reportError("Can not write field name: current context not Object but "+_tokenWriteContext.typeDesc());
        }
        ProtobufField f = _currField;
        final String name = sstr.getValue();
        // important: use current field only if NOT repeated field; repeated
        // field means an array until START_OBJECT
        // NOTE: not ideal -- depends on if it really is sibling field of an array,
        // or an entry within
        if (f != null && _tokenWriteContext.notArray()) {
            f = f.nextIf(name);
            if (f == null) {
                f = _currMessage.field(name);
            }
        } else  {
            f = _currMessage.firstIf(name);
        }
        if (f == null) {
            // May be ok, if we have said so
            if ((_currMessage == UNKNOWN_MESSAGE)
                    || isEnabled(StreamWriteFeature.IGNORE_UNKNOWN)) {
                f = UNKNOWN_FIELD;
            } else {
                _reportError("Unrecognized field '"+name+"' (in Message of type "+_currMessage.getName()
                        +"); known fields are: "+_currMessage.fieldsAsString());
                        
            }
        }
        _tokenWriteContext.setField(f);
        _currField = f;
    }

    @Override
    public void writeFieldId(long id) throws JacksonException {
        // 24-Jul-2019, tatu: Should not force construction of a String here...
        String idStr = Long.valueOf(id).toString(); // since instances for small values cached
        writeFieldName(idStr);
    }

    /*
    /**********************************************************************
    /* Public API: low-level I/O
    /**********************************************************************
     */

    @Override
    public final void flush() throws JacksonException
    {
        // can only flush if we do not need accumulation for length prefixes
        try {
            if (_buffered == null) {
                int start = _currStart;
                int len = _currPtr - start;
                if (len > 0) {
                    _currStart = 0;
                    _currPtr = 0;
                    _output.write(_currBuffer, start, len);
                }
            }
            if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                _output.flush();
            }
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    @Override
    public void close() throws JacksonException
    {
        super.close();
        if (isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT)) {
            ProtobufWriteContext ctxt;
            while ((ctxt = _tokenWriteContext) != null) {
                if (ctxt.inArray()) {
                    writeEndArray();
                } else if (ctxt.inObject()) {
                    writeEndObject();
                } else {
                    break;
                }
            }
        }
        // May need to finalize...
        if (!_complete) {
            _complete();
        }
        if (_output != null) {
            try {
                if (_ioContext.isResourceManaged() || isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET)) {
                    _output.close();
                } else if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                    // 14-Jan-2019, tatu: [dataformats-binary#155]: unless prevented via feature
                    // If we can't close it, we should at least flush
                    _output.flush();
                }
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
        // Internal buffer(s) generator has can now be released as well
        _releaseBuffers();
    }

    /*
    /**********************************************************************
    /* Public API: structural output
    /**********************************************************************
     */

    @Override
    public final void writeStartArray() throws JacksonException
    {
        // First: arrays only legal as Message (~= Object) fields:
        if (!_inObject) {
            _reportError("Current context not an OBJECT, can not write arrays");
        }
        if (_currField == null) { // just a sanity check
            _reportError("Can not write START_ARRAY without field (message type "+_currMessage.getName()+")");
            return; // never gets here but code analyzers can't see that
        }
        if (!_currField.isArray()) {
            _reportError("Can not write START_ARRAY: field '"+_currField.name+"' not declared as 'repeated'");
        }

        // NOTE: do NOT clear _currField; needed for actual element type

        _tokenWriteContext = _tokenWriteContext.createChildArrayContext();
        _writeTag = !_currField.packed;
        /* Unpacked vs packed: if unpacked, nothing special is needed, since it
         * is equivalent to just replicating same field N times.
         * With packed, need length prefix, all that stuff, so need accumulator
         */
        if (!_writeTag) { // packed
            // note: tag SHOULD be written for array itself, but not contents
            _startBuffering(_currField.typedTag);
        }
    }

    @Override
    public final void writeStartArray(Object currValue) throws JacksonException {
        writeStartArray();
        _tokenWriteContext.setCurrentValue(currValue);
    }

    @Override
    public final void writeEndArray() throws JacksonException
    {
        if (!_tokenWriteContext.inArray()) {
            _reportError("Current context not Array but "+_tokenWriteContext.typeDesc());
        }
        _tokenWriteContext = _tokenWriteContext.getParent();
        if (_tokenWriteContext.inRoot()) {
            if (!_complete) {
                _complete();
            }
            _inObject = false;
        } else {
            _inObject = _tokenWriteContext.inObject();
        }

        // no arrays inside arrays, so parent can't be array and so:
        _writeTag = true; 
        // but, did we just end up packed array?
        if (_currField.packed) {
            _finishBuffering();
        }
    }

    @Override
    public final void writeStartObject(Object currValue) throws JacksonException {
        writeStartObject();
        _tokenWriteContext.setCurrentValue(currValue);
    }

    @Override
    public final void writeStartObject() throws JacksonException
    {
        if (_currField == null) {
            // root?
            if (!_tokenWriteContext.inRoot()) {
                _reportError("Can not write START_OBJECT without field (message type "+_currMessage.getName()+")");
            }
            _currMessage = _schema.getRootType();
            // note: no buffering on root
        } else {
            // but also, field value must be Message if so
            if (!_currField.isObject) {
                _reportError("Can not write START_OBJECT: type of field '"+_currField.name+"' not Message but: "+_currField.type);
            }
            _currMessage = _currField.getMessageType();
            // and we need to start buffering, or add more nesting
            // but we may or may not want to write tag for object
            if (_writeTag) {
                _startBuffering(_currField.typedTag);
            } else {
                _startBuffering();
            }
        }
        
        if (_inObject) {
            _tokenWriteContext = _tokenWriteContext.createChildObjectContext(_currMessage);
            _currField = null;
        } else { // must be array, then
            _tokenWriteContext = _tokenWriteContext.createChildObjectContext(_currMessage);
            // but do NOT clear next field here
            _inObject = true;
        }
        // even if within array, object fields use tags
        _writeTag = true; 
    }

    @Override
    public final void writeEndObject() throws JacksonException
    {
        if (!_inObject) {
            _reportError("Current context not Object but "+_tokenWriteContext.typeDesc());
        }
        _tokenWriteContext = _tokenWriteContext.getParent();
        if (_tokenWriteContext.inRoot()) {
            if (!_complete) {
                _complete();
            }
        } else {
            _currMessage = _tokenWriteContext.getMessageType();
        }
        _currField = _tokenWriteContext.getField();
        // possible that we might be within array, which might be packed:
        boolean inObj = _tokenWriteContext.inObject();
        _inObject = inObj;
        _writeTag = inObj || !_tokenWriteContext.inArray() || !_currField.packed;
        if (_buffered != null) { // null for root
            _finishBuffering();
        }
    }

    @Override
    public void writeArray(int[] array, int offset, int length) throws JacksonException
    {
        _verifyArrayWrite(array);
        _verifyOffsets(array.length, offset, length);

        // one minor optimization: empty arrays do not produce anything
        if (length > 0) {
            // NOTE: as a short-cut, leave out construction of intermediate ARRAY
            final int end = offset+length;
            if (_currField.packed) {
                _writePackedArray(array, offset, end);
            } else {
                _writeNonPackedArray(array, offset, end);
            }
            // and then pieces of END_ARRAY
            _writeTag = true; 
        }
    }

    @Override
    public void writeArray(long[] array, int offset, int length) throws JacksonException
    {
        _verifyArrayWrite(array);
        _verifyOffsets(array.length, offset, length);

        // one minor optimization: empty arrays do not produce anything
        if (length > 0) {
            // NOTE: as a short-cut, leave out construction of intermediate ARRAY
            final int end = offset+length;
            if (_currField.packed) {
                _writePackedArray(array, offset, end);
            } else {
                _writeNonPackedArray(array, offset, end);
            }
            // and then pieces of END_ARRAY
            _writeTag = true; 
        }
    }

    @Override
    public void writeArray(double[] array, int offset, int length) throws JacksonException
    {
        _verifyArrayWrite(array);
        _verifyOffsets(array.length, offset, length);

        // one minor optimization: empty arrays do not produce anything
        if (length > 0) {
            // NOTE: as a short-cut, leave out construction of intermediate ARRAY
            final int end = offset+length;
            if (_currField.packed) {
                _writePackedArray(array, offset, end);
            } else {
                _writeNonPackedArray(array, offset, end);
            }
            // and then pieces of END_ARRAY
            _writeTag = true; 
        }
    }

    private void _verifyArrayWrite(Object array) throws JacksonException
    {
        if (array == null) {
            throw new IllegalArgumentException("null array");
        }
        if (!_inObject) {
            _reportError("Current context not an OBJECT, can not write arrays");
        }
        if (_currField == null) { // inlined _verifyValueWrite
            _reportError("Can not write START_ARRAY without field (message type "+_currMessage.getName()+")");
            return; // never gets here but need to help code analyzers
        }
        if (!_currField.isArray()) {
            _reportError("Can not write START_ARRAY: field '"+_currField.name+"' not declared as 'repeated'");
        }
    }

    private void _writePackedArray(int[] array, int i, int end) throws JacksonException
    {
        _startBuffering(_currField.typedTag);
        final int type = _currField.wireType;

        if (type == WireType.VINT) {
            final boolean zigzag = _currField.usesZigZag;
            for (; i < end; ++i) {
                int v = array[i];
                if (zigzag) {
                    v = ProtobufUtil.zigzagEncode(v);
                }
                _writeVIntNoTag(v);
            }
        } else if (type == WireType.FIXED_32BIT) {
            for (; i < end; ++i) {
                _writeInt32NoTag(array[i]);
            }
        } else if (type == WireType.FIXED_64BIT) {
            for (; i < end; ++i) {
                _writeInt64NoTag(array[i]);
            }
        } else {
            _reportWrongWireType("int");
        }
        
        _finishBuffering();
    }

    private void _writePackedArray(long[] array, int i, int end) throws JacksonException
    {
        _startBuffering(_currField.typedTag);
        final int type = _currField.wireType;

        if (type == WireType.VINT) {
            final boolean zigzag = _currField.usesZigZag;
            for (; i < end; ++i) {
                long v = array[i];
                if (zigzag) {
                    v = ProtobufUtil.zigzagEncode(v);
                }
                _writeVLongNoTag(v);
            }
        } else if (type == WireType.FIXED_32BIT) {
            for (; i < end; ++i) {
                _writeInt32NoTag((int) array[i]);
            }
        } else if (type == WireType.FIXED_64BIT) {
            for (; i < end; ++i) {
                _writeInt64NoTag(array[i]);
            }
        } else {
            _reportWrongWireType("int");
        }
        _finishBuffering();
    }

    private void _writePackedArray(double[] array, int i, int end) throws JacksonException
    {
        _startBuffering(_currField.typedTag);
        final int type = _currField.wireType;

        if (type == WireType.FIXED_64BIT) {
            for (; i < end; ++i) {
                _writeInt64NoTag(Double.doubleToLongBits( array[i]));
            }
        } else if (type == WireType.FIXED_32BIT) { // should we support such coercion?
            for (; i < end; ++i) {
                float f = (float) array[i];
                _writeInt32NoTag(Float.floatToRawIntBits(f));
            }
        } else {
            _reportWrongWireType("double");
        }
        _finishBuffering();
    }

    private void _writeNonPackedArray(int[] array, int i, int end) throws JacksonException
    {
        final int type = _currField.wireType;

        if (type == WireType.VINT) {
            final boolean zigzag = _currField.usesZigZag;
            for (; i < end; ++i) {
                int v = array[i];
                if (zigzag) {
                    v = ProtobufUtil.zigzagEncode(v);
                }
                _writeVInt(v);
            }
        } else if (type == WireType.FIXED_32BIT) {
            for (; i < end; ++i) {
                _writeInt32(array[i]);
            }
        } else if (type == WireType.FIXED_64BIT) {
            for (; i < end; ++i) {
                _writeInt64(array[i]);
            }
        } else {
            _reportWrongWireType("int");
        }
    }

    private void _writeNonPackedArray(long[] array, int i, int end) throws JacksonException
    {
        final int type = _currField.wireType;

        if (type == WireType.VINT) {
            final boolean zigzag = _currField.usesZigZag;
            for (; i < end; ++i) {
                long v = array[i];
                if (zigzag) {
                    v = ProtobufUtil.zigzagEncode(v);
                }
                _writeVLong(v);
            }
        } else if (type == WireType.FIXED_32BIT) {
            for (; i < end; ++i) {
                _writeInt32((int) array[i]);
            }
        } else if (type == WireType.FIXED_64BIT) {
            for (; i < end; ++i) {
                _writeInt64(array[i]);
            }
        } else {
            _reportWrongWireType("int");
        }
    }

    private void _writeNonPackedArray(double[] array, int i, int end) throws JacksonException
    {
        final int type = _currField.wireType;

        if (type == WireType.FIXED_64BIT) {
            for (; i < end; ++i) {
                _writeInt64(Double.doubleToLongBits( array[i]));
            }
        } else if (type == WireType.FIXED_32BIT) { // should we support such coercion?
            for (; i < end; ++i) {
                float f = (float) array[i];
                _writeInt32(Float.floatToRawIntBits(f));
            }
        } else {
            _reportWrongWireType("double");
        }
    }

    /*
    /**********************************************************************
    /* Output method implementations, textual
    /**********************************************************************
     */

    @Override
    public void writeString(String text) throws JacksonException
    {
        if (text == null) {
            writeNull();
            return;
        }
        if (_currField.wireType != WireType.LENGTH_PREFIXED) {
            _writeEnum(text);
            return;
        }

        // Couple of choices; short (guaranteed to have length <= 127); medium (guaranteed
        // to fit in single buffer); and large (something else)

        final int clen = text.length();
        // since max encoded = 3*42 == 126, could check for 42 (ta-dah!)
        // ... or, speculate that we commonly get Ascii anyway, and just occasionally need to move
        if (clen > 99) {
            _encodeLongerString(text);
            return;
        }
        if (clen == 0) {
            _writeEmptyString();
            return;
        }
        _verifyValueWrite();
        _ensureRoom(clen+clen+clen+7); // up to 3 bytes per char; and possibly 2 bytes for length, 5 for tag
        int ptr = _writeTag(_currPtr) + 1; // +1 to leave room for length indicator
        final int start = ptr;
        final byte[] buf = _currBuffer;
        int i = 0;

        while (true) {
            int c = text.charAt(i);
            if (c > 0x7F) {
                break;
            }
            buf[ptr++] = (byte) c;
            if (++i >= clen) { // done! Also, we know length is 7-bit
                buf[start-1] = (byte) (ptr - start);
                _currPtr = ptr;
                return;
            }
        }

        // no; non-ASCII stuff, slower loop
        while (i < clen) {
            int c = text.charAt(i++);
            if (c <= 0x7F) {
                buf[ptr++] = (byte) c;
                continue;
            }
            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                buf[ptr++] = (byte) (0xc0 | (c >> 6));
                buf[ptr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            // 3 or 4 bytes (surrogate)
            // Surrogates?
            if (c < SURR1_FIRST || c > SURR2_LAST) { // nope, regular 3-byte character
                buf[ptr++] = (byte) (0xe0 | (c >> 12));
                buf[ptr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                buf[ptr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            // Yup, a surrogate pair
            if (c > SURR1_LAST) { // must be from first range; second won't do
                _throwIllegalSurrogate(c);
            }
            // ... meaning it must have a pair
            if (i >= clen) {
                _throwIllegalSurrogate(c);
            }
            c = _decodeSurrogate(c, text.charAt(i++));
            if (c > 0x10FFFF) { // illegal in JSON as well as in XML
                _throwIllegalSurrogate(c);
            }
            buf[ptr++] = (byte) (0xf0 | (c >> 18));
            buf[ptr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
            buf[ptr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
            buf[ptr++] = (byte) (0x80 | (c & 0x3f));
        }

        // still fits in a single byte?
        int blen = ptr-start;
        
        if (blen <= 0x7F) { // expected case
            buf[start-1] = (byte) blen;
        } else { // but sometimes we got it wrong, need to move (bah)
            System.arraycopy(buf, start, buf, start+1, blen);
            buf[start-1] = (byte) (0x80 + (blen & 0x7F));
            buf[start] = (byte) (blen >> 7);
            ++ptr;
        }
        _currPtr = ptr;
    }

    @Override
    public void writeString(char[] text, int offset, int clen) throws JacksonException
    {
        if (text == null) {
            writeNull();
            return;
        }
        if (_currField.wireType != WireType.LENGTH_PREFIXED) {
            _writeEnum(new String(text, offset, clen));
        }

        // Could guarantee with 42 chars or less; but let's do bit more speculative
        if (clen > 99) {
            _encodeLongerString(text, offset, clen);
            return;
        }
        if (clen == 0) {
            _writeEmptyString();
            return;
        }
        _verifyValueWrite();
        _ensureRoom(clen+clen+clen+7); // up to 3 bytes per char; and possibly 2 bytes for length, 5 for tag
        int ptr = _writeTag(_currPtr) + 1; // +1 to leave room for length indicator
        final int start = ptr;
        final byte[] buf = _currBuffer;
        final int end = offset + clen;

        while (true) {
            int c = text[offset];
            if (c > 0x7F) {
                break;
            }
            buf[ptr++] = (byte) c;
            if (++offset >= end) { // done!
                buf[start-1] = (byte) (ptr - start);
                _currPtr = ptr;
                return;
            }
        }
        while (offset < end) {
            int c = text[offset++];
            if (c <= 0x7F) {
                buf[ptr++] = (byte) c;
                continue;
            }
            if (c < 0x800) {
                buf[ptr++] = (byte) (0xc0 | (c >> 6));
                buf[ptr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            if (c < SURR1_FIRST || c > SURR2_LAST) {
                buf[ptr++] = (byte) (0xe0 | (c >> 12));
                buf[ptr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                buf[ptr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            if (c > SURR1_LAST) {
                _throwIllegalSurrogate(c);
            }
            // ... meaning it must have a pair
            if (offset >= end) {
                _throwIllegalSurrogate(c);
            }
            c = _decodeSurrogate(c, text[offset++]);
            if (c > 0x10FFFF) { // illegal in JSON as well as in XML
                _throwIllegalSurrogate(c);
            }
            buf[ptr++] = (byte) (0xf0 | (c >> 18));
            buf[ptr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
            buf[ptr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
            buf[ptr++] = (byte) (0x80 | (c & 0x3f));
        }

        // still fits in a single byte?
        int blen = ptr-start;
        
        if (blen <= 0x7F) { // expected case
            buf[start-1] = (byte) blen;
        } else { // but sometimes we got it wrong, need to move (bah)
            System.arraycopy(buf, start, buf, start+1, blen);
            buf[start-1] = (byte) (0x80 + (blen & 0x7F));
            buf[start] = (byte) (blen >> 7);
            ++ptr;
        }
        _currPtr = ptr;
    }

    @Override
    public final void writeString(SerializableString sstr) throws JacksonException
    {
        _verifyValueWrite();
        if (_currField.wireType == WireType.LENGTH_PREFIXED) {
            byte[] b = sstr.asUnquotedUTF8();
            _writeLengthPrefixed(b,  0, b.length);
        } else  if (_currField.type == FieldType.ENUM) {
            int index = _currField.findEnumIndex(sstr);
            if (index < 0) {
                _reportEnumError(sstr);
            }
            _writeEnum(index);
        } else {
            _reportWrongWireType("string");
        }
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int len) throws JacksonException
    {
        if (_currField.wireType != WireType.LENGTH_PREFIXED) {
            _reportWrongWireType("string");
            return;
        }
        _verifyValueWrite();
        _writeLengthPrefixed(text, offset, len);
    }

    @Override
    public final void writeUTF8String(byte[] text, int offset, int len) throws JacksonException
    {
        if (_currField.wireType != WireType.LENGTH_PREFIXED) {
            _reportWrongWireType("string");
            return;
        }
        _verifyValueWrite();
        _writeLengthPrefixed(text, offset, len);
    }

    protected void _writeEmptyString() throws JacksonException
    {
        _verifyValueWrite();
        _ensureRoom(6); // up to 5 bytes for tag + 1 for length
        _currPtr = _writeTag(_currPtr);
        _currBuffer[_currPtr++] = 0; // length
    }

    protected void _writeEnum(String str) throws JacksonException
    {
        if (_currField.type != FieldType.ENUM) {
            _reportWrongWireType("string");
        }
        // !!! TODO: optimize
        int index = _currField.findEnumIndex(str);
        if (index < 0) {
            _reportEnumError(str);
        }
        // basically, _writeVInt, but very likely to be very short; but if not:
        final int tag = _currField.typedTag;
        int ptr = _currPtr;
        if (index > 0x7F || tag > 0x7F || (ptr + 1) >= _currBuffer.length) {
            _writeVInt(index);
            return;
        }
        final byte[] buf = _currBuffer;
        buf[ptr++] = (byte) tag;
        buf[ptr++] = (byte) index;
        _currPtr = ptr;
    }

    protected void _writeEnum(int index) throws JacksonException
    {
        // basically, _writeVInt, but very likely to be very short; but if not:
        final int tag = _currField.typedTag;
        int ptr = _currPtr;
        if (index > 0x7F || tag > 0x7F || (ptr + 1) >= _currBuffer.length) {
            _writeVInt(index);
            return;
        }
        final byte[] buf = _currBuffer;
        buf[ptr++] = (byte) tag;
        buf[ptr++] = (byte) index;
        _currPtr = ptr;
    }

    protected void _reportEnumError(Object enumValue) throws JacksonException
    {
        _reportErrorF("No Enum '%s' found for property '%s'; valid values = %s"
                +_currField.getEnumValues(), _currField.name, enumValue);
    }

    /*
    /**********************************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************************
     */

    @Override
    public void writeRaw(String text) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char c) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws JacksonException {
        _reportUnsupportedOperation();
    }

    /*
    /**********************************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************************
     */
    
    @Override
    public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws JacksonException
    {
        if (data == null) {
            writeNull();
            return;
        }
        _verifyValueWrite();

        // Unlike with some other formats, let's NOT Base64 encoded even if nominally
        // expecting String, and rather assume that if so, caller just provides as
        // raw bytes of String
        if (_currField.wireType != WireType.LENGTH_PREFIXED) {
            _reportWrongWireType("binary");
            return;
        }
        _ensureRoom(10);
        _writeLengthPrefixed(data, offset, len);
    }

    /*
    /**********************************************************************
    /* Output method implementations, primitive
    /**********************************************************************
     */

    @Override
    public void writeBoolean(boolean state) throws JacksonException
    {
        _verifyValueWrite();

        // same as 'writeNumber(int)', really
        final int type = _currField.wireType;

        if (type == WireType.VINT) { // first, common case
            int b;
            if (_currField.usesZigZag) {
                b = state ? 2 : 0;
            } else {
                b = state ? 1 : 0;
            }
            _writeVInt(b);
            return;
        }
        if (type == WireType.FIXED_32BIT) {
            _writeInt32(state ? 1 : 0);
            return;
        }
        if (type == WireType.FIXED_64BIT) {
            _writeInt64(state ? 1L : 0L);
            return;
        }
        _reportWrongWireType("boolean");
    }

    @Override
    public void writeNull() throws JacksonException
    {
        _verifyValueWrite();
        if (_currField == UNKNOWN_FIELD) {
            return;
        }

        // protobuf has no way of writing null does it?
        // ...but should we try to add placeholders in arrays?

        /* 10-Nov-2014, tatu: At least one problem: required fields...
         *   Let's complain about those? In future, could add placeholders
         *   for such cases.
         */
        if (_currField.required) {
            _reportError("Can not omit writing of `null` value for required field '"+_currField.name+"' (type "+_currField.type+")");
        }
    }

    @Override
    public void writeNumber(short v) throws JacksonException {
        writeNumber((int) v);
    }

    @Override
    public void writeNumber(int v) throws JacksonException
    {
        _verifyValueWrite();

        final int type = _currField.wireType;

        if (type == WireType.VINT) { // first, common case
            if (_currField.usesZigZag) {
                v = ProtobufUtil.zigzagEncode(v);
            }
            _writeVInt(v);
            return;
        }
        if (type == WireType.FIXED_32BIT) {
            _writeInt32(v);
            return;
        }
        if (type == WireType.FIXED_64BIT) {
            _writeInt64(v);
            return;
        }
        _reportWrongWireType("int");
    }

    @Override
    public void writeNumber(long v) throws JacksonException
    {
        _verifyValueWrite();
        final int type = _currField.wireType;

        if (type == WireType.VINT) { // first, common case
            if (_currField.usesZigZag) {
                v = ProtobufUtil.zigzagEncode(v);
            }
            // is this ok?
            _writeVLong(v);
            return;
        }
        if (type == WireType.FIXED_32BIT) {
            _writeInt32((int) v);
            return;
        }
        if (type == WireType.FIXED_64BIT) {
            _writeInt64(v);
            return;
        }
        _reportWrongWireType("long");
    }

    @Override
    public void writeNumber(BigInteger v) throws JacksonException
    {
        if (v == null) {
            writeNull();
            return;
        }
        if (_currField == UNKNOWN_FIELD) {
            return;
        }
        // !!! TODO: better scheme to detect overflow or something
        writeNumber(v.longValue());
    }

    @Override
    public void writeNumber(double d) throws JacksonException
    {
        _verifyValueWrite();
        final int type = _currField.wireType;

        if (type == WireType.FIXED_32BIT) {
            // should we coerce like this?
            float f = (float) d;
            _writeInt32(Float.floatToRawIntBits(f));
            return;
        }
        if (type == WireType.FIXED_64BIT) {
            _writeInt64(Double.doubleToLongBits(d));
            return;
        }
        if (_currField.type == FieldType.STRING) {
            _encodeLongerString(String.valueOf(d));
            return;
        }
        _reportWrongWireType("double");
    }    

    @Override
    public void writeNumber(float f) throws JacksonException
    {
        _verifyValueWrite();
        final int type = _currField.wireType;

        if (type == WireType.FIXED_32BIT) {
            _writeInt32(Float.floatToRawIntBits(f));
            return;
        }
        if (type == WireType.FIXED_64BIT) {
            _writeInt64(Double.doubleToLongBits((double) f));
            return;
        }
        if (_currField.type == FieldType.STRING) {
            _encodeLongerString(String.valueOf(f));
            return;
        }
        _reportWrongWireType("float");
    }

    @Override
    public void writeNumber(BigDecimal v) throws JacksonException
    {
        if (v == null) {
            writeNull();
            return;
        }
        if (_currField == UNKNOWN_FIELD) {
            return;
        }
        // !!! TODO: better handling here... exception or write as string or... ?
        writeNumber(v.doubleValue());
    }

    @Override
    public void writeNumber(String encodedValue) throws JacksonException {
        throw new UnsupportedOperationException("Can not write 'untyped' numbers");
    }

    protected final void _verifyValueWrite() throws JacksonException {
        if (_currField == null) {
            _reportError("Can not write value without indicating field first (in message of type "+_currMessage.getName()+")");
        }
    }

    /*
    /**********************************************************************
    /* Implementations for methods from base class
    /**********************************************************************
     */

    @Override
    protected void _verifyValueWrite(String typeMsg) throws JacksonException {
        _throwInternal();
    }

    @Override
    protected void _releaseBuffers() {
        byte[] b = _currBuffer;
        if (b != null) {
            _currBuffer = null;
            byte[] b2 = _origCurrBuffer;
            // 07-Mar-2016, tatu: Crude, but until jackson-core 2.8, need
            //    to work around an issue by only returning current buffer
            //    if it is early same as original, or larger
            byte[] toRelease = ((b == b2) || (b.length > b2.length)) ? b : b2;
            _ioContext.releaseWriteEncodingBuffer(toRelease);
        }
    }

    /*
    /**********************************************************************
    /* Internal text/binary writes
    /**********************************************************************
     */

    private final static Charset UTF8 = Charset.forName("UTF-8");
    
    protected void _encodeLongerString(char[] text, int offset, int clen) throws JacksonException
    {
        _verifyValueWrite();
        byte[] b = new String(text, offset, clen).getBytes(UTF8);
        _writeLengthPrefixed(b, 0, b.length);
    }

    protected void _encodeLongerString(String text) throws JacksonException
    {
        byte[] b = text.getBytes(UTF8);
        _writeLengthPrefixed(b, 0, b.length);
    }
    
    
    protected void _writeLengthPrefixed(byte[] data, int offset, int len) throws JacksonException
    {
        // 15-Jun-2017, tatu: [dataformats-binary#94]: need to ensure there is actually
        //    enough space for simple add; if not, need more checking
        _ensureRoom(10); // max tag 5 bytes, ditto max length
        int ptr = _writeTag(_currPtr);
        ptr = ProtobufUtil.appendLengthLength(len, _currBuffer, ptr);

        // and then loop until we are done
        while (len > 0) {
            int max = Math.min(len, _currBuffer.length - ptr);
            System.arraycopy(data, offset, _currBuffer, ptr, max);
            ptr += max;
            if ((len -= max) == 0) {
                _currPtr = ptr;
                break;
            }
            offset += max;

            ByteAccumulator acc = _buffered;
            final int start = _currStart;
            _currStart = 0;
            int toFlush = ptr - start;
            ptr = 0;

            // without accumulation, we know buffer is free for reuse
            if (acc == null) {
                if (toFlush > 0) {
                    try {
                        _output.write(_currBuffer, start, toFlush);
                    } catch (IOException e) {
                        throw _wrapIOFailure(e);
                    }
                }
                ptr = 0;
                continue;
            }
            // but with buffered, need to append, allocate new buffer (since old
            // almost certainly contains buffered data)
            if (toFlush > 0) {
                acc.append(_currBuffer, start, toFlush);
            }
            _currBuffer = ProtobufUtil.allocSecondary(_currBuffer);
        }
    }

    /*
    /**********************************************************************
    /* Internal scalar value writes
    /**********************************************************************
     */

    private final void _writeVInt(int v) throws JacksonException
    {
        // Max tag length 5 bytes, then at most 5 bytes
        _ensureRoom(10);
        int ptr = _writeTag(_currPtr);
        if (v < 0) {
            _currPtr = _writeVIntMax(v, ptr);
            return;
        }

        final byte[] buf = _currBuffer;
        if (v <= 0x7F) {
            buf[ptr++] = (byte) v;
        } else {
            buf[ptr++] = (byte) (0x80 + (v & 0x7F));
            v >>= 7;
            if (v <= 0x7F) {
                buf[ptr++] = (byte) v;
            } else {
                buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
                v >>= 7;
                if (v <= 0x7F) {
                    buf[ptr++] = (byte) v;
                } else {
                    buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
                    v >>= 7;
                    if (v <= 0x7F) {
                        buf[ptr++] = (byte) v;
                    } else {
                        buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
                        v >>= 7;
                        // and now must have at most 3 bits (since negatives were offlined)
                        buf[ptr++] = (byte) (v & 0x7F);
                    }
                }
            }
        }
        _currPtr = ptr;
    }

    // @since 2.8
    private final void _writeVIntNoTag(int v) throws JacksonException
    {
        // Max at most 5 bytes
        _ensureRoom(5);
        int ptr = _currPtr;
        if (v < 0) {
            _currPtr = _writeVIntMax(v, ptr);
            return;
        }

        final byte[] buf = _currBuffer;
        if (v <= 0x7F) {
            buf[ptr++] = (byte) v;
        } else {
            buf[ptr++] = (byte) (0x80 + (v & 0x7F));
            v >>= 7;
            if (v <= 0x7F) {
                buf[ptr++] = (byte) v;
            } else {
                buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
                v >>= 7;
                if (v <= 0x7F) {
                    buf[ptr++] = (byte) v;
                } else {
                    buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
                    v >>= 7;
                    if (v <= 0x7F) {
                        buf[ptr++] = (byte) v;
                    } else {
                        buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
                        v >>= 7;
                        // and now must have at most 3 bits (since negatives were offlined)
                        buf[ptr++] = (byte) (v & 0x7F);
                    }
                }
            }
        }
        _currPtr = ptr;
    }
    
    // off-lined version for 5-byte VInts
    private final int _writeVIntMax(int v, int ptr) throws JacksonException
    {
        final byte[] buf = _currBuffer;
        buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
        v >>>= 7;
        buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
        v >>= 7;
        buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
        v >>= 7;
        buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
        v >>= 7;
        buf[ptr++] = (byte) v;
        return ptr;
    }

    private final void _writeVLong(long v) throws JacksonException
    {
        // Max tag length 5 bytes, then at most 10 bytes
        _ensureRoom(16);
        int ptr = _writeTag(_currPtr);
        if (v < 0L) {
            _currPtr = _writeVLongMax(v, ptr);
            return;
        }

        // first, 4 bytes or less?
        if (v <= 0x0FFFFFFF) {
            int i = (int) v;
            final byte[] buf = _currBuffer;

            if (v <= 0x7F) {
                buf[ptr++] = (byte) v;
            } else {
                do {
                    buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
                    i >>= 7;
                } while (i > 0x7F);
                buf[ptr++] = (byte) i;
            }
            _currPtr = ptr;
            return;
        }
        // nope, so we know 28 LSBs are to be written first
        int i = (int) v;
        final byte[] buf = _currBuffer;

        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);

        v >>>= 28;

        // still got 36 bits, chop of LSB
        if (v <= 0x7F) {
            buf[ptr++] = (byte) v;
        } else {
            buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
            // but then can switch to int for remaining max 28 bits
            i = (int) (v >> 7);
            do {
                buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
                i >>= 7;
            } while (i > 0x7F);
            buf[ptr++] = (byte) i;
        }
        _currPtr = ptr;
    }

    // @since 2.8
    private final void _writeVLongNoTag(long v) throws JacksonException
    {
        // Max: 10 bytes
        _ensureRoom(10);
        int ptr = _currPtr;
        if (v < 0L) {
            _currPtr = _writeVLongMax(v, ptr);
            return;
        }

        // first, 4 bytes or less?
        if (v <= 0x0FFFFFFF) {
            int i = (int) v;
            final byte[] buf = _currBuffer;

            if (v <= 0x7F) {
                buf[ptr++] = (byte) v;
            } else {
                do {
                    buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
                    i >>= 7;
                } while (i > 0x7F);
                buf[ptr++] = (byte) i;
            }
            _currPtr = ptr;
            return;
        }
        // nope, so we know 28 LSBs are to be written first
        int i = (int) v;
        final byte[] buf = _currBuffer;

        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);

        v >>>= 28;

        // still got 36 bits, chop of LSB
        if (v <= 0x7F) {
            buf[ptr++] = (byte) v;
        } else {
            buf[ptr++] = (byte) ((v & 0x7F) + 0x80);
            // but then can switch to int for remaining max 28 bits
            i = (int) (v >> 7);
            do {
                buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
                i >>= 7;
            } while (i > 0x7F);
            buf[ptr++] = (byte) i;
        }
        _currPtr = ptr;
    }
    
    // off-lined version for 10-byte VLongs
    private final int _writeVLongMax(long v, int ptr) throws JacksonException
    {
        final byte[] buf = _currBuffer;
        // first, LSB 28 bits
        int i = (int) v;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);

        // then next 28 (for 56 so far)
        i = (int) (v >>> 28);
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);

        // and last 2 (for 7 bits, and 1 bit, respectively)
        i = (int) (v >>> 56);
        buf[ptr++] = (byte) ((i & 0x7F) + 0x80);
        i >>= 7;
        buf[ptr++] = (byte) i;
        return ptr;
    }

    private final void _writeInt32(int v) throws JacksonException
    {
        _ensureRoom(9); // max tag 5 bytes
        int ptr = _writeTag(_currPtr);
        final byte[] buf = _currBuffer;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        _currPtr =  ptr;
    }

    private final void _writeInt32NoTag(int v) throws JacksonException
    {
        _ensureRoom(4);
        int ptr = _currPtr;
        final byte[] buf = _currBuffer;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        _currPtr =  ptr;
    }

    private final void _writeInt64(long v64) throws JacksonException
    {
        _ensureRoom(13); // max tag 5 bytes
        int ptr = _writeTag(_currPtr);
        final byte[] buf = _currBuffer;

        int v = (int) v64;
        
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;

        v = (int) (v64 >> 32);
        
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        
        _currPtr =  ptr;
    }

    private final void _writeInt64NoTag(long v64) throws JacksonException
    {
        _ensureRoom(8);
        int ptr = _currPtr;
        final byte[] buf = _currBuffer;

        int v = (int) v64;
        
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;

        v = (int) (v64 >> 32);
        
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        v >>= 8;
        buf[ptr++] = (byte) v;
        
        _currPtr =  ptr;
    }

    /*
    /**********************************************************************
    /* Helper methods, buffering
    /**********************************************************************
     */

    private final int _writeTag(int ptr)
    {
        if (_writeTag) {
            final byte[] buf = _currBuffer;
            int tag = _currField.typedTag;
            if (tag <= 0x7F) {
                buf[ptr++] = (byte) tag;
            } else {
                // Note: caller must have ensured space for at least 5 bytes
                do {
                    buf[ptr++] = (byte) ((tag & 0x7F) + 0x80);
                    tag >>= 7;
                } while (tag > 0x7F);
                buf[ptr++] = (byte) tag;
            }
        }
        return ptr;
    }

    /**
     * Method called when buffering an entry that should be prefixed
     * with a type tag.
     */
    private final void _startBuffering(int typedTag) throws JacksonException
    {
        // need to ensure room for tag id, length (10 bytes); might as well ask for bit more
        _ensureRoom(20);
        // and leave the gap of 10 bytes
        int ptr = _currPtr;
        int start = _currStart;

        // root level content to flush first?
        if (_buffered == null) {
            int len = ptr - start;
            if (len > 0) {
                ptr = start = 0;
                try {
                    _output.write(_currBuffer, start, len);
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
            }
        }
        _buffered = new ByteAccumulator(_buffered, typedTag, _currBuffer, ptr, _currStart);
        ptr += 10;
        _currStart = ptr;
        _currPtr = ptr;
    }

    /**
     * Method called when buffering an entry that should not be prefixed
     * with a type tag.
     */
    private final void _startBuffering() throws JacksonException
    {
        // since no tag written, could skimp on space needed
        _ensureRoom(16);
        int ptr = _currPtr;

        /* 02-Jun-2015, tatu: It would seem like we should check for flushing here,
         *  similar to method above. But somehow that does not seem to be needed...
         *  Let's add it just to be safe, still.
         */
        // 04-Apr-2017, tatu: Most likely this is because this can only happen when we are
        //   writing Objects as elements of packed array; and this can not be root-level
        //   value: and if so we must be buffering (to get length prefix)
/*        
        if (_buffered == null) {
            int len = ptr - _currStart;
            if (len > 0) {
                ptr = 0;
                _output.write(_currBuffer, _currStart, len);
            }
        }
*/
        _buffered = new ByteAccumulator(_buffered, -1, _currBuffer, ptr, _currStart);
        ptr += 5;
        _currStart = ptr;
        _currPtr = ptr;
    }

    /**
     * Helper method called when the current buffering scope is closed;
     * when packed array is completed (`writeEndArray()`) or record is
     * completed (`writeEndObject()`).
     */
    private final void _finishBuffering() throws JacksonException
    {
        final int start = _currStart;
        final int newStart = _currPtr;        
        final int currLen = newStart - start;

        ByteAccumulator acc = _buffered;
        try {
            acc = acc.finish(_output, _currBuffer, start, currLen);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        _buffered = acc;
        if (acc == null) {
            _currStart = 0;
            _currPtr = 0;
            return;
        }
        _currStart = newStart;
// already same, no need to change
//      _currPtr = newStart;
    }

    protected final void _ensureRoom(int needed) throws JacksonException
    {
        // common case: we got it already
        if ((_currPtr + needed) > _currBuffer.length) {
            _ensureMore();
        }
    }

    protected final void _ensureMore() throws JacksonException
    {
        // if not, either simple (flush), or 
        final int start = _currStart;
        final int currLen = _currPtr - start;
        
        _currStart = 0;
        _currPtr = 0;

        ByteAccumulator acc = _buffered;
        if (acc == null) {
            // without accumulation, we know buffer is free for reuse
            if (currLen > 0) {
                try {
                    _output.write(_currBuffer, start, currLen);
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
            }
            return;
        }
        // but with buffered, need to append, allocate new buffer (since old
        // almost certainly contains buffered data)
        if (currLen > 0) {
            acc.append(_currBuffer, start, currLen);
        }
        _currBuffer = ProtobufUtil.allocSecondary(_currBuffer);
    }

    protected void _complete() throws JacksonException
    {
        _complete = true;
        final int start = _currStart;
        final int currLen = _currPtr - start;
        _currPtr = start;

        ByteAccumulator acc = _buffered;
        try {
            if (acc == null) {
                if (currLen > 0) {
                    _output.write(_currBuffer, start, currLen);
                    _currStart = 0;
                    _currPtr = 0;
                }
            } else {
                acc = acc.finish(_output, _currBuffer, start, currLen);
                while (acc != null) {
                    acc = acc.finish(_output, _currBuffer);
                }
                _buffered = null;
            }
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    /*
    /**********************************************************************
    /* Helper methods, error reporting
    /**********************************************************************
     */

    protected void _reportWrongWireType(String typeStr) throws JacksonException {
        if (_currField == UNKNOWN_FIELD) {
            return;
        }
        _reportErrorF("Can not write `string` value for '%s' (type %s)",
                _currField.name, _currField.type);
    }

    private void _reportErrorF(String format, Object... args) throws JsonGenerationException {
        _reportError(String.format(format, args));
    }

    private void _throwIllegalSurrogate(int code)
    {
        if (code > 0x10FFFF) { // over max?
            throw new IllegalArgumentException("Illegal character point (0x"+Integer.toHexString(code)+") to output; max is 0x10FFFF as per RFC 4627");
        }
        if (code >= SURR1_FIRST) {
            if (code <= SURR1_LAST) { // Unmatched first part (closing without second part?)
                throw new IllegalArgumentException("Unmatched first part of surrogate pair (0x"+Integer.toHexString(code)+")");
            }
            throw new IllegalArgumentException("Unmatched second part of surrogate pair (0x"+Integer.toHexString(code)+")");
        }
        // should we ever get this?
        throw new IllegalArgumentException("Illegal character point (0x"+Integer.toHexString(code)+") to output");
    }
}
