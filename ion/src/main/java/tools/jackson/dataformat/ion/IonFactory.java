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

package tools.jackson.dataformat.ion;

import java.io.*;
import java.nio.file.Path;

import tools.jackson.core.*;
import tools.jackson.core.base.DecorableTSFactory;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.io.ContentReference;
import tools.jackson.core.io.UTF8Writer;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;

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
    private static final long serialVersionUID = 1L;

    /*
    /**********************************************************************
    /* Constants
    /**********************************************************************
     */

    public final static String FORMAT_NAME_ION = "AmazonIon";

    /**
     * Default setting for binary vs textual output: defaulting to textual.
     */
    protected final static boolean DEFAULT_CREATE_BINARY = false;

    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    protected final static int DEFAULT_ION_PARSER_FEATURE_FLAGS = IonReadFeature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    protected final static int DEFAULT_ION_GENERATOR_FEATURE_FLAGS = IonWriteFeature.collectDefaults();

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
        super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                ErrorReportConfiguration.defaults(),
                DEFAULT_ION_PARSER_FEATURE_FLAGS, DEFAULT_ION_GENERATOR_FEATURE_FLAGS);
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
    public final boolean isEnabled(IonReadFeature f) {
        return (_formatReadFeatures & f.getMask()) != 0;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(IonWriteFeature f) {
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
    public Class<IonReadFeature> getFormatReadFeatureType() {
        return IonReadFeature.class;
    }

    @Override
    public Class<IonWriteFeature> getFormatWriteFeatureType() {
        return IonWriteFeature.class;
    }

    /*
    /**********************************************************************
    /* Factory methods: parsers
    /**********************************************************************
     */

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, File f) {
        IOContext ioCtxt = _createContext(_createContentReference(f), true);
        InputStream in = null;
        boolean inputCleanupDelegated = false;
        try {
            in = _fileInputStream(f);
            in = _decorate(ioCtxt, in);
            // From this point on `_createParser()` handles cleanup of both `in` and `ioCtxt`
            inputCleanupDelegated = true;
            return _createParser(readCtxt, ioCtxt, in, true);
        } catch (RuntimeException e) {
            if (!inputCleanupDelegated) {
                _closeOnFailedConstruction(in, e);
                _releaseContextOnFailedConstruction(ioCtxt, e);
            }
            throw e;
        }
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt,
            Path p) throws JacksonException
    {
        IOContext ioCtxt = _createContext(_createContentReference(p), true);
        InputStream in = null;
        boolean inputCleanupDelegated = false;
        try {
            in = _pathInputStream(p);
            in = _decorate(ioCtxt, in);
            // From this point on `_createParser()` handles cleanup of both `in` and `ioCtxt`
            inputCleanupDelegated = true;
            return _createParser(readCtxt, ioCtxt, in, true);
        } catch (RuntimeException e) {
            if (!inputCleanupDelegated) {
                _closeOnFailedConstruction(in, e);
                _releaseContextOnFailedConstruction(ioCtxt, e);
            }
            throw e;
        }
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, InputStream in) {
        IOContext ioCtxt = _createContext(_createContentReference(in), false);
        try {
            return _createParser(readCtxt, ioCtxt,
                    _decorate(ioCtxt, in));
        } catch (RuntimeException e) {
            _releaseContextOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, Reader r) {
        // false -> we do NOT own Reader (did not create it)
        IOContext ioCtxt = _createContext(_createContentReference(r), false);
        try {
            return _createParser(readCtxt, ioCtxt, _decorate(ioCtxt, r));
        } catch (RuntimeException e) {
            _releaseContextOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, byte[] data) {
        IOContext ioCtxt = _createContext(_createContentReference(data), true);
        try {
            if (_inputDecorator != null) {
                InputStream in = _inputDecorator.decorate(ioCtxt, data, 0, data.length);
                if (in != null) {
                    return _createParser(readCtxt, ioCtxt, in);
                }
            }
            return _createParser(readCtxt, ioCtxt, data, 0, data.length);
        } catch (RuntimeException e) {
            _releaseContextOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, byte[] data, int offset, int len)
    {
        IOContext ioCtxt = _createContext(_createContentReference(data, offset, len),
                true);
        try {
            if (_inputDecorator != null) {
                InputStream in = _inputDecorator.decorate(ioCtxt, data, offset, len);
                if (in != null) {
                    return _createParser(readCtxt, ioCtxt, in);
                }
            }
            return _createParser(readCtxt, ioCtxt, data, offset, len);
        } catch (RuntimeException e) {
            _releaseContextOnFailedConstruction(ioCtxt, e);
            throw e;
        }
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
        return _createParser(readCtxt, _createContext(_createContentReference(content),
                true),
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
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt, Writer w)
    {
         // First things first: no binary writer for Writers:
        if (_cfgBinaryWriters) {
            throw new UnsupportedOperationException("Can only create binary Ion writers that output to OutputStream, not Writer");
        }
        return _createGenerator(writeCtxt, _createContext(_createContentReference(w), false),
                _createTextualIonWriter(writeCtxt, w),
                true, w);
    }

    @Override
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt,
            File f, JsonEncoding enc)
    {
        OutputStream out = null;
        boolean outputCleanupDelegated = false;
        try {
            out = _fileOutputStream(f);
            // From this point on `_createGenerator()` handles cleanup of `out`
            outputCleanupDelegated = true;
            return _createGenerator(writeCtxt, out, enc, true);
        } catch (RuntimeException e) {
            if (!outputCleanupDelegated) {
                _closeOnFailedConstruction(out, e);
            }
            throw e;
        }
    }

    @Override
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt,
            Path p, JsonEncoding enc)
        throws JacksonException
    {
        OutputStream out = null;
        boolean outputCleanupDelegated = false;
        try {
            out = _pathOutputStream(p);
            // From this point on `_createGenerator()` handles cleanup of `out`
            outputCleanupDelegated = true;
            return _createGenerator(writeCtxt, out, enc, true);
        } catch (RuntimeException e) {
            if (!outputCleanupDelegated) {
                _closeOnFailedConstruction(out, e);
            }
            throw e;
        }
    }

    /*
    /**********************************************************************
    /* Factory methods: context objects (since we don't extend textual or
    /* binary factory)
    /**********************************************************************
     */

    @Override
    protected ContentReference _createContentReference(Object contentRef) {
        return ContentReference.construct(!_cfgBinaryWriters, contentRef,
                errorReportConfiguration());
    }

    @Override
    protected ContentReference _createContentReference(Object contentRef,
            int offset, int length)
    {
        return ContentReference.construct(!_cfgBinaryWriters,
                contentRef, offset, length,
                errorReportConfiguration());
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
        return new IonParser(readCtxt, _createContext(_createContentReference(in), false),
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                readCtxt.getFormatReadFeatures(_formatReadFeatures),
                in, _system);
    }

    public IonParser createParser(ObjectReadContext readCtxt, IonValue value) {
        IonReader in = value.getSystem().newReader(value);
        return new IonParser(readCtxt, _createContext(_createContentReference(in), true),
                readCtxt.getStreamReadFeatures(_streamReadFeatures),
                readCtxt.getFormatReadFeatures(_formatReadFeatures),
                in, _system);
    }

    public IonGenerator createGenerator(ObjectWriteContext writeCtxt, IonWriter out) {
        return _createGenerator(writeCtxt, _createContext(_createContentReference(out), false),
                out, false, out);
    }

    /*
    /**********************************************************************
    /* Helper methods, parsers
    /**********************************************************************
     */

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in)
    {
        return _createParser(readCtxt, ioCtxt, in, false);
    }

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in, boolean closeInputOnFailedConstruction)
    {
        IonReader ion = null;
        IOContext ionCtxt = null;
        try {
            ion = _system.newReader(in);
            // [dataformats-binary#325]: Re-create context for auto-close
            ionCtxt = _createContext(_createContentReference(ion), true);
            JsonParser p = new IonParser(readCtxt, ionCtxt,
                    readCtxt.getStreamReadFeatures(_streamReadFeatures),
                    readCtxt.getFormatReadFeatures(_formatReadFeatures),
                    ion, _system);
            // Parser only uses `ionCtxt`, so release the one passed in
            ioCtxt.close();
            return p;
        } catch (RuntimeException e) {
            // Only close input we created ourselves (from `File` / `Path`): caller-provided
            // `InputStream` must be left alone. And note that closing `IonReader` -- once
            // created -- also closes the underlying `InputStream`.
            if (closeInputOnFailedConstruction) {
                _closeOnFailedConstruction((ion == null) ? in : ion, e);
            }
            _releaseContextOnFailedConstruction(ionCtxt, e);
            _releaseContextOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            Reader r)
    {
        IonReader ion = null;
        IOContext ionCtxt = null;
        try {
            ion = _system.newReader(r);
            // [dataformats-binary#325]: Re-create context for auto-close
            ionCtxt = _createContext(_createContentReference(ion), true);
            JsonParser p = new IonParser(readCtxt, ionCtxt,
                    readCtxt.getStreamReadFeatures(_streamReadFeatures),
                    readCtxt.getFormatReadFeatures(_formatReadFeatures),
                    ion, _system);
            // Parser only uses `ionCtxt`, so release the one passed in
            ioCtxt.close();
            return p;
        } catch (RuntimeException e) {
            // NOTE: `Reader` is caller-provided (or wraps caller-provided content), so
            // not closed here; closing `IonReader` would close it as well
            _releaseContextOnFailedConstruction(ionCtxt, e);
            _releaseContextOnFailedConstruction(ioCtxt, e);
            throw e;
        }
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
        IonReader ion = null;
        IOContext ionCtxt = null;
        try {
            ion = _system.newReader(data, offset, len);
            // [dataformats-binary#325]: Re-create context for auto-close
            ionCtxt = _createContext(_createContentReference(ion), true);
            JsonParser p = new IonParser(readCtxt, ionCtxt,
                    readCtxt.getStreamReadFeatures(_streamReadFeatures),
                    readCtxt.getFormatReadFeatures(_formatReadFeatures),
                    ion, _system);
            // Parser only uses `ionCtxt`, so release the one passed in
            ioCtxt.close();
            return p;
        } catch (RuntimeException e) {
            // `IonReader` created over caller's `byte[]`: no caller resource to leave open
            _closeOnFailedConstruction(ion, e);
            _releaseContextOnFailedConstruction(ionCtxt, e);
            _releaseContextOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    /*
    /**********************************************************************
    /* Helper methods, generators
    /**********************************************************************
     */

    protected IonGenerator _createGenerator(ObjectWriteContext writeCtxt,
            OutputStream out, JsonEncoding enc, boolean isManaged)
     {
        IOContext ioCtxt = _createContext(_createContentReference(out), isManaged);
        IonWriter ion = null;
        Closeable dst = null; // not necessarily same as 'out'...
        try {
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
                final Writer w = new UTF8Writer(ioCtxt, out);
                dst = w;
                ion = _createTextualIonWriter(writeCtxt, w);
            }
            // `true` for "ionWriterIsManaged" since we created it:
            return _createGenerator(writeCtxt, ioCtxt, ion, true, dst);
        } catch (RuntimeException e) {
            // Only close things we created ourselves: caller-provided `OutputStream`
            // must be left alone (closing `IonWriter` / `Writer` would close it too).
            // And since closing the outermost resource cascades down to `out`, only
            // one of them gets closed here
            if (isManaged) {
                if (ion != null) {
                    _closeOnFailedConstruction(ion, e);
                } else {
                    _closeOnFailedConstruction((dst == null) ? out : dst, e);
                }
            }
            _releaseContextOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    protected IonWriter _createTextualIonWriter(ObjectWriteContext writeCtxt,
            Writer w)
    {
        // 18-Feb-2021, tatu: [dataformats-binary#245] pretty-printing.
        //   note: Cannot really make use of Jackson PP, just rely on Ion default
        //   (for now?)
        if (writeCtxt.hasPrettyPrinter()) {
            return IonTextWriterBuilder.pretty().build(w);
        }
        return _system.newTextWriter(w);
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

    private static void _releaseContextOnFailedConstruction(IOContext ioCtxt,
            RuntimeException failure)
    {
        if (ioCtxt != null) {
            try {
                ioCtxt.close();
            } catch (Exception e) {
                failure.addSuppressed(e);
            }
        }
    }
}
