package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.core.util.TextBuffer;
import com.fasterxml.jackson.core.util.VersionUtil;
import com.fasterxml.jackson.dataformat.protobuf.schema.*;

public class ProtobufParser extends ParserMinimalBase
{
    // State constants

    // State right after parser created; may start root Object
    private final static int STATE_INITIAL = 0;

    // State in which we expect another root-object entry key
    private final static int STATE_ROOT_KEY = 1;

    // State after STATE_ROOT_KEY, when we are about to get a value
    // (scalar or structured)
    private final static int STATE_ROOT_VALUE = 2;

    // Similar to root-key state, but for nested messages
    private final static int STATE_NESTED_KEY = 3;
    
    private final static int STATE_NESTED_VALUE = 4;

    // State in which an unpacked array is starting
    private final static int STATE_ARRAY_START = 5;

    private final static int STATE_ARRAY_START_PACKED = 6;

    // first array of unpacked array
    private final static int STATE_ARRAY_VALUE_FIRST = 7;

    // other values of an unpacked array
    private final static int STATE_ARRAY_VALUE_OTHER = 8;

    private final static int STATE_ARRAY_VALUE_PACKED = 9;

    private final static int STATE_ARRAY_END = 10;

    // state in which the final END_OBJECT is to be returned
    private final static int STATE_MESSAGE_END = 11;
    
    // State after either reaching end-of-input, or getting explicitly closed
    private final static int STATE_CLOSED = 12;

    private final static int[] UTF8_UNIT_CODES = ProtobufUtil.sUtf8UnitLengths;
    
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    
    /**
     * Codec used for data binding when (if) requested.
     */
    protected ObjectCodec _objectCodec;

    protected ProtobufSchema _schema;
    
    /*
    /**********************************************************
    /* Generic I/O state
    /**********************************************************
     */

    /**
     * I/O context for this reader. It handles buffer allocation
     * for the reader.
     */
    final protected IOContext _ioContext;

    /**
     * Flag that indicates whether parser is closed or not. Gets
     * set when parser is either closed by explicit call
     * ({@link #close}) or when end-of-input is reached.
     */
    protected boolean _closed;

    /*
    /**********************************************************
    /* Current input data
    /**********************************************************
     */

    // Note: type of actual buffer depends on sub-class, can't include

    /**
     * Pointer to next available character in buffer
     */
    protected int _inputPtr = 0;

    /**
     * Index of character after last available one in the buffer.
     */
    protected int _inputEnd = 0;

    /*
    /**********************************************************
    /* Current input location information
    /**********************************************************
     */

    /**
     * Number of characters/bytes that were contained in previous blocks
     * (blocks that were already processed prior to the current buffer).
     */
    protected long _currInputProcessed = 0L;

    /*
    /**********************************************************
    /* Information about starting location of event
    /* Reader is pointing to; updated on-demand
    /**********************************************************
     */

    // // // Location info at point when current token was started

    /**
     * Total number of bytes/characters read before start of current token.
     * For big (gigabyte-sized) sizes are possible, needs to be long,
     * unlike pointers and sizes related to in-memory buffers.
     */
    protected long _tokenInputTotal = 0; 

    /**
     * Input row on which current token starts, 1-based
     */
    protected int _tokenInputRow = 1;

    /**
     * Column on input row that current token starts; 0-based (although
     * in the end it'll be converted to 1-based)
     */
    protected int _tokenInputCol = 0;
    
    /*
    /**********************************************************
    /* Parsing state
    /**********************************************************
     */

    /**
     * Information about parser context, context in which
     * the next token is to be parsed (root, array, object).
     */
    protected ProtobufReadContext _parsingContext;

    /**
     * Buffer that contains contents of String values, including
     * field names if necessary (name split across boundary,
     * contains escape sequence, or access needed to char array)
     */
    protected final TextBuffer _textBuffer;

    /**
     * Temporary buffer that is needed if field name is accessed
     * using {@link #getTextCharacters} method (instead of String
     * returning alternatives)
     */
    protected char[] _nameCopyBuffer = null;

    /**
     * Flag set to indicate whether the field name is available
     * from the name copy buffer or not (in addition to its String
     * representation  being available via read context)
     */
    protected boolean _nameCopied = false;
    
    /**
     * ByteArrayBuilder is needed if 'getBinaryValue' is called. If so,
     * we better reuse it for remainder of content.
     */
    protected ByteArrayBuilder _byteArrayBuilder = null;

    /**
     * We will hold on to decoded binary data, for duration of
     * current event, so that multiple calls to
     * {@link #getBinaryValue} will not need to decode data more
     * than once.
     */
    protected byte[] _binaryValue;

    /*
    /**********************************************************
    /* Input source config, state (from ex StreamBasedParserBase)
    /**********************************************************
     */

    /**
     * Input stream that can be used for reading more content, if one
     * in use. May be null, if input comes just as a full buffer,
     * or if the stream has been closed.
     */
    protected InputStream _inputStream;

    /**
     * Current buffer from which data is read; generally data is read into
     * buffer from input source, but in some cases pre-loaded buffer
     * is handed to the parser.
     */
    protected byte[] _inputBuffer;

    /**
     * Flag that indicates whether the input buffer is recycable (and
     * needs to be returned to recycler once we are done) or not.
     *<p>
     * If it is not, it also means that parser can NOT modify underlying
     * buffer.
     */
    protected boolean _bufferRecyclable;

    /*
    /**********************************************************
    /* Additional parsing state
    /**********************************************************
     */

    /**
     * The innermost Object type ("message" in proto lingo) we are handling.
     */
    protected ProtobufMessage _currentMessage;

    protected ProtobufField _currentField;
    
    /**
     * Flag that indicates that the current token has not yet
     * been fully processed, and needs to be finished for
     * some access (or skipped to obtain the next token)
     */
    protected boolean _tokenIncomplete = false;

    /**
     * Current state of the parser.
     */
    protected int _state = STATE_INITIAL;

    protected int _nextTag;

    /**
     * Length of the value that parser points to, for scalar values that use length
     * prefixes (Strings, binary data).
     */
    protected int _decodedLength;

    protected int _currentEndOffset = Integer.MAX_VALUE;
    
    /*
    /**********************************************************
    /* Numeric conversions
    /**********************************************************
     */

    /**
     * Bitfield that indicates which numeric representations
     * have been calculated for the current type
     */
    protected int _numTypesValid = 0;

    // First primitives

    protected int _numberInt;
    protected float _numberFloat;

    protected long _numberLong;
    protected double _numberDouble;

    // And then object types

    protected BigInteger _numberBigInt;
    protected BigDecimal _numberBigDecimal;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public ProtobufParser(IOContext ctxt, int parserFeatures,
            ObjectCodec codec,
            InputStream in, byte[] inputBuffer, int start, int end,
            boolean bufferRecyclable)
    {
        super(parserFeatures);
        _ioContext = ctxt;
        _objectCodec = codec;

        _inputStream = in;
        _inputBuffer = inputBuffer;
        _inputPtr = start;
        _inputEnd = end;
        _bufferRecyclable = bufferRecyclable;
        _textBuffer = ctxt.constructTextBuffer();
        _parsingContext = ProtobufReadContext.createRootContext();

        _tokenInputRow = -1;
        _tokenInputCol = -1;
    }

    public void setSchema(ProtobufSchema schema)
    {
        if (_schema == schema) {
            return;
        }
        if (_state != STATE_INITIAL) {
            throw new IllegalStateException("Can not change Schema after parsing has started");
        }
        _schema = schema;
        // start with temporary root...
//        _currentContext = _rootContext = ProtobufWriteContext.createRootContext(this, schema);
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }

    /*                                                                                       
    /**********************************************************                              
    /* Versioned                                                                             
    /**********************************************************                              
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************
    /* Abstract impls
    /**********************************************************
     */

    @Override
    public int releaseBuffered(OutputStream out) throws IOException
    {
        int count = _inputEnd - _inputPtr;
        if (count < 1) {
            return 0;
        }
        // let's just advance ptr to end
        int origPtr = _inputPtr;
        out.write(_inputBuffer, origPtr, count);
        return count;
    }
    
