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

    protected AvroFieldWrapper(String name) {
        _name = name;
    }

    public static AvroFieldWrapper construct(String name, ScalarDecoder scalarReader) {
        return new Scalar(name, scalarReader);
    }

    public static AvroFieldWrapper construct(String name, AvroStructureReader structureReader) {
        return new Structured(name, structureReader);
    }

    public static AvroFieldWrapper constructSkipper(String name, ScalarDecoder scalarReader) {
        return new Scalar(name, scalarReader);
    }

    public static AvroFieldWrapper constructSkipper(String name, AvroStructureReader structureReader) {
        return new Structured(name, structureReader);
    }
    
    public String getName() { return _name; }

    public abstract JsonToken readValue(AvroReadContext parent,
            AvroParserImpl parser, Decoder avroDecoder) throws IOException;

    public abstract void skipValue(Decoder decoder) throws IOException;

    /**
     * Implementation used for scalar-valued fields
     */
    private final static class Scalar extends AvroFieldWrapper {
        protected final ScalarDecoder _decoder;

        public Scalar(String name, ScalarDecoder dec) {
            super(name);
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

        public Structured(String name, AvroStructureReader r) {
            super(name);
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
