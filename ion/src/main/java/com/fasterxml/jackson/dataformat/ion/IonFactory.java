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

import com.amazon.ion.*;
import com.amazon.ion.system.IonSystemBuilder;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.UTF8Writer;
import com.fasterxml.jackson.core.util.TextBuffer;

/**
 * Sub-class of {@link JsonFactory} that will work on Ion content, instead of JSON
 * content.
 */
@SuppressWarnings("resource")
public class IonFactory extends JsonFactory
{
    private static final long serialVersionUID = 1L;

    public final static String FORMAT_NAME_ION = "AmazonIon";

    protected final IonSystem _system;

    /**
     * Whether we will produce binary or text Ion writers: default is textual.
     */
    protected boolean _cfgCreateBinaryWriters = false;

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

    protected int _ionParserFeatures = DEFAULT_ION_PARSER_FEATURE_FLAGS;

    protected int _ionGeneratorFeatures = DEFAULT_ION_GENERATOR_FEATURE_FLAGS;

    public IonFactory() {
        this((ObjectCodec) null);
    }

    public IonFactory(ObjectCodec mapper) {
        this(mapper, IonSystemBuilder.standard().build());
    }

    public IonFactory(ObjectCodec mapper, IonSystem system) {
        super(mapper);
        _system = system;
    }

    protected IonFactory(IonFactory src, ObjectCodec oc)
    {
        super(src, oc);
        // 21-Feb-2017, tatu: Not 100% sure if this should be made copy of
        //    too; for now assume it may be shared.
        _system = src._system;
        _cfgCreateBinaryWriters = src._cfgCreateBinaryWriters;
    }

