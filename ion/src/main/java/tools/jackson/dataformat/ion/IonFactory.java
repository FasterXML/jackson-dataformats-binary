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
        InputStream rawIn = null;
        // [dataformats-binary#798]: track outermost source separately from the one
        //   Jackson opened, so that a failing `close()` of a decorated stream does
        //   not leave the underlying one open
        Closeable inputToClose = null;
        boolean inputCleanupDelegated = false;
        try {
            rawIn = _fileInputStream(f);
            inputToClose = rawIn;
            InputStream in = _decorate(ioCtxt, rawIn);
            inputToClose = in;
            // From this point on `_createParser()` handles cleanup of both input and `ioCtxt`
            inputCleanupDelegated = true;
            return _createParser(readCtxt, ioCtxt, in, rawIn);
        } catch (RuntimeException e) {
            if (!inputCleanupDelegated) {
                _closeOnFailedConstruction(inputToClose, rawIn, e);
                _releaseOnFailedConstruction(ioCtxt, e);
            }
            throw e;
        }
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt,
            Path p) throws JacksonException
    {
        IOContext ioCtxt = _createContext(_createContentReference(p), true);
        InputStream rawIn = null;
        // [dataformats-binary#798]: track outermost source separately from the one
        //   Jackson opened, so that a failing `close()` of a decorated stream does
        //   not leave the underlying one open
        Closeable inputToClose = null;
        boolean inputCleanupDelegated = false;
        try {
            rawIn = _pathInputStream(p);
            inputToClose = rawIn;
            InputStream in = _decorate(ioCtxt, rawIn);
            inputToClose = in;
            // From this point on `_createParser()` handles cleanup of both input and `ioCtxt`
            inputCleanupDelegated = true;
            return _createParser(readCtxt, ioCtxt, in, rawIn);
        } catch (RuntimeException e) {
            if (!inputCleanupDelegated) {
                _closeOnFailedConstruction(inputToClose, rawIn, e);
                _releaseOnFailedConstruction(ioCtxt, e);
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
            _releaseOnFailedConstruction(ioCtxt, e);
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
            _releaseOnFailedConstruction(ioCtxt, e);
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
                    // `InputStream` created by decorator, not caller, so we do own it
                    // (and is itself the outermost resource Jackson opened)
                    return _createParser(readCtxt, ioCtxt, in, in);
                }
            }
            return _createParser(readCtxt, ioCtxt, data, 0, data.length);
        } catch (RuntimeException e) {
            _releaseOnFailedConstruction(ioCtxt, e);
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
                    // `InputStream` created by decorator, not caller, so we do own it
                    // (and is itself the outermost resource Jackson opened)
                    return _createParser(readCtxt, ioCtxt, in, in);
                }
            }
            return _createParser(readCtxt, ioCtxt, data, offset, len);
        } catch (RuntimeException e) {
            _releaseOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    @Override
    public JsonParser createParser(ObjectReadContext readCtxt, String content) {
        IOContext ioCtxt = _createContext(_createContentReference(content), true);
        try {
            // `Reader` created by us, not caller, so we do own it
            Reader rawR = new StringReader(content);
            return _createParser(readCtxt, ioCtxt, _decorate(ioCtxt, rawR), rawR);
        } catch (RuntimeException e) {
            _releaseOnFailedConstruction(ioCtxt, e);
            throw e;
        }
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
        IOContext ioCtxt = _createContext(_createContentReference(w), false);
        try {
            return _createGenerator(writeCtxt, ioCtxt,
                    _createTextualIonWriter(writeCtxt, w),
                    true, w);
        } catch (RuntimeException e) {
            // NOTE: `Writer` is caller-provided so not closed here (and closing the
            // `IonWriter` would close it as well); `IOContext` we do need to release
            _releaseOnFailedConstruction(ioCtxt, e);
            throw e;
        }
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
        IOContext ioCtxt = _createContext(_createContentReference(in), false);
        try {
            return new IonParser(readCtxt, ioCtxt,
                    readCtxt.getStreamReadFeatures(_streamReadFeatures),
                    readCtxt.getFormatReadFeatures(_formatReadFeatures),
                    in, _system);
        } catch (RuntimeException e) {
            // NOTE: caller-provided `IonReader`, so not closed by us
            _releaseOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    public IonParser createParser(ObjectReadContext readCtxt, IonValue value) {
        IonReader in = value.getSystem().newReader(value);
        IOContext ioCtxt = null;
        try {
            ioCtxt = _createContext(_createContentReference(in), true);
            return new IonParser(readCtxt, ioCtxt,
                    readCtxt.getStreamReadFeatures(_streamReadFeatures),
                    readCtxt.getFormatReadFeatures(_formatReadFeatures),
                    in, _system);
        } catch (RuntimeException e) {
            // `IonReader` created by us (over `IonValue`), so we do own it
            _closeOnFailedConstruction(in, e);
            _releaseOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    public IonGenerator createGenerator(ObjectWriteContext writeCtxt, IonWriter out) {
        IOContext ioCtxt = _createContext(_createContentReference(out), false);
        try {
            return _createGenerator(writeCtxt, ioCtxt, out, false, out);
        } catch (RuntimeException e) {
            // NOTE: caller-provided `IonWriter`, so not closed by us
            _releaseOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    /*
    /**********************************************************************
    /* Helper methods, parsers
    /**********************************************************************
     */

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in)
    {
        // caller-provided `InputStream`: nothing for us to close
        return _createParser(readCtxt, ioCtxt, in, null);
    }

    /**
     * @param in Source to read from: caller-provided, or one Jackson opened (and
     *    possibly decorated)
     * @param rawIn Source Jackson opened, if any; {@code null} for caller-provided
     *    source, which we must not close. Closed only if closing of the outermost
     *    resource fails, so that what Jackson opened does not leak.
     */
    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            InputStream in, InputStream rawIn)
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
            if (rawIn != null) {
                // [dataformats-binary#798]: ... but if closing of the outermost resource
                //   fails, close what Jackson opened so it does not leak
                _closeOnFailedConstruction((ion == null) ? in : ion, rawIn, e);
            }
            _releaseOnFailedConstruction(ionCtxt, e);
            _releaseOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            Reader r)
    {
        // caller-provided `Reader`: nothing for us to close
        return _createParser(readCtxt, ioCtxt, r, null);
    }

    /**
     * @param r Source to read from: caller-provided, or one Jackson created (and
     *    possibly decorated)
     * @param rawR Source Jackson created, if any; {@code null} for caller-provided
     *    source, which we must not close. Closed only if closing of the outermost
     *    resource fails, so that what Jackson created does not leak.
     */
    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            Reader r, Reader rawR)
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
            // Only close `Reader` we created ourselves (over `String` / `char[]`):
            // caller-provided one must be left alone. And note that closing
            // `IonReader` -- once created -- also closes the underlying `Reader`.
            if (rawR != null) {
                // [dataformats-binary#798]: ... but if closing of the outermost resource
                //   fails, close what Jackson created so it does not leak
                _closeOnFailedConstruction((ion == null) ? r : ion, rawR, e);
            }
            _releaseOnFailedConstruction(ionCtxt, e);
            _releaseOnFailedConstruction(ioCtxt, e);
            throw e;
        }
    }

    private JsonParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
            char[] data, int offset, int len,
            boolean recyclable)
    {
        // `Reader` created by us, not caller, so we do own it
        Reader r = new CharArrayReader(data, offset, len);
        return _createParser(readCtxt, ioCtxt, r, r);
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
            _releaseOnFailedConstruction(ionCtxt, e);
            _releaseOnFailedConstruction(ioCtxt, e);
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
        IOContext ioCtxt = null;
        IonWriter ion = null;
        Closeable dst = null; // not necessarily same as 'out'...
        try {
            // NOTE: context creation within `try` since callers have delegated cleanup
            //   of `out` to this method
            ioCtxt = _createContext(_createContentReference(out), isManaged);
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
            // one of them gets closed here -- unless that close fails, in which case
            // `out` is closed as fallback so it does not leak [dataformats-binary#798]
            if (isManaged) {
                _closeOnFailedConstruction((ion != null) ? ion : ((dst == null) ? out : dst),
                        out, e);
            }
            _releaseOnFailedConstruction(ioCtxt, e);
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

}
