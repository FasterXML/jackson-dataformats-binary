/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.fasterxml.jackson.dataformat.ion;

import java.io.*;
import java.net.URL;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.DecorableTSFactory;
import com.fasterxml.jackson.core.io.IOContext;

import com.fasterxml.jackson.dataformat.ion.util.CloseSafeUTF8Writer;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;

/**
 * Sub-class of {@link TokenStreamFactory} that will work on Ion content, instead of JSON
 * content.
 */
@SuppressWarnings("resource")
public class IonFactory
//30-Sep-2017, tatu: Since Ion can use either textual OR binary format, we have to
// extend a lower level base class.
    extends DecorableTSFactory
    implements java.io.Serializable
{
    /*
    /**********************************************************************
    /* Constants
    /**********************************************************************
     */

    private static final long serialVersionUID = 1L;

    public final static String FORMAT_NAME_ION = "AmazonIon";

    /**
     * Default setting for binary vs textual output: defaulting to textual.
     */
    protected final static boolean DEFAULT_CREATE_BINARY = false;

    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    protected final static int DEFAULT_ION_PARSER_FEATURE_FLAGS = IonParser.Feature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    protected final static int DEFAULT_ION_GENERATOR_FEATURE_FLAGS = IonGenerator.Feature.collectDefaults();

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    /**
     * Whether we will produce binary (true) or textual (false) Ion writers.
     */
    protected final boolean _cfgBinaryWriters;

    protected final IonSystem _system;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public IonFactory() {
        super(DEFAULT_ION_PARSER_FEATURE_FLAGS, DEFAULT_ION_GENERATOR_FEATURE_FLAGS);
        _cfgBinaryWriters = DEFAULT_CREATE_BINARY;
        _system = IonSystemBuilder.standard().build();
    }

    protected IonFactory(IonFactory src)
    {
        super(src);
        _cfgBinaryWriters = src._cfgBinaryWriters;
        // 21-Feb-2017, tatu: Not 100% sure if this should be made copy of
        //    too; for now assume it may be shared.
        _system = src._system;
    }

    /**
     * Constructors used by {@link IonFactoryBuilder} for instantiation.
     *
     * @since 3.0
     */
    protected IonFactory(IonFactoryBuilder b) {
        super(b);
        _cfgBinaryWriters = b.willCreateBinaryWriters();
        _system = b.ionSystem();
    }

    @Override
    public IonFactoryBuilder rebuild() {
        return new IonFactoryBuilder(this);
    }

    /**
     * Method for creating {@link IonFactory} that will
     * create binary (not textual) writers.
     */
    public static IonFactory forBinaryWriters() {
        return new IonFactoryBuilder(true).build();
    }

    /**
     * Method for creating {@link IonFactoryBuilder} initialized with settings to
     * create binary (not textual) writers.
     */
    public static IonFactoryBuilder builderForBinaryWriters() {
        return new IonFactoryBuilder(true);
    }

    /**
     * Method for creating {@link IonFactory} that will
     * create textual (not binary) writers.
     */
    public static IonFactory forTextualWriters() {
        return new IonFactoryBuilder(false).build();
    }

    /**
     * Method for creating {@link IonFactoryBuilder} initialized with settings to
     * create textual (not binary) writers.
     */
    public static IonFactoryBuilder builderForTextualWriters() {
        return new IonFactoryBuilder(false);
    }

    @Override
    public IonFactory copy() {
        return new IonFactory(this);
    }

    /**
     * Instances are immutable so just return `this`
     */
    @Override
    public TokenStreamFactory snapshot() {
        return this;
    }

    /*
    /**********************************************************************
    /* Serializable overrides
    /**********************************************************************
     */

    /**
     * Method that we need to override to actually make restoration go
     * through constructors etc.
     */
    protected Object readResolve() {
        return new IonFactory(this);
    }

    /*
    /**********************************************************************
    /* Capability introspection
    /**********************************************************************
     */
    
    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public boolean canParseAsync() {
        // 30-Sep-2017, tatu: No async implementation exists
        return false;
    }

    @Override
    public boolean canHandleBinaryNatively() {
        // 21-Feb-2017, tatu: I think only support with binary backend
        return _cfgBinaryWriters;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(IonParser.Feature f) {
        return (_formatReadFeatures & f.getMask()) != 0;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(IonGenerator.Feature f) {
        return (_formatWriteFeatures & f.getMask()) != 0;
    }

    /*
    /**********************************************************************
    /* Format support
    /**********************************************************************
     */

    @Override
    public String getFormatName() {
        return FORMAT_NAME_ION;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return false;
    }

    @Override
    public Class<IonParser.Feature> getFormatReadFeatureType() {
        return IonParser.Feature.class;
    }

    @Override
    public Class<IonGenerator.Feature> getFormatWriteFeatureType() {
        return IonGenerator.Feature.class;
    }

    /*
    /***************************************************************
    /* Extended API
    /***************************************************************
     */

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, File f) {
        // true, since we create InputStream from File
        InputStream in;
        try {
            in = new FileInputStream(f);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
        IOContext ioCtxt = _createContext(f, true);
        return _createParser(readCtxt, ioCtxt, _decorate(ioCtxt, in));
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, URL url) {
        // true, since we create InputStream from URL
        InputStream in = _optimizedStreamFromURL(url);
        IOContext ioCtxt = _createContext(url, true);
        return _createParser(readCtxt, ioCtxt, _decorate(ioCtxt, in));
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, InputStream in) {
        IOContext ioCtxt = _createContext(in, false);
        return _createParser(readCtxt, ioCtxt, _decorate(ioCtxt, in));
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, Reader r) {
        // false -> we do NOT own Reader (did not create it)
        IOContext ioCtxt = _createContext(r, false);
        return _createParser(readCtxt, ioCtxt, _decorate(ioCtxt, r));
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, byte[] data) {
        IOContext ioCtxt = _createContext(data, true);
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ioCtxt, data, 0, data.length);
            if (in != null) {
                return _createParser(readCtxt, ioCtxt, in);
            }
        }
        return _createParser(readCtxt, ioCtxt, data, 0, data.length);
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, byte[] data, int offset, int len) 
    {
        IOContext ioCtxt = _createContext(data, true);
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ioCtxt, data, offset, len);
            if (in != null) {
                return _createParser(readCtxt, ioCtxt, in);
            }
        }
        return _createParser(readCtxt, ioCtxt, data, offset, len);
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, String content) {
        return createParser(readCtxt, new StringReader(content));
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt,
            char[] content, int offset, int len) {
        if (_inputDecorator != null) { // easier to just wrap in a Reader than extend InputDecorator
            return createParser(readCtxt, new CharArrayReader(content, offset, len));
        }
        return _createParser(readCtxt, _createContext(content, true),
                content, offset, len,
                // important: buffer is NOT recyclable, as it's from caller
                false);
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, DataInput in) {
        return _unsupported();
    }        

    /*
    /**********************************************************************
    /* Factory methods: generators
    /**********************************************************************
     */

    @Override
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt,
            OutputStream out, JsonEncoding enc)
    {
        return _createGenerator(writeCtxt, out, enc, false);
    }

    @Override
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt, Writer out)
    {
         // First things first: no binary writer for Writers:
        if (_cfgBinaryWriters) {
            throw new UnsupportedOperationException("Can only create binary Ion writers that output to OutputStream, not Writer");
        }
        return _createGenerator(writeCtxt, _createContext(out, false),
                _system.newTextWriter(out), true, out);
    }

    @Override
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt,
            File f, JsonEncoding enc)
    {
        try {
            return _createGenerator(writeCtxt,
                    new FileOutputStream(f), enc, true);
        } catch (IOException e) {
            throw _wrapIOFailure(e);
        }
    }

    /*
    /**********************************************************************
    /* Extended API: additional factory methods, accessors
    /**********************************************************************
     */

    public IonSystem getIonSystem() {
        return _system;
    }

    public IonParser createParser(ObjectReadContext readCtxt, IonReader in) {
        return new IonParser(readCtxt, _createContext(in, false),
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                in, _system);
    }

    public IonParser createParser(ObjectReadContext readCtxt, IonValue value) {
        IonReader in = value.getSystem().newReader(value);
        return new IonParser(readCtxt, _createContext(in, true),
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                in, _system);
    }

    public IonGenerator createGenerator(ObjectWriteContext writeCtxt, IonWriter out) {
        return _createGenerator(writeCtxt, _createContext(out, false),
                out, false, out);
    }

    /*
    /**********************************************************************
    /* Helper methods, parsers
    /**********************************************************************
     */

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt, InputStream in)
    {
        IonReader ion = _system.newReader(in);
        return new IonParser(readCtxt, ioCtxt,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                ion, _system);
    }

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt, Reader r)
    {
        return new IonParser(readCtxt, ioCtxt,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                _system.newReader(r), _system);
    }

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            char[] data, int offset, int len,
            boolean recyclable)
    {
        return _createParser(readCtxt, ioCtxt,
                new CharArrayReader(data, offset, len));
    }

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            byte[] data, int offset, int len)
    {
        return new IonParser(readCtxt, ioCtxt,
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                _system.newReader(data, offset, len), _system);
    }

    /*
    /**********************************************************************
    /* Helper methods, generators
    /**********************************************************************
     */

    protected IonGenerator _createGenerator(ObjectWriteContext writeCtxt,
            OutputStream out, JsonEncoding enc, boolean isManaged)
     {
        IonWriter ion;
        IOContext ioCtxt = _createContext(out, isManaged);
        Closeable dst; // not necessarily same as 'out'...

        // Binary writers are simpler: no alternate encodings
        if (_cfgBinaryWriters) {
            ioCtxt.setEncoding(enc);
            ion = _system.newBinaryWriter(out);
            dst = out;
        } else {
            if (enc != JsonEncoding.UTF8) { // not sure if non-UTF-8 encodings would be legal...
                throw _wrapIOFailure(
                        new IOException("Ion only supports UTF-8 encoding, can not use "+enc));
            }
            // In theory Ion package could take some advantage of getting OutputStream.
            // In practice we seem to be better off using Jackson's efficient buffering encoder
            ioCtxt.setEncoding(enc);
            // This is bit unfortunate, since out != dst now...
            Writer w = new CloseSafeUTF8Writer(ioCtxt, out);
            ion = _system.newTextWriter(w);
            dst = w;
        }
        // `true` for "ionWriterIsManaged" since we created it:
        return _createGenerator(writeCtxt, ioCtxt, ion, true, dst);
    }

    protected IonGenerator _createGenerator(ObjectWriteContext writeCtxt,
            IOContext ioCtxt,
            IonWriter ion, boolean ionWriterIsManaged, Closeable dst)
    {
        return new IonGenerator(writeCtxt, ioCtxt,
                writeCtxt.getStreamWriteFeatures(_streamWriteFeatures),
                writeCtxt.getFormatWriteFeatures(_formatWriteFeatures),
                ion, ionWriterIsManaged, dst);
    }
}
