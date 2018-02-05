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

    protected ProtobufFactory(ProtobufFactory src) {
        super(src);
    }

    /**
     * Constructors used by {@link CBORFactoryBuilder} for instantiation.
     *
     * @since 3.0
     */
    protected ProtobufFactory(ProtobufFactoryBuilder b) {
        super(b);
    }

    @Override
    public ProtobufFactoryBuilder rebuild() {
        return new ProtobufFactoryBuilder(this);
    }

    /**
     * Main factory method to use for constructing {@link ProtobufFactory} instances with
     * different configuration.
     */
    public static ProtobufFactoryBuilder builder() {
        return new ProtobufFactoryBuilder();
    }

    @Override
    public ProtobufFactory copy(){
        return new ProtobufFactory(this);
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
        return new ProtobufFactory(this);
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
    
    @Override
    public int getFormatParserFeatures() { return 0; }

    @Override
    public int getFormatGeneratorFeatures() { return 0; }
    
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
    protected ProtobufParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in) throws IOException
    {
        byte[] buf = ioCtxt.allocReadIOBuffer();
        return new ProtobufParser(readCtxt, ioCtxt,
                readCtxt.getParserFeatures(_parserFeatures),
                (ProtobufSchema) readCtxt.getSchema(),
                in, buf, 0, 0, true);
    }

    @Override
    protected ProtobufParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            byte[] data, int offset, int len) throws IOException
    {
        return new ProtobufParser(readCtxt, ioCtxt,
                readCtxt.getParserFeatures(_parserFeatures),
                (ProtobufSchema) readCtxt.getSchema(),
                null, data, offset, len, false);
    }

    @Override
    protected JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            DataInput input) throws IOException {
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
    protected ProtobufGenerator _createGenerator(ObjectWriteContext writeCtxt,
            IOContext ioCtxt, OutputStream out) throws IOException
    {
        return new ProtobufGenerator(writeCtxt, ioCtxt,
                writeCtxt.getGeneratorFeatures(_generatorFeatures),
                (ProtobufSchema) writeCtxt.getSchema(),
                out);
    }
}