    /**
     * Constructors used by {@link IonFactoryBuilder} for instantiation.
     */
    protected IonFactory(IonFactoryBuilder b) {
        super(b, false);
        _cfgCreateBinaryWriters = b.willCreateBinaryWriters();
        _system = b.ionSystem();
        _ionParserFeatures = b.formatParserFeaturesMask();
        _ionGeneratorFeatures = b.formatGeneratorFeaturesMask();
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
     * Method for creating {@link IonFactory} that will
     * create textual (not binary) writers.
     */
    public static IonFactory forTextualWriters() {
        return new IonFactoryBuilder(false).build();
    }

    /**
     * Method for creating {@link IonFactoryBuilder} initialized with settings to
     * create binary (not textual) writers.
     */
    public static IonFactoryBuilder builderForBinaryWriters() {
        return new IonFactoryBuilder(true);
    }

    /**
     * Method for creating {@link IonFactoryBuilder} initialized with settings to
     * create textual (not binary) writers.
     */
    public static IonFactoryBuilder builderForTextualWriters() {
        return new IonFactoryBuilder(false);
    }

    @Override
    public IonFactory copy()
    {
        _checkInvalidCopy(IonFactory.class);
        // note: as with base class, must NOT copy mapper reference
        return new IonFactory(this, null);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public String getFormatName() {
        return FORMAT_NAME_ION;
    }

    public void setCreateBinaryWriters(boolean b) {
        _cfgCreateBinaryWriters = b;
    }

    public boolean createBinaryWriters() {
        return _cfgCreateBinaryWriters;
    }

    @Override // since 2.3
    public boolean canHandleBinaryNatively() {
        // 21-Feb-2017, tatu: I think only support with binary backend
        return _cfgCreateBinaryWriters;
    }

    @Override // since 2.4
    public boolean canUseCharArrays() {
        return false;
    }

    /*
    /**********************************************************
    /* Configuration, parser settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified parser feature
     * (check {@link IonParser.Feature} for list of features)
     */
    public final IonFactory configure(IonParser.Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /**
     * Method for enabling specified parser feature
     * (check {@link IonParser.Feature} for list of features)
     */
    public IonFactory enable(IonParser.Feature f) {
        _ionParserFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified parser features
     * (check {@link IonParser.Feature} for list of features)
     */
    public IonFactory disable(IonParser.Feature f) {
        _ionParserFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(IonParser.Feature f) {
        return (_ionParserFeatures & f.getMask()) != 0;
    }

    @Override
    public int getFormatParserFeatures() {
        return _ionParserFeatures;
    }

    /*
    /**********************************************************
    /* Configuration, generator settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified generator feature
     * (check {@link IonGenerator.Feature} for list of features)
     */
    public final IonFactory configure(IonGenerator.Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }


    /**
     * Method for enabling specified generator features
     * (check {@link IonGenerator.Feature} for list of features)
     */
    public IonFactory enable(IonGenerator.Feature f) {
        _ionGeneratorFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified generator feature
     * (check {@link IonGenerator.Feature} for list of features)
     */
    public IonFactory disable(IonGenerator.Feature f) {
        _ionGeneratorFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(IonGenerator.Feature f) {
        return (_ionGeneratorFeatures & f.getMask()) != 0;
    }

    @Override
    public int getFormatGeneratorFeatures() {
        return _ionGeneratorFeatures;
    }


    /*
    ***************************************************************
    * Extended API
    ***************************************************************
     */

    /**
     * @since 2.7
     */
    public IonParser createParser(IonReader in) {
        return new IonParser(in, _system,
                _createContext(_createContentReference(in), false), getCodec(),
                _ionParserFeatures);
    }

    /**
     * @since 2.7
     */
    public IonParser createParser(IonValue value) {
        IonReader in = value.getSystem().newReader(value);
        return new IonParser(in, _system,
                _createContext(_createContentReference(in), true), getCodec(),
                _ionParserFeatures);
    }

    // NOTE! Suboptimal return type -- but can't change safely before 3.0 as return
    // type is part of signature
    /**
     * @since 2.7
     */
    public JsonGenerator createGenerator(IonWriter out) {
        return _createGenerator(out, false,
                _createContext(_createContentReference(out), false), out);
    }

    // actually added in 2.10.5 / 2.11.1 but officially part of 2.12 API
    /**
     * @since 2.12
     */
    public IonSystem getIonSystem() {
        return _system;
    }

    /**
     * @deprecated Since 2.7
     */
    @Deprecated
    public IonParser createJsonParser(IonReader in) {
        return createParser(in);
    }

    /**
     * @deprecated Since 2.7
     */
    @Deprecated
    public IonParser createJsonParser(IonValue value) {
        return createParser(value);
    }

    /**
     * @deprecated Since 2.7
     */
    @Deprecated
    public JsonGenerator createJsonGenerator(IonWriter out) {
        return createGenerator(out);
    }

    /*
    ***************************************************************
    * Overridden factory methods
    ***************************************************************
     */

    @Override
    protected JsonParser _createParser(InputStream in, IOContext ctxt)
        throws IOException
    {
        // [dataformats-binary#805]: `IonReader` pulls from the source itself, so
        //   document length is tracked by counting what it reads
        if (_streamReadConstraints.hasMaxDocumentLength()) {
            in = new LengthCheckingInputStream(in, _streamReadConstraints);
        }
        return _createParser(_newReader(in), ctxt);
    }

    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt)
        throws IOException
    {
        // [dataformats-binary#805]: as above; for textual sources length is
        //   counted in `char`s, same as `ReaderBasedJsonParser` does
        if (_streamReadConstraints.hasMaxDocumentLength()) {
            r = new LengthCheckingReader(r, _streamReadConstraints);
        }
        return _createParser(_newReader(r), ctxt);
    }

    @Override
    protected JsonParser _createParser(char[] data, int offset, int len, IOContext ctxt,
            boolean recyclable) throws IOException
    {
        // [dataformats-binary#805]: length known up front for fixed buffers, so
        //   validated exactly -- and no counting wrapper needed
        _streamReadConstraints.validateDocumentLength(len);
        return _createParser(_newReader(new CharArrayReader(data, offset, len)), ctxt);
    }

    @Override
    protected JsonParser _createParser(byte[] data, int offset, int len, IOContext ctxt)
        throws IOException
    {
        // [dataformats-binary#805]: length known up front for fixed buffers
        _streamReadConstraints.validateDocumentLength(len);
        return _createParser(_system.newReader(data, offset, len), ctxt);
    }

    private JsonParser _createParser(IonReader ion, IOContext ctxt) {
        return new IonParser(ion, _system,
                _createContext(_createContentReference(ion), true), getCodec(), _ionParserFeatures);
    }

    /**
     * Constructs {@code IonReader} over given source, unwrapping constraint violations:
     * {@code IonReader} reads from the source while being constructed, so a document
     * length limit can be exceeded before any token is read, and ion-java would wrap
     * our {@code IOException} as {@code IonException} [dataformats-binary#805].
     */
    private IonReader _newReader(InputStream in) throws IOException {
        try {
            return _system.newReader(in);
        } catch (IonException e) {
            IonParser._rethrowIfConstraintViolation(e);
            throw e;
        }
    }

    private IonReader _newReader(Reader r) throws IOException {
        try {
            return _system.newReader(r);
        } catch (IonException e) {
            IonParser._rethrowIfConstraintViolation(e);
            throw e;
        }
    }

    @Override
    public JsonGenerator createGenerator(OutputStream out, JsonEncoding enc)
        throws IOException
    {
        return _createGenerator(out, enc, false);
    }

    @Override
    public JsonGenerator createGenerator(Writer out)
        throws IOException
    {
         // First things first: no binary writer for Writers:
        if (createBinaryWriters()) {
            throw new IOException("Can only create binary Ion writers that output to OutputStream, not Writer");
        }
        return _createGenerator(_system.newTextWriter(out), true,
                _createContext(_createContentReference(out), false), out);
    }

    @Override
    public JsonGenerator createGenerator(File f, JsonEncoding enc)
        throws IOException
    {
        return _createGenerator(new FileOutputStream(f), enc, true);
    }

    /*
    /***************************************************************
    /* Helper methods
    /***************************************************************
     */

    @Deprecated
    protected String _readAll(Reader r, IOContext ctxt) throws IOException
    {
        // Let's use Jackson's efficient aggregators... better than JDK defaults
        TextBuffer tb = ctxt.constructTextBuffer();
        char[] buf = tb.emptyAndGetCurrentSegment();

        // this gets bit ugly. Blah.
        int offset = 0;

        main_loop:
        while (true) {

            while (offset < buf.length) {
                int count = r.read(buf, offset, buf.length - offset);
                if (count < 0) {
                    break main_loop;
                }
                offset += count;
            }
            // got full buffer; get another one
            buf = tb.finishCurrentSegment();
            offset = 0;
        }
        // Ok, last one is incomplete...
        tb.setCurrentLength(offset);
        String result = tb.contentsAsString();
        tb.releaseBuffers();
        return result;
    }

    protected IonGenerator _createGenerator(OutputStream out, JsonEncoding enc, boolean isManaged)
         throws IOException
     {
        IonWriter ion;
        IOContext ctxt = _createContext(_createContentReference(out), isManaged);
        Closeable dst; // not necessarily same as 'out'...

        // Binary writers are simpler: no alternate encodings
        if (createBinaryWriters()) {
            ctxt.setEncoding(enc);
            ion = _system.newBinaryWriter(out);
            dst = out;
        } else {
            if (enc != JsonEncoding.UTF8) { // not sure if non-UTF-8 encodings would be legal...
                throw new IOException("Ion only supports UTF-8 encoding, can not use "+enc);
            }
            // In theory Ion package could take some advantage of getting OutputStream.
            // In practice we seem to be better off using Jackson's efficient buffering encoder

            ctxt.setEncoding(enc);
            Writer w = new UTF8Writer(ctxt, out);
            ion = _system.newTextWriter(w);
            dst = w;
        }
        return _createGenerator(ion, true, ctxt, dst);
    }

    protected IonGenerator _createGenerator(IonWriter ion, boolean ionWriterIsManaged,
            IOContext ctxt, Closeable dst)
    {
        return new IonGenerator(_generatorFeatures, _ionGeneratorFeatures, _objectCodec,
                ion, ionWriterIsManaged, ctxt, dst);
    }

    /*
    ***************************************************************
    * Helper classes
    ***************************************************************
     */

    /**
     * {@link InputStream} wrapper that applies
     * {@link StreamReadConstraints#validateDocumentLength} to the number of bytes read
     * so far: {@code IonReader} reads from the source directly, so Jackson never sees
     * the content itself.
     *<p>
     * What is counted is bytes pulled from the source, which may run ahead of the actual
     * decoding position, by up to the reader's buffer size. A source that fits within the
     * limit can never trip the check (no more bytes can be read than it holds); but a
     * caller that stops reading early may still be failed, if the source itself is longer
     * than the limit allows.
     *
     * @since 2.18.11
     */
    private final static class LengthCheckingInputStream extends FilterInputStream
    {
        private final StreamReadConstraints _constraints;

        private long _bytesRead;

        LengthCheckingInputStream(InputStream in, StreamReadConstraints constraints) {
            super(in);
            _constraints = constraints;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                ++_bytesRead;
                _validateLength();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = in.read(b, off, len);
            if (count > 0) {
                _bytesRead += count;
                _validateLength();
            }
            return count;
        }

        @Override
        public long skip(long n) throws IOException {
            long count = in.skip(n);
            if (count > 0) {
                _bytesRead += count;
                _validateLength();
            }
            return count;
        }

        private void _validateLength() throws IOException {
            _constraints.validateDocumentLength(_bytesRead);
        }
    }

    /**
     * {@link Reader} equivalent of {@link LengthCheckingInputStream}, for textual Ion
     * sources; counts {@code char}s, the way {@code ReaderBasedJsonParser} does.
     *
     * @since 2.18.11
     */
    private final static class LengthCheckingReader extends FilterReader
    {
        private final StreamReadConstraints _constraints;

        private long _charsRead;

        LengthCheckingReader(Reader r, StreamReadConstraints constraints) {
            super(r);
            _constraints = constraints;
        }

        @Override
        public int read() throws IOException {
            int c = in.read();
            if (c >= 0) {
                ++_charsRead;
                _validateLength();
            }
            return c;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            int count = in.read(cbuf, off, len);
            if (count > 0) {
                _charsRead += count;
                _validateLength();
            }
            return count;
        }

        @Override
        public long skip(long n) throws IOException {
            long count = in.skip(n);
            if (count > 0) {
                _charsRead += count;
                _validateLength();
            }
            return count;
        }

        private void _validateLength() throws IOException {
            _constraints.validateDocumentLength(_charsRead);
        }
    }

}
