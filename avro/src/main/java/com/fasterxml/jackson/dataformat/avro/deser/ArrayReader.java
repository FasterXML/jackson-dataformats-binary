package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

abstract class ArrayReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_ELEMENTS = 1;
    protected final static int STATE_END = 2;
    protected final static int STATE_DONE = 3;

    protected final BinaryDecoder _decoder;
    protected final AvroParserImpl _parser;

    protected int _state;
    protected long _count;

    protected String _currentName;
    
    protected ArrayReader(AvroReadContext parent,
            AvroParserImpl parser, BinaryDecoder decoder)
    {
        super(parent, TYPE_ARRAY);
        _parser = parser;
        _decoder = decoder;
    }

    public static ArrayReader scalar(AvroScalarReader reader) {
        return new Scalar(reader);
    }

    public static ArrayReader nonScalar(AvroStructureReader reader) {
        return new NonScalar(reader);
    }

    @Override
    public String nextFieldName() throws IOException {
        nextToken();
        return null;
    }
    
    @Override
    public String getCurrentName() {
        if (_currentName == null) {
            _currentName = _parent.getCurrentName();
        }
        return _currentName;
    }

    @Override
    protected void appendDesc(StringBuilder sb) {
        sb.append('[');
        sb.append(getCurrentIndex());
        sb.append(']');
    }
    
    /*
    /**********************************************************************
    /* Reader implementations for Avro arrays
    /**********************************************************************
     */

    private final static class Scalar extends ArrayReader
    {
        private final AvroScalarReader _elementReader;
        
        public Scalar(AvroScalarReader reader) {
            this(null, reader, null, null);
        }

        private Scalar(AvroReadContext parent, AvroScalarReader reader, 
                AvroParserImpl parser, BinaryDecoder decoder) {
            super(parent, parser, decoder);
            _elementReader = reader;
        }
        
        @Override
        public Scalar newReader(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder decoder) {
            return new Scalar(parent, _elementReader, parser, decoder);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _index = 0;
                _count = _decoder.readArrayStart();
                _state = (_count > 0) ? STATE_ELEMENTS : STATE_END;
                return (_currToken = JsonToken.START_ARRAY);
            case STATE_ELEMENTS:
                if (_index < _count) {
                    break;
                }
                if ((_count = _decoder.arrayNext()) > 0L) { // got more data
                    _index = 0;
                    break;
                }
                // otherwise, we are done: fall through
            case STATE_END:
                final AvroReadContext parent = getParent();
                // as per [dataformats-binary#38], may need to reset, instead of bailing out
                if (parent.inRoot()) {
                    if (!_decoder.isEnd()) {
                        _index = 0;
                        _state = STATE_START;
                        return (_currToken = JsonToken.END_ARRAY);
                    }
                }
                _state = STATE_DONE;
                _parser.setAvroContext(parent);
                return (_currToken = JsonToken.END_ARRAY);
            case STATE_DONE:
            default:
                throwIllegalState(_state);
                return null;
            }

            // all good, just need to read the element value:
            ++_index;
            JsonToken t = _elementReader.readValue(_parser, _decoder);
            _currToken = t;
            return t;
        }
    }

    private final static class NonScalar extends ArrayReader
    {
        private final AvroStructureReader _elementReader;
        
        public NonScalar(AvroStructureReader reader) {
            this(null, reader, null, null);
        }

        private NonScalar(AvroReadContext parent,
                AvroStructureReader reader, 
                AvroParserImpl parser, BinaryDecoder decoder) {
            super(parent, parser, decoder);
            _elementReader = reader;
        }
        
        @Override
        public NonScalar newReader(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder decoder) {
            return new NonScalar(parent, _elementReader, parser, decoder);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _count = _decoder.readArrayStart();
                _state = (_count > 0) ? STATE_ELEMENTS : STATE_END;
                return (_currToken = JsonToken.START_ARRAY);
            case STATE_ELEMENTS:
                if (_index < _count) {
                    break;
                }
                if ((_count = _decoder.arrayNext()) > 0L) { // got more data
                    _index = 0;
                    break;
                }
                // otherwise, we are done: fall through
            case STATE_END:
                final AvroReadContext parent = getParent();
                // as per [dataformats-binary#38], may need to reset, instead of bailing out
                if (parent.inRoot()) {
                    if (!_decoder.isEnd()) {
                        _index = 0;
                        _state = STATE_START;
                        return (_currToken = JsonToken.END_ARRAY);
                    }
                }
                _state = STATE_DONE;
                _parser.setAvroContext(parent);
                return (_currToken = JsonToken.END_ARRAY);
            case STATE_DONE:
            default:
                throwIllegalState(_state);
            }
            ++_index;
            AvroStructureReader r = _elementReader.newReader(this, _parser, _decoder);
            _parser.setAvroContext(r);
            return (_currToken = r.nextToken());
        }
    }
}