    @Override
    public Object getInputSource() {
        return _inputStream;
    }

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public JsonLocation getTokenLocation()
    {
        // token location is correctly managed...
        return new JsonLocation(_ioContext.getSourceReference(),
                _tokenInputTotal, // bytes
                -1, -1, (int) _tokenInputTotal); // char offset, line, column
    }   

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override
    public JsonLocation getCurrentLocation()
    {
        final long offset = _currInputProcessed + _inputPtr;
        return new JsonLocation(_ioContext.getSourceReference(),
                offset, // bytes
                -1, -1, (int) offset); // char offset, line, column
    }

    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override
    public String getCurrentName() throws IOException
    {
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            ProtobufReadContext parent = _parsingContext.getParent();
            return parent.getCurrentName();
        }
        return _parsingContext.getCurrentName();
    }

    @Override
    public void overrideCurrentName(String name)
    {
        // Simple, but need to look for START_OBJECT/ARRAY's "off-by-one" thing:
        ProtobufReadContext ctxt = _parsingContext;
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            ctxt = ctxt.getParent();
        }
        ctxt.setCurrentName(name);
    }
    
    @Override
    public void close() throws IOException
    {
        _state = STATE_CLOSED;
        if (!_closed) {
            _closed = true;
            try {
                _closeInput();
            } finally {
                // as per [JACKSON-324], do in finally block
                // Also, internal buffer(s) can now be released as well
                _releaseBuffers();
            }
        }
    }

    @Override
    public boolean isClosed() { return _closed; }

    @Override
    public ProtobufReadContext getParsingContext() {
        return _parsingContext;
    }

    /*
    /**********************************************************
    /* Overridden methods
    /**********************************************************
     */

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return (schema instanceof ProtobufSchema);
    }

    @Override public ProtobufSchema getSchema() {
        return _schema;
    }

    @Override
    public void setSchema(FormatSchema schema)
    {
        if (!(schema instanceof ProtobufSchema)) {
            throw new IllegalArgumentException("Can not use FormatSchema of type "
                    +schema.getClass().getName());
        }
        setSchema((ProtobufSchema) schema);
    }
    
    @Override
    public boolean hasTextCharacters()
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            return _textBuffer.hasTextAsCharacters();
        }
        if (_currToken == JsonToken.FIELD_NAME) {
            return _nameCopied;
        }
        return false;
    }

    protected void _releaseBuffers() throws IOException
    {
         if (_bufferRecyclable) {
             byte[] buf = _inputBuffer;
             if (buf != null) {
                 _inputBuffer = null;
                 _ioContext.releaseReadIOBuffer(buf);
             }
         }
         _textBuffer.releaseBuffers();
         char[] buf = _nameCopyBuffer;
         if (buf != null) {
             _nameCopyBuffer = null;
             _ioContext.releaseNameCopyBuffer(buf);
         }
    }

    /*
    /**********************************************************
    /* JsonParser impl
    /**********************************************************
     */

    /*
    @Override
    public JsonToken nextToken() throws IOException
    {
        JsonToken t = nextTokenX();
        if (t == JsonToken.FIELD_NAME) {
            System.out.print("Field name: "+getCurrentName());
        } else if (t == JsonToken.VALUE_NUMBER_INT) {
            System.out.print("Int: "+getIntValue());
        } else if (t == JsonToken.VALUE_STRING) {
            System.out.print("String: '"+getText()+"'");
        } else {
            System.out.print("Next: "+t);
        }
        System.out.println(" (state now: "+_state+", ptr "+_inputPtr+")");
        return t;
    }

    public JsonToken nextTokenX() throws IOException {
    */

    @Override
    public JsonToken nextToken() throws IOException
    {
        _numTypesValid = NR_UNKNOWN;
        // For longer tokens (text, binary), we'll only read when requested
        if (_tokenIncomplete) {
            _tokenIncomplete = false;
            _skipBytes(_decodedLength);
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _binaryValue = null;

        switch (_state) {
        case STATE_INITIAL:
            if (_schema == null) {
                _reportError("No Schema has been assigned: can not decode content");
            }
            _currentMessage = _schema.getRootType();
            _currentField = _currentMessage.firstField();
            _state = STATE_ROOT_KEY;
            _parsingContext.setMessageType(_currentMessage);            
            return (_currToken = JsonToken.START_OBJECT);

        case STATE_ROOT_KEY:
            // end-of-input?
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    close();
                    return (_currToken = JsonToken.END_OBJECT);
                }
            }
            return _handleRootKey(_decodeVInt());
        case STATE_ROOT_VALUE:
            {
                JsonToken t = _readNextValue(_currentField.type, STATE_ROOT_KEY);
                _currToken = t;
                return t;
            }
        case STATE_NESTED_KEY:
            if (_checkEnd()) {
                return (_currToken = JsonToken.END_OBJECT);
            }
            return _handleNestedKey(_decodeVInt());

        case STATE_ARRAY_START:
            _parsingContext = _parsingContext.createChildArrayContext(_currentField);            
            _state = STATE_ARRAY_VALUE_FIRST;
            return (_currToken = JsonToken.START_ARRAY);

        case STATE_ARRAY_START_PACKED:

            int len = _decodeLength();
            int newEnd = _inputPtr + len;

            // First: validate that we do not extend past end offset of enclosing message
            if (!_parsingContext.inRoot()) {
                if (newEnd > _currentEndOffset) {
                    _reportErrorF("Packed array for field '%s' (of type %s) extends past end of enclosing message: %d > %d (length: %d)",
                            _currentField.name, _currentMessage.getName(), newEnd, _currentEndOffset, len);
                }
            }
            _currentEndOffset = newEnd; 
            _parsingContext = _parsingContext.createChildArrayContext(_currentField, newEnd);            
            _state = STATE_ARRAY_VALUE_PACKED;
            return (_currToken = JsonToken.START_ARRAY);

        case STATE_ARRAY_VALUE_FIRST: // unpacked
            {
                // false -> not root... or should we check?
                JsonToken t = _readNextValue(_currentField.type, STATE_ARRAY_VALUE_OTHER);
                _currToken = t;
                return t;
            }

        case STATE_ARRAY_VALUE_OTHER: // unpacked
            if (_checkEnd()) { // need to check constraints set by surrounding Message (object)
                return (_currToken = JsonToken.END_ARRAY);
            }
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    ProtobufReadContext parent = _parsingContext.getParent();
                    // Ok to end if and only if root value
                    if (!parent.inRoot()) {
                        _reportInvalidEOF();
                    }
                    _parsingContext = parent;
                    _currentField = parent.getField();
                    _state = STATE_MESSAGE_END;
                    return (_currToken = JsonToken.END_ARRAY);
                }
            }
            {
                int tag = _decodeVInt();
                // expected case: another value in same array
                if (_currentField.id == (tag >> 3)) {
                    JsonToken t = _readNextValue(_currentField.type, STATE_ARRAY_VALUE_OTHER);
                    _currToken = t;
                    // remain in same state
                    return t;
                }
                // otherwise, different field, need to end this array
                _nextTag = tag;
                ProtobufReadContext parent = _parsingContext.getParent();
                _parsingContext = parent;
                _currentField = parent.getField();
                _state = STATE_ARRAY_END;
                return (_currToken = JsonToken.END_ARRAY);
            }

        case STATE_ARRAY_VALUE_PACKED:
            if (_checkEnd()) { // need to check constraints of this array itself
                return (_currToken = JsonToken.END_ARRAY);
            }
            {
                JsonToken t = _readNextValue(_currentField.type, STATE_ARRAY_VALUE_PACKED);
                _currToken = t;
                // remain in same state
                return t;
            }
            
        case STATE_ARRAY_END: // only used with unpacked and with "_nextTag"

            // We have returned END_ARRAY; now back to similar to STATE_ROOT_KEY / STATE_NESTED_KEY
            
            // First, similar to STATE_ROOT_KEY:
            if (_parsingContext.inRoot()) {
                return _handleRootKey(_nextTag);
            }
            return _handleNestedKey(_nextTag);

        case STATE_NESTED_VALUE:
            {
                JsonToken t = _readNextValue(_currentField.type, STATE_NESTED_KEY);
                _currToken = t;
                return t;
            }

        case STATE_MESSAGE_END: // occurs if we end with array
            close(); // sets state to STATE_CLOSED
            return (_currToken = JsonToken.END_OBJECT);

        case STATE_CLOSED:
            return null;

        default:
        }
        VersionUtil.throwInternal();
        return null;
    }

    private boolean _checkEnd() throws IOException
    {
        if (_inputPtr < _currentEndOffset) {
            return false;
        }
        if (_inputPtr > _currentEndOffset) {
            _reportErrorF("Decoding: current inputPtr (%d) exceeds end offset (%d) (for message of type %s): corrupt content?",
                    _inputPtr, _currentEndOffset, _currentMessage.getName());
        }
        ProtobufReadContext parentCtxt = _parsingContext.getParent();
        _parsingContext = parentCtxt;
        _currentMessage = parentCtxt.getMessageType();
        _currentEndOffset = parentCtxt.getEndOffset();
        _currentField = parentCtxt.getField();
        if (_parsingContext.inRoot()) {
            _state =  STATE_ROOT_KEY;
        } else if (_parsingContext.inArray()) {
            _state = _currentField.packed ? STATE_ARRAY_VALUE_PACKED : STATE_ARRAY_VALUE_OTHER;
        } else {
            _state = STATE_NESTED_KEY;
        }
        return true;
    }

    private JsonToken _handleRootKey(int tag) throws IOException
    {
        int wireType = (tag & 0x7);
        int id = (tag >> 3);

        ProtobufField f;
        if (_currentField != null) {
            if ((f = _currentField.nextOrThisIf(id)) == null) {
                if ((f = _currentMessage.field(id)) == null) {
                    return _skipUnknownField(id, wireType);
                }
            }
        } else {
            if ((f = _currentMessage.field(id)) == null) {
                return _skipUnknownField(id, wireType);
            }
        }
        _parsingContext.setCurrentName(f.name);
        // otherwise quickly validate compatibility
        if (!f.isValidFor(wireType)) {
            _reportIncompatibleType(f, wireType);
        }
        // array?
        if (f.repeated) {
            if (f.packed) {
                _state = STATE_ARRAY_START_PACKED;
            } else {
                _state = STATE_ARRAY_START;
            }                    
        } else {
            _state = STATE_ROOT_VALUE;
        }
        _currentField = f;
        return (_currToken = JsonToken.FIELD_NAME);
    }

    private JsonToken _handleNestedKey(int tag) throws IOException
    {
        int wireType = (tag & 0x7);
        int id = (tag >> 3);

        ProtobufField f;
        if (_currentField != null) {
            if ((f = _currentField.nextOrThisIf(id)) == null) {
                if ((f = _currentMessage.field(id)) == null) {
                    return _skipUnknownField(id, wireType);
                }
            }
        } else {
            if ((f = _currentMessage.field(id)) == null) {
                return _skipUnknownField(id, wireType);
            }
        }
        
        if ((_currentField == null) || (f = _currentField.nextOrThisIf(id)) == null) {
            f = _currentMessage.field(id);
        }
        // Note: may be null; if so, value needs to be skipped
        if (f == null) {
            return _skipUnknownField(id, wireType);
        }
        _parsingContext.setCurrentName(f.name);
        if (!f.isValidFor(wireType)) {
            _reportIncompatibleType(f, wireType);
        }

        // array?
        if (f.repeated) {
            if (f.packed) {
                _state = STATE_ARRAY_START_PACKED;
            } else {
                _state = STATE_ARRAY_START;
            }                    
        } else {
            _state = STATE_NESTED_VALUE;
        }
        _currentField = f;
        return (_currToken = JsonToken.FIELD_NAME);
    }

    private JsonToken _readNextValue(FieldType t, int nextState) throws IOException
    {
        JsonToken type;

        switch (_currentField.type) {
        case DOUBLE:
            _numberDouble = Double.longBitsToDouble(_decode64Bits());
            _numTypesValid = NR_DOUBLE;
            type = JsonToken.VALUE_NUMBER_FLOAT;
            break;
        case FLOAT:
            _numberFloat = Float.intBitsToFloat(_decode32Bits());
            _numTypesValid = NR_FLOAT;
            type =  JsonToken.VALUE_NUMBER_FLOAT;
            break;
        case VINT32_Z:
            _numberInt = ProtobufUtil.zigzagDecode(_decodeVInt());
            _numTypesValid = NR_INT;
            type =  JsonToken.VALUE_NUMBER_INT;
            break;
        case VINT64_Z:
            _numberLong = ProtobufUtil.zigzagDecode(_decodeVLong());
            _numTypesValid = NR_LONG;
            type =  JsonToken.VALUE_NUMBER_INT;
            break;
        case VINT32_STD:
            _numberInt = _decodeVInt();
            _numTypesValid = NR_INT;
            type =  JsonToken.VALUE_NUMBER_INT;
            break;
        case VINT64_STD:
            _numberLong = _decodeVLong();
            _numTypesValid = NR_LONG;
            type =  JsonToken.VALUE_NUMBER_INT;
            break;
        case FIXINT32:
            _numberInt = _decode32Bits();
            _numTypesValid = NR_INT;
            type =  JsonToken.VALUE_NUMBER_INT;
            break;
        case FIXINT64:
            _numberLong = _decode64Bits();
            _numTypesValid = NR_LONG;
            type =  JsonToken.VALUE_NUMBER_INT;
            break;
        case BOOLEAN:
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            {
                int i = _inputBuffer[_inputPtr++];
                // let's be strict here. But keep in mind that it's zigzag encoded so
                // we shall value values of '1' and '2'
                if (i == 1) {
                    type = JsonToken.VALUE_TRUE; 
                } else if (i == 0) {
                    type = JsonToken.VALUE_FALSE; 
                } else {
                    _reportError(String.format("Invalid byte value for bool field %s: 0x%2x; should be either 0x0 or 0x1",
                            _currentField.name, i));
                    type = null;
                }
            }
            break;

        case STRING:
            {
                int len = _decodeLength();
                _decodedLength = len;            
                if (len == 0) {
                    _textBuffer.resetWithEmpty();
                } else {
                    _tokenIncomplete = true;
                }
            }
            type = JsonToken.VALUE_STRING;
            break;

        case BYTES:
            {
                int len = _decodeLength();
                _decodedLength = len;            
                if (len == 0) {
                    _binaryValue = ByteArrayBuilder.NO_BYTES;
                } else {
                    _tokenIncomplete = true;
                }
            }
            type = JsonToken.VALUE_EMBEDDED_OBJECT;
            break;

        case ENUM:
            // 12-Feb-2015, tatu: Can expose as index (int) or name, but internally encoded as VInt.
            //    So for now, expose as is; may add a feature to choose later on.
            // But! May or may not be directly mapped; may need to translate
            {
                int ix = _decodeLength();
                if (_currentField.isStdEnum) {
                    _numberInt = ix;
                    _numTypesValid = NR_INT;
                    type =  JsonToken.VALUE_NUMBER_INT;
                } else {
                    // Could translate to better id, but for now let databind
                    // handle that part
                    String enumStr = _currentField.findEnumByIndex(ix);
                    if (enumStr == null) {
                        _reportErrorF("Unknown id %d (for enum field %s)", ix, _currentField.name);
                    }
                    type = JsonToken.VALUE_STRING;
                    _textBuffer.resetWithString(enumStr);
                }
            }
            break;

        case MESSAGE:
            {
                ProtobufMessage msg = _currentField.getMessageType();
                _currentMessage = msg;
                int len = _decodeLength();
                int newEnd = _inputPtr + len;

                // First: validate that we do not extend past end offset of enclosing message
                if (newEnd > _currentEndOffset) {
                    _reportErrorF("Message for field '%s' (of type %s) extends past end of enclosing message: %d > %d (length: %d)",
                            _currentField.name, msg.getName(), newEnd, _currentEndOffset, len);
                }
                _currentEndOffset = newEnd; 
                _state = STATE_NESTED_KEY;
                _parsingContext = _parsingContext.createChildObjectContext(msg, _currentField, newEnd);            
                _currentField = msg.firstField();
            }
            return JsonToken.START_OBJECT;

        default:
            throw new UnsupportedOperationException("Type "+_currentField.type+" not yet supported");
        }
        _state = nextState;
        return type;
    }

    private JsonToken _skipUnknownField(int tag, int wireType) throws IOException
    {
        // First: is this even allowed?
        if (!isEnabled(JsonParser.Feature.IGNORE_UNDEFINED)) {
            _reportErrorF("Undefined property (id %d, wire type %d) for message type %s: not allowed to ignore, as `JsonParser.Feature.IGNORE_UNDEFINED` disabled",
                    tag, wireType, _currentMessage.getName());
        }
        while (true) {
            _skipUnknownValue(wireType);
            // 05-Dec-2017, tatu: as per [#126] seems like we need to check this not just for
            //    STATE_NESTED_KEY but for arrays too at least?
            if (_checkEnd()) {
                return (_currToken = JsonToken.END_OBJECT);
            }
            if (_state == STATE_NESTED_KEY) {
                if (_inputPtr >= _inputEnd) {
                    loadMoreGuaranteed();
                }
            } else if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    close();
                    return (_currToken = JsonToken.END_OBJECT);
                }
            }
            tag = _decodeVInt();
            
            wireType = (tag & 0x7);
            // Note: may be null; if so, value needs to be skipped
            _currentField = _currentMessage.field(tag >> 3);
            if (_currentField == null) {
                continue;
            }
            _parsingContext.setCurrentName(_currentField.name);
            _state = STATE_ROOT_VALUE;
            // otherwise quickly validate compatibility
            if (!_currentField.isValidFor(wireType)) {
                _reportIncompatibleType(_currentField, wireType);
            }
            return (_currToken = JsonToken.FIELD_NAME);
        }
    }
        
    private void _skipUnknownValue(int wireType) throws IOException
    {
        switch (wireType) {
        case WireType.VINT:
            _skipVInt();
            break;
        case WireType.FIXED_32BIT:
            _skipBytes(4);
            break;
        case WireType.FIXED_64BIT:
            _skipBytes(8);
            break;
        case WireType.LENGTH_PREFIXED:
            int len = _decodeLength();
            _skipBytes(len);
            break;
        default:
            _reportError(String.format("Unrecognized wire type 0x%x for unknown field within message of type %s)",
                    wireType, _currentMessage.getName()));
        }
    }

    /*
    /**********************************************************
    /* Public API, traversal, nextXxxValue/nextFieldName
    /**********************************************************
     */

    @Override
    public boolean nextFieldName(SerializableString sstr) throws IOException
    {
        if (_state == STATE_ROOT_KEY) {
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    close();
                    _currToken = JsonToken.END_OBJECT;
                    return false;
                }
            }
            int tag = _decodeVInt();
            // inlined _handleRootKey()

            int wireType = (tag & 0x7);
            int id = (tag >> 3);

            ProtobufField f = _findField(id);
            if (f == null) {
                _skipUnknownField(id, wireType);
                // may or may not match, but let caller figure it out
                return false;
            }
            String name = _currentField.name;
            _parsingContext.setCurrentName(name);
            if (!_currentField.isValidFor(wireType)) {
                _reportIncompatibleType(_currentField, wireType);
            }

            // array?
            if (_currentField.repeated) {
                if (_currentField.packed) {
                    _state = STATE_ARRAY_START_PACKED;
                } else {
                    _state = STATE_ARRAY_START;
                }                    
            } else {
                _state = STATE_ROOT_VALUE;
            }
            _currToken = JsonToken.FIELD_NAME;
            return name.equals(sstr.getValue());
        }
        if (_state == STATE_NESTED_KEY) {
            if (_checkEnd()) {
                _currToken = JsonToken.END_OBJECT;
                return false;
            }
            int tag = _decodeVInt();
            // inlined '_handleNestedKey()'

            int wireType = (tag & 0x7);
            int id = (tag >> 3);

            ProtobufField f = _findField(id);
            if (f == null) {
                _skipUnknownField(id, wireType);
                // may or may not match, but let caller figure it out
                return false;
            }
            final String name = _currentField.name;
            _parsingContext.setCurrentName(name);
            if (!_currentField.isValidFor(wireType)) {
                _reportIncompatibleType(_currentField, wireType);
            }

            // array?
            if (_currentField.repeated) {
                if (_currentField.packed) {
                    _state = STATE_ARRAY_START_PACKED;
                } else {
                    _state = STATE_ARRAY_START;
                }                    
            } else {
                _state = STATE_NESTED_VALUE;
            }
            _currToken = JsonToken.FIELD_NAME;
            return name.equals(sstr.getValue());
        }
        return (nextToken() == JsonToken.FIELD_NAME) && sstr.getValue().equals(getCurrentName());
    }

    @Override
    public String nextFieldName() throws IOException
    {
        if (_state == STATE_ROOT_KEY) {
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    close();
                    _currToken = JsonToken.END_OBJECT;
                    return null;
                }
            }
            int tag = _decodeVInt();
            // inlined _handleRootKey()

            int wireType = (tag & 0x7);
            int id = (tag >> 3);

            ProtobufField f = _findField(id);
            if (f == null) {
                if (_skipUnknownField(id, wireType) != JsonToken.FIELD_NAME) {
                    return null;
                }
                // sub-optimal as skip method already set it, but:
            }
            String name = _currentField.name;
            _parsingContext.setCurrentName(name);
            if (!_currentField.isValidFor(wireType)) {
                _reportIncompatibleType(_currentField, wireType);
            }

            // array?
            if (_currentField.repeated) {
                if (_currentField.packed) {
                    _state = STATE_ARRAY_START_PACKED;
                } else {
                    _state = STATE_ARRAY_START;
                }                    
            } else {
                _state = STATE_ROOT_VALUE;
            }
            _currToken = JsonToken.FIELD_NAME;
            return name;
        }
        if (_state == STATE_NESTED_KEY) {
            if (_checkEnd()) {
                _currToken = JsonToken.END_OBJECT;
                return null;
            }
            int tag = _decodeVInt();
            // inlined '_handleNestedKey()'

            int wireType = (tag & 0x7);
            int id = (tag >> 3);

            ProtobufField f = _findField(id);
            if (f == null) {
                if (_skipUnknownField(id, wireType) != JsonToken.FIELD_NAME) {
                    return null;
                }
                // sub-optimal as skip method already set it, but:
            }
            final String name = _currentField.name;
            _parsingContext.setCurrentName(name);
            if (!_currentField.isValidFor(wireType)) {
                _reportIncompatibleType(_currentField, wireType);
            }

            // array?
            if (_currentField.repeated) {
                if (_currentField.packed) {
                    _state = STATE_ARRAY_START_PACKED;
                } else {
                    _state = STATE_ARRAY_START;
                }                    
            } else {
                _state = STATE_NESTED_VALUE;
            }
            _currToken = JsonToken.FIELD_NAME;
            return name;
        }
        return (nextToken() == JsonToken.FIELD_NAME) ? getCurrentName() : null;
    }

    @Override
    public String nextTextValue() throws IOException
    {
        // Copied from `nexdtToken()`, as appropriate
        _numTypesValid = NR_UNKNOWN;
        if (_tokenIncomplete) {
            _tokenIncomplete = false;
            _skipBytes(_decodedLength);
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        _binaryValue = null;

        switch (_state) {
        case STATE_ROOT_VALUE:
            {
                JsonToken t = _readNextValue(_currentField.type, STATE_ROOT_KEY);
                _currToken = t;
                return (t == JsonToken.VALUE_STRING) ? getText() : null;
            }
        case STATE_NESTED_VALUE:
            {
                JsonToken t = _readNextValue(_currentField.type, STATE_NESTED_KEY);
                _currToken = t;
                return (t == JsonToken.VALUE_STRING) ? getText() : null;
            }
        case STATE_ARRAY_VALUE_FIRST: // unpacked
            if (_currentField.type == FieldType.STRING) {
                _state = STATE_ARRAY_VALUE_OTHER;
                break;
            }
            _currToken = _readNextValue(_currentField.type, STATE_ARRAY_VALUE_OTHER);
            return null;
        case STATE_ARRAY_VALUE_OTHER: // unpacked
            if (_checkEnd()) { // need to check constraints set by surrounding Message (object)
                _currToken = JsonToken.END_ARRAY;
                return null;
            }
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    ProtobufReadContext parent = _parsingContext.getParent();
                    // Ok to end if and only if root value
                    if (!parent.inRoot()) {
                        _reportInvalidEOF();
                    }
                    _parsingContext = parent;
                    _currentField = parent.getField();
                    _state = STATE_MESSAGE_END;
                    _currToken = JsonToken.END_ARRAY;
                    return null;
                }
            }
            {
                int tag = _decodeVInt();
                // expected case: another value in same array
                if (_currentField.id == (tag >> 3)) {
                    if (_currentField.type == FieldType.STRING) {
                        break;
                    }
                    _currToken = _readNextValue(_currentField.type, STATE_ARRAY_VALUE_OTHER);
                    return null;
                }
                // otherwise, different field, need to end this array
                _nextTag = tag;
                ProtobufReadContext parent = _parsingContext.getParent();
                _parsingContext = parent;
                _currentField = parent.getField();
            }
            _state = STATE_ARRAY_END;
            _currToken = JsonToken.END_ARRAY;
            return null;

        case STATE_ARRAY_VALUE_PACKED:
            if (_checkEnd()) { // need to check constraints of this array itself
                _currToken = JsonToken.END_ARRAY;
                return null;
            }
            if (_currentField.type != FieldType.STRING) {
                _currToken = _readNextValue(_currentField.type, STATE_ARRAY_VALUE_PACKED);
                return null;
            }
            break;
        default:
            return (nextToken() == JsonToken.VALUE_STRING) ? getText() : null;
        }

        // At this point we know we have text token so:
        final int len = _decodeLength();
        _decodedLength = len;            
        _currToken = JsonToken.VALUE_STRING;
        if (len == 0) {
            _textBuffer.resetWithEmpty();
            return "";
        }
        if ((_inputPtr + len) <= _inputEnd) {
            return _finishShortText(len);
        }
        _finishToken();
        return _textBuffer.contentsAsString();
    }

    private final ProtobufField _findField(int id)
    {
        ProtobufField f;
        if ((_currentField == null) || (f = _currentField.nextOrThisIf(id)) == null) {
            f = _currentMessage.field(id);
        }
        _currentField = f;
        return f;
    }

    /*
    /**********************************************************
    /* Public API, access to token information, text
    /**********************************************************
     */

    /**
     * Method for accessing textual representation of the current event;
     * if no current event (before first call to {@link #nextToken}, or
     * after encountering end-of-input), returns null.
     * Method can be called for any event.
     */
    @Override    
    public String getText() throws IOException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                // inlined '_finishToken()`
                final int len = _decodedLength;
                if ((_inputPtr + len) <= _inputEnd) {
                    _tokenIncomplete = false;
                    return _finishShortText(len);
                }
                _finishToken();
            }
            return _textBuffer.contentsAsString();
        }
        // incompleteness ok for binary; won't result in usable text anyway
        JsonToken t = _currToken;
        if (t == null) { // null only before/after document
            return null;
        }
        if (t == JsonToken.FIELD_NAME) {
            return _parsingContext.getCurrentName();
        }
        if (t.isNumeric()) {
            return getNumberValue().toString();
        }
        return _currToken.asString();
    }

    @Override
    public char[] getTextCharacters() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _finishToken();
            }
            switch (_currToken) {                
            case VALUE_STRING:
                return _textBuffer.getTextBuffer();
            case FIELD_NAME:
                return _parsingContext.getCurrentName().toCharArray();
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return getNumberValue().toString().toCharArray();
                
            default:
                return _currToken.asCharArray();
            }
        }
        return null;
    }

    @Override    
    public int getTextLength() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _finishToken();
            }
            switch (_currToken) {
            case VALUE_STRING:
                return _textBuffer.size();                
            case FIELD_NAME:
                return _parsingContext.getCurrentName().length();
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return getNumberValue().toString().length();
                
            default:
                return _currToken.asCharArray().length;
            }
        }
        return 0;
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }

    @Override
    public String getValueAsString() throws IOException
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                // inlined '_finishToken()`
                final int len = _decodedLength;
                if ((_inputPtr + len) <= _inputEnd) {
                    _tokenIncomplete = false;
                    return _finishShortText(len);
                }
                _finishToken();
            }
            return _textBuffer.contentsAsString();
        }
        if (_currToken == null || _currToken == JsonToken.VALUE_NULL || !_currToken.isScalarValue()) {
            return null;
        }
        return getText();
    }

    @Override
    public String getValueAsString(String defaultValue) throws IOException
    {
        if (_currToken != JsonToken.VALUE_STRING) {
            if (_currToken == null || _currToken == JsonToken.VALUE_NULL || !_currToken.isScalarValue()) {
                return defaultValue;
            }
        }
        return getText();
    }

    @Override // since 2.8
    public int getText(Writer writer) throws IOException
    {
        JsonToken t = _currToken;
        if (t == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                // inlined '_finishToken()`
                final int len = _decodedLength;
                if ((_inputPtr + len) <= _inputEnd) {
                    _tokenIncomplete = false;
                    _finishShortText(len);
                } else {
                    _finishToken();
                }
            }
            return _textBuffer.contentsToWriter(writer);
        }
        if (t == JsonToken.FIELD_NAME) {
            String n = _parsingContext.getCurrentName();
            writer.write(n);
            return n.length();
        }
        if (t != null) {
            if (t.isNumeric()) {
                return _textBuffer.contentsToWriter(writer);
            }
            char[] ch = t.asCharArray();
            writer.write(ch);
            return ch.length;
        }
        return 0;
    }

    /*
    /**********************************************************
    /* Public API, access to token information, binary
    /**********************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            // TODO, maybe: support base64 for text?
            _reportError("Current token ("+_currToken+") not VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        return _binaryValue;
    }

    @Override
    public Object getEmbeddedObject() throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            return _binaryValue;
        }
        return null;
    }

    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out) throws IOException
    {
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT ) {
            _reportError("Current token ("+_currToken+") not VALUE_EMBEDDED_OBJECT, can not access as binary");
        }

        // !!! TBI
        return -1;
    }

    /*
    /**********************************************************
    /* Numeric accessors of public API
    /**********************************************************
     */

    @Override // since 2.9
    public boolean isNaN() {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            if ((_numTypesValid & NR_DOUBLE) != 0) {
                // 10-Mar-2017, tatu: Alas, `Double.isFinite(d)` only added in JDK 8
                double d = _numberDouble;
                return Double.isNaN(d) || Double.isInfinite(d);
            }
            if ((_numTypesValid & NR_FLOAT) != 0) {
                float f = _numberFloat;
                return Float.isNaN(f) || Float.isInfinite(f);
            }
        }
        return false;
    }

    @Override
    public Number getNumberValue() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        // Separate types for int types
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return _numberInt;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return _numberLong;
            }
            if ((_numTypesValid & NR_BIGINT) != 0) {
                return _numberBigInt;
            }
            // Shouldn't get this far but if we do
            return _numberBigDecimal;
        }

        // And then floating point types. But here optimal type
        // needs to be big decimal, to avoid losing any data?
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return _numberBigDecimal;
        }
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            return _numberDouble;
        }
        if ((_numTypesValid & NR_FLOAT) == 0) { // sanity check
            _throwInternal();
        }
        return _numberFloat;
    }
    
    @Override
    public NumberType getNumberType() throws IOException
    {
        if (_numTypesValid == NR_UNKNOWN) {
            _checkNumericValue(NR_UNKNOWN); // will also check event type
        }
        if (_currToken == JsonToken.VALUE_NUMBER_INT) {
            if ((_numTypesValid & NR_INT) != 0) {
                return NumberType.INT;
            }
            if ((_numTypesValid & NR_LONG) != 0) {
                return NumberType.LONG;
            }
            return NumberType.BIG_INTEGER;
        }
    
        /* And then floating point types. Here optimal type
         * needs to be big decimal, to avoid losing any data?
         * However... using BD is slow, so let's allow returning
         * double as type if no explicit call has been made to access
         * data as BD?
         */
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            return NumberType.BIG_DECIMAL;
        }
        if ((_numTypesValid & NR_DOUBLE) != 0) {
            return NumberType.DOUBLE;
        }
        return NumberType.FLOAT;
    }

    @Override
    public int getIntValue() throws IOException
    {
        if ((_numTypesValid & NR_INT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) { // not parsed at all
                _checkNumericValue(NR_INT); // will also check event type
            }
            if ((_numTypesValid & NR_INT) == 0) { // wasn't an int natively?
                convertNumberToInt(); // let's make it so, if possible
            }
        }
        return _numberInt;
    }
    
    @Override
    public long getLongValue() throws IOException
    {
        if ((_numTypesValid & NR_LONG) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_LONG);
            }
            if ((_numTypesValid & NR_LONG) == 0) {
                convertNumberToLong();
            }
        }
        return _numberLong;
    }
    
    @Override
    public BigInteger getBigIntegerValue() throws IOException
    {
        if ((_numTypesValid & NR_BIGINT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_BIGINT);
            }
            if ((_numTypesValid & NR_BIGINT) == 0) {
                convertNumberToBigInteger();
            }
        }
        return _numberBigInt;
    }
    
    @Override
    public float getFloatValue() throws IOException
    {
        if ((_numTypesValid & NR_FLOAT) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_FLOAT);
            }
            if ((_numTypesValid & NR_FLOAT) == 0) {
                convertNumberToFloat();
            }
        }
        // Bounds/range checks would be tricky here, so let's not bother even trying...
        /*
        if (value < -Float.MAX_VALUE || value > MAX_FLOAT_D) {
            _reportError("Numeric value ("+getText()+") out of range of Java float");
        }
        */
        return _numberFloat;
    }
    
    @Override
    public double getDoubleValue() throws IOException
    {
        if ((_numTypesValid & NR_DOUBLE) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_DOUBLE);
            }
            if ((_numTypesValid & NR_DOUBLE) == 0) {
                convertNumberToDouble();
            }
        }
        return _numberDouble;
    }
    
    @Override
    public BigDecimal getDecimalValue() throws IOException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) == 0) {
            if (_numTypesValid == NR_UNKNOWN) {
                _checkNumericValue(NR_BIGDECIMAL);
            }
            if ((_numTypesValid & NR_BIGDECIMAL) == 0) {
                convertNumberToBigDecimal();
            }
        }
        return _numberBigDecimal;
    }

    /*
    /**********************************************************
    /* Numeric conversions
    /**********************************************************
     */    

    protected void _checkNumericValue(int expType) throws IOException
    {
        // Int or float?
        if (_currToken == JsonToken.VALUE_NUMBER_INT || _currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return;
        }
        _reportError("Current token ("+_currToken+") not numeric, can not use numeric value accessors");
    }

    protected void convertNumberToInt() throws IOException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                _reportError("Numeric value ("+getText()+") out of range of int");
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0 
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                reportOverflowInt();
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_INT_D || _numberFloat > MAX_INT_D) {
                reportOverflowInt();
            }
            _numberInt = (int) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                reportOverflowInt();
            }
            _numberInt = _numberBigDecimal.intValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_INT;
    }
    
    protected void convertNumberToLong() throws IOException
    {
        if ((_numTypesValid & NR_INT) != 0) {
            _numberLong = (long) _numberInt;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_LONG.compareTo(_numberBigInt) > 0 
                    || BI_MAX_LONG.compareTo(_numberBigInt) < 0) {
                reportOverflowLong();
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                reportOverflowLong();
            }
            _numberLong = (long) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_LONG_D || _numberFloat > MAX_LONG_D) {
                reportOverflowInt();
            }
            _numberLong = (long) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0 
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                reportOverflowLong();
            }
            _numberLong = _numberBigDecimal.longValue();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_LONG;
    }
    
    protected void convertNumberToBigInteger() throws IOException
    {
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            // here it'll just get truncated, no exceptions thrown
            _numberBigInt = _numberBigDecimal.toBigInteger();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigInt = BigInteger.valueOf(_numberInt);
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberDouble).toBigInteger();
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberBigInt = BigDecimal.valueOf(_numberFloat).toBigInteger();
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGINT;
    }

    protected void convertNumberToFloat() throws IOException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberFloat = _numberBigDecimal.floatValue();
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberFloat = _numberBigInt.floatValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            _numberFloat = (float) _numberDouble;
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberFloat = (float) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberFloat = (float) _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_FLOAT;
    }

    protected void convertNumberToDouble() throws IOException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            _numberDouble = _numberBigDecimal.doubleValue();
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            _numberDouble = (double) _numberFloat;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberDouble = _numberBigInt.doubleValue();
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberDouble = (double) _numberLong;
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberDouble = (double) _numberInt;
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_DOUBLE;
    }
    
    protected void convertNumberToBigDecimal() throws IOException
    {
        // Note: this MUST start with more accurate representations, since we don't know which
        //  value is the original one (others get generated when requested)
        if ((_numTypesValid & (NR_DOUBLE | NR_FLOAT)) != 0) {
            // Let's parse from String representation, to avoid rounding errors that
            //non-decimal floating operations would incur
            _numberBigDecimal = NumberInput.parseBigDecimal(getText());
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            _numberBigDecimal = new BigDecimal(_numberBigInt);
        } else if ((_numTypesValid & NR_LONG) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberLong);
        } else if ((_numTypesValid & NR_INT) != 0) {
            _numberBigDecimal = BigDecimal.valueOf(_numberInt);
        } else {
            _throwInternal();
        }
        _numTypesValid |= NR_BIGDECIMAL;
    }

    /*
    /**********************************************************
    /* Internal methods, secondary parsing
    /**********************************************************
     */

    /**
     * Method called to finish parsing of a token so that token contents
     * are retriable
     */
    protected void _finishToken() throws IOException
    {
        _tokenIncomplete = false;

        if (_currToken == JsonToken.VALUE_STRING) {
            final int len = _decodedLength;
            if (len > (_inputEnd - _inputPtr)) {
                // or if not, could we read?
                if (len >= _inputBuffer.length) {
                    // If not enough space, need different handling
                    _finishLongText(len);
                    return;
                }
                _loadToHaveAtLeast(len);
            }
            // offline for better optimization
            _finishShortText(len);
            return;
        }
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT) {
            _binaryValue = _finishBytes(_decodedLength);
            return;
        }
        // should never happen but:
        _throwInternal();
    }

    protected byte[] _finishBytes(int len) throws IOException
    {
        byte[] b = new byte[len];
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int ptr = 0;
        while (true) {
            int toAdd = Math.min(len, _inputEnd - _inputPtr);
            System.arraycopy(_inputBuffer, _inputPtr, b, ptr, toAdd);
            _inputPtr += toAdd;
            ptr += toAdd;
            len -= toAdd;
            if (len <= 0) {
                return b;
            }
            loadMoreGuaranteed();
        }
    }

    private final String _finishShortText(int len) throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (outBuf.length < len) { // one minor complication
            outBuf = _textBuffer.expandCurrentSegment(len);
        }
        int outPtr = 0;
        int inPtr = _inputPtr;
        _inputPtr += len;
        final byte[] inputBuf = _inputBuffer;

        // Let's actually do a tight loop for ASCII first:
        final int end = inPtr + len;

        int i;
        while ((i = inputBuf[inPtr]) >= 0) {
            outBuf[outPtr++] = (char) i;
            if (++inPtr == end) {
                return _textBuffer.setCurrentAndReturn(outPtr);
            }
        }

        final int[] codes = UTF8_UNIT_CODES;
        do {
            i = inputBuf[inPtr++] & 0xFF;
            switch (codes[i]) {
            case 0:
                break;
            case 1:
                i = ((i & 0x1F) << 6) | (inputBuf[inPtr++] & 0x3F);
                break;
            case 2:
                i = ((i & 0x0F) << 12)
                   | ((inputBuf[inPtr++] & 0x3F) << 6)
                   | (inputBuf[inPtr++] & 0x3F);
                break;
            case 3:
                i = ((i & 0x07) << 18)
                 | ((inputBuf[inPtr++] & 0x3F) << 12)
                 | ((inputBuf[inPtr++] & 0x3F) << 6)
                 | (inputBuf[inPtr++] & 0x3F);
                // note: this is the codepoint value; need to split, too
                i -= 0x10000;
                outBuf[outPtr++] = (char) (0xD800 | (i >> 10));
                i = 0xDC00 | (i & 0x3FF);
                break;
            default: // invalid
                _reportError("Invalid byte "+Integer.toHexString(i)+" in Unicode text block");
            }
            outBuf[outPtr++] = (char) i;
        } while (inPtr < end);
        return _textBuffer.setCurrentAndReturn(outPtr);
    }

    private final void _finishLongText(int len) throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final int[] codes = UTF8_UNIT_CODES;
        int outEnd = outBuf.length;

        while (--len >= 0) {
            int c = _nextByte() & 0xFF;
            int code = codes[c];
            if (code == 0 && outPtr < outEnd) {
                outBuf[outPtr++] = (char) c;
                continue;
            }
            if ((len -= code) < 0) { // may need to improve error here but...
                throw _constructError("Malformed UTF-8 character at end of long (non-chunked) text segment");
            }
            
            switch (code) {
            case 0:
                break;
            case 1: // 2-byte UTF
                {
                    int d = _nextByte();
                    if ((d & 0xC0) != 0x080) {
                        _reportInvalidOther(d & 0xFF, _inputPtr);
                    }
                    c = ((c & 0x1F) << 6) | (d & 0x3F);
                }
                break;
            case 2: // 3-byte UTF
                c = _decodeUTF8_3(c);
                break;
            case 3: // 4-byte UTF
                c = _decodeUTF8_4(c);
                // Let's add first part right away:
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                    outEnd = outBuf.length;
                }
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                // Is this good enough error message?
                _reportInvalidChar(c);
            }
            // Need more room?
            if (outPtr >= outEnd) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
                outEnd = outBuf.length;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
    }

    private final int _decodeUTF8_3(int c1) throws IOException
    {
        c1 &= 0x0F;
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }
    
    /**
     * @return Character value <b>minus 0x10000</c>; this so that caller
     *    can readily expand it to actual surrogates
     */
    private final int _decodeUTF8_4(int c) throws IOException
    {
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }
    
    private final int _nextByte() throws IOException {
        int inPtr = _inputPtr;
        if (inPtr < _inputEnd) {
            int ch = _inputBuffer[inPtr];
            _inputPtr = inPtr+1;
            return ch;
        }
        loadMoreGuaranteed();
        return _inputBuffer[_inputPtr++];
    }

    /*
    /**********************************************************
    /* Low-level reading: buffer reload
    /**********************************************************
     */

    protected final boolean loadMore() throws IOException
    {
        if (_inputStream != null) {
            _currInputProcessed += _inputEnd;

            int count = _inputStream.read(_inputBuffer, 0, _inputBuffer.length);
            if (count > 0) {
                _currentEndOffset = _parsingContext.adjustEnd(_inputEnd);
                _inputPtr = 0;
                _inputEnd = count;
                return true;
            }
            // End of input
            _closeInput();
            // Should never return 0, so let's fail
            if (count == 0) {
                throw new IOException("InputStream.read() returned 0 characters when trying to read "+_inputBuffer.length+" bytes");
            }
        }
        return false;
    }

    protected final void loadMoreGuaranteed() throws IOException {
        if (!loadMore()) { _reportInvalidEOF(); }
    }
    
    /**
     * Helper method that will try to load at least specified number bytes in
     * input buffer, possible moving existing data around if necessary
     */
    protected final void _loadToHaveAtLeast(int minAvailable) throws IOException
    {
        // No input stream, no leading (either we are closed, or have non-stream input source)
        if (_inputStream == null) {
            throw _constructError("Needed to read "+minAvailable+" bytes, reached end-of-input");
        }
        // Need to move remaining data in front?
        int ptr = _inputPtr;
        int amount = _inputEnd - ptr;
        
        if (ptr > 0) {
            _currInputProcessed += ptr;
            if (amount > 0) {
                System.arraycopy(_inputBuffer, ptr, _inputBuffer, 0, amount);
            }
            _currentEndOffset = _parsingContext.adjustEnd(ptr);
        }
        _inputPtr = 0;
        _inputEnd = amount;
        while (_inputEnd < minAvailable) {
            int count = _inputStream.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            if (count < 1) {
                // End of input
                _closeInput();
                // Should never return 0, so let's fail
                if (count == 0) {
                    throw new IOException("InputStream.read() returned 0 characters when trying to read "+amount+" bytes");
                }
                throw _constructError("Needed to read "+minAvailable+" bytes, missed "+minAvailable+" before end-of-input");
            }
            _inputEnd += count;
        }
    }

    /*
    /**********************************************************
    /* Low-level reading: other
    /**********************************************************
     */
    
    protected ByteArrayBuilder _getByteArrayBuilder() {
        if (_byteArrayBuilder == null) {
            _byteArrayBuilder = new ByteArrayBuilder();
        } else {
            _byteArrayBuilder.reset();
        }
        return _byteArrayBuilder;
    }

    protected void _closeInput() throws IOException {
        if (_inputStream != null) {
            if (_ioContext.isResourceManaged() || isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE)) {
                _inputStream.close();
            }
            _inputStream = null;
        }
    }

    @Override
    protected void _handleEOF() throws JsonParseException {
        if (!_parsingContext.inRoot()) {
            String marker = _parsingContext.inArray() ? "Array" : "Object";
            _reportInvalidEOF(String.format(
                    ": expected close marker for %s (start marker at %s)",
                    marker,
                    _parsingContext.getStartLocation(
                            _ioContext.getSourceReference(),  _currInputProcessed)),
                    null);
        }
    }

    /*
    /**********************************************************
    /* Helper methods, skipping
    /**********************************************************
     */

    protected void _skipBytes(int len) throws IOException
    {
        while (true) {
            int toAdd = Math.min(len, _inputEnd - _inputPtr);
            _inputPtr += toAdd;
            len -= toAdd;
            if (len <= 0) {
                return;
            }
            loadMoreGuaranteed();
        }
    }

    protected void _skipVInt() throws IOException
    {
        int ptr = _inputPtr;
        if ((ptr + 10) > _inputEnd) {
            _skipVIntSlow();
            return;
        }
        final byte[] buf = _inputBuffer;
        // inline checks for first 4 bytes
        if ((buf[ptr++] >= 0) || (buf[ptr++] >= 0) || (buf[ptr++] >= 0) || (buf[ptr++] >= 0)) {
            _inputPtr = ptr;
            return;
        }
        // but loop beyond
        for (int end = ptr+6; ptr < end; ++ptr) {
            if (buf[ptr] >= 0) {
                _inputPtr = ptr+1;
                return;
            }
        }
        _reportTooLongVInt(buf[ptr-1]);
    }

    protected void _skipVIntSlow() throws IOException
    {
        for (int i = 0; i < 10; ++i) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++];
            if (ch >= 0) {
                return;
            }
        }
        _reportTooLongVInt(_inputBuffer[_inputPtr-1]);
    }
    
    /*
    /**********************************************************
    /* Helper methods, decoding
    /**********************************************************
     */

    private int _decodeVInt() throws IOException
    {
        int ptr = _inputPtr;
        // 5 x 7 = 35 bits -> all we need is 32
        if ((ptr + 5) > _inputEnd) {
            return _decodeVIntSlow();
        }

        final byte[] buf = _inputBuffer;
        int v = buf[ptr++];

        if (v < 0) { // keep going
            v &= 0x7F;
            // Tag VInts guaranteed to stay in 32 bits, i.e. no more than 5 bytes
            int ch = buf[ptr++];
            if (ch < 0) {
                v |= ((ch & 0x7F) << 7);
                ch = buf[ptr++];
                if (ch < 0) {
                    v |= ((ch & 0x7F) << 14);
                    ch = buf[ptr++];
                    if (ch < 0) {
                        v |= ((ch & 0x7F) << 21);

                        // and now the last byte; at most 4 bits
                        int last = buf[ptr++] & 0xFF;
                        
                        if (last > 0x1F) { // should have at most 5 one bits
                            _inputPtr = ptr;
                            _reportTooLongVInt(last);
                        }
                        v |= (last << 28);
                    } else {
                        v |= (ch << 21);
                    }
                } else {
                    v |= (ch << 14);
                }
            } else {
                v |= (ch << 7);
            }
        }
        _inputPtr = ptr;
        return v;
    }

    // Similar to '_decodeVInt()', but also ensure that no
    // negative values allowed
    private int _decodeLength() throws IOException
    {
        int ptr = _inputPtr;

        if ((ptr + 5) > _inputEnd) {
            int v = _decodeVIntSlow();
            if (v < 0) {
                _reportInvalidLength(v);
            }
            return v;
        }

        final byte[] buf = _inputBuffer;
        int v = buf[ptr++];
        
        if (v < 0) { // keep going
            v &= 0x7F;
            // Tag VInts guaranteed to stay in 32 bits, i.e. no more than 5 bytes
            int ch = buf[ptr++];
            if (ch < 0) {
                v |= ((ch & 0x7F) << 7);
                ch = buf[ptr++];
                if (ch < 0) {
                    v |= ((ch & 0x7F) << 14);
                    ch = buf[ptr++];
                    if (ch < 0) {
                        v |= ((ch & 0x7F) << 21);

                        // and now the last byte; at most 4 bits
                        int last = buf[ptr++] & 0xFF;
                        
                        if (last > 0x0F) {
                            _inputPtr = ptr;
                            _reportTooLongVInt(last);
                        }
                        v |= (last << 28);
                    } else {
                        v |= (ch << 21);
                    }
                } else {
                    v |= (ch << 14);
                }
            } else {
                v |= (ch << 7);
            }
        }
        _inputPtr = ptr;
        if (v < 0) {
            _reportInvalidLength(v);
        }
        return v;
    }
    
    protected int _decodeVIntSlow() throws IOException
    {
        int v = 0;
        int shift = 0;
        
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++];
            if (shift >= 28) { // must end
                ch &= 0xFF;
                if (ch > 0x0F) { // should have at most 4 one bits
                    _reportTooLongVInt(ch);
                }
            }
            if (ch >= 0) {
                return v | (ch << shift);
            }
            v |= ((ch & 0x7f) << shift);
            shift += 7;
        }
    }
    
    private long _decodeVLong() throws IOException
    {
        // 10 x 7 = 70 bits -> all we need is 64
        if ((_inputPtr + 10) > _inputEnd) {
            return _decodeVLongSlow();
        }
        final byte[] buf = _inputBuffer;
        
        // First things first: can start by accumulating as int, first 4 bytes

        int v = buf[_inputPtr++];
        if (v >= 0) {
            return v;
        }
        v &= 0x7F;
        int ch = buf[_inputPtr++];
        if (ch >= 0) {
            return v | (ch << 7);
        }
        v |= ((ch & 0x7F) << 7);
        ch = buf[_inputPtr++];
        if (ch >= 0) {
            return v | (ch << 14);
        }
        v |= ((ch & 0x7F) << 14);
        ch = buf[_inputPtr++];
        if (ch >= 0) {
            return v | (ch << 21);
        }
        v |= ((ch & 0x7F) << 21);
        
        // 4 bytes gotten. How about 4 more?
        long l = (long) v;
        
        v = buf[_inputPtr++];
        if (v >= 0) {
            return (((long) v) << 28) | l;
        }
        v &= 0x7F;
        ch = buf[_inputPtr++];
        if (ch >= 0) {
            long l2 = (v | (ch << 7));
            return (l2 << 28) | l;
        }
        v |= ((ch & 0x7F) << 7);
        ch = buf[_inputPtr++];
        if (ch >= 0) {
            long l2 = (v | (ch << 14));
            return (l2 << 28) | l;
        }
        v |= ((ch & 0x7F) << 14);
        ch = buf[_inputPtr++];
        if (ch >= 0) {
            long l2 = (v | (ch << 21));
            return (l2 << 28) | l;
        }
        v |= ((ch & 0x7F) << 21);

        // So far so good. Possibly 2 more bytes to get and we are done
        l |= (((long) v) << 28);

        v = buf[_inputPtr++];
        if (v >= 0) {
            return (((long) v) << 56) | l;
        }
        v &= 0x7F;
        ch = buf[_inputPtr++] & 0xFF;
        if (ch > 0x1) { // error; should have at most 1 bit at the last value
            _reportTooLongVInt(ch);
        }
        v |= ((ch & 0x7F) << 7);

        return (((long) v) << 56) | l;
    }

    protected long _decodeVLongSlow() throws IOException
    {
        // since only called rarely, no need to optimize int vs long
        long v = 0;
        int shift = 0;

        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++];
            if (shift >= 63) { // must end
                ch &= 0xFF;
                if (ch > 0x1) { // at most a single bit here
                    _reportTooLongVLong(ch);
                }
            }
            if (ch >= 0) {
                long l = (long) ch;
                return v | (l << shift);
            }
            ch &= 0x7F;
            long l = (long) ch;
            v |= (l << shift);
            shift += 7;
        }
    }
    
    protected final int _decode32Bits() throws IOException {
        int ptr = _inputPtr;
        if ((ptr + 3) >= _inputEnd) {
            return _slow32();
        }
        final byte[] b = _inputBuffer;
        int v = (b[ptr] & 0xFF) + ((b[ptr+1] & 0xFF) << 8)
                + ((b[ptr+2] & 0xFF) << 16) + ((b[ptr+3] & 0xFF) << 24);
        _inputPtr = ptr+4;
        return v;
    }

    protected final int _slow32() throws IOException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int v = _inputBuffer[_inputPtr++] & 0xFF;
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        v |= ((_inputBuffer[_inputPtr++] & 0xFF) << 8);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        v |= ((_inputBuffer[_inputPtr++] & 0xFF) << 16);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return v | (_inputBuffer[_inputPtr++] << 24); // sign will shift away
    }

    protected final long _decode64Bits() throws IOException {
        int ptr = _inputPtr;
        if ((ptr + 7) >= _inputEnd) {
            return _slow64();
        }
        final byte[] b = _inputBuffer;
        int i1 = (b[ptr++] & 0xFF) | ((b[ptr++] & 0xFF) << 8)
                | ((b[ptr++] & 0xFF) << 16) | (b[ptr++] << 24);
        int i2 = (b[ptr++] & 0xFF) | ((b[ptr++] & 0xFF) << 8)
                | ((b[ptr++] & 0xFF) << 16) | (b[ptr++] << 24);
        _inputPtr = ptr;
        return _long(i1, i2);
    }

    protected final long _slow64() throws IOException {
        return _long(_decode32Bits(), _decode32Bits());
    }

    protected final static long _long(int i1, int i2)
    {
        // important: LSB all the way, hence:
        long high = i2;
        high <<= 32;
        long low = i1;
        low = (low << 32) >>> 32;
        return high | low;
    }

    /*
    /**********************************************************
    /* Helper methods, error reporting
    /**********************************************************
     */

    private void _reportErrorF(String format, Object... args) throws JsonParseException {
        _reportError(String.format(format, args));
    }

    private void _reportIncompatibleType(ProtobufField field, int wireType) throws JsonParseException
    {
        _reportError(String.format
                ("Incompatible wire type (0x%x) for field '%s': not valid for field of type %s (expected 0x%x)",
                        wireType, field.name, field.type, field.type.getWireType()));
    }

    private void _reportInvalidLength(int len) throws JsonParseException {
        _reportError("Invalid length (%d): must be positive number", len);
    }

    private void _reportTooLongVInt(int fifth) throws JsonParseException {
        _reportError("Too long tag VInt: fifth byte 0x%x", fifth);
    }

    private void _reportTooLongVLong(int fifth) throws JsonParseException {
        _reportError("Too long tag VLong: tenth byte 0x%x", fifth);
    }

    private void _reportInvalidInitial(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 start byte 0x%x", mask);
    }

    private void _reportInvalidOther(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 middle byte 0x%x", mask);
    }

    private void _reportInvalidOther(int mask, int ptr) throws JsonParseException {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }

    private void _reportInvalidChar(int c) throws JsonParseException {
        // Either invalid WS or illegal UTF-8 start char
        if (c < ' ') {
            _throwInvalidSpace(c);
        }
        _reportInvalidInitial(c);
    }
}
