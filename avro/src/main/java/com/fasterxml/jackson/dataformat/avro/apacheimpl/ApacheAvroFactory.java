package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroParser;

/**
 * Alternative {@link AvroFactory} implementation that uses
 * codecs from Apache Avro library instead of Jackson "native"
 * codecs.
 */
public class ApacheAvroFactory extends AvroFactory
{
    private static final long serialVersionUID = 1L;

    public ApacheAvroFactory() {
        this(null);
    }

    public ApacheAvroFactory(ObjectCodec oc) {
        super(oc);
    }

    protected ApacheAvroFactory(AvroFactory src, ObjectCodec oc) {
        super(src, oc);
    }

    /*
    /**********************************************************
    /* Factory method overrides
    /**********************************************************
     */
    
    @Override
    public AvroFactory copy()
    {
        _checkInvalidCopy(ApacheAvroFactory.class);
        return new ApacheAvroFactory(this, null);
    }

    @Override
    protected AvroParser _createParser(InputStream in, IOContext ctxt) throws IOException {
        return new ApacheAvroParserImpl(ctxt, _parserFeatures, _avroParserFeatures,
                _objectCodec, in);
    }

    @Override
    protected AvroParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException {
        return new ApacheAvroParserImpl(ctxt, _parserFeatures, _avroParserFeatures,
                _objectCodec, data, offset, len);
    }
}
