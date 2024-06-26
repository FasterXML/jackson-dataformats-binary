package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.fasterxml.jackson.core.util.TextBuffer;

import static com.fasterxml.jackson.dataformat.cbor.CBORConstants.*;

public class CBORParser extends ParserMinimalBase
{
    /**
     * Enumeration that defines all togglable features for CBOR generators.
     */
    public enum Feature implements FormatFeature
    {
//        BOGUS(false)
        ;

        final boolean _defaultState;
        final int _mask;

        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }

        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }

        @Override public boolean enabledByDefault() { return _defaultState; }
        @Override public int getMask() { return _mask; }
        @Override public boolean enabledIn(int flags) { return (flags & _mask) != 0; }
    }

    /**
     * Class for keeping track of tags in an optimized manner.
     *
     * @since 2.15
     */
    public static final class TagList
    {
        public TagList() {
            _tags = new int[8];
            _tagCount = 0;
        }

        /**
         * Gets the number of tags available.
         *
         * @return The number of tags.
         */
        public int size() {
            return _tagCount;
        }

        /**
         * Checks whether the tag list is empty.
         *
         * @return {@code true} if there are no tags, {@code false} if there are tags..
         */
        public boolean isEmpty() {
            return _tagCount == 0;
        }

        /**
         * Clears the tags from the list.
         */
        public void clear() {
            _tagCount = 0;
        }

        /**
         * Adds a tag to the list.
         *
         * @param tag The tag to add.
         */
        public void add(int tag) {
            if (_tagCount == _tags.length) {
                // Linear growth since we expect a small number of tags.
                int[] newTags = new int[_tagCount + 8];
                System.arraycopy(_tags, 0, newTags, 0, _tagCount);
                _tags = newTags;
            }

            _tags[_tagCount++] = tag;
        }

        /**
         * Checks if a tag is present.
         *
         * @param tag The tag to check.
         * @return {@code true} if the tag is present, {@code false} if it is not.
         */
        public boolean contains(int tag) {
            for (int i = 0; i < _tagCount; ++i) {
                if (_tags[i] == tag) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Gets the first tag in the list. This is primarily to support the legacy API.
         *
         * @return The first tag or -1 if there are no tags.
         */
        public int getFirstTag() {
            if (_tagCount == 0) {
                return -1;
            }
            return _tags[0];
        }

        private int[] _tags;
        private int _tagCount;
    }

    private final static Charset UTF8 = StandardCharsets.UTF_8;

    private final static int[] UTF8_UNIT_CODES = CBORConstants.sUtf8UnitLengths;

    // Constants for handling of 16-bit "mini-floats"
    private final static double MATH_POW_2_10 = Math.pow(2, 10);
    private final static double MATH_POW_2_NEG14 = Math.pow(2, -14);

    // 2.11.4: [dataformats-binary#186] Avoid OOME/DoS for bigger binary;
    //  read only up to 250k
    protected final static int LONGEST_NON_CHUNKED_BINARY = 250_000;

    // @since 2.14 - require some overrides
    protected final static JacksonFeatureSet<StreamReadCapability> CBOR_READ_CAPABILITIES =
            DEFAULT_READ_CAPABILITIES.with(StreamReadCapability.EXACT_FLOATS);

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    /**
     * Codec used for data binding when (if) requested.
     */
    protected ObjectCodec _objectCodec;

    /*
    /**********************************************************
    /* Generic I/O state
    /**********************************************************
     */

    /**
     * I/O context for this reader. It handles buffer allocation
     * for the reader.
     */
    protected final IOContext _ioContext;

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

    /**
     * Current row location of current point in input buffer, starting
     * from 1, if available.
     */
    protected int _currInputRow = 1;

    /**
     * Current index of the first character of the current row in input
     * buffer. Needed to calculate column position, if necessary; benefit
     * of not having column itself is that this only has to be updated
     * once per line.
     */
    protected int _currInputRowStart = 0;

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
     * <p>
     * NOTE: before 2.13 was "_parsingContext"
     */
    protected CBORReadContext _streamReadContext;

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

    /**
     * Helper variables used when dealing with chunked content.
     */
    private int _chunkLeft, _chunkEnd;

    /**
     * We will keep track of tag values for possible future use.
     * @since 2.15
     */
    protected TagList _tagValues = new TagList();

    /**
     * Flag that indicates that the current token has not yet
     * been fully processed, and needs to be finished for
     * some access (or skipped to obtain the next token)
     */
    protected boolean _tokenIncomplete = false;

    /**
     * Type byte of the current token
     */
    protected int _typeByte;

    /**
     * Type to keep track of a list of string references. A depth is stored to know when to pop the
     * references off the stack for nested namespaces.
     *
     * @since 2.15
     */
    protected static final class StringRefList
    {
        public StringRefList(int depth) {
            this.depth = depth;
        }

        public ArrayList<Object> stringRefs = new ArrayList<>();
        public int depth;
    }

    /**
     * Type to keep a stack of string refs based on namespaces within the document.
     *
     * @since 2.15
     */
    protected static final class StringRefListStack {
        public void push(boolean hasNamespace) {
            if (hasNamespace) {
                _stringRefs.push(new StringRefList(_nestedDepth));
            }
            ++_nestedDepth;
        }

        public void pop() {
            --_nestedDepth;
            if (!_stringRefs.empty() && _stringRefs.peek().depth == _nestedDepth) {
                _stringRefs.pop();
            }
        }

        public StringRefList peek() {
            return _stringRefs.peek();
        }

        public boolean empty() {
            return _stringRefs.empty();
        }

        private Stack<StringRefList> _stringRefs = new Stack<>();
        private int _nestedDepth = 0;
    }

    /**
     * Stack of text and binary string references.
     * @since 2.15
     */
    protected StringRefListStack _stringRefs = new StringRefListStack();

    /**
     * Shared string that should be used in place of _textBuffer when a string reference is used.
     * @since 2.15
     */
    protected String _sharedString;

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
     * <p>
     * If it is not, it also means that parser can NOT modify underlying
     * buffer.
     */
    protected boolean _bufferRecyclable;

    /*
    /**********************************************************
    /* Symbol handling, decoding
    /**********************************************************
     */

    /**
     * Symbol table that contains field names encountered so far
     */
    final protected ByteQuadsCanonicalizer _symbols;

    /**
     * Temporary buffer used for name parsing.
     */
    protected int[] _quadBuffer = NO_INTS;

    /**
     * Quads used for hash calculation
     */
    protected int _quad1, _quad2, _quad3;

    /**
     * Marker flag to indicate that standard symbol handling is used
     * (one with symbol table assisted canonicalization. May be disabled
     * in which case alternate stream-line, non-canonicalizing handling
     * is used: usually due to set of symbols
     * (Object property names) is unbounded and will not benefit from
     * canonicalization attempts.
     *
     * @since 2.13
     */
    protected final boolean _symbolsCanonical;

    /*
    /**********************************************************
    /* Constants and fields of former 'JsonNumericParserBase'
    /**********************************************************
     */

    // Also, we need some numeric constants

    @SuppressWarnings("hiding") // only since 2.9, remove in 3.0
    final static BigInteger BI_MIN_INT = BigInteger.valueOf(Integer.MIN_VALUE);
    @SuppressWarnings("hiding") // only since 2.9, remove in 3.0
    final static BigInteger BI_MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    @SuppressWarnings("hiding") // only since 2.9, remove in 3.0
    final static BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    @SuppressWarnings("hiding") // only since 2.9, remove in 3.0
    final static BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    @SuppressWarnings("hiding") // only since 2.9, remove in 3.0
    final static BigDecimal BD_MIN_LONG = new BigDecimal(BI_MIN_LONG);
    @SuppressWarnings("hiding") // only since 2.9, remove in 3.0
    final static BigDecimal BD_MAX_LONG = new BigDecimal(BI_MAX_LONG);

    @SuppressWarnings("hiding") // only since 2.9, remove in 3.0
    final static BigDecimal BD_MIN_INT = new BigDecimal(BI_MIN_INT);
    @SuppressWarnings("hiding") // only since 2.9, remove in 3.0
    final static BigDecimal BD_MAX_INT = new BigDecimal(BI_MAX_INT);

    // Numeric value holders: multiple fields used for
    // for efficiency

    /**
     * Bitfield that indicates which numeric representations
     * have been calculated for the current type
     */
    protected int _numTypesValid = NR_UNKNOWN;

    // First primitives

    protected int _numberInt;
    protected long _numberLong;
    protected float _numberFloat;
    protected double _numberDouble;

    // And then object types

    protected BigInteger _numberBigInt;
    protected BigDecimal _numberBigDecimal;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public CBORParser(IOContext ctxt, int parserFeatures, int cborFeatures,
            ObjectCodec codec, ByteQuadsCanonicalizer sym,
            InputStream in, byte[] inputBuffer, int start, int end,
            boolean bufferRecyclable)
    {
        super(parserFeatures, ctxt.streamReadConstraints());
        _ioContext = ctxt;
        _objectCodec = codec;
        _symbols = sym;
        _symbolsCanonical = sym.isCanonicalizing();

        _inputStream = in;
        _inputBuffer = inputBuffer;
        _inputPtr = start;
        _inputEnd = end;
        _bufferRecyclable = bufferRecyclable;
        _textBuffer = ctxt.constructReadConstrainedTextBuffer();
        DupDetector dups = JsonParser.Feature.STRICT_DUPLICATE_DETECTION.enabledIn(parserFeatures)
                ? DupDetector.rootDetector(this) : null;
        _streamReadContext = CBORReadContext.createRootContext(dups);

        _tokenInputRow = -1;
        _tokenInputCol = -1;
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
    /* Configuration
    /**********************************************************
     */

//    public JsonParser overrideStdFeatures(int values, int mask)

    @Override
    public int getFormatFeatures() {
        // No parser features, yet
        return 0;
    }

    @Override // since 2.12
    public JacksonFeatureSet<StreamReadCapability> getReadCapabilities() {
        return CBOR_READ_CAPABILITIES;
    }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    /**
     * Method that can be used to access tag id associated with
     * the most recently decoded value (whether completely, for
     * scalar values, or partially, for Objects/Arrays), if any.
     * If no tag was associated with it, -1 is returned.
     *
     * @since 2.5
     */
    public int getCurrentTag() {
        return _tagValues.getFirstTag();
    }

    /**
     * Method that can be used to access all tag ids associated with
     * the most recently decoded value (whether completely, for
     * scalar values, or partially, for Objects/Arrays), if any.
     *
     * @since 2.15
     */
    public TagList getCurrentTags() {
        return _tagValues;
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
    @Override // since 2.17
    public JsonLocation currentLocation()
    {
        final long offset = _currInputProcessed + _inputPtr;
        return new JsonLocation(_ioContext.contentReference(),
                offset, // bytes
                -1, -1, (int) offset); // char offset, line, column
    }

    /**
     * Overridden since we do not really have character-based locations,
     * but we do have byte offset to specify.
     */
    @Override // since 2.17
    public JsonLocation currentTokenLocation()
    {
        // token location is correctly managed...
        return new JsonLocation(_ioContext.contentReference(),
                _tokenInputTotal, // bytes
                -1, -1, (int) _tokenInputTotal); // char offset, line, column
    }

    @Deprecated // since 2.17
    @Override
    public JsonLocation getCurrentLocation() { return currentLocation(); }

    @Deprecated // since 2.17
    @Override
    public JsonLocation getTokenLocation() { return currentTokenLocation(); }

    /**
     * Method that can be called to get the name associated with
     * the current event.
     */
    @Override // since 2.17
    public String currentName() throws IOException
    {
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            CBORReadContext parent = _streamReadContext.getParent();
            return parent.getCurrentName();
        }
        return _streamReadContext.getCurrentName();
    }

    @Deprecated // since 2.17
    @Override
    public String getCurrentName() throws IOException { return currentName(); }

    @Override
    public void overrideCurrentName(String name)
    {
        // Simple, but need to look for START_OBJECT/ARRAY's "off-by-one" thing:
        CBORReadContext ctxt = _streamReadContext;
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            ctxt = ctxt.getParent();
        }
        // Unfortunate, but since we did not expose exceptions, need to wrap
        try {
            ctxt.setCurrentName(name);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (!_closed) {
            _closed = true;
            _symbols.release();
            try {
                _closeInput();
            } finally {
                // as per [JACKSON-324], do in finally block
                // Also, internal buffer(s) can now be released as well
                _releaseBuffers();
            }
            _ioContext.close();
        }
    }

    @Override
    public boolean isClosed() { return _closed; }

    @Override
    public CBORReadContext getParsingContext() {
        return _streamReadContext;
    }

    /*
    /**********************************************************
    /* Overridden methods
    /**********************************************************
     */

    @Override
    public boolean hasTextCharacters()
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            // yes; is or can be made available efficiently as char[]
            return _sharedString != null || _textBuffer.hasTextAsCharacters();
        }
        if (_currToken == JsonToken.FIELD_NAME) {
            // not necessarily; possible but:
            return _nameCopied;
        }
        // other types, no benefit from accessing as char[]
        return false;
    }

    /**
     * Method called to release internal buffers owned by the base
     * reader. This may be called along with {@link #_closeInput} (for
     * example, when explicitly closing this reader instance), or
     * separately (if need be).
     */
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

    @Override
    public JsonToken nextToken() throws IOException
    {
        // For longer tokens (text, binary), we'll only read when requested
        if (_tokenIncomplete) {
            _skipIncomplete();
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr;
        // also: clear any data retained so far
        _numTypesValid = NR_UNKNOWN;
        _binaryValue = null;

        // First: need to keep track of lengths of defined-length Arrays and
        // Objects (to materialize END_ARRAY/END_OBJECT as necessary);
        // as well as handle names for Object entries.
        if (_streamReadContext.inObject()) {
            if (_currToken != JsonToken.FIELD_NAME) {
                _tagValues.clear();
                // completed the whole Object?
                if (!_streamReadContext.expectMoreValues()) {
                    _stringRefs.pop();
                    _streamReadContext = _streamReadContext.getParent();
                    return _updateToken(JsonToken.END_OBJECT);
                }
                return _updateToken(_decodePropertyName());
            }
        } else {
            if (!_streamReadContext.expectMoreValues()) {
                _stringRefs.pop();
                _tagValues.clear();
                _streamReadContext = _streamReadContext.getParent();
                return _updateToken(JsonToken.END_ARRAY);
            }
        }
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                return _eofAsNextToken();
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        int type = (ch >> 5);
        int lowBits = ch & 0x1F;

        // One special case: need to consider tag as prefix first:
        _tagValues.clear();
        while (type == 6) {
            _tagValues.add(_decodeTag(lowBits));
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    return _eofAsNextToken();
                }
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            type = (ch >> 5);
            lowBits = ch & 0x1F;
        }

        boolean stringrefNamespace = _tagValues.contains(TAG_ID_STRINGREF_NAMESPACE);

        switch (type) {
        case 0: // positive int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = lowBits;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = _decode8Bits();
                    break;
                case 1:
                    _numberInt = _decode16Bits();
                    break;
                case 2:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        int v = _decode32Bits();
                        if (v >= 0) {
                            _numberInt = v;
                        } else {
                            long l = (long) v;
                            _numberLong = l & 0xFFFFFFFFL;
                            _numTypesValid = NR_LONG;
                        }
                    }
                    break;
                case 3:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = l;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigPositive(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            if (!_tagValues.isEmpty()) {
                return _handleTaggedInt(_tagValues);
            }
            return _updateToken(JsonToken.VALUE_NUMBER_INT);
        case 1: // negative int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = -lowBits - 1;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = -_decode8Bits() - 1;
                    break;
                case 1:
                    _numberInt = -_decode16Bits() - 1;
                    break;
                case 2:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        int v = _decode32Bits();
                        if (v < 0) {
                            long unsignedBase = (long) v & 0xFFFFFFFFL;
                            _numberLong = -unsignedBase - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberInt = -v - 1;
                        }
                    }
                    break;
                case 3:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = -l - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigNegative(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            return _updateToken(JsonToken.VALUE_NUMBER_INT);

        case 2: // byte[]
            _typeByte = ch;
            _tokenIncomplete = true;
            if (!_tagValues.isEmpty()) {
                return _handleTaggedBinary(_tagValues);
            }
            return _updateToken(JsonToken.VALUE_EMBEDDED_OBJECT);

        case 3: // String
            _typeByte = ch;
            _tokenIncomplete = true;
            return _updateToken(JsonToken.VALUE_STRING);

        case 4: // Array
            _stringRefs.push(stringrefNamespace);
            {
                int len = _decodeExplicitLength(lowBits);
                if (!_tagValues.isEmpty()) {
                    return _handleTaggedArray(_tagValues, len);
                }
                createChildArrayContext(len);
            }
            return _updateToken(JsonToken.START_ARRAY);

        case 5: // Object
            _stringRefs.push(stringrefNamespace);
            _updateToken(JsonToken.START_OBJECT);
            {
                int len = _decodeExplicitLength(lowBits);
                createChildObjectContext(len);
            }
            return _currToken;

        case 7:
        default: // misc: tokens, floats
            switch (lowBits) {
            case 20:
                return _updateToken(JsonToken.VALUE_FALSE);
            case 21:
                return _updateToken(JsonToken.VALUE_TRUE);
            case 22:
                return _updateToken(JsonToken.VALUE_NULL);
            case 23:
                return _updateToken(_decodeUndefinedValue());

            case 25: // 16-bit float...
                // As per [http://stackoverflow.com/questions/5678432/decompressing-half-precision-floats-in-javascript]
                {
                    _numberFloat = (float) _decodeHalfSizeFloat();
                    _numTypesValid = NR_FLOAT;
                }
                return _updateToken(JsonToken.VALUE_NUMBER_FLOAT);
            case 26: // Float32
                {
                    _numberFloat = Float.intBitsToFloat(_decode32Bits());
                    _numTypesValid = NR_FLOAT;
                }
                return _updateToken(JsonToken.VALUE_NUMBER_FLOAT);
            case 27: // Float64
                _numberDouble = Double.longBitsToDouble(_decode64Bits());
                _numTypesValid = NR_DOUBLE;
                return _updateToken(JsonToken.VALUE_NUMBER_FLOAT);
            case 31: // Break
                if (_streamReadContext.inArray()) {
                    if (!_streamReadContext.hasExpectedLength()) {
                        _stringRefs.pop();
                        _streamReadContext = _streamReadContext.getParent();
                        return _updateToken(JsonToken.END_ARRAY);
                    }
                }
                // Object end-marker can't occur here
                _reportUnexpectedBreak();
            }
            return _updateToken(_decodeSimpleValue(lowBits, ch));
        }
    }

    protected String _numberToName(int ch, boolean neg, TagList tags) throws IOException
    {
        boolean isStringref = tags.contains(TAG_ID_STRINGREF);
        final int lowBits = ch & 0x1F;
        int i;
        if (lowBits <= 23) {
            i = lowBits;
        } else {
            switch (lowBits) {
            case 24:
                i = _decode8Bits();
                break;
            case 25:
                i = _decode16Bits();
                break;
            case 26:
                i = _decode32Bits();
                // [dataformats-binary#269] (and earlier [dataformats-binary#30]),
                // got some edge case to consider
                if (i < 0) {
                    if (isStringref) {
                        _reportError("String reference index too large");
                    }
                    long l;
                    if (neg) {
                        long unsignedBase = (long) i & 0xFFFFFFFFL;
                        l = -unsignedBase - 1L;
                    } else {
                        l = (long) i;
                        l = l & 0xFFFFFFFFL;
                    }
                    return String.valueOf(l);
                }
                break;
            case 27:
                {
                    if (isStringref) {
                        _reportError("String reference index too large");
                    }
                    long l = _decode64Bits();
                    if (neg) {
                        l = -l - 1L;
                    }
                    return String.valueOf(l);
                }
            default:
                throw _constructReadException("Invalid length indicator for ints (%d), token 0x%s",
                        lowBits, Integer.toHexString(ch));
            }
        }
        if (neg) {
            i = -i - 1;
        }

        if (isStringref) {
            if (_stringRefs.empty()) {
                _reportError("String reference outside of a namespace");
            }

            StringRefList stringRefs = _stringRefs.peek();
            if (i < 0 || i >= stringRefs.stringRefs.size()) {
                _reportError("String reference (" + i + ") out of range");
            }

            Object str = stringRefs.stringRefs.get(i);
            if (str instanceof String) {
                return (String) str;
            }
            return new String((byte[]) str, UTF8);
        }
        return String.valueOf(i);
    }

    protected JsonToken _handleTaggedInt(TagList tags) throws IOException {
        // For now all we should get is stringref
        if (!tags.contains(TAG_ID_STRINGREF)) {
            return _updateToken(JsonToken.VALUE_NUMBER_INT);
        }

        if (_stringRefs.empty()) {
            _reportError("String reference outside of a namespace");
        } else if (_numTypesValid != NR_INT) {
            _reportError("String reference index too large");
        }

        StringRefList stringRefs = _stringRefs.peek();

        if (_numberInt < 0 || _numberInt >= stringRefs.stringRefs.size()) {
            _reportError("String reference (" + _numberInt + ") out of range");
        }

        Object str = stringRefs.stringRefs.get(_numberInt);
        if (str instanceof String) {
            _sharedString = (String) str;
            return _updateToken(JsonToken.VALUE_STRING);
        }
        _binaryValue = (byte[]) str;
        return _handleTaggedBinary(tags);
    }

    protected JsonToken _handleTaggedBinary(TagList tags) throws IOException
    {
        // For now all we should get is BigInteger
        boolean neg;
        if (tags.contains(TAG_BIGNUM_POS)) {
            neg = false;
        } else if (tags.contains(TAG_BIGNUM_NEG)) {
            neg = true;
        } else {
            // 12-May-2016, tatu: Since that's all we know, let's otherwise
            //   just return default Binary data marker
            // 16-Jan-2024, tatu: Esoteric edge case where we have marked
            //   `int` as being tokenized
            _numTypesValid = NR_UNKNOWN;
            return _updateToken(JsonToken.VALUE_EMBEDDED_OBJECT);
        }

        // First: get the data
        if (_tokenIncomplete) {
            _finishToken();
        }

        // [dataformats-binar#261]: handle this special case
        if (_binaryValue.length == 0) {
            _numberBigInt = BigInteger.ZERO;
        } else {
            _streamReadConstraints.validateIntegerLength(_binaryValue.length);
            BigInteger nr = new BigInteger(_binaryValue);
            if (neg) {
                nr = nr.negate();
            }
            _numberBigInt = nr;
        }
        _numTypesValid = NR_BIGINT;
        _tagValues.clear();
        return _updateToken(JsonToken.VALUE_NUMBER_INT);
    }

    protected JsonToken _handleTaggedArray(TagList tags, int len) throws IOException
    {
        // For simplicity, let's create matching array context -- in perfect
        // world that wouldn't be necessarily, but in this one there are
        // some constraints that make it necessary
        createChildArrayContext(len);

        // BigDecimal is the only thing we know for sure
        if (!tags.contains(CBORConstants.TAG_DECIMAL_FRACTION)) {
            return _updateToken(JsonToken.START_ARRAY);
        }
        _updateToken(JsonToken.START_ARRAY);

        // but has to have length of 2; otherwise we have a problem...
        if (len != 2) {
            _reportError("Unexpected array size ("+len+") for tagged 'bigfloat' value; should have exactly 2 number elements");
        }
        // and then use recursion to get values
        // First: exponent, which MUST be a simple integer value
        if (!_checkNextIsIntInArray("bigfloat")) {
            _reportError("Unexpected token ("+currentToken()+") as the first part of 'bigfloat' value: should get VALUE_NUMBER_INT");
        }
        // 27-Nov-2019, tatu: As per [dataformats-binary#139] need to change sign here
        int exp = -getIntValue();

        // Should get an integer value; int/long/BigInteger
        if (!_checkNextIsIntInArray("bigfloat")) {
            _reportError("Unexpected token ("+currentToken()+") as the second part of 'bigfloat' value: should get VALUE_NUMBER_INT");
        }

        // important: check number type here
        BigDecimal dec;
        NumberType numberType = getNumberType();
        if (numberType == NumberType.BIG_INTEGER) {
            dec = new BigDecimal(getBigIntegerValue(), exp);
        } else  {
            dec = BigDecimal.valueOf(getLongValue(), exp);
        }

        // but verify closing END_ARRAY here, as this will now override current token
        if (!_checkNextIsEndArray()) {
            _reportError("Unexpected token ("+currentToken()+") after 2 elements of 'bigfloat' value");
        }

        // which needs to be reset here
        _numberBigDecimal = dec;
        _numTypesValid = NR_BIGDECIMAL;
        return _updateToken(JsonToken.VALUE_NUMBER_FLOAT);
    }

    /**
     * Heavily simplified method that does a subset of what {@code nextToken()} does to basically
     * only (1) determine that we are getting {@code JsonToken.VALUE_NUMBER_INT} (if not,
     * return with no processing) and (2) if so, prepare state so that number accessor
     * method will work).
     * <p>
     * Note that in particular this method DOES NOT reset state that {@code nextToken()} would do,
     * but will change current token type to allow access.
     */
    protected final boolean _checkNextIsIntInArray(final String typeDesc) throws IOException
    {
        // We know we are in array, with length prefix so:
        if (!_streamReadContext.expectMoreValues()) {
            _tagValues.clear();
            _stringRefs.pop();
            _streamReadContext = _streamReadContext.getParent();
            _updateToken(JsonToken.END_ARRAY);
            return false;
        }

        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _eofAsNextToken();
                return false;
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        int type = (ch >> 5);
        int lowBits = ch & 0x1F;

        // 01-Nov-2019, tatu: We may actually need tag so decode it, but do not assign
        //   (that'd override tag we already have)
        TagList tagValues = null;
        while (type == 6) {
            if (tagValues == null) {
                tagValues = new TagList();
            }
            tagValues.add(_decodeTag(lowBits));
            if ((_inputPtr >= _inputEnd) && !loadMore()) {
                _eofAsNextToken();
                return false;
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            type = (ch >> 5);
            lowBits = ch & 0x1F;
        }

        switch (type) {
        case 0: // positive int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = lowBits;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = _decode8Bits();
                    break;
                case 1:
                    _numberInt = _decode16Bits();
                    break;
                case 2:
                    {
                        int v = _decode32Bits();
                        if (v >= 0) {
                            _numberInt = v;
                        } else {
                            long l = (long) v;
                            _numberLong = l & 0xFFFFFFFFL;
                            _numTypesValid = NR_LONG;
                        }
                    }
                    break;
                case 3:
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = l;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigPositive(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            if (tagValues == null) {
                _updateToken(JsonToken.VALUE_NUMBER_INT);
            } else {
                _handleTaggedInt(tagValues);
            }
            return true;
        case 1: // negative int
            _numTypesValid = NR_INT;
            if (lowBits <= 23) {
                _numberInt = -lowBits - 1;
            } else {
                switch (lowBits - 24) {
                case 0:
                    _numberInt = -_decode8Bits() - 1;
                    break;
                case 1:
                    _numberInt = -_decode16Bits() - 1;
                    break;
                case 2:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        int v = _decode32Bits();
                        if (v < 0) {
                            long unsignedBase = (long) v & 0xFFFFFFFFL;
                            _numberLong = -unsignedBase - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberInt = -v - 1;
                        }
                    }
                    break;
                case 3:
                    // 15-Oct-2016, as per [dataformats-binary#30], we got an edge case here
                    {
                        long l = _decode64Bits();
                        if (l >= 0L) {
                            _numberLong = -l - 1L;
                            _numTypesValid = NR_LONG;
                        } else {
                            _numberBigInt = _bigNegative(l);
                            _numTypesValid = NR_BIGINT;
                        }
                    }
                    break;
                default:
                    _invalidToken(ch);
                }
            }
            _updateToken(JsonToken.VALUE_NUMBER_INT);
            return true;

        case 2: // byte[]
            // ... but we only really care about very specific case of `BigInteger`
            if (tagValues == null) {
                break;
            }
            _typeByte = ch;
            _tokenIncomplete = true;
            _updateToken(_handleTaggedBinary(tagValues));
            return (_currToken == JsonToken.VALUE_NUMBER_INT);
        }

        // Important! Need to push back the last byte read (but not consumed)
        --_inputPtr;
        // and now it is safe to decode next token, too
        nextToken();
        return false;
    }

    protected final boolean _checkNextIsEndArray() throws IOException
    {
        // We know we are in array, with length prefix, and this is where we should be:
        if (!_streamReadContext.expectMoreValues()) {
            _tagValues.clear();
            _stringRefs.pop();
            _streamReadContext = _streamReadContext.getParent();
            _updateToken(JsonToken.END_ARRAY);
            return true;
        }

        // But while we otherwise could bail out we should check what follows for better
        // error reporting... yet we ALSO must avoid direct call to `nextToken()` to avoid
        // [dataformats-binary#185]
        int ch = _inputBuffer[_inputPtr++];
        int type = (ch >> 5) & 0x7;

        // No use for tag but removing it is necessary
        while (type == 6) {
            if ((_inputPtr >= _inputEnd) && !loadMore()) {
                _eofAsNextToken();
                return false;
            }
            ch = _inputBuffer[_inputPtr++];
            type = (ch >> 5) & 0x7;
        }
        // and that's what we need to do for safety; now can drop to generic handling:

        // Important! Need to push back the last byte read (but not consumed)
        --_inputPtr;
        return nextToken() == JsonToken.END_ARRAY; // should never match
    }

    // base impl is fine:
    //public String getCurrentName() throws IOException

    /**
     * Method for forcing full read of current token, even if it might otherwise
     * only be read if data is accessed via {@link #getText} and similar methods.
     */
    @Override
    public void finishToken() throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
    }

    /*
    /**********************************************************
    /* Public API, traversal, nextXxxValue/nextFieldName
    /**********************************************************
     */

    @Override
    public boolean nextFieldName(SerializableString str) throws IOException
    {
        // Two parsing modes; can only succeed if expecting field name, so handle that first:
        if (_streamReadContext.inObject() && _currToken != JsonToken.FIELD_NAME) {
            _numTypesValid = NR_UNKNOWN;
            if (_tokenIncomplete) {
                _skipIncomplete();
            }
            _tokenInputTotal = _currInputProcessed + _inputPtr;
            _binaryValue = null;
            _tagValues.clear();
            // completed the whole Object?
            if (!_streamReadContext.expectMoreValues()) {
                _stringRefs.pop();
                _streamReadContext = _streamReadContext.getParent();
                _updateToken(JsonToken.END_OBJECT);
                return false;
            }
            byte[] nameBytes = str.asQuotedUTF8();
            final int byteLen = nameBytes.length;
            // fine; require room for up to 2-byte marker, data itself
            int ptr = _inputPtr;
            if ((ptr + byteLen + 1) < _inputEnd) {
                final int ch = _inputBuffer[ptr++];
                // only handle usual textual type
                if (((ch >> 5) & 0x7) == MAJOR_TYPE_TEXT) {
                    int lenMarker = ch & 0x1F;
                    if (lenMarker <= 24) {
                        if (lenMarker == 23) {
                            lenMarker = _inputBuffer[ptr++] & 0xFF;
                        }
                        if (lenMarker == byteLen) {
                            int i = 0;
                            while (true) {
                                if (i == lenMarker) {
                                    _inputPtr = ptr + i;
                                    String strValue = str.getValue();
                                    if (!_stringRefs.empty() &&
                                            shouldReferenceString(_stringRefs.peek().stringRefs.size(),
                                                    byteLen)) {
                                        _stringRefs.peek().stringRefs.add(strValue);
                                    }
                                    _streamReadContext.setCurrentName(strValue);
                                    _updateToken(JsonToken.FIELD_NAME);
                                    return true;
                                }
                                if (nameBytes[i] != _inputBuffer[ptr + i]) {
                                    break;
                                }
                                ++i;
                            }
                        }
                    }
                }
            }
        }
        // otherwise just fall back to default handling; should occur rarely
        return (nextToken() == JsonToken.FIELD_NAME) && str.getValue().equals(getCurrentName());
    }

    @Override
    public String nextFieldName() throws IOException
    {
        if (_streamReadContext.inObject() && _currToken != JsonToken.FIELD_NAME) {
            _numTypesValid = NR_UNKNOWN;
            if (_tokenIncomplete) {
                _skipIncomplete();
            }
            _tokenInputTotal = _currInputProcessed + _inputPtr;
            _binaryValue = null;
            _tagValues.clear();
            // completed the whole Object?
            if (!_streamReadContext.expectMoreValues()) {
                _stringRefs.pop();
                _streamReadContext = _streamReadContext.getParent();
                _updateToken(JsonToken.END_OBJECT);
                return null;
            }
            // inlined "_decodeFieldName()"

            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _eofAsNextToken();
                }
            }
            int ch = _inputBuffer[_inputPtr++] & 0xFF;
            int type = (ch >> 5);
            int lowBits = ch & 0x1F;

            // One special case: need to consider tag as prefix first:
            while (type == 6) {
                _tagValues.add(_decodeTag(lowBits));
                if (_inputPtr >= _inputEnd) {
                    if (!loadMore()) {
                        _eofAsNextToken();
                        return null;
                    }
                }
                ch = _inputBuffer[_inputPtr++] & 0xFF;
                type = (ch >> 5);
                lowBits = ch & 0x1F;
            }

            // offline non-String cases, as they are expected to be rare
            if (type != CBORConstants.MAJOR_TYPE_TEXT) {
                if (ch == 0xFF) { // end-of-object, common
                    if (!_streamReadContext.hasExpectedLength()) {
                        _stringRefs.pop();
                        _streamReadContext = _streamReadContext.getParent();
                        _updateToken(JsonToken.END_OBJECT);
                        return null;
                    }
                    _reportUnexpectedBreak();
                }
                _decodeNonStringName(ch, _tagValues);
                _updateToken(JsonToken.FIELD_NAME);
                return getText();
            }
            final int lenMarker = ch & 0x1F;
            _sharedString = null;
            String name;
            boolean chunked = false;
            if (lenMarker <= 23) {
                if (lenMarker == 0) {
                    name = "";
                } else {
                    if ((_inputEnd - _inputPtr) < lenMarker) {
                        _loadToHaveAtLeast(lenMarker);
                    }
                    if (_symbolsCanonical) {
                        name = _findDecodedFromSymbols(lenMarker);
                        if (name != null) {
                            _inputPtr += lenMarker;
                        } else {
                            name = _decodeContiguousName(lenMarker);
                            name = _addDecodedToSymbols(lenMarker, name);
                        }
                    } else {
                        name = _decodeContiguousName(lenMarker);
                    }
                }
            } else {
                final int actualLen = _decodeExplicitLength(lenMarker);
                if (actualLen < 0) {
                    chunked = true;
                    name = _decodeChunkedName();
                } else {
                    name = _decodeLongerName(actualLen);
                }
            }
            if (!chunked && !_stringRefs.empty() &&
                    shouldReferenceString(_stringRefs.peek().stringRefs.size(), lenMarker)) {
                _stringRefs.peek().stringRefs.add(name);
                _sharedString = name;
            }
            _streamReadContext.setCurrentName(name);
            _updateToken(JsonToken.FIELD_NAME);
            return name;
        }
        // otherwise just fall back to default handling; should occur rarely
        return (nextToken() == JsonToken.FIELD_NAME) ? currentName() : null;
    }

    // 06-Apr-2023, tatu: Before Jackson 2.15, we had optimized variant, but
    //    due to sheer complexity this was removed from 2.15 to avoid subtle
    //    bugs (like [dataformats-binary#372]
    @Override
    public String nextTextValue() throws IOException
    {
        if (nextToken() == JsonToken.VALUE_STRING) {
            return getText();
        }
        return null;
    }

    @Override
    public int nextIntValue(int defaultValue) throws IOException
    {
        if (nextToken() == JsonToken.VALUE_NUMBER_INT) {
            return getIntValue();
        }
        return defaultValue;
    }

    @Override
    public long nextLongValue(long defaultValue) throws IOException
    {
        if (nextToken() == JsonToken.VALUE_NUMBER_INT) {
            return getLongValue();
        }
        return defaultValue;
    }

    @Override
    public Boolean nextBooleanValue() throws IOException
    {
        JsonToken t = nextToken();
        if (t == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        }
        if (t == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE;
        }
        return null;
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
        JsonToken t = _currToken;
        if (_tokenIncomplete) {
            if (t == JsonToken.VALUE_STRING) {
                return _finishTextToken(_typeByte);
            }
        }
        if (t == JsonToken.VALUE_STRING) {
            return _sharedString == null ? _textBuffer.contentsAsString() : _sharedString;
        }
        if (t == null) { // null only before/after document
            return null;
        }
        if (t == JsonToken.FIELD_NAME) {
            return _streamReadContext.getCurrentName();
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
            if (_currToken == JsonToken.VALUE_STRING) {
                return _sharedString == null ? _textBuffer.getTextBuffer() : _sharedString.toCharArray();
            }
            if (_currToken == JsonToken.FIELD_NAME) {
                return _streamReadContext.getCurrentName().toCharArray();
            }
            if ((_currToken == JsonToken.VALUE_NUMBER_INT)
                    || (_currToken == JsonToken.VALUE_NUMBER_FLOAT)) {
                return getNumberValue().toString().toCharArray();
            }
            return _currToken.asCharArray();
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
            if (_currToken == JsonToken.VALUE_STRING) {
                return _sharedString == null ? _textBuffer.size() : _sharedString.length();
            }
            if (_currToken == JsonToken.FIELD_NAME) {
                return _streamReadContext.getCurrentName().length();
            }
            if ((_currToken == JsonToken.VALUE_NUMBER_INT)
                    || (_currToken == JsonToken.VALUE_NUMBER_FLOAT)) {
                return getNumberValue().toString().length();
            }
            final char[] ch = _currToken.asCharArray();
            if (ch != null) {
                return ch.length;
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
        // inlined 'getText()' for common case of having String
        if (_tokenIncomplete) {
            if (_currToken == JsonToken.VALUE_STRING) {
                return _finishTextToken(_typeByte);
            }
        }
        if (_currToken == JsonToken.VALUE_STRING) {
            return _sharedString == null ? _textBuffer.contentsAsString() : _sharedString;
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
        if (_tokenIncomplete) {
            _finishToken();
        }
        JsonToken t = _currToken;
        if (t == JsonToken.VALUE_STRING) {
            if (_sharedString == null) {
                return _textBuffer.contentsToWriter(writer);
            } else {
                writer.write(_sharedString);
                return _sharedString.length();
            }
        }
        if (t == JsonToken.FIELD_NAME) {
            String n = _streamReadContext.getCurrentName();
            writer.write(n);
            return n.length();
        }
        if (t != null) {
            if (t.isNumeric()) {
                if (_sharedString == null) {
                    return _textBuffer.contentsToWriter(writer);
                } else {
                    writer.write(_sharedString);
                    return _sharedString.length();
                }
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
        if (_currToken == JsonToken.VALUE_EMBEDDED_OBJECT ) {
            if (_tokenIncomplete) {
                _finishToken();
            }
        } else  if (_currToken == JsonToken.VALUE_STRING) {
            return _getBinaryFromString(b64variant);
        } else {
            throw _constructReadException(
"Current token (%s) not VALUE_EMBEDDED_OBJECT or VALUE_STRING, can not access as binary",
                    currentToken());
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
        if (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT) {
            if (_currToken == JsonToken.VALUE_STRING) {
                // 26-Jun-2021, tatu: Not optimized; could make streaming if we
                //    really want in future
                final byte[] b = _getBinaryFromString(b64variant);
                final int len = b.length;
                out.write(b, 0, len);
                return len;
            }
            throw _constructReadException(
"Current token (%s) not VALUE_EMBEDDED_OBJECT or VALUE_STRING, can not access as binary",
                    currentToken());
        }
        if (!_tokenIncomplete) { // someone already decoded or read
            if (_binaryValue == null) { // if this method called twice in a row
                return 0;
            }
            final int len = _binaryValue.length;
            out.write(_binaryValue, 0, len);
            return len;
        }

        _tokenIncomplete = false;
        int len = _decodeExplicitLength(_typeByte & 0x1F);
        if (!_stringRefs.empty()) {
            out.write(_finishBytes(len));
            return len;
        }

        if (len >= 0) { // non-chunked
            return _readAndWriteBytes(out, len);
        }
        // Chunked...
        int total = 0;
        while (true) {
            len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_BYTES);
            if (len < 0) {
                return total;
            }
            total += _readAndWriteBytes(out, len);
        }
    }

    private int _readAndWriteBytes(OutputStream out, final int total) throws IOException
    {
        int left = total;
        while (left > 0) {
            int avail = _inputEnd - _inputPtr;
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _reportIncompleteBinaryRead(total, total-left);
                }
                avail = _inputEnd - _inputPtr;
            }
            int count = Math.min(avail, left);
            out.write(_inputBuffer, _inputPtr, count);
            _inputPtr += count;
            left -= count;
        }
        _tokenIncomplete = false;
        return total;
    }

    // @since 2.13
    private final byte[] _getBinaryFromString(Base64Variant variant) throws IOException
    {
        if (_tokenIncomplete) {
            _finishToken();
        }
        if (_binaryValue == null) {
            // 26-Jun-2021, tatu: Copied from ParserBase
            ByteArrayBuilder builder = _getByteArrayBuilder();
            _decodeBase64(getText(), builder, variant);
            _binaryValue = builder.toByteArray();
        }
        return _binaryValue;
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
                return !Double.isFinite(_numberDouble);
            }
            if ((_numTypesValid & NR_FLOAT) != 0) {
                return !Float.isFinite(_numberFloat);
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

    @Override // @since 2.12 -- for (most?) binary formats exactness guaranteed anyway
    public final Number getNumberValueExact() throws IOException {
        return getNumberValue();
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

    @Override // since 2.17
    public NumberTypeFP getNumberTypeFP() throws IOException
    {
        if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
            if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
                return NumberTypeFP.BIG_DECIMAL;
            }
            if ((_numTypesValid & NR_DOUBLE) != 0) {
                return NumberTypeFP.DOUBLE64;
            }
            if ((_numTypesValid & NR_FLOAT) != 0) {
                return NumberTypeFP.FLOAT32;
            }
        }
        return NumberTypeFP.UNKNOWN;
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
        _reportError("Current token ("+currentToken()+") not numeric, can not use numeric value accessors");
    }

    protected void convertNumberToInt() throws IOException
    {
        // First, converting from long ought to be easy
        if ((_numTypesValid & NR_LONG) != 0) {
            // Let's verify it's lossless conversion by simple roundtrip
            int result = (int) _numberLong;
            if (((long) result) != _numberLong) {
                reportOverflowInt(String.valueOf(_numberLong));
            }
            _numberInt = result;
        } else if ((_numTypesValid & NR_BIGINT) != 0) {
            if (BI_MIN_INT.compareTo(_numberBigInt) > 0
                    || BI_MAX_INT.compareTo(_numberBigInt) < 0) {
                reportOverflowInt(String.valueOf(_numberBigInt));
            }
            _numberInt = _numberBigInt.intValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            // Need to check boundaries
            if (_numberDouble < MIN_INT_D || _numberDouble > MAX_INT_D) {
                reportOverflowInt(String.valueOf(_numberDouble));
            }
            _numberInt = (int) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_INT_D || _numberFloat > MAX_INT_D) {
                reportOverflowInt(String.valueOf(_numberFloat));
            }
            _numberInt = (int) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_INT.compareTo(_numberBigDecimal) > 0
                || BD_MAX_INT.compareTo(_numberBigDecimal) < 0) {
                reportOverflowInt(String.valueOf(_numberBigDecimal));
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
                reportOverflowLong(String.valueOf(_numberBigInt));
            }
            _numberLong = _numberBigInt.longValue();
        } else if ((_numTypesValid & NR_DOUBLE) != 0) {
            if (_numberDouble < MIN_LONG_D || _numberDouble > MAX_LONG_D) {
                reportOverflowLong(String.valueOf(_numberDouble));
            }
            _numberLong = (long) _numberDouble;
        } else if ((_numTypesValid & NR_FLOAT) != 0) {
            if (_numberFloat < MIN_LONG_D || _numberFloat > MAX_LONG_D) {
                reportOverflowLong(String.valueOf(_numberFloat));
            }
            _numberLong = (long) _numberFloat;
        } else if ((_numTypesValid & NR_BIGDECIMAL) != 0) {
            if (BD_MIN_LONG.compareTo(_numberBigDecimal) > 0
                || BD_MAX_LONG.compareTo(_numberBigDecimal) < 0) {
                reportOverflowLong(String.valueOf(_numberBigDecimal));
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
            _streamReadConstraints.validateBigIntegerScale(_numberBigDecimal.scale());
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
            final String text = getText();
            // 16-Jan-2024, tatu: OSS-Fuzz managed to trigger this; let's fail
            //   explicitly
            if (text == null) {
                _throwInternal();
            }
            _streamReadConstraints.validateFPLength(text.length());
            _numberBigDecimal = NumberInput.parseBigDecimal(
                    text, isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER));
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
        _sharedString = null;
        int ch = _typeByte;
        final int type = ((ch >> 5) & 0x7);
        ch &= 0x1F;

        // Either String or byte[]
        if (type != CBORConstants.MAJOR_TYPE_TEXT) {
            if (type == CBORConstants.MAJOR_TYPE_BYTES) {
                _binaryValue = _finishBytes(_decodeExplicitLength(ch));
                return;
            }
            // should never happen so
            _throwInternal();
        }

        // String value, decode
        final int len = _decodeExplicitLength(ch);

        if (len <= 0) {
            if (len < 0) {
                _finishChunkedText();
            } else {
                _textBuffer.resetWithEmpty();
            }
            return;
        }
        // 29-Jan-2021, tatu: as per [dataformats-binary#238] must keep in mind that
        //    the longest individual unit is 4 bytes (surrogate pair) so we
        //    actually need len+3 bytes to avoid bounds checks
        // 18-Jan-2024, tatu: For malicious input / Fuzzers, need to worry about overflow
        //    like Integer.MAX_VALUE
        final int needed = Math.max(len, len + 3);
        final int available = _inputEnd - _inputPtr;

        if ((available >= needed)
                // if not, could we read? NOTE: we do not require it, just attempt to read
                    || ((_inputBuffer.length >= needed)
                            && _tryToLoadToHaveAtLeast(needed))) {
                _finishShortText(len);
                return;
        }
        // If not enough space, need handling similar to chunked
        _finishLongText(len);
    }

    /**
     * @since 2.6
     */
    protected String _finishTextToken(int ch) throws IOException
    {
        _tokenIncomplete = false;
        _sharedString = null;
        final int type = ((ch >> 5) & 0x7);
        ch &= 0x1F;

        // sanity check
        if (type != CBORConstants.MAJOR_TYPE_TEXT) {
            // should never happen so
            _throwInternal();
        }

        // String value, decode
        final int len = _decodeExplicitLength(ch);
        if (len <= 0) {
            if (len == 0) {
                _textBuffer.resetWithEmpty();
                return "";
            }
            _finishChunkedText();
            return _textBuffer.contentsAsString();
        }
        // 29-Jan-2021, tatu: as per [dataformats-binary#238] must keep in mind that
        //    the longest individual unit is 4 bytes (surrogate pair) so we
        //    actually need len+3 bytes to avoid bounds checks

        // 19-Mar-2021, tatu: [dataformats-binary#259] shows the case where length
        //    we get is Integer.MAX_VALUE, leading to overflow. Could change values
        //    to longs but simpler to truncate "needed" (will never pass following test
        //    due to inputBuffer never being even close to that big).

        final int needed = Math.max(len + 3, len);
        final int available = _inputEnd - _inputPtr;

        if ((available >= needed)
            // if not, could we read? NOTE: we do not require it, just attempt to read
                || ((_inputBuffer.length >= needed)
                        && _tryToLoadToHaveAtLeast(needed))) {
            return _finishShortText(len);
        }
        // If not enough space, need handling similar to chunked
        return _finishLongText(len);
    }

    private final String _finishShortText(int len) throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (outBuf.length <= len) { // one minor complication
            // +1 to catch possible broken 4-byte UTF-8 code point (which
            // expands to 2 chars, unlike 1 - 3 byte ones) at the end
            outBuf = _textBuffer.expandCurrentSegment(len+1);
        }

        StringRefList stringRefs = null;
        if (!_stringRefs.empty() &&
                shouldReferenceString(_stringRefs.peek().stringRefs.size(), len)) {
            stringRefs = _stringRefs.peek();
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
                String str = _textBuffer.setCurrentAndReturn(outPtr);
                if (stringRefs != null) {
                    stringRefs.stringRefs.add(str);
                    _sharedString = str;
                }
                return str;
            }
        }
        final int[] codes = UTF8_UNIT_CODES;
        do {
            i = inputBuf[inPtr++] & 0xFF;
            switch (codes[i]) {
            case 0:
                break;
            case 1:
                {
                    final int c2 = inputBuf[inPtr++];
                    if ((c2 & 0xC0) != 0x080) {
                        _reportInvalidOther(c2 & 0xFF, inPtr);
                    }
                    i = ((i & 0x1F) << 6) | (c2 & 0x3F);
                }
                break;
            case 2:
                {
                    final int c2 = inputBuf[inPtr++];
                    if ((c2 & 0xC0) != 0x080) {
                        _reportInvalidOther(c2 & 0xFF, inPtr);
                    }
                    final int c3 = inputBuf[inPtr++];
                    if ((c3 & 0xC0) != 0x080) {
                        _reportInvalidOther(c3 & 0xFF, inPtr);
                    }
                    i = ((i & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);
                }
                break;
            case 3:
                // 30-Jan-2021, tatu: TODO - validate these too?
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
                _reportInvalidInitial(i);
            }
            outBuf[outPtr++] = (char) i;
        } while (inPtr < end);
        // 11-Jan-2024, tatu: Not the best way to deal with malformed last
        //    character, but let's try this wrt
        //    https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=35979
        if (inPtr > end) {
            throw _constructReadException("Malformed UTF-8 character at the end of a (non-chunked) text segment");
        }
        String str = _textBuffer.setCurrentAndReturn(outPtr);
        if (stringRefs != null) {
            stringRefs.stringRefs.add(str);
            _sharedString = str;
        }
        return str;
    }

    private final String _finishLongText(int len) throws IOException
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
                throw _constructReadException("Malformed UTF-8 character at the end of a (non-chunked) text segment");
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
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                    outEnd = outBuf.length;
                }
                // Let's add first part right away:
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                // Is this good enough error message?
                _reportInvalidInitial(c);
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
        String str = _textBuffer.setCurrentAndReturn(outPtr);
        if (!_stringRefs.empty() &&
                shouldReferenceString(_stringRefs.peek().stringRefs.size(), len)) {
            _stringRefs.peek().stringRefs.add(str);
            _sharedString = str;
        }
        return str;
    }

    private final void _finishChunkedText() throws IOException
    {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        final int[] codes = UTF8_UNIT_CODES;
        int outEnd = outBuf.length;
        final byte[] input = _inputBuffer;

        _chunkEnd = _inputPtr;
        _chunkLeft = 0;

        while (true) {
            // at byte boundary fine to get break marker, hence different:
            if (_inputPtr >= _chunkEnd) {
                // end of chunk? get a new one, if there is one; if not, we are done
                if (_chunkLeft == 0) {
                    int len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_TEXT);
                    if (len <= 0) { // fine at this point (but not later)
                        // 01-Apr-2021 (sic!), tatu: 0-byte length legal if nonsensical
                        if (len == 0) {
                            continue;
                        }
                        break;
                    }
                    _chunkLeft = len;
                    int end = _inputPtr + len;
                    if (end <= _inputEnd) { // all within buffer
                        _chunkLeft = 0;
                        _chunkEnd = end;
                    } else { // stretches beyond
                        _chunkLeft = (end - _inputEnd);
                        _chunkEnd = _inputEnd;
                    }
                }
                // besides of which just need to ensure there's content
                if (_inputPtr >= _inputEnd) { // end of buffer, but not necessarily chunk
                    loadMoreGuaranteed();
                    int end = _inputPtr + _chunkLeft;
                    if (end <= _inputEnd) { // all within buffer
                        _chunkLeft = 0;
                        _chunkEnd = end;
                    } else { // stretches beyond
                        _chunkLeft = (end - _inputEnd);
                        _chunkEnd = _inputEnd;
                    }
                }
            }
            int c = input[_inputPtr++] & 0xFF;
            int code = codes[c];
            if (code == 0 && outPtr < outEnd) {
                outBuf[outPtr++] = (char) c;
                continue;
            }

            switch (code) {
            case 0:
                break;
            case 1: // 2-byte UTF
                {
                    int d = _nextChunkedByte();
                    if ((d & 0xC0) != 0x080) {
                        _reportInvalidOther(d & 0xFF, _inputPtr);
                    }
                    c = ((c & 0x1F) << 6) | (d & 0x3F);
                }
                break;
            case 2: // 3-byte UTF
                c = _decodeChunkedUTF8_3(c);
                break;
            case 3: // 4-byte UTF
                c = _decodeChunkedUTF8_4(c);
                // Let's add first part right away:
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                    outEnd = outBuf.length;
                }
                outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                c = 0xDC00 | (c & 0x3FF);
                // And let the other char output down below
                break;
            default:
                // Is this good enough error message?
                _reportInvalidInitial(c);
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

    // NOTE! ALWAYS called for non-first byte of multi-byte UTF-8 code point
    private final int _nextChunkedByte() throws IOException {
        int inPtr = _inputPtr;

        // NOTE: _chunkEnd less than or equal to _inputEnd
        if (inPtr >= _chunkEnd) {
            return _nextChunkedByte2();
        }
        int ch = _inputBuffer[inPtr];
        _inputPtr = inPtr+1;
        return ch;
    }

    // NOTE! ALWAYS called for non-first byte of multi-byte UTF-8 code point
    private final int _nextChunkedByte2() throws IOException
    {
        // two possibilities: either end of buffer (in which case, just load more),
        // or end of chunk

        if (_inputPtr >= _inputEnd) { // end of buffer, but not necessarily chunk
            loadMoreGuaranteed();
            if (_chunkLeft > 0) {
                int end = _inputPtr + _chunkLeft;
                if (end <= _inputEnd) { // all within buffer
                    _chunkLeft = 0;
                    _chunkEnd = end;
                } else { // stretches beyond
                    _chunkLeft = (end - _inputEnd);
                    _chunkEnd = _inputEnd;
                }
                // either way, got it now
                return _inputBuffer[_inputPtr++];
            }
        }
        int len = _decodeChunkLength(CBORConstants.MAJOR_TYPE_TEXT);
        // not actually acceptable if we got a split character
        // 29-Jun-2021, tatu: As per CBOR spec:
        // "Note that this implies that the bytes of a single UTF-8 character cannot be
        //  spread between chunks: a new chunk can only be started at a character boundary."
        // -> 0-length chunk not allowed either
        if (len <= 0) {
            _reportInvalidEOF(": chunked Text ends with partial UTF-8 character",
                    JsonToken.VALUE_STRING);
        }
        if (_inputPtr >= _inputEnd) { // Must have at least one byte to return
            loadMoreGuaranteed();
        }

        int end = _inputPtr + len;
        if (end <= _inputEnd) { // all within buffer
            _chunkLeft = 0;
            _chunkEnd = end;
        } else { // stretches beyond
            _chunkLeft = (end - _inputEnd);
            _chunkEnd = _inputEnd;
        }
        // either way, got it now
        return _inputBuffer[_inputPtr++];
    }

    /**
     * Helper called to complete reading of binary data ("byte string") in
     * case contents are needed.
     */
    @SuppressWarnings("resource")
    protected byte[] _finishBytes(int len) throws IOException
    {
        // Chunked?
        // First, simple: non-chunked
        if (len <= 0) {
            if (len == 0) {
                return NO_BYTES;
            }
            return _finishChunkedBytes();
        }

        StringRefList stringRefs = null;
        if (!_stringRefs.empty() &&
                shouldReferenceString(_stringRefs.peek().stringRefs.size(), len)) {
            stringRefs = _stringRefs.peek();
        }

        // Non-chunked, contiguous
        if (len > LONGEST_NON_CHUNKED_BINARY) {
            // [dataformats-binary#186]: avoid immediate allocation for longest
            byte[] b = _finishLongContiguousBytes(len);
            if (stringRefs != null) {
                stringRefs.stringRefs.add(b);
            }
            return b;
        }

        final byte[] b = new byte[len];
        final int expLen = len;
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _reportIncompleteBinaryRead(expLen, 0);
            }
        }

        int ptr = 0;
        while (true) {
            int toAdd = Math.min(len, _inputEnd - _inputPtr);
            System.arraycopy(_inputBuffer, _inputPtr, b, ptr, toAdd);
            _inputPtr += toAdd;
            ptr += toAdd;
            len -= toAdd;
            if (len <= 0) {
                if (stringRefs != null) {
                    stringRefs.stringRefs.add(b);
                }
                return b;
            }
            if (!loadMore()) {
                _reportIncompleteBinaryRead(expLen, ptr);
            }
        }
    }

    // @since 2.12
    protected byte[] _finishChunkedBytes() throws IOException
    {
        // or, if not, chunked...
        ByteArrayBuilder bb = _getByteArrayBuilder();
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++] & 0xFF;
            if (ch == 0xFF) { // end marker
                break;
            }
            // verify that type matches
            int type = (ch >> 5);
            if (type != CBORConstants.MAJOR_TYPE_BYTES) {
                throw _constructReadException(
"Mismatched chunk in chunked content: expected %d but encountered %d",
CBORConstants.MAJOR_TYPE_BYTES, type);
            }
            int len = _decodeExplicitLength(ch & 0x1F);
            if (len < 0) {
                throw _constructReadException("Illegal chunked-length indicator within chunked-length value (type %d)",
                            CBORConstants.MAJOR_TYPE_BYTES);
            }
            final int chunkLen = len;
            while (len > 0) {
                int avail = _inputEnd - _inputPtr;
                if (_inputPtr >= _inputEnd) {
                    if (!loadMore()) {
                        _reportIncompleteBinaryRead(chunkLen, chunkLen-len);
                    }
                    avail = _inputEnd - _inputPtr;
                }
                int count = Math.min(avail, len);
                bb.write(_inputBuffer, _inputPtr, count);
                _inputPtr += count;
                len -= count;
            }
        }
        return bb.toByteArray();
    }

    // @since 2.12
    protected byte[] _finishLongContiguousBytes(final int expLen) throws IOException
    {
        int left = expLen;

        // 04-Dec-2020, tatu: Let's NOT use recycled instance since we have much
        //   longer content and there is likely less benefit of trying to recycle
        //   segments
        try (final ByteArrayBuilder bb = new ByteArrayBuilder(LONGEST_NON_CHUNKED_BINARY >> 1)) {
            while (left > 0) {
                int avail = _inputEnd - _inputPtr;
                if (avail <= 0) {
                    if (!loadMore()) {
                        _reportIncompleteBinaryRead(expLen, expLen-left);
                    }
                    avail = _inputEnd - _inputPtr;
                }
                int count = Math.min(avail, left);
                bb.write(_inputBuffer, _inputPtr, count);
                _inputPtr += count;
                left -= count;
            }
            return bb.toByteArray();
        }
    }

    protected final JsonToken _decodePropertyName() throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            // 30-Jan-2021, tatu: To get more specific exception, won't use
            //   "loadMoreGuaranteed()" but instead:
            if (!loadMore()) {
                _eofAsNextToken();
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        int type = (ch >> 5);
        int lowBits = ch & 0x1F;

        // One special case: need to consider tag as prefix first:
        while (type == 6) {
            _tagValues.add(_decodeTag(lowBits));
            if (_inputPtr >= _inputEnd) {
                if (!loadMore()) {
                    _eofAsNextToken();
                    return null;
                }
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            type = (ch >> 5);
            lowBits = ch & 0x1F;
        }

        // Expecting a String, but may need to allow other types too
        if (type != CBORConstants.MAJOR_TYPE_TEXT) { // the usual case
            if (ch == 0xFF) {
                if (!_streamReadContext.hasExpectedLength()) {
                    _stringRefs.pop();
                    _streamReadContext = _streamReadContext.getParent();
                    return JsonToken.END_OBJECT;
                }
                _reportUnexpectedBreak();
            }
            // offline non-String cases, as they are expected to be rare
            _decodeNonStringName(ch, _tagValues);
            return JsonToken.FIELD_NAME;
        }
        final int lenMarker = ch & 0x1F;
        boolean chunked = false;
        String name;
        if (lenMarker <= 23) {
            if (lenMarker == 0) {
                name = "";
            } else {
                if ((_inputEnd - _inputPtr) < lenMarker) {
                    _loadToHaveAtLeast(lenMarker);
                }
                if (_symbolsCanonical) {
                    name = _findDecodedFromSymbols(lenMarker);
                    if (name != null) {
                        _inputPtr += lenMarker;
                    } else {
                        name = _decodeContiguousName(lenMarker);
                        name = _addDecodedToSymbols(lenMarker, name);
                    }
                } else {
                    name = _decodeContiguousName(lenMarker);
                }
            }
        } else {
            final int actualLen = _decodeExplicitLength(lenMarker);
            if (actualLen < 0) {
                chunked = true;
                name = _decodeChunkedName();
            } else {
                name = _decodeLongerName(actualLen);
            }
        }
        if (!chunked && !_stringRefs.empty() &&
                shouldReferenceString(_stringRefs.peek().stringRefs.size(), lenMarker)) {
            _stringRefs.peek().stringRefs.add(name);
            _sharedString = name;
        }
        _streamReadContext.setCurrentName(name);
        return JsonToken.FIELD_NAME;
    }

    private final String _decodeContiguousName(final int len) throws IOException
    {
        // note: caller ensures we have enough bytes available
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (outBuf.length < len) { // one minor complication
            outBuf = _textBuffer.expandCurrentSegment(len);
        }
        int inPtr = _inputPtr;
        _inputPtr += len;
        final int[] codes = UTF8_UNIT_CODES;
        final byte[] inBuf = _inputBuffer;

        // First a tight loop for ASCII
        final int end = inPtr + len;
        while (true) {
            int i = inBuf[inPtr] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                break;
            }
            outBuf[outPtr++] = (char) i;
            if (++inPtr == end) {
                return _textBuffer.setCurrentAndReturn(outPtr);
            }
        }

        // But in case there's multi-byte char, use a full loop
        while (inPtr < end) {
            int i = inBuf[inPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                // 05-Jul-2021, tatu: As per [dataformats-binary#289] need to
                //     be careful wrt end-of-buffer truncated codepoints
                if ((inPtr + code) > end) {
                    final int firstCharOffset = len - (end - inPtr) - 1;
                    _reportTruncatedUTF8InName(len, firstCharOffset, i, code);
                }

                switch (code) {
                case 1:
                    {
                        final int c2 = inBuf[inPtr++];
                        if ((c2 & 0xC0) != 0x080) {
                            _reportInvalidOther(c2 & 0xFF, inPtr);
                        }
                        i = ((i & 0x1F) << 6) | (c2 & 0x3F);
                    }
                    break;
                case 2:
                    {
                        final int c2 = inBuf[inPtr++];
                        if ((c2 & 0xC0) != 0x080) {
                            _reportInvalidOther(c2 & 0xFF, inPtr);
                        }
                        final int c3 = inBuf[inPtr++];
                        if ((c3 & 0xC0) != 0x080) {
                            _reportInvalidOther(c3 & 0xFF, inPtr);
                        }
                        i = ((i & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);
                    }
                    break;
                case 3:
                    // 30-Jan-2021, tatu: TODO - validate surrogate case too?
                    i = ((i & 0x07) << 18)
                        | ((inBuf[inPtr++] & 0x3F) << 12)
                        | ((inBuf[inPtr++] & 0x3F) << 6)
                        | (inBuf[inPtr++] & 0x3F);
                    // note: this is the codepoint value; need to split, too
                    i -= 0x10000;
                    outBuf[outPtr++] = (char) (0xD800 | (i >> 10));
                    i = 0xDC00 | (i & 0x3FF);
                    break;
                default: // invalid
                    throw _constructReadException("Invalid UTF-8 byte 0x%s in Object property name",
                            Integer.toHexString(i));
                }
            }
            outBuf[outPtr++] = (char) i;
        }
        return _textBuffer.setCurrentAndReturn(outPtr);
    }

    private final String _decodeLongerName(int len) throws IOException
    {
        // [dataformats-binary#288]: non-canonical length of 0 needs to be
        // dealt with
        if (len == 0)  {
            return "";
        }
        // Do we have enough buffered content to read?
        if ((_inputEnd - _inputPtr) < len) {
            // or if not, could we read?
            if (len >= _inputBuffer.length) {
                // If not enough space, need handling similar to chunked
                return _finishLongText(len);
            }
            _loadToHaveAtLeast(len);
        }
        if (_symbolsCanonical) {
            String name = _findDecodedFromSymbols(len);
            if (name != null) {
                _inputPtr += len;
                return name;
            }
            name = _decodeContiguousName(len);
            return _addDecodedToSymbols(len, name);
        }
        return _decodeContiguousName(len);
    }

    private final String _decodeChunkedName() throws IOException
    {
        _finishChunkedText();
        return _textBuffer.contentsAsString();
    }

    /**
     * Method that handles initial token type recognition for token
     * that has to be either FIELD_NAME or END_OBJECT.
     */
    protected final void _decodeNonStringName(int ch, TagList tags) throws IOException
    {
        final int type = ((ch >> 5) & 0x7);
        String name;
        if (type == CBORConstants.MAJOR_TYPE_INT_POS) {
            name = _numberToName(ch, false, tags);
        } else if (type == CBORConstants.MAJOR_TYPE_INT_NEG) {
            name = _numberToName(ch, true, tags);
        } else if (type == CBORConstants.MAJOR_TYPE_BYTES) {
            // 08-Sep-2014, tatu: As per [Issue#5], there are codecs
            //   (f.ex. Perl module "CBOR::XS") that use Binary data...
            final int blen = _decodeExplicitLength(ch & 0x1F);
            byte[] b = _finishBytes(blen);
            // TODO: Optimize, if this becomes commonly used & bottleneck; we have
            //  more optimized UTF-8 codecs available.
            name = new String(b, UTF8);
        } else {
            if ((ch & 0xFF) == CBORConstants.INT_BREAK) {
                _reportUnexpectedBreak();
            }
            throw _constructReadException("Unsupported major type (%d) for CBOR Objects, not (yet?) supported, only Strings",
                    type);
        }
        _streamReadContext.setCurrentName(name);
    }

    /**
     * Helper method for trying to find specified encoded UTF-8 byte sequence
     * from symbol table; if successful avoids actual decoding to String.
     * <p>
     * NOTE: caller MUST ensure input buffer has enough content.
     */
    private final String _findDecodedFromSymbols(final int len) throws IOException
    {
        // First: maybe we already have this name decoded?
        if (len < 5) {
            int inPtr = _inputPtr;
            final byte[] inBuf = _inputBuffer;
            int q = _padQuadForNulls(inBuf[inPtr]);
            if (len > 1) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (len > 2) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    if (len > 3) {
                        q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                    }
                }
            }
            _quad1 = q;
            return _symbols.findName(q);
        }

        final byte[] inBuf = _inputBuffer;
        int inPtr = _inputPtr;

        // First quadbyte is easy
        int q1 = (inBuf[inPtr++] & 0xFF);
        q1 = (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        q1 = (q1 << 8) | (inBuf[inPtr++] & 0xFF);
        q1 = (q1 << 8) | (inBuf[inPtr++] & 0xFF);

        if (len < 9) {
            int q2 = _padQuadForNulls(inBuf[inPtr++]);
            int left = len - 5;
            if (left > 0) {
                q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                if (left > 1) {
                    q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                    if (left > 2) {
                        q2 = (q2 << 8) + (inBuf[inPtr++] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            return _symbols.findName(q1, q2);
        }

        int q2 = (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);
        q2 =  (q2 << 8) | (inBuf[inPtr++] & 0xFF);

        if (len < 13) {
            int q3 = _padQuadForNulls(inBuf[inPtr++]);
            int left = len - 9;
            if (left > 0) {
                q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                if (left > 1) {
                    q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                    if (left > 2) {
                        q3 = (q3 << 8) + (inBuf[inPtr++] & 0xFF);
                    }
                }
            }
            _quad1 = q1;
            _quad2 = q2;
            _quad3 = q3;
            return _symbols.findName(q1, q2, q3);
        }
        return _findDecodedLong(len, q1, q2);
    }

    /**
     * Method for locating names longer than 8 bytes (in UTF-8)
     */
    private final String _findDecodedLong(int len, int q1, int q2) throws IOException
    {
        // first, need enough buffer to store bytes as ints:
        {
            int bufLen = (len + 3) >> 2;
            if (bufLen > _quadBuffer.length) {
                _quadBuffer = _growArrayTo(_quadBuffer, bufLen);
            }
        }
        _quadBuffer[0] = q1;
        _quadBuffer[1] = q2;

        // then decode, full quads first
        int offset = 2;
        int inPtr = _inputPtr+8;
        len -= 8;

        final byte[] inBuf = _inputBuffer;
        do {
            int q = (inBuf[inPtr++] & 0xFF);
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            q = (q << 8) | inBuf[inPtr++] & 0xFF;
            _quadBuffer[offset++] = q;
        } while ((len -= 4) > 3);
        // and then leftovers
        if (len > 0) {
            int q = _padQuadForNulls(inBuf[inPtr]);
            if (len > 1) {
                q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                if (len > 2) {
                    q = (q << 8) + (inBuf[++inPtr] & 0xFF);
                }
            }
            _quadBuffer[offset++] = q;
        }
        return _symbols.findName(_quadBuffer, offset);
    }

    private final String _addDecodedToSymbols(int len, String name) throws IOException {
        if (len < 5) {
            return _symbols.addName(name, _quad1);
        }
        if (len < 9) {
            return _symbols.addName(name, _quad1, _quad2);
        }
        if (len < 13) {
            return _symbols.addName(name, _quad1, _quad2, _quad3);
        }
        int qlen = (len + 3) >> 2;
        return _symbols.addName(name, _quadBuffer, qlen);
    }

    private static int[] _growArrayTo(int[] arr, int minSize) {
        return Arrays.copyOf(arr, minSize+4);
    }

    // Helper method needed to fix [dataformats-binary#312], masking of 0x00 character
    private final static int _padQuadForNulls(int firstByte) {
        return (firstByte & 0xFF) | 0xFFFFFF00;
    }

    /*
    /**********************************************************
    /* Internal methods, skipping
    /**********************************************************
     */

    /**
     * Method called to skip remainders of an incomplete token, when
     * contents themselves will not be needed any more.
     * Only called or byte array and text.
     */
    protected void _skipIncomplete() throws IOException
    {
        _tokenIncomplete = false;
        final int type = ((_typeByte >> 5) & 0x7);

        // Either String or byte[]
        if (type != CBORConstants.MAJOR_TYPE_TEXT
                && type != CBORConstants.MAJOR_TYPE_BYTES) {
            _throwInternal();
        }
        final int lowBits = _typeByte & 0x1F;
        if (lowBits <= 23) {
            if (lowBits > 0) {
                _skipBytes(lowBits);
            }
            return;
        }
        switch (lowBits) {
        case 24:
            _skipBytes(_decode8Bits());
            break;
        case 25:
            _skipBytes(_decode16Bits());
            break;
        case 26:
            _skipBytes(_decode32Bits());
            break;
        case 27: // seriously?
            _skipBytesL(_decode64Bits());
            break;
        case 31:
            _skipChunked(type);
            break;
        default:
            _invalidToken(_typeByte);
        }
    }

    protected void _skipChunked(int expectedType) throws IOException
    {
        while (true) {
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            int ch = _inputBuffer[_inputPtr++] & 0xFF;
            if (ch == 0xFF) {
                return;
            }
            // verify that type matches
            int type = (ch >> 5);
            if (type != expectedType) {
                throw _constructReadException(
"Mismatched chunk in chunked content: expected %d but encountered %s", expectedType, type);
            }

            final int lowBits = ch & 0x1F;

            if (lowBits <= 23) {
                if (lowBits > 0) {
                    _skipBytes(lowBits);
                }
                continue;
            }
            switch (lowBits) {
            case 24:
                _skipBytes(_decode8Bits());
                break;
            case 25:
                _skipBytes(_decode16Bits());
                break;
            case 26:
                _skipBytes(_decode32Bits());
                break;
            case 27: // seriously?
                _skipBytesL(_decode64Bits());
                break;
            case 31:
                throw _constructReadException(
"Invalid chunked-length indicator within chunked-length value (type %d)",
expectedType);
            default:
                _invalidToken(_typeByte);
            }
        }
    }

    protected void _skipBytesL(long llen) throws IOException
    {
        if (llen < 0L) {
            throw _constructReadException(
"Corrupt content: invalid length indicator (%d) encountered during skipping, current token: %s",
llen, currentToken());
        }
        while (llen > MAX_INT_L) {
            _skipBytes((int) MAX_INT_L);
            llen -= MAX_INT_L;
        }
        _skipBytes((int) llen);
    }

    protected void _skipBytes(int len) throws IOException
    {
        if (len < 0) {
            throw _constructReadException(
"Corrupt content: invalid length indicator (%d) encountered during skipping, current token: %s",
len, currentToken());
        }
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

    /*
    /**********************************************************
    /* Internal methods, length/number decoding
    /**********************************************************
     */

    private final int _decodeTag(int lowBits) throws IOException
    {
        if (lowBits <= 23) {
            return lowBits;
        }
        switch (lowBits - 24) {
        case 0:
            return _decode8Bits();
        case 1:
            return _decode16Bits();
        case 2:
            return _decode32Bits();
        case 3:
            // 16-Jan-2014, tatu: Technically legal, but nothing defined, so let's
            //   only allow for cases where encoder is being wasteful...
            long l = _decode64Bits();
            if (l < MIN_INT_L || l > MAX_INT_L) {
                throw _constructReadException("Illegal Tag value: %d", l);
            }
            return (int) l;
        }
        throw _constructReadException("Invalid low bits for Tag token: 0x%s",
                Integer.toHexString(lowBits));
    }

    /**
     * Method used to decode explicit length of a variable-length value
     * (or, for indefinite/chunked, indicate that one is not known).
     * Note that long (64-bit) length is only allowed if it fits in
     * 32-bit signed int, for now; expectation being that longer values
     * are always encoded as chunks.
     */
    private final int _decodeExplicitLength(int lowBits) throws IOException
    {
        // common case, indefinite length; relies on marker
        if (lowBits == 31) {
            return -1;
        }
        if (lowBits <= 23) {
            return lowBits;
        }
        switch (lowBits - 24) {
        case 0:
            return _decode8Bits();
        case 1:
            return _decode16Bits();
        case 2:
            return _decode32Bits();
        case 3:
            long l = _decode64Bits();
            if (l < 0 || l > MAX_INT_L) {
                throw _constructError("Illegal length for "+currentToken()+": "+l);
            }
            return (int) l;
        }
        throw _constructError(String.format(
                "Invalid 5-bit length indicator for `JsonToken.%s`: 0x%02X; only 0x00-0x17, 0x1F allowed",
                currentToken(), lowBits));
    }

    private int _decodeChunkLength(int expType) throws IOException
    {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int ch = (int) _inputBuffer[_inputPtr++] & 0xFF;
        if (ch == CBORConstants.INT_BREAK) {
            return -1;
        }
        int type = (ch >> 5);
        if (type != expType) {
            throw _constructError(String.format(
"Mismatched chunk in chunked content: expected major type %d but encountered %d (byte 0x%02X)",
expType, type, ch));
        }
        int len = _decodeExplicitLength(ch & 0x1F);
        if (len < 0) {
            throw _constructReadException(
"Illegal chunked-length indicator within chunked-length value (major type %d)", expType);
        }
        return len;
    }

    private float _decodeHalfSizeFloat() throws IOException
    {
        int i16 = _decode16Bits() & 0xFFFF;

        boolean neg = (i16 >> 15) != 0;
        int e = (i16 >> 10) & 0x1F;
        int f = i16 & 0x03FF;

        if (e == 0) {
            float result = (float) (MATH_POW_2_NEG14 * (f / MATH_POW_2_10));
            return neg ? -result : result;
        }
        if (e == 0x1F) {
            if (f != 0) return Float.NaN;
            return neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        float result = (float) (Math.pow(2, e - 15) * (1 + f / MATH_POW_2_10));
        return neg ? -result : result;
    }

    private final int _decode8Bits() throws IOException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return _inputBuffer[_inputPtr++] & 0xFF;
    }

    private final int _decode16Bits() throws IOException {
        int ptr = _inputPtr;
        if ((ptr + 1) >= _inputEnd) {
            return _slow16();
        }
        final byte[] b = _inputBuffer;
        int v = ((b[ptr] & 0xFF) << 8) + (b[ptr+1] & 0xFF);
        _inputPtr = ptr+2;
        return v;
    }

    private final int _slow16() throws IOException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int v = (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
    }

    private final int _decode32Bits() throws IOException {
        int ptr = _inputPtr;
        if ((ptr + 3) >= _inputEnd) {
            return _slow32();
        }
        final byte[] b = _inputBuffer;
        int v = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        _inputPtr = ptr;
        return v;
    }

    private final int _slow32() throws IOException {
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        int v = _inputBuffer[_inputPtr++]; // sign will disappear anyway
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        v = (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        v = (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
        if (_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        return (v << 8) + (_inputBuffer[_inputPtr++] & 0xFF);
    }

    private final long _decode64Bits() throws IOException {
        int ptr = _inputPtr;
        if ((ptr + 7) >= _inputEnd) {
            return _slow64();
        }
        final byte[] b = _inputBuffer;
        int i1 = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        int i2 = (b[ptr++] << 24) + ((b[ptr++] & 0xFF) << 16)
                + ((b[ptr++] & 0xFF) << 8) + (b[ptr++] & 0xFF);
        _inputPtr = ptr;
        return _long(i1, i2);
    }

    private final long _slow64() throws IOException {
        return _long(_decode32Bits(), _decode32Bits());
    }

    private final static long _long(int i1, int i2)
    {
        long l1 = i1;
        long l2 = i2;
        l2 = (l2 << 32) >>> 32;
        return (l1 << 32) + l2;
    }

    /**
     * Helper method to encapsulate details of handling of mysterious `undefined` value
     * that is allowed to be used as something encoder could not handle (as per spec),
     * whatever the heck that should be.
     * Current definition for 2.9 is that we will be return {@link JsonToken#VALUE_NULL}, but
     * for later versions it is likely that we will alternatively allow decoding as
     * {@link JsonToken#VALUE_EMBEDDED_OBJECT} with "embedded value" of `null`.
     *
     * @since 2.9.6
     */
    protected JsonToken _decodeUndefinedValue() throws IOException {
        return JsonToken.VALUE_NULL;
    }

    /**
     * Helper method that deals with details of decoding unallocated "simple values"
     * and exposing them as expected token.
     * <p>
     * As of Jackson 2.12, simple values are exposed as
     * {@link JsonToken#VALUE_NUMBER_INT}s,
     * but in later versions this is planned to be changed to separate value type.
     *
     * @since 2.12
     */
    public JsonToken _decodeSimpleValue(int lowBits, int ch) throws IOException {
        if (lowBits > 24) {
            _invalidToken(ch);
        }
        if (lowBits < 24) {
            _numberInt = lowBits;
        } else { // need another byte
            if (_inputPtr >= _inputEnd) {
                loadMoreGuaranteed();
            }
            _numberInt = _inputBuffer[_inputPtr++] & 0xFF;
            // As per CBOR spec, values below 32 not allowed to avoid
            // confusion (as well as guarantee uniqueness of encoding)
            if (_numberInt < 32) {
                throw _constructError("Invalid second byte for simple value: 0x"
                        +Integer.toHexString(_numberInt)+" (only values 0x20 - 0xFF allowed)");
            }
        }

        // 25-Nov-2020, tatu: Although ideally we should report these
        //    as `JsonToken.VALUE_EMBEDDED_OBJECT`, due to late addition
        //    of handling in 2.12, simple value in 2.12 will be reported
        //    as simple ints.

        _numTypesValid = NR_INT;
        return (JsonToken.VALUE_NUMBER_INT);
    }

    /*
    /**********************************************************
    /* Internal methods, UTF8 decoding
    /**********************************************************
     */

    /*
    private final int X_decodeUTF8_2(int c) throws IOException {
        int d = _nextByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }
    */

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

    private final int _decodeChunkedUTF8_3(int c1) throws IOException
    {
        c1 &= 0x0F;
        int d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = _nextChunkedByte();
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

    private final int _decodeChunkedUTF8_4(int c) throws IOException
    {
        int d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        d = _nextChunkedByte();
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }

    /*
    /**********************************************************
    /* Low-level reading, other
    /**********************************************************
     */

    protected boolean loadMore() throws IOException
    {
        if (_inputStream != null) {
            _currInputProcessed += _inputEnd;
            _streamReadConstraints.validateDocumentLength(_currInputProcessed);
            int count = _inputStream.read(_inputBuffer, 0, _inputBuffer.length);
            if (count > 0) {
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

    protected void loadMoreGuaranteed() throws IOException {
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
        int amount = _inputEnd - _inputPtr;
        if (amount > 0 && _inputPtr > 0) {
            //_currInputRowStart -= _inputPtr;
            System.arraycopy(_inputBuffer, _inputPtr, _inputBuffer, 0, amount);
            _inputEnd = amount;
        } else {
            _inputEnd = 0;
        }
        // Needs to be done here, as per [dataformats-binary#178]
        _currInputProcessed += _inputPtr;
        _streamReadConstraints.validateDocumentLength(_currInputProcessed);
        _inputPtr = 0;
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

    // @since 2.12.2
    protected final boolean _tryToLoadToHaveAtLeast(int minAvailable) throws IOException
    {
        // No input stream, no leading (either we are closed, or have non-stream input source)
        if (_inputStream == null) {
            return false;
        }
        // Need to move remaining data in front?
        int amount = _inputEnd - _inputPtr;
        if (amount > 0 && _inputPtr > 0) {
            //_currInputRowStart -= _inputPtr;
            System.arraycopy(_inputBuffer, _inputPtr, _inputBuffer, 0, amount);
            _inputEnd = amount;
        } else {
            _inputEnd = 0;
        }
        // Needs to be done here, as per [dataformats-binary#178]
        _currInputProcessed += _inputPtr;
        _streamReadConstraints.validateDocumentLength(_currInputProcessed);
        _inputPtr = 0;
        while (_inputEnd < minAvailable) {
            int count = _inputStream.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            if (count < 1) {
                // End of input; not ideal but we'll accept it here
                _closeInput();
                return false;
            }
            _inputEnd += count;
        }
        return true;
    }

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
            if (_ioContext.isResourceManaged() || isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE)) {
                _inputStream.close();
            }
            _inputStream = null;
        }
    }

    @Override
    protected void _handleEOF() throws JsonParseException {
        if (_streamReadContext.inRoot()) {
            return;
        }
        // Ok; end-marker or fixed-length Array/Object?
        final JsonLocation loc = _streamReadContext.startLocation(_ioContext.contentReference());
        final String startLocDesc = (loc == null) ? "[N/A]" : loc.sourceDescription();
        if (_streamReadContext.hasExpectedLength()) { // specific length
            final int expMore = _streamReadContext.getRemainingExpectedLength();
            if (_streamReadContext.inArray()) {
                _reportInvalidEOF(String.format(
                        " in Array value: expected %d more elements (start token at %s)",
                        expMore, startLocDesc),
                        null);
            } else {
                _reportInvalidEOF(String.format(
                        " in Object value: expected %d more properties (start token at %s)",
                        expMore, startLocDesc),
                        null);
            }
        } else {
            if (_streamReadContext.inArray()) {
                _reportInvalidEOF(String.format(
                        " in Array value: expected an element or close marker (0xFF) (start token at %s)",
                        startLocDesc),
                        null);
            } else {
                _reportInvalidEOF(String.format(
                        " in Object value: expected a property or close marker (0xFF) (start token at %s)",
                        startLocDesc),
                        null);
            }
        }
    }

    // Was "_handleCBOREOF()" before 2.13
    protected JsonToken _eofAsNextToken() throws IOException {
        // NOTE: here we can and should close input, release buffers, since
        // this is "hard" EOF, not a boundary imposed by header token.
        _tagValues.clear();
        close();
        // 30-Jan-2021, tatu: But also MUST verify that end-of-content is actually
        //   allowed (see [dataformats-binary#240] for example)
        _handleEOF();
        return _updateTokenToNull();
    }

    /*
    /**********************************************************
    /* Internal methods, error handling, reporting
    /**********************************************************
     */

    protected void _invalidToken(int ch) throws JsonParseException {
        ch &= 0xFF;
        if (ch == 0xFF) {
            throw _constructError("Mismatched BREAK byte (0xFF): encountered where value expected");
        }
        throw _constructError("Invalid CBOR value token (first byte): 0x"+Integer.toHexString(ch));
    }

    protected <T> T _reportUnexpectedBreak() throws IOException {
        if (_streamReadContext.inRoot()) {
            throw _constructError("Unexpected Break (0xFF) token in Root context");
        }
        throw _constructError("Unexpected Break (0xFF) token in definite length ("
                +_streamReadContext.getExpectedLength()+") "
                +(_streamReadContext.inObject() ? "Object" : "Array" ));
    }

    protected void _reportInvalidInitial(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 start byte 0x"+Integer.toHexString(mask));
    }

    protected void _reportInvalidOther(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 middle byte 0x"+Integer.toHexString(mask));
    }

    protected void _reportInvalidOther(int mask, int ptr) throws JsonParseException {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }

    // @since 2.12
    protected void _reportIncompleteBinaryRead(int expLen, int actLen) throws IOException
    {
        _reportInvalidEOF(String.format(" for Binary value: expected %d bytes, only found %d",
                expLen, actLen), _currToken);
    }

    // @since 2.13
    /*
    private String _reportTruncatedUTF8InString(int strLenBytes, int truncatedCharOffset,
            int firstUTFByteValue, int bytesExpected)
        throws IOException
    {
        throw _constructError(String.format(
"Truncated UTF-8 character in Chunked Unicode String value (%d bytes): "
+"byte 0x%02X at offset #%d indicated %d more bytes needed",
strLenBytes, firstUTFByteValue, truncatedCharOffset, bytesExpected));
    }
    */

    // @since 2.13
    private String _reportTruncatedUTF8InName(int strLenBytes, int truncatedCharOffset,
            int firstUTFByteValue, int bytesExpected)
        throws IOException
    {
        throw _constructReadException(String.format(
"Truncated UTF-8 character in Map key (%d bytes): "
+"byte 0x%02X at offset #%d indicated %d more bytes needed",
strLenBytes, firstUTFByteValue, truncatedCharOffset, bytesExpected));
    }

    /*
    /**********************************************************
    /* Internal methods, other
    /**********************************************************
     */

    private final static BigInteger BIT_63 = BigInteger.ONE.shiftLeft(63);

    private final BigInteger _bigPositive(long l) {
        BigInteger biggie = BigInteger.valueOf((l << 1) >>> 1);
        return biggie.or(BIT_63);
    }

    private final BigInteger _bigNegative(long l) {
        // 03-Dec-2017, tatu: [dataformats-binary#124] Careful with overflow
        BigInteger unsignedBase = _bigPositive(l);
        return unsignedBase.negate().subtract(BigInteger.ONE);
    }

    private void createChildArrayContext(final int len) throws IOException {
        _streamReadContext = _streamReadContext.createChildArrayContext(len);
        _streamReadConstraints.validateNestingDepth(_streamReadContext.getNestingDepth());
    }

    private void createChildObjectContext(final int len) throws IOException {
        _streamReadContext = _streamReadContext.createChildObjectContext(len);
        _streamReadConstraints.validateNestingDepth(_streamReadContext.getNestingDepth());
    }
}
