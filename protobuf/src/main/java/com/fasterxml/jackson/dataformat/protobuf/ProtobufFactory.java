package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.BinaryTSFactory;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;

public class ProtobufFactory
    extends BinaryTSFactory
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1;

    /*
    /**********************************************************
    /* Factory construction, configuration
    /**********************************************************
     */

    public ProtobufFactory() { }

    public ProtobufFactory(ObjectCodec codec) {
        super(codec);
    }

    protected ProtobufFactory(ProtobufFactory src, ObjectCodec oc)
    {
        super(src, oc);
    }

    @Override
    public ProtobufFactory copy()
    {
        return new ProtobufFactory(this, null);
    }

    /*
    /**********************************************************
    /* Serializable overrides
    /**********************************************************
     */

    /**
     * Method that we need to override to actually make restoration go
     * through constructors etc.
     */
    protected Object readResolve() {
        return new ProtobufFactory(this, _objectCodec);
    }

    /*                                                                                       
    /**********************************************************                              
    /* Basic introspection                                                                  
    /**********************************************************                              
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    // Protobuf is not positional
    @Override
    public boolean requiresPropertyOrdering() {
        return false;
    }

    @Override
    public boolean canParseAsync() {
        // 30-Sep-2017, tatu: No async implementation exists yet
        return false;
    }

    // No format-specific configuration, yet:
/*    
    @Override
    public Class<? extends FormatFeature> getFormatReadFeatureType() {
        return null;
    }

    @Override
    public Class<? extends FormatFeature> getFormatWriteFeatureType() {
        return null;
    }
*/

    /*
    /**********************************************************
    /* Format detection functionality
    /**********************************************************
     */

    @Override
    public String getFormatName() {
        return ProtobufSchema.FORMAT_NAME_PROTOBUF;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return (schema instanceof ProtobufSchema);
    }

    /*
    /******************************************************
    /* Factory methods: parsers
    /******************************************************
     */

    @Override
    protected IOContext _createContext(Object srcRef, boolean resourceManaged) {
        return super._createContext(srcRef, resourceManaged);
    }

    @Override
    protected ProtobufParser _createParser(InputStream in, IOContext ctxt) throws IOException
    {
        byte[] buf = ctxt.allocReadIOBuffer();
        return new ProtobufParser(ctxt, _parserFeatures,
                _objectCodec, in, buf, 0, 0, true);
    }

    @Override
    protected ProtobufParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException
    {
        return new ProtobufParser(ctxt, _parserFeatures,
                _objectCodec, null, data, offset, len, false);
    }

    @Override
    protected JsonParser _createParser(DataInput input, IOContext ctxt)
            throws IOException {
        // 30-Sep-2017, tatu: As of now not supported although should be quite possible
        //    to support
        return _unsupported();
    }
   
    /*
    /******************************************************
    /* Factory methods: generators
    /******************************************************
     */

    @Override
    protected ProtobufGenerator _createGenerator(OutputStream out, IOContext ctxt) throws IOException {
        return new ProtobufGenerator(ctxt, _generatorFeatures, _objectCodec, out);
    }
}
