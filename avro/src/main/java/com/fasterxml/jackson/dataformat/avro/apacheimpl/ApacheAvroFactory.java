package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.ObjectReadContext;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroParser;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

/**
 * Alternative {@link AvroFactory} implementation that uses
 * codecs from Apache Avro library instead of Jackson "native"
 * codecs.
 */
public class ApacheAvroFactory extends AvroFactory
{
    private static final long serialVersionUID = 1L;

    public ApacheAvroFactory() {
        super();
    }

    protected ApacheAvroFactory(AvroFactory src) {
        super(src);
    }

    /*
    /**********************************************************
    /* Factory method overrides
    /**********************************************************
     */
    
    @Override
    public AvroFactory copy()
    {
        return new ApacheAvroFactory(this);
    }

    @Override
    protected AvroParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in) throws IOException {
        return new ApacheAvroParserImpl(readCtxt, ioCtxt,
                readCtxt.getParserFeatures(_parserFeatures),
                readCtxt.getFormatReadFeatures(_avroParserFeatures),
                (AvroSchema) readCtxt.getSchema(),
                in);
    }

    @Override
    protected AvroParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            byte[] data, int offset, int len) throws IOException {
        return new ApacheAvroParserImpl(readCtxt, ioCtxt,
                readCtxt.getParserFeatures(_parserFeatures),
                readCtxt.getFormatReadFeatures(_avroParserFeatures),
                (AvroSchema) readCtxt.getSchema(),
                data, offset, len);
    }
}
