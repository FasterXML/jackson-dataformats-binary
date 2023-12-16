package tools.jackson.dataformat.smile;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import tools.jackson.core.*;
import tools.jackson.core.io.*;
import tools.jackson.core.json.DupDetector;
import tools.jackson.core.util.JacksonFeatureSet;
import tools.jackson.core.util.SimpleStreamWriteContext;
import tools.jackson.core.base.GeneratorBase;

import static tools.jackson.dataformat.smile.SmileConstants.*;

/**
 * {@link JsonGenerator} implementation for Smile-encoded content
 * (see <a href="http://wiki.fasterxml.com/SmileFormatSpec">Smile Format Specification</a>)
 */
public class SmileGenerator
    extends GeneratorBase
{
    // @since 2.16
    protected final static int DEFAULT_NAME_BUFFER_LENGTH = 64;    

    // @since 2.16
    protected final static int DEFAULT_STRING_VALUE_BUFFER_LENGTH = 64;

    /**
     * Enumeration that defines all togglable features for Smile generators.
     */
    public enum Feature
        implements FormatFeature
    {
        /**
         * Whether to write 4-byte header sequence when starting output or not.
         * If disabled, no header is written; this may be useful in embedded cases
         * where context is enough to know that content is encoded using this format.
         * Note, however, that omitting header means that default settings for
         * shared names/string values can not be changed.
         *<p>
         * Default setting is true, meaning that header will be written.
         */
        WRITE_HEADER(true),

        /**
         * Whether write byte marker that signifies end of logical content segment
         * ({@link SmileConstants#BYTE_MARKER_END_OF_CONTENT}) when
         * {@link #close} is called or not. This can be useful when outputting
         * multiple adjacent logical content segments (documents) into single
         * physical output unit (file).
         *<p>
         * Default setting is false meaning that such marker is not written.
         */
        WRITE_END_MARKER(false),

        /**
         * Whether to use simple 7-bit per byte encoding for binary content when output.
         * This is necessary ensure that byte 0xFF will never be included in content output.
         * For other data types this limitation is handled automatically. This setting is enabled
         * by default, however, overhead for binary data (14% size expansion, processing overhead)
         * is non-negligible. If no binary data is output, feature has no effect.
         *<p>
         * Default setting is true, indicating that binary data is quoted as 7-bit bytes
         * instead of written raw.
         */
        ENCODE_BINARY_AS_7BIT(true),

        /**
         * Whether generator should check if it can "share" property names during generating
         * content or not. If enabled, can replace repeating property names with back references,
         * which are more compact and should faster to decode. Downside is that there is some
         * overhead for writing (need to track existing values, check), as well as decoding.
         *<p>
         * Since property names tend to repeat quite often, this setting is enabled by default.
         */
        CHECK_SHARED_NAMES(true),

        /**
         * Whether generator should check if it can "share" short (at most 64 bytes encoded)
         * String value during generating
         * content or not. If enabled, can replace repeating Short String values with back references,
         * which are more compact and should faster to decode. Downside is that there is some
         * overhead for writing (need to track existing values, check), as well as decoding.
         *<p>
         * Since efficiency of this option depends a lot on type of content being produced,
         * this option is disabled by default, and should only be enabled if it is likely that
         * same values repeat relatively often.
         */
        CHECK_SHARED_STRING_VALUES(false),

        /**
         * Feature that determines if an invalid surrogate encoding found in the
         * incoming String should fail with an exception or silently be output
         * as the Unicode 'REPLACEMENT CHARACTER' (U+FFFD) or not; if not,
         * an exception will be thrown to indicate invalid content.
         *<p>
         * Default value is {@code false} (for backwards compatibility) meaning that
         * an invalid surrogate will result in exception ({@code StreamWriteException}).
         *
         * @since 2.13
         */
        LENIENT_UTF_ENCODING(false),
        ;

        protected final boolean _defaultState;
        protected final int _mask;

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
     * Helper class used for keeping track of possibly shareable String
     * references (for property names and/or short String values)
     */
    public final static class SharedStringNode
    {
        public final String value;
        public final int index;
        public SharedStringNode next;

        public SharedStringNode(String value, int index, SharedStringNode next)
        {
            this.value = value;
            this.index = index;
            this.next = next;
        }
    }

    /**
     * To simplify certain operations, we require output buffer length
     * to allow outputting of contiguous 256 character UTF-8 encoded String
     * value. Length of the longest UTF-8 code point (from Java char) is 3 bytes,
     * and we need both initial token byte and single-byte end marker
     * so we get following value.
     *<p>
     * Note: actually we could live with shorter one; absolute minimum would
     * be for encoding 64-character Strings.
     */
    private final static int MIN_BUFFER_LENGTH = (3 * 256) + 2;

    protected final static byte TOKEN_BYTE_LONG_STRING_ASCII = TOKEN_MISC_LONG_TEXT_ASCII;

    protected final static byte TOKEN_BYTE_INT_32 =  (byte) (SmileConstants.TOKEN_PREFIX_INTEGER + TOKEN_MISC_INTEGER_32);
    protected final static byte TOKEN_BYTE_INT_64 =  (byte) (SmileConstants.TOKEN_PREFIX_INTEGER + TOKEN_MISC_INTEGER_64);
    protected final static byte TOKEN_BYTE_BIG_INTEGER =  (byte) (SmileConstants.TOKEN_PREFIX_INTEGER + TOKEN_MISC_INTEGER_BIG);

    protected final static byte TOKEN_BYTE_FLOAT_32 =  (byte) (SmileConstants.TOKEN_PREFIX_FP | TOKEN_MISC_FLOAT_32);
    protected final static byte TOKEN_BYTE_FLOAT_64 =  (byte) (SmileConstants.TOKEN_PREFIX_FP | TOKEN_MISC_FLOAT_64);
    protected final static byte TOKEN_BYTE_BIG_DECIMAL =  (byte) (SmileConstants.TOKEN_PREFIX_FP | TOKEN_MISC_FLOAT_BIG);

    protected final static long MIN_INT_AS_LONG = (long) Integer.MIN_VALUE;
    protected final static long MAX_INT_AS_LONG = (long) Integer.MAX_VALUE;

    /**
     * The replacement character to use to fix invalid Unicode sequences
     * (mismatched surrogate pair).
     */
    protected final static int REPLACEMENT_CHAR = 0xfffd;

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    protected final OutputStream _out;

    /**
     * Bit flag composed of bits that indicate which
     * {@link tools.jackson.dataformat.smile.SmileGenerator.Feature}s
     * are enabled.
     */
    protected int _formatFeatures;

    /*
    /**********************************************************************
    /* Output state
    /**********************************************************************
     */

    /**
     * Object that keeps track of the current contextual state of the generator.
     */
    protected SimpleStreamWriteContext _streamWriteContext;

    /*
    /**********************************************************************
    /* Output buffering
    /**********************************************************************
     */

    /**
     * Intermediate buffer in which contents are buffered before
     * being written using {@link #_out}.
     */
    protected byte[] _outputBuffer;

    /**
     * Pointer to the next available byte in {@link #_outputBuffer}
     */
    protected int _outputTail = 0;

    /**
     * Offset to index after the last valid index in {@link #_outputBuffer}.
     * Typically same as length of the buffer.
     */
    protected final int _outputEnd;

    /**
     * Let's keep track of how many bytes have been output, may prove useful
     * when debugging. This does <b>not</b> include bytes buffered in
     * the output buffer, just bytes that have been written using underlying
     * stream writer.
     */
    protected int _bytesWritten;

    /*
    /**********************************************************************
    /* Shared String detection
    /**********************************************************************
     */

    /**
     * Raw data structure used for checking whether property name to
     * write can be output using back reference or not.
     */
    protected SharedStringNode[] _seenNames;

    /**
     * Number of entries in {@link #_seenNames}; -1 if no shared name
     * detection is enabled
     */
    protected int _seenNameCount;

    /**
     * Raw data structure used for checking whether String value to
     * write can be output using back reference or not.
     */
    protected SharedStringNode[] _seenStringValues;

    /**
     * Number of entries in {@link #_seenStringValues}; -1 if no shared text value
     * detection is enabled
     */
    protected int _seenStringValueCount;

    /**
     * Flag that indicates whether the output buffer is recyclable (and
     * needs to be returned to recycler once we are done) or not.
     */
    protected boolean _bufferRecyclable;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public SmileGenerator(ObjectWriteContext writeCtxt, IOContext ioCtxt,
            int streamWriteFeatures, int smileFeatures, OutputStream out)
    {
        super(writeCtxt, ioCtxt, streamWriteFeatures);
        _formatFeatures = smileFeatures;
        final DupDetector dups = StreamWriteFeature.STRICT_DUPLICATE_DETECTION.enabledIn(streamWriteFeatures)
                ? DupDetector.rootDetector(this) : null;
        _streamWriteContext = SimpleStreamWriteContext.createRootContext(dups);
        _out = out;
        _bufferRecyclable = true;
        _outputBuffer = ioCtxt.allocWriteEncodingBuffer();
        _outputEnd = _outputBuffer.length;
        // let's just sanity check to prevent nasty odd errors
        if (_outputEnd < MIN_BUFFER_LENGTH) {
            throw new IllegalStateException(String.format(
                    "Internal encoding buffer length (%d) too short, must be at least %d",
                    _outputEnd, MIN_BUFFER_LENGTH));
        }
        if (!Feature.CHECK_SHARED_NAMES.enabledIn(smileFeatures)) {
            _seenNames = null;
            _seenNameCount = -1;
        } else {
            _seenNames = new SharedStringNode[DEFAULT_NAME_BUFFER_LENGTH];
            _seenNameCount = 0;
        }

        if (!Feature.CHECK_SHARED_STRING_VALUES.enabledIn(smileFeatures)) {
            _seenStringValues = null;
            _seenStringValueCount = -1;
        } else {
            _seenStringValues = new SharedStringNode[DEFAULT_STRING_VALUE_BUFFER_LENGTH];
            _seenStringValueCount = 0;
        }
    }

    public SmileGenerator(ObjectWriteContext writeCtxt, IOContext ioCtxt,
            int streamWriteFeatures, int smileFeatures, OutputStream out,
            byte[] outputBuffer, int offset, boolean bufferRecyclable)
    {
        super(writeCtxt, ioCtxt, streamWriteFeatures);
        _formatFeatures = smileFeatures;
        final DupDetector dups = StreamWriteFeature.STRICT_DUPLICATE_DETECTION.enabledIn(streamWriteFeatures)
                ? DupDetector.rootDetector(this) : null;
                _streamWriteContext = SimpleStreamWriteContext.createRootContext(dups);
        _out = out;
        _bufferRecyclable = bufferRecyclable;
        _outputTail = offset;
        _outputBuffer = outputBuffer;
        _outputEnd = _outputBuffer.length;
        // let's just sanity check to prevent nasty odd errors
        if (_outputEnd < MIN_BUFFER_LENGTH) {
            throw new IllegalStateException(String.format(
                    "Internal encoding buffer length (%d) too short, must be at least %d",
                    _outputEnd, MIN_BUFFER_LENGTH));
        }
        if (!Feature.CHECK_SHARED_NAMES.enabledIn(smileFeatures)) {
            _seenNames = null;
            _seenNameCount = -1;
        } else {
            _seenNames = new SharedStringNode[DEFAULT_NAME_BUFFER_LENGTH];
            _seenNameCount = 0;
        }

        if (!Feature.CHECK_SHARED_STRING_VALUES.enabledIn(smileFeatures)) {
            _seenStringValues = null;
            _seenStringValueCount = -1;
        } else {
            _seenStringValues = new SharedStringNode[DEFAULT_STRING_VALUE_BUFFER_LENGTH];
            _seenStringValueCount = 0;
        }
    }

    /**
     * Method that can be called to explicitly write Smile document header.
     * Note that usually you do not need to call this for first document to output,
     * but rather only if you intend to write multiple root-level documents
     * with same generator (and even in that case this is optional thing to do).
     * As a result usually only {@link SmileFactory} calls this method.
     */
    public JsonGenerator writeHeader() throws JacksonException
    {
        int last = HEADER_BYTE_4;
        if (Feature.CHECK_SHARED_NAMES.enabledIn(_formatFeatures)) {
            last |= SmileConstants.HEADER_BIT_HAS_SHARED_NAMES;
        }
        if (Feature.CHECK_SHARED_STRING_VALUES.enabledIn(_formatFeatures)) {
            last |= SmileConstants.HEADER_BIT_HAS_SHARED_STRING_VALUES;
        }
        if (!Feature.ENCODE_BINARY_AS_7BIT.enabledIn(_formatFeatures)) {
            last |= SmileConstants.HEADER_BIT_HAS_RAW_BINARY;
        }
        _writeBytes(HEADER_BYTE_1, HEADER_BYTE_2, HEADER_BYTE_3, (byte) last);
        return this;
    }

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
    public JacksonFeatureSet<StreamWriteCapability> streamWriteCapabilities() {
        return DEFAULT_BINARY_WRITE_CAPABILITIES;
    }

    /*
    /**********************************************************************
    /* Overridden methods, configuration
    /**********************************************************************
     */

    @Override
    public Object streamWriteOutputTarget() {
        return _out;
    }

    @Override
    public int streamWriteOutputBuffered() {
        return _outputTail;
    }

    /*
    /**********************************************************************
    /* Overridden methods, output context (and related)
    /**********************************************************************
     */

    @Override
    public Object currentValue() {
        return _streamWriteContext.currentValue();
    }

    @Override
    public void assignCurrentValue(Object v) {
        _streamWriteContext.assignCurrentValue(v);
    }

    @Override
    public TokenStreamContext streamWriteContext() {
        return _streamWriteContext;
    }

    /*
    /**********************************************************************
    /* Overridden methods, write methods
    /**********************************************************************
     */

    /* And then methods overridden to make final, streamline some
     * aspects...
     */

    @Override
    public JsonGenerator writeName(String name)  throws JacksonException
    {
        if (!_streamWriteContext.writeName(name)) {
            throw _constructWriteException("Cannot write a property name, expecting a value");
        }
        _writeName(name);
        return this;
    }

    @Override
    public JsonGenerator writeName(SerializableString name)
        throws JacksonException
    {
        // Object is a value, need to verify it's allowed
        if (!_streamWriteContext.writeName(name.getValue())) {
            throw _constructWriteException("Cannot write a property name, expecting a value");
        }
        _writeName(name);
        return this;
    }

    @Override
    public JsonGenerator writePropertyId(long id) throws JacksonException {
        // 24-Jul-2019, tatu: Should not force construction of a String here...
        String idStr = Long.valueOf(id).toString(); // since instances for small values cached
        if (!_streamWriteContext.writeName(idStr)) {
            throw _constructWriteException("Cannot write a property id, expecting a value");
        }
        _writeName(idStr);
        return this;
    }

    /*
    /**********************************************************************
    /* Extended API, configuration
    /**********************************************************************
     */

    public SmileGenerator enable(Feature f) {
        _formatFeatures |= f.getMask();
        return this;
    }

    public SmileGenerator disable(Feature f) {
        _formatFeatures &= ~f.getMask();
        return this;
    }

    public final boolean isEnabled(Feature f) {
        return (_formatFeatures & f.getMask()) != 0;
    }

    public SmileGenerator configure(Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /*
    /**********************************************************************
    /* Extended API, other
    /**********************************************************************
     */

    /**
     * Method for directly inserting specified byte in output at
     * current position.
     *<p>
     * NOTE: only use this method if you really know what you are doing.
     */
    public JsonGenerator writeRaw(byte b) throws JacksonException
    {
        // 08-Jan-2014, tatu: Should we just rather throw an exception? For now,
        //   allow... maybe have a feature to cause an exception.
        _writeByte(b);
        return this;
    }

    /**
     * Method for directly inserting specified bytes in output at
     * current position.
     *<p>
     * NOTE: only use this method if you really know what you are doing.
     */
    public JsonGenerator writeBytes(byte[] data, int offset, int len) throws JacksonException
    {
        _writeBytes(data, offset, len);
        return this;
    }

    /*
    /**********************************************************************
    /* Output method implementations, structural
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeStartArray() throws JacksonException
    {
        _verifyValueWrite("start an array");
        _streamWriteContext = _streamWriteContext.createChildArrayContext(null);
        streamWriteConstraints().validateNestingDepth(_streamWriteContext.getNestingDepth());
        _writeByte(TOKEN_LITERAL_START_ARRAY);
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(Object forValue) throws JacksonException
    {
        _verifyValueWrite("start an array");
        _streamWriteContext = _streamWriteContext.createChildArrayContext(forValue);
        streamWriteConstraints().validateNestingDepth(_streamWriteContext.getNestingDepth());
        _writeByte(TOKEN_LITERAL_START_ARRAY);
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(Object forValue, int size) throws JacksonException
    {
        _verifyValueWrite("start an array");
        _streamWriteContext = _streamWriteContext.createChildArrayContext(forValue);
        streamWriteConstraints().validateNestingDepth(_streamWriteContext.getNestingDepth());
        _writeByte(TOKEN_LITERAL_START_ARRAY);
        return this;
    }

    @Override
    public JsonGenerator writeEndArray() throws JacksonException
    {
        if (!_streamWriteContext.inArray()) {
            _reportError("Current context not Array but "+_streamWriteContext.typeDesc());
        }
        _writeByte(TOKEN_LITERAL_END_ARRAY);
        _streamWriteContext = _streamWriteContext.getParent();
        return this;
    }

    @Override
    public JsonGenerator writeStartObject() throws JacksonException
    {
        _verifyValueWrite("start an object");
        SimpleStreamWriteContext ctxt = _streamWriteContext.createChildObjectContext(null);
        streamWriteConstraints().validateNestingDepth(ctxt.getNestingDepth());
        _streamWriteContext = ctxt;
        _writeByte(TOKEN_LITERAL_START_OBJECT);
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(Object forValue) throws JacksonException
    {
        _verifyValueWrite("start an object");
        SimpleStreamWriteContext ctxt = _streamWriteContext.createChildObjectContext(forValue);
        streamWriteConstraints().validateNestingDepth(ctxt.getNestingDepth());
        _streamWriteContext = ctxt;
        _writeByte(TOKEN_LITERAL_START_OBJECT);
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(Object forValue, int size) throws JacksonException
    {
        _verifyValueWrite("start an object");
        SimpleStreamWriteContext ctxt = _streamWriteContext.createChildObjectContext(forValue);
        streamWriteConstraints().validateNestingDepth(ctxt.getNestingDepth());
        _streamWriteContext = ctxt;
        _writeByte(TOKEN_LITERAL_START_OBJECT);
        return this;
    }

    @Override
    public JsonGenerator writeEndObject() throws JacksonException
    {
        if (!_streamWriteContext.inObject()) {
            _reportError("Current context not Object but "+_streamWriteContext.typeDesc());
        }
        _streamWriteContext = _streamWriteContext.getParent();
        _writeByte(TOKEN_LITERAL_END_OBJECT);
        return this;
    }

    @Override
    public JsonGenerator writeArray(int[] array, int offset, int length)
        throws JacksonException
    {
        _verifyOffsets(array.length, offset, length);
        // short-cut, do not create child array context etc
        _verifyValueWrite("write int array");

        _writeByte(TOKEN_LITERAL_START_ARRAY);
        int ptr = _outputTail;
        final int outputEnd = _outputEnd;
        for (int i = offset, end = offset+length; i < end; ++i) {
            // TODO: optimize boundary checks for common case
            if ((ptr + 6) >= outputEnd) { // at most 6 bytes per element
                _outputTail = ptr;
                _flushBuffer();
                ptr = _outputTail;
            }
            ptr = _writeNumberNoChecks(ptr, array[i]);
        }
        _outputTail = ptr;
        _writeByte(TOKEN_LITERAL_END_ARRAY);
        return this;
    }

    @Override
    public JsonGenerator writeArray(long[] array, int offset, int length)
        throws JacksonException
    {
        _verifyOffsets(array.length, offset, length);
        // short-cut, do not create child array context etc
        _verifyValueWrite("write int array");

        _writeByte(TOKEN_LITERAL_START_ARRAY);
        int ptr = _outputTail;
        final int outputEnd = _outputEnd;
        for (int i = offset, end = offset+length; i < end; ++i) {
            if ((ptr + 11) >= outputEnd) { // at most 11 bytes per element
                _outputTail = ptr;
                _flushBuffer();
                ptr = _outputTail;
            }
            ptr = _writeNumberNoChecks(ptr, array[i]);
        }
        _outputTail = ptr;
        _writeByte(TOKEN_LITERAL_END_ARRAY);
        return this;
    }

    @Override
    public JsonGenerator writeArray(double[] array, int offset, int length)
        throws JacksonException
    {
        _verifyOffsets(array.length, offset, length);
        // short-cut, do not create child array context etc
        _verifyValueWrite("write int array");

        _writeByte(TOKEN_LITERAL_START_ARRAY);
        int ptr = _outputTail;
        final int outputEnd = _outputEnd;
        for (int i = offset, end = offset+length; i < end; ++i) {
            if ((ptr + 10) >= outputEnd) { // at most 11 bytes per element
                _outputTail = ptr;
                _flushBuffer();
                ptr = _outputTail;
            }
            ptr = _writeNumberNoChecks(ptr, array[i]);
        }
        _outputTail = ptr;
        _writeByte(TOKEN_LITERAL_END_ARRAY);
        return this;
    }

    private final void _writeName(String name) throws JacksonException
    {
        int len = name.length();
        if (len == 0) {
            _writeByte(TOKEN_KEY_EMPTY_STRING);
            return;
        }
        // First: is it something we can share?
        if (_seenNameCount >= 0) {
            int ix = _findSeenName(name);
            if (ix >= 0) {
                _writeSharedNameReference(ix);
                return;
            }
        }
        if (len > MAX_SHORT_NAME_ANY_BYTES) { // can not be a 'short' String; off-line (rare case)
            _writeNonShortFieldName(name, len);
            return;
        }

        // first: ensure we have enough space
        if ((_outputTail + MIN_BUFFER_FOR_POSSIBLE_SHORT_STRING) >= _outputEnd) {
            _flushBuffer();
        }
        // then let's copy String chars to char buffer, faster than using getChar (measured, profiled)
        int origOffset = _outputTail;
        ++_outputTail; // to reserve space for type token
        int byteLen = _shortUTF8Encode(name, 0, len);
        byte typeToken;

        // ASCII?
        if (byteLen == len) {
            if (byteLen <= MAX_SHORT_NAME_ASCII_BYTES) { // yes, is short indeed
                typeToken = (byte) ((TOKEN_PREFIX_KEY_ASCII - 1) + byteLen);
            } else { // longer albeit ASCII
                typeToken = TOKEN_KEY_LONG_STRING;
                // and we will need String end marker byte
                _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
            }
        } else { // not all ASCII
            if (byteLen <= MAX_SHORT_NAME_UNICODE_BYTES) { // yes, is short indeed
                // note: since 2 is smaller allowed length, offset differs from one used for
                typeToken = (byte) ((TOKEN_PREFIX_KEY_UNICODE - 2) + byteLen);
            } else { // nope, longer non-ASCII Strings
                typeToken = TOKEN_KEY_LONG_STRING;
                // and we will need String end marker byte
                _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
            }
        }
        // and then sneak in type token now that know the details
        _outputBuffer[origOffset] = typeToken;
        // Also, keep track if we can use back-references (shared names)
        if (_seenNameCount >= 0) {
            _addSeenName(name);
        }
    }

    private final void _writeNonShortFieldName(final String name, final int len) throws JacksonException
    {
        _writeByte(TOKEN_KEY_LONG_STRING);
        // can we still make a temp copy?
        // but will encoded version fit in buffer?
        int maxLen = len + len + len;
        if (maxLen <= _outputBuffer.length) { // yes indeed
            if ((_outputTail + maxLen) >= _outputEnd) {
                _flushBuffer();
            }
             _shortUTF8Encode(name, 0, len);
        } else { // nope, need bit slower variant
            _mediumUTF8Encode(name, 0, len);
        }
        if (_seenNameCount >= 0) {
            _addSeenName(name);
        }
        if (_outputTail >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
    }

    protected final void _writeName(SerializableString name) throws JacksonException
    {
        final int charLen = name.charLength();
        if (charLen == 0) {
            _writeByte(TOKEN_KEY_EMPTY_STRING);
            return;
        }
        // Then: is it something we can share?
        if (_seenNameCount >= 0) {
            int ix = _findSeenName(name.getValue());
            if (ix >= 0) {
                _writeSharedNameReference(ix);
                return;
            }
        }
        final byte[] bytes = name.asUnquotedUTF8();
        final int byteLen = bytes.length;
        if (byteLen != charLen) {
            _writeNameUnicode(name, bytes);
            return;
        }
        // Common case: short ASCII name that fits in buffer as is
        if (byteLen <= MAX_SHORT_NAME_ASCII_BYTES) {
            // output buffer is bigger than what we need, always, so
            if ((_outputTail + byteLen) >= _outputEnd) { // need marker byte and actual bytes
                _flushBuffer();
            }
            _outputBuffer[_outputTail++] = (byte) ((TOKEN_PREFIX_KEY_ASCII - 1) + byteLen);
            System.arraycopy(bytes, 0, _outputBuffer, _outputTail, byteLen);
            _outputTail += byteLen;
        } else {
            _writeLongAsciiFieldName(bytes);
        }
        // Also, keep track if we can use back-references (shared names)
        if (_seenNameCount >= 0) {
            _addSeenName(name.getValue());
        }
    }

    private final void _writeLongAsciiFieldName(byte[] bytes)
        throws JacksonException
    {
        final int byteLen = bytes.length;
        if (_outputTail >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = TOKEN_KEY_LONG_STRING;
        // Ok. Enough room?
        if ((_outputTail + byteLen + 1) < _outputEnd) {
            System.arraycopy(bytes, 0, _outputBuffer, _outputTail, byteLen);
            _outputTail += byteLen;
        } else {
            _flushBuffer();
            // either way, do intermediate copy if name is relatively short
            // Need to copy?
            if (byteLen < MIN_BUFFER_LENGTH) {
                System.arraycopy(bytes, 0, _outputBuffer, _outputTail, byteLen);
                _outputTail += byteLen;
            } else {
                // otherwise, just write as is
                if (_outputTail > 0) {
                    _flushBuffer();
                }
                try {
                    _out.write(bytes, 0, byteLen);
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
            }
        }
        _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
    }

    protected final void _writeNameUnicode(SerializableString name, byte[] bytes)
        throws JacksonException
    {
        final int byteLen = bytes.length;

        // Common case: short Unicode name that fits in output buffer
        if (byteLen <= MAX_SHORT_NAME_UNICODE_BYTES) {
            if ((_outputTail + byteLen) >= _outputEnd) { // need marker byte and actual bytes
                _flushBuffer();
            }
            // note: since 2 is smaller allowed length, offset differs from one used for
            _outputBuffer[_outputTail++] = (byte) ((TOKEN_PREFIX_KEY_UNICODE - 2) + byteLen);

            System.arraycopy(bytes, 0, _outputBuffer, _outputTail, byteLen);
            _outputTail += byteLen;
            // Also, keep track if we can use back-references (shared names)
            if (_seenNameCount >= 0) {
                _addSeenName(name.getValue());
            }
            return;
        }
        if (_outputTail >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = TOKEN_KEY_LONG_STRING;
        // Ok. Enough room?
        if ((_outputTail + byteLen + 1) < _outputEnd) {
            System.arraycopy(bytes, 0, _outputBuffer, _outputTail, byteLen);
            _outputTail += byteLen;
        } else {
            _flushBuffer();
            // either way, do intermediate copy if name is relatively short
            // Need to copy?
            if (byteLen < MIN_BUFFER_LENGTH) {
                System.arraycopy(bytes, 0, _outputBuffer, _outputTail, byteLen);
                _outputTail += byteLen;
            } else {
                // otherwise, just write as is
                if (_outputTail > 0) {
                    _flushBuffer();
                }
                try {
                    _out.write(bytes, 0, byteLen);
                } catch (IOException e) {
                    throw _wrapIOFailure(e);
                }
            }
        }
        _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
        // Also, keep track if we can use back-references (shared names)
        if (_seenNameCount >= 0) {
            _addSeenName(name.getValue());
        }
    }

    private final void _writeSharedNameReference(int ix)
        throws JacksonException
    {
        // 03-Mar-2011, tatu: Related to [JACKSON-525], let's add a sanity check here
        if (ix >= _seenNameCount) {
            throw new IllegalArgumentException("Internal error: trying to write shared name with index "+ix
                    +"; but have only seen "+_seenNameCount+" so far!");
        }
        if (ix < 64) {
            _writeByte((byte) (TOKEN_PREFIX_KEY_SHARED_SHORT + ix));
        } else {
            _writeBytes(((byte) (TOKEN_PREFIX_KEY_SHARED_LONG + (ix >> 8))), (byte) ix);
        }
    }

    /*
    /**********************************************************************
    /* Output method implementations, textual
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeString(String text) throws JacksonException
    {
        if (text == null) {
            return writeNull();
        }
        _verifyValueWrite("write String value");
        int len = text.length();
        if (len == 0) {
            _writeByte(TOKEN_LITERAL_EMPTY_STRING);
            return this;
        }
        // Longer string handling off-lined
        if (len > MAX_SHARED_STRING_LENGTH_BYTES) {
            _writeNonSharedString(text, len);
            return this;
        }
        // Then: is it something we can share?
        if (_seenStringValueCount >= 0) {
            int ix = _findSeenStringValue(text);
            if (ix >= 0) {
                _writeSharedStringValueReference(ix);
                return this;
            }
        }

        // possibly short string (but not necessarily)
        // first: ensure we have enough space
        if ((_outputTail + MIN_BUFFER_FOR_POSSIBLE_SHORT_STRING) >= _outputEnd) {
            _flushBuffer();
        }
        // then let's copy String chars to char buffer, faster than using getChar (measured, profiled)
        int origOffset = _outputTail;
        ++_outputTail; // to leave room for type token
        int byteLen = _shortUTF8Encode(text, 0, len);
        if (byteLen <= MAX_SHORT_VALUE_STRING_BYTES) { // yes, is short indeed
            // plus keep reference, if it could be shared:
            if (_seenStringValueCount >= 0) {
                _addSeenStringValue(text);
            }
            if (byteLen == len) { // and all ASCII
                _outputBuffer[origOffset] = (byte) ((TOKEN_PREFIX_TINY_ASCII - 1) + byteLen);
            } else { // not just ASCII
                // note: since length 1 can not be used here, value range is offset by 2, not 1
                _outputBuffer[origOffset] = (byte) ((TOKEN_PREFIX_TINY_UNICODE - 2) +  byteLen);
            }
        } else { // nope, longer String
            _outputBuffer[origOffset] = (byteLen == len) ? TOKEN_BYTE_LONG_STRING_ASCII
                    : SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE;
            // and we will need String end marker byte
            _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
        }
        return this;
    }

    private final void _writeSharedStringValueReference(int ix) throws JacksonException
    {
        // 03-Mar-2011, tatu: Related to [JACKSON-525], let's add a sanity check here
        if (ix >= _seenStringValueCount) {
            throw new IllegalArgumentException("Internal error: trying to write shared String value with index "+ix
                    +"; but have only seen "+_seenStringValueCount+" so far!");
        }
        if (ix < 31) { // add 1, as byte 0 is omitted
            _writeByte((byte) (TOKEN_PREFIX_SHARED_STRING_SHORT + 1 + ix));
        } else {
            _writeBytes(((byte) (TOKEN_PREFIX_SHARED_STRING_LONG + (ix >> 8))), (byte) ix);
        }
    }

    /**
     * Helper method called to handle cases where String value to write is known
     * to be long enough not to be shareable.
     */
    private final void _writeNonSharedString(final String text, final int len) throws JacksonException
    {
        // Expansion can be 3x for Unicode; and then there's type byte and end marker, so:
        int maxLen = len + len + len + 2;
        // Next: does it always fit within output buffer?
        if (maxLen > _outputBuffer.length) { // nope
            // can't rewrite type buffer, so can't speculate it might be all-ASCII
            _writeByte(SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE);
            _mediumUTF8Encode(text, 0, len);
            _writeByte(BYTE_MARKER_END_OF_STRING);
            return;
        }

        if ((_outputTail + maxLen) >= _outputEnd) {
            _flushBuffer();
        }
        int origOffset = _outputTail;
        // can't say for sure if it's ASCII or Unicode, so:
        _writeByte(TOKEN_BYTE_LONG_STRING_ASCII);
        int byteLen = _shortUTF8Encode(text, 0, len);
        // If not ASCII, fix type:
        if (byteLen > len) {
            _outputBuffer[origOffset] = SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE;
        }
        _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
    }

    @Override
    public JsonGenerator writeString(char[] text, int offset, int len) throws JacksonException
    {
        // Shared strings are tricky; easiest to just construct String, call the other method
        if (len <= MAX_SHARED_STRING_LENGTH_BYTES && _seenStringValueCount >= 0 && len > 0) {
            return writeString(new String(text, offset, len));
        }
        _verifyValueWrite("write String value");
        if (len == 0) {
            _writeByte(TOKEN_LITERAL_EMPTY_STRING);
            return this;
        }
        if (len <= MAX_SHORT_VALUE_STRING_BYTES) { // possibly short strings (not necessarily)
            // first: ensure we have enough space
            if ((_outputTail + MIN_BUFFER_FOR_POSSIBLE_SHORT_STRING) >= _outputEnd) {
                _flushBuffer();
            }
            int origOffset = _outputTail;
            ++_outputTail; // to leave room for type token
            int byteLen = _shortUTF8Encode(text, offset, offset+len);
            byte typeToken;
            if (byteLen <= MAX_SHORT_VALUE_STRING_BYTES) { // yes, is short indeed
                if (byteLen == len) { // and all ASCII
                    typeToken = (byte) ((TOKEN_PREFIX_TINY_ASCII - 1) + byteLen);
                } else { // not just ASCII
                    typeToken = (byte) ((TOKEN_PREFIX_TINY_UNICODE - 2) + byteLen);
                }
            } else { // nope, longer non-ASCII Strings
                typeToken = SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE;
                // and we will need String end marker byte
                _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
            }
            // and then sneak in type token now that know the details
            _outputBuffer[origOffset] = typeToken;
        } else { // "long" String, never shared
            // but might still fit within buffer?
            int maxLen = len + len + len + 2;
            if (maxLen <= _outputBuffer.length) { // yes indeed
                if ((_outputTail + maxLen) >= _outputEnd) {
                    _flushBuffer();
                }
                int origOffset = _outputTail;
                _writeByte(SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE);
                int byteLen = _shortUTF8Encode(text, offset, offset+len);
                // if it's ASCII, let's revise our type determination (to help decoder optimize)
                if (byteLen == len) {
                    _outputBuffer[origOffset] = TOKEN_BYTE_LONG_STRING_ASCII;
                }
                _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
            } else {
                _writeByte(SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE);
                _mediumUTF8Encode(text, offset, offset+len);
                _writeByte(BYTE_MARKER_END_OF_STRING);
            }
        }
        return this;
    }

    @Override
    public JsonGenerator writeString(SerializableString sstr)
        throws JacksonException
    {
        _verifyValueWrite("write String value");
        // First: is it empty?
        String str = sstr.getValue();
        int len = str.length();
        if (len == 0) {
            _writeByte(TOKEN_LITERAL_EMPTY_STRING);
            return this;
        }
        // Second: something we can share?
        if (len <= MAX_SHARED_STRING_LENGTH_BYTES && _seenStringValueCount >= 0) {
            int ix = _findSeenStringValue(str);
            if (ix >= 0) {
                _writeSharedStringValueReference(ix);
                return this;
            }
        }
        // If not, use pre-encoded version
        byte[] raw = sstr.asUnquotedUTF8();
        final int byteLen = raw.length;

        if (byteLen <= MAX_SHORT_VALUE_STRING_BYTES) { // short string
            // first: ensure we have enough space
            if ((_outputTail + byteLen + 1) >= _outputEnd) {
                _flushBuffer();
            }
            // ASCII or Unicode?
            int typeToken = (byteLen == len)
                    ? ((TOKEN_PREFIX_TINY_ASCII - 1) + byteLen)
                    : ((TOKEN_PREFIX_TINY_UNICODE - 2) + byteLen)
                    ;
            _outputBuffer[_outputTail++] = (byte) typeToken;
            System.arraycopy(raw, 0, _outputBuffer, _outputTail, byteLen);
            _outputTail += byteLen;
            // plus keep reference, if it could be shared:
            if (_seenStringValueCount >= 0) {
                _addSeenStringValue(sstr.getValue());
            }
        } else { // "long" String, never shared
            // but might still fit within buffer?
            byte typeToken = (byteLen == len) ? TOKEN_BYTE_LONG_STRING_ASCII
                    : SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE;
            _writeByte(typeToken);
            _writeBytes(raw, 0, raw.length);
            _writeByte(BYTE_MARKER_END_OF_STRING);
        }
        return this;
    }

    @Override
    public JsonGenerator writeRawUTF8String(byte[] text, int offset, int len)
        throws JacksonException
    {
        _verifyValueWrite("write String value");
        // first: is it empty String?
        if (len == 0) {
            _writeByte(TOKEN_LITERAL_EMPTY_STRING);
            return this;
        }
        // Sanity check: shared-strings incompatible with raw String writing
        if (_seenStringValueCount >= 0) {
            throw new UnsupportedOperationException("Can not use direct UTF-8 write methods when 'Feature.CHECK_SHARED_STRING_VALUES' enabled");
        }
        /* Other practical limitation is that we do not really know if it might be
         * ASCII or not; and figuring it out is rather slow. So, best we can do is
         * to declare we do not know it is ASCII (i.e. "is Unicode").
         */
        if (len <= MAX_SHARED_STRING_LENGTH_BYTES) { // up to 65 Unicode bytes
            // first: ensure we have enough space
            if ((_outputTail + len) >= _outputEnd) { // bytes, plus one for type indicator
                _flushBuffer();
            }
            /* 11-Feb-2011, tatu: As per [JACKSON-492], mininum length for "Unicode"
             *    String is 2; 1 byte length must be ASCII.
             */
            if (len == 1) {
                _outputBuffer[_outputTail++] = TOKEN_PREFIX_TINY_ASCII; // length of 1 cancels out (len-1)
                _outputBuffer[_outputTail++] = text[offset];
            } else {
                _outputBuffer[_outputTail++] = (byte) ((TOKEN_PREFIX_TINY_UNICODE - 2) + len);
                System.arraycopy(text, offset, _outputBuffer, _outputTail, len);
                _outputTail += len;
            }
        } else { // "long" String
            // but might still fit within buffer?
            int maxLen = len + len + len + 2;
            if (maxLen <= _outputBuffer.length) { // yes indeed
                if ((_outputTail + maxLen) >= _outputEnd) {
                    _flushBuffer();
                }
                _outputBuffer[_outputTail++] = SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE;
                System.arraycopy(text, offset, _outputBuffer, _outputTail, len);
                _outputTail += len;
                _outputBuffer[_outputTail++] = BYTE_MARKER_END_OF_STRING;
            } else {
                _writeByte(SmileConstants.TOKEN_MISC_LONG_TEXT_UNICODE);
                _writeBytes(text, offset, len);
                _writeByte(BYTE_MARKER_END_OF_STRING);
            }
        }
        return this;
    }

    @Override
    public JsonGenerator writeUTF8String(byte[] text, int offset, int len)
        throws JacksonException
    {
        // Since no escaping is needed, same as 'writeRawUTF8String'
        return writeRawUTF8String(text, offset, len);
    }

    /*
    /**********************************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeRaw(String text) throws JacksonException {
        throw _notSupported();
    }

    @Override
    public JsonGenerator writeRaw(String text, int offset, int len) throws JacksonException {
        throw _notSupported();
    }

    @Override
    public JsonGenerator writeRaw(char[] text, int offset, int len) throws JacksonException {
        throw _notSupported();
    }

    @Override
    public JsonGenerator writeRaw(char c) throws JacksonException {
        throw _notSupported();
    }

    @Override
    public JsonGenerator writeRawValue(String text) throws JacksonException {
        throw _notSupported();
    }

    @Override
    public JsonGenerator writeRawValue(String text, int offset, int len) throws JacksonException {
        throw _notSupported();
    }

    @Override
    public JsonGenerator writeRawValue(char[] text, int offset, int len) throws JacksonException {
        throw _notSupported();
    }

    /*
    /**********************************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws JacksonException
    {
        if (data == null) {
            return writeNull();
        }
        _verifyValueWrite("write Binary value");
        if (isEnabled(Feature.ENCODE_BINARY_AS_7BIT)) {
            _writeByte(TOKEN_MISC_BINARY_7BIT);
            _write7BitBinaryWithLength(data, offset, len);
        } else {
            _writeByte(TOKEN_MISC_BINARY_RAW);
            _writePositiveVInt(len);
            // raw is dead simple of course:
            _writeBytes(data, offset, len);
        }
        return this;
    }

    @Override
    public int writeBinary(InputStream data, int dataLength)
        throws JacksonException
    {
        // Smile requires knowledge of length in advance, since binary is length-prefixed
        if (dataLength < 0) {
            throw new UnsupportedOperationException("Must pass actual length for Smile encoded data");
        }
        _verifyValueWrite("write Binary value");
        int missing;
        if (isEnabled(Feature.ENCODE_BINARY_AS_7BIT)) {
            _writeByte(TOKEN_MISC_BINARY_7BIT);
            byte[] encodingBuffer = _ioContext.allocBase64Buffer();
            try {
                missing = _write7BitBinaryWithLength(data, dataLength, encodingBuffer);
            } finally {
                _ioContext.releaseBase64Buffer(encodingBuffer);
            }
        } else {
            _writeByte(TOKEN_MISC_BINARY_RAW );
            _writePositiveVInt(dataLength);
            // raw is dead simple of course:
            missing = _writeBytes(data, dataLength);
        }
        if (missing > 0) {
            _reportError("Too few bytes available: missing "+missing+" bytes (out of "+dataLength+")");
        }
        return dataLength;
    }

    @Override
    public int writeBinary(Base64Variant b64variant, InputStream data, int dataLength)
        throws JacksonException
    {
        return writeBinary(data, dataLength);
    }

    /*
    /**********************************************************************
    /* Output method implementations, primitive
    /**********************************************************************
     */

    @Override
    public JsonGenerator writeBoolean(boolean state) throws JacksonException
    {
        _verifyValueWrite("write boolean value");
        if (state) {
            _writeByte(TOKEN_LITERAL_TRUE);
        } else {
            _writeByte(TOKEN_LITERAL_FALSE);
        }
        return this;
    }

    @Override
    public JsonGenerator writeNull() throws JacksonException
    {
        _verifyValueWrite("write null value");
        _writeByte(TOKEN_LITERAL_NULL);
        return this;
    }

    @Override
    public JsonGenerator writeNumber(short v) throws JacksonException {
        return writeNumber((int) v);
    }

    @Override
    public JsonGenerator writeNumber(int i) throws JacksonException
    {
        _verifyValueWrite("write number");
        // First things first: let's zigzag encode number
        i = SmileUtil.zigzagEncode(i);
        // tiny (single byte) or small (type + 6-bit value) number?
        if (i <= 0x3F && i >= 0) {
            if (i <= 0x1F) { // tiny
                _writeByte((byte) (TOKEN_PREFIX_SMALL_INT + i));
                return this;
            }
            // nope, just small, 2 bytes (type, 1-byte zigzag value) for 6 bit value
            _writeBytes(TOKEN_BYTE_INT_32, (byte) (0x80 + i));
            return this;
        }
        // Ok: let's find minimal representation then
        byte b0 = (byte) (0x80 + (i & 0x3F));
        i >>>= 6;
        if (i <= 0x7F) { // 13 bits is enough (== 3 byte total encoding)
            _writeBytes(TOKEN_BYTE_INT_32, (byte) i, b0);
            return this;
        }
        byte b1 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            _writeBytes(TOKEN_BYTE_INT_32, (byte) i, b1, b0);
            return this;
        }
        byte b2 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            _writeBytes(TOKEN_BYTE_INT_32, (byte) i, b2, b1, b0);
            return this;
        }
        // no, need all 5 bytes
        byte b3 = (byte) (i & 0x7F);
        _writeBytes(TOKEN_BYTE_INT_32, (byte) (i >> 7), b3, b2, b1, b0);
        return this;
    }

    // since 2.8: same as `writeNumber(int)` minus validity checks for
    // value write AND boundary checks
    private final int _writeNumberNoChecks(int ptr, int i) throws JacksonException
    {
        final byte[] output = _outputBuffer;
        i = SmileUtil.zigzagEncode(i);
        // tiny (single byte) or small (type + 6-bit value) number?
        if (i <= 0x3F && i >= 0) {
            if (i <= 0x1F) { // tiny
                output[ptr++] = (byte) (TOKEN_PREFIX_SMALL_INT + i);
                return ptr;
            }
            // nope, just small, 2 bytes (type, 1-byte zigzag value) for 6 bit value
            output[ptr++] = TOKEN_BYTE_INT_32;
            output[ptr++] = (byte) (0x80 + i);
            return ptr;
        }
        output[ptr++] = TOKEN_BYTE_INT_32;
        // Ok: let's find minimal representation then
        byte b0 = (byte) (0x80 + (i & 0x3F));
        i >>>= 6;
        if (i <= 0x7F) { // 13 bits is enough (== 3 byte total encoding)
            output[ptr++] = (byte) i;
            output[ptr++] = b0;
            return ptr;
        }
        byte b1 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            output[ptr++] = (byte) i;
            output[ptr++] = b1;
            output[ptr++] = b0;
            return ptr;
        }
        byte b2 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            output[ptr++] = (byte) i;
            output[ptr++] = b2;
            output[ptr++] = b1;
            output[ptr++] = b0;
            return ptr;
        }
        // no, need all 5 bytes
        byte b3 = (byte) (i & 0x7F);
        output[ptr++] = (byte) (i >> 7);
        output[ptr++] = b3;
        output[ptr++] = b2;
        output[ptr++] = b1;
        output[ptr++] = b0;
        return ptr;
    }

    @Override
    public JsonGenerator writeNumber(long l) throws JacksonException
    {
        // First: maybe 32 bits is enough?
        if (l <= MAX_INT_AS_LONG && l >= MIN_INT_AS_LONG) {
            writeNumber((int) l);
            return this;
        }
        _verifyValueWrite("write number");
        // Then let's zigzag encode it

        l = SmileUtil.zigzagEncode(l);
        // Ok, well, we do know that 5 lowest-significant bytes are needed
        int i = (int) l;
        // 4 can be extracted from lower int
        byte b0 = (byte) (0x80 + (i & 0x3F)); // sign bit set in the last byte
        byte b1 = (byte) ((i >> 6) & 0x7F);
        byte b2 = (byte) ((i >> 13) & 0x7F);
        byte b3 = (byte) ((i >> 20) & 0x7F);
        // fifth one is split between ints:
        l >>>= 27;
        byte b4 = (byte) (((int) l) & 0x7F);

        // which may be enough?
        i = (int) (l >> 7);
        if (i == 0) {
            _writeBytes(TOKEN_BYTE_INT_64, b4, b3, b2, b1, b0);
            return this;
        }

        if (i <= 0x7F) {
            _writeBytes(TOKEN_BYTE_INT_64, (byte) i);
            _writeBytes(b4, b3, b2, b1, b0);
            return this;
        }
        byte b5 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            _writeBytes(TOKEN_BYTE_INT_64, (byte) i);
            _writeBytes(b5, b4, b3, b2, b1, b0);
            return this;
        }
        byte b6 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            _writeBytes(TOKEN_BYTE_INT_64, (byte) i, b6);
            _writeBytes(b5, b4, b3, b2, b1, b0);
            return this;
        }
        byte b7 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            _writeBytes(TOKEN_BYTE_INT_64, (byte) i, b7, b6);
            _writeBytes(b5, b4, b3, b2, b1, b0);
            return this;
        }
        byte b8 = (byte) (i & 0x7F);
        i >>= 7;
        // must be done, with 10 bytes! (9 * 7 + 6 == 69 bits; only need 63)
        _writeBytes(TOKEN_BYTE_INT_64, (byte) i, b8, b7, b6);
        _writeBytes(b5, b4, b3, b2, b1, b0);
        return this;
    }

    // since 2.8: same as `writeNumber(int)` minus validity checks for
    // value write AND boundary checks
    private final int _writeNumberNoChecks(int ptr, long l) throws JacksonException
    {
        // First: maybe 32 bits is enough?
        if (l <= MAX_INT_AS_LONG && l >= MIN_INT_AS_LONG) {
            return _writeNumberNoChecks(ptr, (int) l);
        }
        l = SmileUtil.zigzagEncode(l);
        // Ok, well, we do know that 5 lowest-significant bytes are needed
        int i = (int) l;
        // 4 can be extracted from lower int
        byte b0 = (byte) (0x80 + (i & 0x3F)); // sign bit set in the last byte
        byte b1 = (byte) ((i >> 6) & 0x7F);
        byte b2 = (byte) ((i >> 13) & 0x7F);
        byte b3 = (byte) ((i >> 20) & 0x7F);
        // fifth one is split between ints:
        l >>>= 27;
        byte b4 = (byte) (((int) l) & 0x7F);

        final byte[] output = _outputBuffer;
        output[ptr++] = TOKEN_BYTE_INT_64;

        // which may be enough?
        i = (int) (l >> 7);
        if (i == 0) {
            output[ptr++] = b4;
            output[ptr++] = b3;
            output[ptr++] = b2;
            output[ptr++] = b1;
            output[ptr++] = b0;
            return ptr;
        }

        if (i <= 0x7F) {
            output[ptr++] = (byte) i;
            output[ptr++] = b4;
            output[ptr++] = b3;
            output[ptr++] = b2;
            output[ptr++] = b1;
            output[ptr++] = b0;
            return ptr;
        }
        byte b5 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            output[ptr++] = (byte) i;
            output[ptr++] = b5;
            output[ptr++] = b4;
            output[ptr++] = b3;
            output[ptr++] = b2;
            output[ptr++] = b1;
            output[ptr++] = b0;
            return ptr;
        }
        byte b6 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            output[ptr++] = (byte) i;
            output[ptr++] = b6;
            output[ptr++] = b5;
            output[ptr++] = b4;
            output[ptr++] = b3;
            output[ptr++] = b2;
            output[ptr++] = b1;
            output[ptr++] = b0;
            return ptr;
        }
        byte b7 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            output[ptr++] = (byte) i;
            output[ptr++] = b7;
            output[ptr++] = b6;
            output[ptr++] = b5;
            output[ptr++] = b4;
            output[ptr++] = b3;
            output[ptr++] = b2;
            output[ptr++] = b1;
            output[ptr++] = b0;
            return ptr;
        }
        byte b8 = (byte) (i & 0x7F);
        i >>= 7;
        // must be done, with 10 bytes! (9 * 7 + 6 == 69 bits; only need 63)
        output[ptr++] = (byte) i;
        output[ptr++] = b8;
        output[ptr++] = b7;
        output[ptr++] = b6;
        output[ptr++] = b5;
        output[ptr++] = b4;
        output[ptr++] = b3;
        output[ptr++] = b2;
        output[ptr++] = b1;
        output[ptr++] = b0;
        return ptr;
    }

    @Override
    public JsonGenerator writeNumber(BigInteger v) throws JacksonException
    {
        if (v == null) {
            return writeNull();
        }
        _verifyValueWrite("write number");
        // quite simple: type, and then VInt-len prefixed 7-bit encoded binary data:
        _writeByte(TOKEN_BYTE_BIG_INTEGER);
        byte[] data = v.toByteArray();
        _write7BitBinaryWithLength(data, 0, data.length);
        return this;
    }

    @Override
    public JsonGenerator writeNumber(double d) throws JacksonException
    {
        // Ok, now, we needed token type byte plus 10 data bytes (7 bits each)
        _ensureRoomForOutput(11);
        _verifyValueWrite("write number");
        /* 17-Apr-2010, tatu: could also use 'doubleToIntBits', but it seems more accurate to use
         * exact representation; and possibly faster. However, if there are cases
         * where collapsing of NaN was needed (for non-Java clients), this can
         * be changed
         */
        long l = Double.doubleToRawLongBits(d);
        _outputBuffer[_outputTail++] = TOKEN_BYTE_FLOAT_64;
        // Handle first 29 bits (single bit first, then 4 x 7 bits)
        int hi5 = (int) (l >>> 35);
        _outputBuffer[_outputTail+4] = (byte) (hi5 & 0x7F);
        hi5 >>= 7;
        _outputBuffer[_outputTail+3] = (byte) (hi5 & 0x7F);
        hi5 >>= 7;
        _outputBuffer[_outputTail+2] = (byte) (hi5 & 0x7F);
        hi5 >>= 7;
        _outputBuffer[_outputTail+1] = (byte) (hi5 & 0x7F);
        hi5 >>= 7;
        _outputBuffer[_outputTail] = (byte) hi5;
        _outputTail += 5;
        // Then split byte (one that crosses lo/hi int boundary), 7 bits
        {
            int mid = (int) (l >> 28);
            _outputBuffer[_outputTail++] = (byte) (mid & 0x7F);
        }
        // and then last 4 bytes (28 bits)
        int lo4 = (int) l;
        _outputBuffer[_outputTail+3] = (byte) (lo4 & 0x7F);
        lo4 >>= 7;
        _outputBuffer[_outputTail+2] = (byte) (lo4 & 0x7F);
        lo4 >>= 7;
        _outputBuffer[_outputTail+1] = (byte) (lo4 & 0x7F);
        lo4 >>= 7;
        _outputBuffer[_outputTail] = (byte) (lo4 & 0x7F);
        _outputTail += 4;
        return this;
    }

    private final int _writeNumberNoChecks(int ptr, double d) throws JacksonException
    {
        long l = Double.doubleToRawLongBits(d);
        final byte[] output = _outputBuffer;
        output[ptr++] = TOKEN_BYTE_FLOAT_64;
        // Handle first 29 bits (single bit first, then 4 x 7 bits)
        int hi5 = (int) (l >>> 35);
        output[ptr+4] = (byte) (hi5 & 0x7F);
        hi5 >>= 7;
        output[ptr+3] = (byte) (hi5 & 0x7F);
        hi5 >>= 7;
        output[ptr+2] = (byte) (hi5 & 0x7F);
        hi5 >>= 7;
        output[ptr+1] = (byte) (hi5 & 0x7F);
        hi5 >>= 7;
        output[ptr] = (byte) hi5;
        ptr += 5;
        // Then split byte (one that crosses lo/hi int boundary), 7 bits
        {
            int mid = (int) (l >> 28);
            output[ptr++] = (byte) (mid & 0x7F);
        }
        // and then last 4 bytes (28 bits)
        int lo4 = (int) l;
        output[ptr+3] = (byte) (lo4 & 0x7F);
        lo4 >>= 7;
        output[ptr+2] = (byte) (lo4 & 0x7F);
        lo4 >>= 7;
        output[ptr+1] = (byte) (lo4 & 0x7F);
        lo4 >>= 7;
        output[ptr] = (byte) (lo4 & 0x7F);
        return ptr + 4;
    }

    @Override
    public JsonGenerator writeNumber(float f) throws JacksonException
    {
        // Ok, now, we needed token type byte plus 5 data bytes (7 bits each)
        _ensureRoomForOutput(6);
        _verifyValueWrite("write number");

        /* 17-Apr-2010, tatu: could also use 'floatToIntBits', but it seems more accurate to use
         * exact representation; and possibly faster. However, if there are cases
         * where collapsing of NaN was needed (for non-Java clients), this can
         * be changed
         */
        int i = Float.floatToRawIntBits(f);
        _outputBuffer[_outputTail++] = TOKEN_BYTE_FLOAT_32;
        _outputBuffer[_outputTail+4] = (byte) (i & 0x7F);
        i >>= 7;
        _outputBuffer[_outputTail+3] = (byte) (i & 0x7F);
        i >>= 7;
        _outputBuffer[_outputTail+2] = (byte) (i & 0x7F);
        i >>= 7;
        _outputBuffer[_outputTail+1] = (byte) (i & 0x7F);
        i >>= 7;
        _outputBuffer[_outputTail] = (byte) (i & 0x7F);
        _outputTail += 5;
        return this;
    }

    @Override
    public JsonGenerator writeNumber(BigDecimal dec) throws JacksonException
    {
        if (dec == null) {
            return writeNull();
        }
        _verifyValueWrite("write number");
        _writeByte(TOKEN_BYTE_BIG_DECIMAL);
        int scale = dec.scale();
        // Ok, first output scale as VInt
        _writeSignedVInt(scale);
        BigInteger unscaled = dec.unscaledValue();
        byte[] data = unscaled.toByteArray();
        // And then binary data in "safe" mode (7-bit values)
        _write7BitBinaryWithLength(data, 0, data.length);
        return this;
    }

    @Override
    public JsonGenerator writeNumber(String encodedValue) throws JacksonException
    {
        if (encodedValue == null) {
            return writeNull();
        }

        // 28-May-2014, tatu: Let's actually try to support this method; should be doable
        final int len = encodedValue.length();
        boolean neg = encodedValue.startsWith("-");

        // Let's see if it's integral or not
        int i = neg ? 1 : 0;
        if (i >= len) {
            _writeIntegralNumber(encodedValue, neg);
            return this;
        }
        while (true) {
            char c = encodedValue.charAt(i);
            if (c > '9' || c < '0') {
                break;
            }
            if (++i == len) {
                _writeIntegralNumber(encodedValue, neg);
                return this;
            }
        }
        _writeDecimalNumber(encodedValue);
        return this;
    }

    protected void _writeIntegralNumber(String enc, boolean neg) throws JacksonException
    {
        int len = enc.length();
        // 16-Dec-2023, tatu: Guard against too-big numbers
        _streamReadConstraints().validateIntegerLength(len);
        if (neg) {
            --len;
        }
        // let's do approximate optimization
        try {
            if (len <= 9) {
                // Avoid exception from empty String
                if (len > 0) {
                    writeNumber(Integer.parseInt(enc));
                }
            } else if (len <= 18) {
                writeNumber(Long.parseLong(enc));
            } else {
                writeNumber(NumberInput.parseBigInteger(enc, false));
            }
            return;
        } catch (NumberFormatException e) { }
        throw _constructWriteException("Invalid String representation for Number ('"+enc
                +"'); can not write using Smile format");
    }

    protected void _writeDecimalNumber(String enc) throws JacksonException
    {
        // 16-Dec-2023, tatu: Guard against too-big numbers
        _streamReadConstraints().validateFPLength(enc.length());
        // ... and check basic validity too
        if (NumberInput.looksLikeValidNumber(enc)) {
            try {
                writeNumber(NumberInput.parseBigDecimal(enc, false));
                return;
            } catch (NumberFormatException e) { }
        }
        throw _constructWriteException("Invalid String representation for Number ('"+enc
                +"'); can not write using Smile format");
    }

    /*
    /**********************************************************************
    /* Implementations for other methods
    /**********************************************************************
     */

    @Override
    protected final void _verifyValueWrite(String typeMsg)
        throws JacksonException
    {
        if (!_streamWriteContext.writeValue()) {
            throw _constructWriteException("Cannot "+typeMsg+", expecting a property name");
        }
    }

    /*
    /**********************************************************************
    /* Low-level output handling
    /**********************************************************************
     */

    @Override
    public final void flush() throws JacksonException
    {
        _flushBuffer();
        if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            try {
                _out.flush();
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
        }
    }

    @Override
    protected void _closeInput() throws IOException
    {
        // First: let's see that we still have buffers...
        if (_outputBuffer != null
            && isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT)) {
            while (true) {
                TokenStreamContext ctxt = streamWriteContext();
                if (ctxt.inArray()) {
                    writeEndArray();
                } else if (ctxt.inObject()) {
                    writeEndObject();
                } else {
                    break;
                }
            }
        }
        boolean wasClosed = _closed;
        if (!wasClosed && isEnabled(Feature.WRITE_END_MARKER)) {
            _writeByte(BYTE_MARKER_END_OF_CONTENT);
        }
        _flushBuffer();

        if (_ioContext.isResourceManaged() || isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET)) {
            _out.close();
        } else if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            // If we can't close it, we should at least flush
            // 14-Jan-2019, tatu: [dataformats-binary#155]: unless prevented via feature
            _out.flush();
        }
    }

    /*
    /**********************************************************************
    /* Internal methods, UTF-8 encoding
    /**********************************************************************
     */

    /**
     * Helper method called when the whole character sequence is known to
     * fit in the output buffer regardless of UTF-8 expansion.
     */
    private final int _shortUTF8Encode(char[] str, int i, int end)
        throws JacksonException
    {
        // First: let's see if it's all ASCII: that's rather fast
        int ptr = _outputTail;
        final byte[] outBuf = _outputBuffer;
        do {
            int c = str[i];
            if (c > 0x7F) {
                return _shortUTF8Encode2(str, i, end, ptr);
            }
            outBuf[ptr++] = (byte) c;
        } while (++i < end);
        int codedLen = ptr - _outputTail;
        _outputTail = ptr;
        return codedLen;
    }

    /**
     * Helper method called when the whole character sequence is known to
     * fit in the output buffer, but not all characters are single-byte (ASCII)
     * characters.
     */
    private final int _shortUTF8Encode2(char[] str, int i, int end, int outputPtr)
        throws JacksonException
    {
        final byte[] outBuf = _outputBuffer;
        while (i < end) {
            int c = str[i++];
            if (c <= 0x7F) {
                outBuf[outputPtr++] = (byte) c;
                continue;
            }
            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                outBuf[outputPtr++] = (byte) (0xc0 | (c >> 6));
                outBuf[outputPtr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            // 3 or 4 bytes (surrogate)
            // Surrogates?
            if (c < SURR1_FIRST || c > SURR2_LAST) { // nope, regular 3-byte character
                outBuf[outputPtr++] = (byte) (0xe0 | (c >> 12));
                outBuf[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                outBuf[outputPtr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            // Yup, looks like a surrogate pair... but is it?
            if ((c <= SURR1_LAST) && (i < end)) { // must be from first range and have another char
                final int d = str[i];
                if ((d <= SURR2_LAST) && (d >= SURR2_FIRST)) {
                    ++i;
                    outputPtr = _decodeAndWriteSurrogate(c, d, outBuf, outputPtr);
                    continue;
                }
                outputPtr = _invalidSurrogateEnd(c, d, outBuf, outputPtr);
                continue;
            }
            // Nah, something wrong
            outputPtr = _invalidSurrogateStart(c, outBuf, outputPtr);
        }
        int codedLen = outputPtr - _outputTail;
        _outputTail = outputPtr;
        return codedLen;
    }

    private final int _shortUTF8Encode(String str, int i, int end)
        throws JacksonException
    {
        // First: let's see if it's all ASCII: that's rather fast
        int ptr = _outputTail;
        final byte[] outBuf = _outputBuffer;
        do {
            int c = str.charAt(i);
            if (c > 0x7F) {
                return _shortUTF8Encode2(str, i, end, ptr);
            }
            outBuf[ptr++] = (byte) c;
        } while (++i < end);
        int codedLen = ptr - _outputTail;
        _outputTail = ptr;
        return codedLen;
    }

    private final int _shortUTF8Encode2(String str, int i, int end, int outputPtr)
        throws JacksonException
    {
        final byte[] outBuf = _outputBuffer;
        while (i < end) {
            int c = str.charAt(i++);
            if (c <= 0x7F) {
                outBuf[outputPtr++] = (byte) c;
                continue;
            }
            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                outBuf[outputPtr++] = (byte) (0xc0 | (c >> 6));
                outBuf[outputPtr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            // 3 or 4 bytes (surrogate)
            // Surrogates?
            if (c < SURR1_FIRST || c > SURR2_LAST) { // nope, regular 3-byte character
                outBuf[outputPtr++] = (byte) (0xe0 | (c >> 12));
                outBuf[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                outBuf[outputPtr++] = (byte) (0x80 | (c & 0x3f));
                continue;
            }
            // Yup, looks like a surrogate pair... but is it?
            if ((c <= SURR1_LAST) && (i < end)) { // must be from first range and have another char
                final int d = str.charAt(i);
                if ((d <= SURR2_LAST) && (d >= SURR2_FIRST)) {
                    ++i;
                    outputPtr = _decodeAndWriteSurrogate(c, d, outBuf, outputPtr);
                    continue;
                }
                outputPtr = _invalidSurrogateEnd(c, d, outBuf, outputPtr);
                continue;
            }
            // Nah, something wrong
            outputPtr = _invalidSurrogateStart(c, outBuf, outputPtr);
        }
        int codedLen = outputPtr - _outputTail;
        _outputTail = outputPtr;
        return codedLen;
    }

    private void _mediumUTF8Encode(char[] str, int inputPtr, int inputEnd) throws JacksonException
    {
        final int bufferEnd = _outputEnd - 4;

        output_loop:
        while (inputPtr < inputEnd) {
            // First, let's ensure we can output at least 4 bytes
            // (longest UTF-8 encoded codepoint):
            if (_outputTail >= bufferEnd) {
                _flushBuffer();
            }
            int c = str[inputPtr++];
            // And then see if we have an ASCII char:
            if (c <= 0x7F) { // If so, can do a tight inner loop:
                _outputBuffer[_outputTail++] = (byte)c;
                // Let's calc how many ASCII chars we can copy at most:
                int maxInCount = (inputEnd - inputPtr);
                int maxOutCount = (bufferEnd - _outputTail);

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount;
                }
                maxInCount += inputPtr;
                ascii_loop:
                while (true) {
                    if (inputPtr >= maxInCount) { // done with max. ascii seq
                        continue output_loop;
                    }
                    c = str[inputPtr++];
                    if (c > 0x7F) {
                        break ascii_loop;
                    }
                    _outputBuffer[_outputTail++] = (byte) c;
                }
            }

            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                _outputBuffer[_outputTail++] = (byte) (0xc0 | (c >> 6));
                _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) {
                    _outputBuffer[_outputTail++] = (byte) (0xe0 | (c >> 12));
                    _outputBuffer[_outputTail++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                    _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
                    continue;
                }
                // Yup, looks like a surrogate pair... but is it?
                if ((c <= SURR1_LAST) && (inputPtr < inputEnd)) { // must be from first range and have another char
                    final int d = str[inputPtr];
                    if ((d <= SURR2_LAST) && (d >= SURR2_FIRST)) {
                        ++inputPtr;
                        _outputTail = _decodeAndWriteSurrogate(c, d, _outputBuffer, _outputTail);
                        continue;
                    }
                    _outputTail = _invalidSurrogateEnd(c, d, _outputBuffer, _outputTail);
                    continue;
                }
                // Nah, something wrong
                _outputTail = _invalidSurrogateStart(c, _outputBuffer, _outputTail);
            }
        }
    }

    private void _mediumUTF8Encode(String str, int inputPtr, int inputEnd) throws JacksonException
    {
        final int bufferEnd = _outputEnd - 4;

        output_loop:
        while (inputPtr < inputEnd) {
            // First, let's ensure we can output at least 4 bytes
            // (longest UTF-8 encoded codepoint):
            if (_outputTail >= bufferEnd) {
                _flushBuffer();
            }
            int c = str.charAt(inputPtr++);
            // And then see if we have an ASCII char:
            if (c <= 0x7F) { // If so, can do a tight inner loop:
                _outputBuffer[_outputTail++] = (byte)c;
                // Let's calc how many ASCII chars we can copy at most:
                int maxInCount = (inputEnd - inputPtr);
                int maxOutCount = (bufferEnd - _outputTail);

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount;
                }
                maxInCount += inputPtr;
                ascii_loop:
                while (true) {
                    if (inputPtr >= maxInCount) { // done with max. ascii seq
                        continue output_loop;
                    }
                    c = str.charAt(inputPtr++);
                    if (c > 0x7F) {
                        break ascii_loop;
                    }
                    _outputBuffer[_outputTail++] = (byte) c;
                }
            }

            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                _outputBuffer[_outputTail++] = (byte) (0xc0 | (c >> 6));
                _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) {
                    _outputBuffer[_outputTail++] = (byte) (0xe0 | (c >> 12));
                    _outputBuffer[_outputTail++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                    _outputBuffer[_outputTail++] = (byte) (0x80 | (c & 0x3f));
                    continue;
                }
                // Yup, looks like a surrogate pair... but is it?
                if ((c <= SURR1_LAST) && (inputPtr < inputEnd)) { // must be from first range and have another char
                    final int d = str.charAt(inputPtr);
                    if ((d <= SURR2_LAST) && (d >= SURR2_FIRST)) {
                        ++inputPtr;
                        _outputTail = _decodeAndWriteSurrogate(c, d, _outputBuffer, _outputTail);
                        continue;
                    }
                    _outputTail = _invalidSurrogateEnd(c, d, _outputBuffer, _outputTail);
                    continue;
                }
                // Nah, something wrong
                _outputTail = _invalidSurrogateStart(c, _outputBuffer, _outputTail);
            }
        }
    }

    /*
    /**********************************************************************
    /* Internal methods, surrogate pair handling
    /**********************************************************************
     */

    private int _invalidSurrogateStart(int code, byte[] outBuf, int outputPtr)
        throws JacksonException
    {
        if (isEnabled(Feature.LENIENT_UTF_ENCODING)) {
            return _appendReplacementChar(outBuf, outputPtr);
        }
        // Will be called in two distinct cases: either first character is
        // invalid (code range of second part), or first character is valid
        // but there is no second part to encode
        if (code <= SURR1_LAST) {
            // Unmatched first part (closing without second part?)
            _reportError(String.format(
"Unmatched surrogate pair, starts with valid high surrogate (0x%04X) but ends without low surrogate",
code));
        }
        _reportError(String.format(
"Invalid surrogate pair, starts with invalid high surrogate (0x%04X), not in valid range [0xD800, 0xDBFF]",
code));
        return 0; // never gets here
    }

    private int _invalidSurrogateEnd(int surr1, int surr2,
            byte[] outBuf, int outputPtr)
        throws JacksonException
    {
        if (isEnabled(Feature.LENIENT_UTF_ENCODING)) {
            return _appendReplacementChar(outBuf, outputPtr);
        }
        _reportError(String.format(
"Invalid surrogate pair, starts with valid high surrogate (0x%04X)"
+" but ends with invalid low surrogate (0x%04X), not in valid range [0xDC00, 0xDFFF]",
surr1, surr2));
        return 0; // never gets here
    }

    private int _appendReplacementChar(byte[] outBuf, int outputPtr) {
        outBuf[outputPtr++] = (byte) (0xe0 | (REPLACEMENT_CHAR >> 12));
        outBuf[outputPtr++] = (byte) (0x80 | ((REPLACEMENT_CHAR >> 6) & 0x3f));
        outBuf[outputPtr++] = (byte) (0x80 | (REPLACEMENT_CHAR & 0x3f));
        return outputPtr;
    }

    private int _decodeAndWriteSurrogate(int surr1, int surr2,
            byte[] outBuf, int outputPtr)
    {
        final int c = 0x10000 + ((surr1 - SURR1_FIRST) << 10)
                + (surr2 - SURR2_FIRST);
        outBuf[outputPtr++] = (byte) (0xf0 | (c >> 18));
        outBuf[outputPtr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
        outBuf[outputPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
        outBuf[outputPtr++] = (byte) (0x80 | (c & 0x3f));
        return outputPtr;
    }

    /*
    /**********************************************************************
    /* Internal methods, writing bytes
    /**********************************************************************
     */

    private final void _ensureRoomForOutput(int needed) throws JacksonException
    {
        if ((_outputTail + needed) >= _outputEnd) {
            _flushBuffer();
        }
    }

    private final void _writeByte(byte b) throws JacksonException
    {
        if (_outputTail >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = b;
    }

    private final void _writeBytes(byte b1, byte b2) throws JacksonException
    {
        if ((_outputTail + 1) >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = b1;
        _outputBuffer[_outputTail++] = b2;
    }

    private final void _writeBytes(byte b1, byte b2, byte b3) throws JacksonException
    {
        if ((_outputTail + 2) >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = b1;
        _outputBuffer[_outputTail++] = b2;
        _outputBuffer[_outputTail++] = b3;
    }

    private final void _writeBytes(byte b1, byte b2, byte b3, byte b4) throws JacksonException
    {
        if ((_outputTail + 3) >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = b1;
        _outputBuffer[_outputTail++] = b2;
        _outputBuffer[_outputTail++] = b3;
        _outputBuffer[_outputTail++] = b4;
    }

    private final void _writeBytes(byte b1, byte b2, byte b3, byte b4, byte b5) throws JacksonException
    {
        if ((_outputTail + 4) >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = b1;
        _outputBuffer[_outputTail++] = b2;
        _outputBuffer[_outputTail++] = b3;
        _outputBuffer[_outputTail++] = b4;
        _outputBuffer[_outputTail++] = b5;
    }

    private final void _writeBytes(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6) throws JacksonException
    {
        if ((_outputTail + 5) >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = b1;
        _outputBuffer[_outputTail++] = b2;
        _outputBuffer[_outputTail++] = b3;
        _outputBuffer[_outputTail++] = b4;
        _outputBuffer[_outputTail++] = b5;
        _outputBuffer[_outputTail++] = b6;
    }

    private final void _writeBytes(byte[] data, int offset, int len) throws JacksonException
    {
        if (len == 0) {
            return;
        }
        if ((_outputTail + len) >= _outputEnd) {
            _writeBytesLong(data, offset, len);
            return;
        }
        // common case, non-empty, fits in just fine:
        System.arraycopy(data, offset, _outputBuffer, _outputTail, len);
        _outputTail += len;
    }

    private final int _writeBytes(InputStream in, int bytesLeft) throws JacksonException
    {
        while (bytesLeft > 0) {
            int room = _outputEnd - _outputTail;
            if (room <= 0) {
                _flushBuffer();
                room = _outputEnd - _outputTail;
            }
            if (room > bytesLeft) {
                room = bytesLeft;
            }
            int count;
            try {
                count = in.read(_outputBuffer, _outputTail, room);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            if (count < 0) {
                break;
            }
            _outputTail += count;
            bytesLeft -= count;
        }
        return bytesLeft;
    }

    private final void _writeBytesLong(byte[] data, int offset, int len) throws JacksonException
    {
        if (_outputTail >= _outputEnd) {
            _flushBuffer();
        }
        while (true) {
            int currLen = Math.min(len, (_outputEnd - _outputTail));
            System.arraycopy(data, offset, _outputBuffer, _outputTail, currLen);
            _outputTail += currLen;
            if ((len -= currLen) == 0) {
                break;
            }
            offset += currLen;
            _flushBuffer();
        }
    }

    /**
     * Helper method for writing a 32-bit positive (really 31-bit then) value.
     * Value is NOT zigzag encoded (since there is no sign bit to worry about)
     */
    private void _writePositiveVInt(int i) throws JacksonException
    {
        // At most 5 bytes (4 * 7 + 6 bits == 34 bits)
        _ensureRoomForOutput(5);
        byte b0 = (byte) (0x80 + (i & 0x3F));
        i >>= 6;
        if (i <= 0x7F) { // 6 or 13 bits is enough (== 2 or 3 byte total encoding)
            if (i > 0) {
                _outputBuffer[_outputTail++] = (byte) i;
            }
            _outputBuffer[_outputTail++] = b0;
            return;
        }
        byte b1 = (byte) (i & 0x7F);
        i >>= 7;
        if (i <= 0x7F) {
            _outputBuffer[_outputTail++] = (byte) i;
            _outputBuffer[_outputTail++] = b1;
            _outputBuffer[_outputTail++] = b0;
        } else {
            byte b2 = (byte) (i & 0x7F);
            i >>= 7;
            if (i <= 0x7F) {
                _outputBuffer[_outputTail++] = (byte) i;
                _outputBuffer[_outputTail++] = b2;
                _outputBuffer[_outputTail++] = b1;
                _outputBuffer[_outputTail++] = b0;
            } else {
                byte b3 = (byte) (i & 0x7F);
                _outputBuffer[_outputTail++] = (byte) (i >> 7);
                _outputBuffer[_outputTail++] = b3;
                _outputBuffer[_outputTail++] = b2;
                _outputBuffer[_outputTail++] = b1;
                _outputBuffer[_outputTail++] = b0;
            }
        }
    }

    /**
     * Helper method for writing 32-bit signed value, using
     * "zig zag encoding" (see protocol buffers for explanation -- basically,
     * sign bit is moved as LSB, rest of value shifted left by one)
     * coupled with basic variable length encoding
     */
    private void _writeSignedVInt(int input) throws JacksonException
    {
        _writePositiveVInt(SmileUtil.zigzagEncode(input));
    }

    protected void _write7BitBinaryWithLength(byte[] data, int offset, int len) throws JacksonException
    {
        _writePositiveVInt(len);
        // first, let's handle full 7-byte chunks
        while (len >= 7) {
            if ((_outputTail + 8) >= _outputEnd) {
                _flushBuffer();
            }
            int i = data[offset++]; // 1st byte
            _outputBuffer[_outputTail++] = (byte) ((i >> 1) & 0x7F);
            i = (i << 8) | (data[offset++] & 0xFF); // 2nd
            _outputBuffer[_outputTail++] = (byte) ((i >> 2) & 0x7F);
            i = (i << 8) | (data[offset++] & 0xFF); // 3rd
            _outputBuffer[_outputTail++] = (byte) ((i >> 3) & 0x7F);
            i = (i << 8) | (data[offset++] & 0xFF); // 4th
            _outputBuffer[_outputTail++] = (byte) ((i >> 4) & 0x7F);
            i = (i << 8) | (data[offset++] & 0xFF); // 5th
            _outputBuffer[_outputTail++] = (byte) ((i >> 5) & 0x7F);
            i = (i << 8) | (data[offset++] & 0xFF); // 6th
            _outputBuffer[_outputTail++] = (byte) ((i >> 6) & 0x7F);
            i = (i << 8) | (data[offset++] & 0xFF); // 7th
            _outputBuffer[_outputTail++] = (byte) ((i >> 7) & 0x7F);
            _outputBuffer[_outputTail++] = (byte) (i & 0x7F);
            len -= 7;
        }
        // and then partial piece, if any
        if (len > 0) {
            // up to 6 bytes to output, resulting in at most 7 bytes (which can encode 49 bits)
            if ((_outputTail + 7) >= _outputEnd) {
                _flushBuffer();
            }
            int i = data[offset++];
            _outputBuffer[_outputTail++] = (byte) ((i >> 1) & 0x7F);
            if (len > 1) {
                i = ((i & 0x01) << 8) | (data[offset++] & 0xFF); // 2nd
                _outputBuffer[_outputTail++] = (byte) ((i >> 2) & 0x7F);
                if (len > 2) {
                    i = ((i & 0x03) << 8) | (data[offset++] & 0xFF); // 3rd
                    _outputBuffer[_outputTail++] = (byte) ((i >> 3) & 0x7F);
                    if (len > 3) {
                        i = ((i & 0x07) << 8) | (data[offset++] & 0xFF); // 4th
                        _outputBuffer[_outputTail++] = (byte) ((i >> 4) & 0x7F);
                        if (len > 4) {
                            i = ((i & 0x0F) << 8) | (data[offset++] & 0xFF); // 5th
                            _outputBuffer[_outputTail++] = (byte) ((i >> 5) & 0x7F);
                            if (len > 5) {
                                i = ((i & 0x1F) << 8) | (data[offset++] & 0xFF); // 6th
                                _outputBuffer[_outputTail++] = (byte) ((i >> 6) & 0x7F);
                                _outputBuffer[_outputTail++] = (byte) (i & 0x3F); // last 6 bits
                            } else {
                                _outputBuffer[_outputTail++] = (byte) (i & 0x1F); // last 5 bits
                            }
                        } else {
                            _outputBuffer[_outputTail++] = (byte) (i & 0x0F); // last 4 bits
                        }
                    } else {
                        _outputBuffer[_outputTail++] = (byte) (i & 0x07); // last 3 bits
                    }
                } else {
                    _outputBuffer[_outputTail++] = (byte) (i & 0x03); // last 2 bits
                }
            } else {
                _outputBuffer[_outputTail++] = (byte) (i & 0x01); // last bit
            }
        }
    }

    protected int _write7BitBinaryWithLength(InputStream in, int bytesLeft, byte[] buffer)
        throws JacksonException
    {
        _writePositiveVInt(bytesLeft);
        int inputPtr = 0;
        int inputEnd = 0;
        int lastFullOffset = -7;

        // first, let's handle full 7-byte chunks
        while (bytesLeft >= 7) {
            if (inputPtr > lastFullOffset) {
                inputEnd = _readMore(in, buffer, inputPtr, inputEnd, bytesLeft);
                inputPtr = 0;
                if (inputEnd < 7) { // required to try to read to have at least 7 bytes
                    bytesLeft -= inputEnd; // just to give accurate error messages wrt how much was gotten
                    break;
                }
                lastFullOffset = inputEnd-7;
            }
            if ((_outputTail + 8) >= _outputEnd) {
                _flushBuffer();
            }
            int i = buffer[inputPtr++]; // 1st byte
            _outputBuffer[_outputTail++] = (byte) ((i >> 1) & 0x7F);
            i = (i << 8) | (buffer[inputPtr++] & 0xFF); // 2nd
            _outputBuffer[_outputTail++] = (byte) ((i >> 2) & 0x7F);
            i = (i << 8) | (buffer[inputPtr++] & 0xFF); // 3rd
            _outputBuffer[_outputTail++] = (byte) ((i >> 3) & 0x7F);
            i = (i << 8) | (buffer[inputPtr++] & 0xFF); // 4th
            _outputBuffer[_outputTail++] = (byte) ((i >> 4) & 0x7F);
            i = (i << 8) | (buffer[inputPtr++] & 0xFF); // 5th
            _outputBuffer[_outputTail++] = (byte) ((i >> 5) & 0x7F);
            i = (i << 8) | (buffer[inputPtr++] & 0xFF); // 6th
            _outputBuffer[_outputTail++] = (byte) ((i >> 6) & 0x7F);
            i = (i << 8) | (buffer[inputPtr++] & 0xFF); // 7th
            _outputBuffer[_outputTail++] = (byte) ((i >> 7) & 0x7F);
            _outputBuffer[_outputTail++] = (byte) (i & 0x7F);
            bytesLeft -= 7;
        }

        // and then partial piece, if any
        if (bytesLeft > 0) {
            // up to 6 bytes to output, resulting in at most 7 bytes (which can encode 49 bits)
            if ((_outputTail + 7) >= _outputEnd) {
                _flushBuffer();
            }
            inputEnd = _readMore(in, buffer, inputPtr, inputEnd, bytesLeft);
            inputPtr = 0;
            if (inputEnd > 0) { // yes, but do we have room for output?
                bytesLeft -= inputEnd;
                int i = buffer[inputPtr++];
                _outputBuffer[_outputTail++] = (byte) ((i >> 1) & 0x7F);
                if (inputEnd > 1) {
                    i = ((i & 0x01) << 8) | (buffer[inputPtr++] & 0xFF); // 2nd
                    _outputBuffer[_outputTail++] = (byte) ((i >> 2) & 0x7F);
                    if (inputEnd > 2) {
                        i = ((i & 0x03) << 8) | (buffer[inputPtr++] & 0xFF); // 3rd
                        _outputBuffer[_outputTail++] = (byte) ((i >> 3) & 0x7F);
                        if (inputEnd > 3) {
                            i = ((i & 0x07) << 8) | (buffer[inputPtr++] & 0xFF); // 4th
                            _outputBuffer[_outputTail++] = (byte) ((i >> 4) & 0x7F);
                            if (inputEnd > 4) {
                                i = ((i & 0x0F) << 8) | (buffer[inputPtr++] & 0xFF); // 5th
                                _outputBuffer[_outputTail++] = (byte) ((i >> 5) & 0x7F);
                                if (inputEnd > 5) {
                                    i = ((i & 0x1F) << 8) | (buffer[inputPtr++] & 0xFF); // 6th
                                    _outputBuffer[_outputTail++] = (byte) ((i >> 6) & 0x7F);
                                    _outputBuffer[_outputTail++] = (byte) (i & 0x3F); // last 6 bits
                                } else {
                                    _outputBuffer[_outputTail++] = (byte) (i & 0x1F); // last 5 bits
                                }
                            } else {
                                _outputBuffer[_outputTail++] = (byte) (i & 0x0F); // last 4 bits
                            }
                        } else {
                            _outputBuffer[_outputTail++] = (byte) (i & 0x07); // last 3 bits
                        }
                    } else {
                        _outputBuffer[_outputTail++] = (byte) (i & 0x03); // last 2 bits
                    }
                } else {
                    _outputBuffer[_outputTail++] = (byte) (i & 0x01); // last bit
                }
            }
        }
        return bytesLeft;
    }

    private int _readMore(InputStream in,
            byte[] readBuffer, int inputPtr, int inputEnd,
            int maxRead) throws JacksonException
    {
        // anything to shift to front?
        int i = 0;
        while (inputPtr < inputEnd) {
            readBuffer[i++]  = readBuffer[inputPtr++];
        }
        inputPtr = 0;
        inputEnd = i;

        maxRead = Math.min(maxRead, readBuffer.length);

        do {
            // 26-Feb-2013, tatu: Similar to jackson-core issue #55, need to ensure
            //   we have something to read.
            int length = maxRead - inputEnd;
            if (length == 0) {
                break;
            }
            int count;
            try {
                count = in.read(readBuffer, inputEnd, length);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            if (count < 0) {
                return inputEnd;
            }
            inputEnd += count;
        } while (inputEnd < 7);
        return inputEnd;
    }

    /*
    /**********************************************************************
    /* Internal methods, buffer handling
    /**********************************************************************
     */

    @Override
    protected void _releaseBuffers()
    {
        byte[] buf = _outputBuffer;
        if (buf != null && _bufferRecyclable) {
            _outputBuffer = null;
            _ioContext.releaseWriteEncodingBuffer(buf);
        }
    }

    protected final void _flushBuffer() throws JacksonException
    {
        if (_outputTail > 0) {
            _bytesWritten += _outputTail;
            try {
                _out.write(_outputBuffer, 0, _outputTail);
            } catch (IOException e) {
                throw _wrapIOFailure(e);
            }
            _outputTail = 0;
        }
    }

    /*
    /**********************************************************************
    /* Internal methods, handling shared string "maps"
    /**********************************************************************
     */

    private final int _findSeenName(String name)
    {
        int hash = name.hashCode();
        SharedStringNode head = _seenNames[hash & (_seenNames.length-1)];
        if (head == null) {
            return -1;
        }
        SharedStringNode node = head;
        // first, identity match; assuming most of the time we get intern()ed String
        // And do unrolled initial check; 90+% likelihood head node has all info we need:
        if (node.value == name) {
            return node.index;
        }
        while ((node = node.next) != null) {
            if (node.value == name) {
                return node.index;
            }
        }
        // If not, equality check; we already know head is not null
        node = head;
        do {
            String value = node.value;
            if (value.hashCode() == hash && value.equals(name)) {
                return node.index;
            }
            node = node.next;
        } while (node != null);
        return -1;
    }

    private final void _addSeenName(String name)
    {
        // first: do we need to expand?
        if (_seenNameCount == _seenNames.length) {
            if (_seenNameCount == MAX_SHARED_NAMES) { // we are too full, restart from empty
                Arrays.fill(_seenNames, null);
                _seenNameCount = 0;
            } else { // we always start with modest default size (like 64), so expand to full
                SharedStringNode[] old = _seenNames;
                _seenNames = new SharedStringNode[MAX_SHARED_NAMES];
                final int mask = MAX_SHARED_NAMES-1;
                for (SharedStringNode node : old) {
                    while (node != null) {
                        int ix = node.value.hashCode() & mask;
                        SharedStringNode next = node.next;
                        node.next = _seenNames[ix];
                        _seenNames[ix] = node;
                        node = next;
                    }
                }
            }
        }
        // other than that, just slap it there
        int ref = _seenNameCount;
        if (_validBackRef(ref)) {
            int ix = name.hashCode() & (_seenNames.length-1);
            _seenNames[ix] = new SharedStringNode(name, ref, _seenNames[ix]);
        }
        _seenNameCount = ref+1;
    }

    private final int _findSeenStringValue(String text)
    {
        int hash = text.hashCode();
        SharedStringNode head = _seenStringValues[hash & (_seenStringValues.length-1)];
        if (head != null) {
            SharedStringNode node = head;
            // first, identity match; assuming most of the time we get intern()ed String
            do {
                if (node.value == text) {
                    return node.index;
                }
                node = node.next;
            } while (node != null);
            // and then comparison, if no match yet
            node = head;
            do {
                String value = node.value;
                if (value.hashCode() == hash && value.equals(text)) {
                    return node.index;
                }
                node = node.next;
            } while (node != null);
        }
        return -1;
    }

    private final void _addSeenStringValue(String text)
    {
        // first: do we need to expand?
        if (_seenStringValueCount == _seenStringValues.length) {
            if (_seenStringValueCount == MAX_SHARED_STRING_VALUES) { // we are too full, restart from empty
                Arrays.fill(_seenStringValues, null);
                _seenStringValueCount = 0;
            } else { // we always start with modest default size (like 64), so expand to full
                SharedStringNode[] old = _seenStringValues;
                _seenStringValues = new SharedStringNode[MAX_SHARED_STRING_VALUES];
                final int mask = MAX_SHARED_STRING_VALUES-1;
                for (SharedStringNode node : old) {
                    while (node != null) {
                        int ix = node.value.hashCode() & mask;
                        SharedStringNode next = node.next;
                        node.next = _seenStringValues[ix];
                        _seenStringValues[ix] = node;
                        node = next;
                    }
                }
            }
        }
        // other than that, just slap it there
        /* [Issue#18]: Except need to avoid producing bytes 0xFE and 0xFF in content;
         *  so skip additions of those; this may produce duplicate values (and lower
         *  efficiency), but it must be done to since these bytes must be avoided by
         *  encoder, as per specification (except for native byte content, or as explicit
         *  end markers). Avoiding nulls is sort of
         */
        int ref = _seenStringValueCount;
        if (_validBackRef(ref)) {
            int ix = text.hashCode() & (_seenStringValues.length-1);
            _seenStringValues[ix] = new SharedStringNode(text, ref, _seenStringValues[ix]);
        }
        _seenStringValueCount = ref+1;
    }

    /**
     * Helper method used to ensure that we do not use back-reference values
     * that would produce illegal byte sequences (ones with byte 0xFE or 0xFF).
     * Note that we do not try to avoid null byte (0x00) by default, although
     * it would be technically possible as well.
     */
    private final static boolean _validBackRef(int index) {
        return (index & 0xFF) < 0xFE;
    }

    /*
    /**********************************************************************
    /* Internal methods, error reporting
    /**********************************************************************
     */

    /**
     * Method for accessing offset of the next byte within the whole output
     * stream that this generator has produced.
     */
    protected long outputOffset() {
        return _bytesWritten + _outputTail;
    }

    protected UnsupportedOperationException _notSupported() {
        return new UnsupportedOperationException();
    }

    /*
    /**********************************************************
    /* Internal methods, misc other
    /**********************************************************
     */

    /**
     * We need access to some reader-side constraints for safety-check within
     * number decoding for {@linl #writeNumber(String)}: for now we need to
     * rely on global defaults; should be ok for basic safeguarding.
     *
     * @since 2.17
     */
    protected StreamReadConstraints _streamReadConstraints() {
        return StreamReadConstraints.defaults();
    }
}
