package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;
import java.util.List;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.BinaryTSFactory;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.CaseInsensitiveNameMatcher;
import com.fasterxml.jackson.core.sym.FieldNameMatcher;
import com.fasterxml.jackson.core.sym.InternedNameMatcher;
import com.fasterxml.jackson.core.util.Named;
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

    /*
    /**********************************************************
    /* Factory methods for helper objects
    /**********************************************************
     */

    /**
     * @param matches Names to match, including both primary names (which should come first)
     *     and possible aliases (after primary names)
     * @param propCount Number of primary matches; if less than `matches` rest are aliases
     * @param caseInsensitive Whether matching should be case-insensitive or not
     */
    @Override
    public FieldNameMatcher constructFieldNameMatcher(List<Named> matches,
            int propCount, boolean caseInsensitive) {
        // 13-Nov-2017, tatu: Case-insensitive could perhaps be optimized, but it is
        //    not trivial; so for now use standard one for it
        if (caseInsensitive) {
            return CaseInsensitiveNameMatcher.constructFrom(matches);
        }
        return InternedNameMatcher.constructFrom(matches);
    }
}
