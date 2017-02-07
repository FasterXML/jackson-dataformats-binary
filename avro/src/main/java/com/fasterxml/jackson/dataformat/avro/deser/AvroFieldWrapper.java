package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.Decoder;

import com.fasterxml.jackson.core.JsonToken;

/**
 * Entity that encapsulates details of accessing value of a single field
 * of a "Record" (Avro term, corresponding roughly to JSON Object).
 */
public abstract class AvroFieldWrapper
{
    protected final String _name;
    protected final boolean _isSkipper;

    protected AvroFieldWrapper(String name, boolean isSkipper) {
        _name = name;
        _isSkipper = isSkipper;
    }

    public static AvroFieldWrapper construct(String name, ScalarDecoder scalarReader) {
        return new Scalar(name, false, scalarReader);
    }

    public static AvroFieldWrapper construct(String name, AvroStructureReader structureReader) {
        return new Structured(name, false, structureReader);
    }

    public static AvroFieldWrapper constructSkipper(String name, ScalarDecoder scalarReader) {
        return new Scalar(name, true, scalarReader);
    }

    public static AvroFieldWrapper constructSkipper(String name, AvroStructureReader structureReader) {
        return new Structured(name, true, structureReader);
    }

    public final String getName() { return _name; }
    public final boolean isSkipper() { return _isSkipper; }

    public abstract JsonToken readValue(AvroReadContext parent,
            AvroParserImpl parser, Decoder avroDecoder) throws IOException;

    public abstract void skipValue(Decoder decoder) throws IOException;

    /**
     * Implementation used for scalar-valued fields
     */
    private final static class Scalar extends AvroFieldWrapper {
        protected final ScalarDecoder _decoder;

        public Scalar(String name, boolean skipper, ScalarDecoder dec) {
            super(name, skipper);
            _decoder = dec;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, Decoder avroDecoder) throws IOException
        {
            return _decoder.decodeValue(parser, avroDecoder);
        }

        @Override
        public void skipValue(Decoder decoder) throws IOException {
            _decoder.skipValue(decoder);
        }
    }

    /**
     * Implementation used for non-scalar-valued (structured) fields
     */
    private final static class Structured extends AvroFieldWrapper {
        protected final AvroStructureReader _reader;

        public Structured(String name, boolean skipper, AvroStructureReader r) {
            super(name, skipper);
            _reader = r;
        }

        @Override
        public JsonToken readValue(AvroReadContext parent,
                AvroParserImpl parser, Decoder decoder)
            throws IOException
        {
            return _reader.newReader(parent, parser, decoder).nextToken();
        }

        @Override
        public void skipValue(Decoder decoder) throws IOException {
            _reader.skipValue(decoder);
        }
    }
}
