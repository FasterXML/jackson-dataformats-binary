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

import software.amazon.ion.IonReader;
import software.amazon.ion.IonSystem;
import software.amazon.ion.IonValue;
import software.amazon.ion.IonWriter;
import software.amazon.ion.system.IonSystemBuilder;

/**
 * Sub-class of {@link JsonFactory} that will work on Ion content, instead of JSON
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

    public final static String FORMAT_NAME_ION = "AmazonIon";

    final IonSystem _system;

    /**
     * Whether we will produce binary or text Ion writers: default is textual.
     */
    protected boolean _cfgCreateBinaryWriters = false;
    
    public IonFactory() {
        this(null);
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

    @Override
    public IonFactory copy()
    {
        // note: as with base class, must NOT copy mapper reference
        return new IonFactory(this, null);
    }

    public void setCreateBinaryWriters(boolean b) {
        _cfgCreateBinaryWriters = b;
    }

    public boolean createBinaryWriters() {
        return _cfgCreateBinaryWriters;
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

    @Override
    public boolean canHandleBinaryNatively() {
        // 21-Feb-2017, tatu: I think only support with binary backend
        return _cfgCreateBinaryWriters;
    }

    @Override
    public boolean canParseAsync() {
        // 30-Sep-2017, tatu: No async implementation exists
        return false;
    }
    
    /*
    /**********************************************************
    /* Data format support
    /**********************************************************
     */

    @Override
    public String getFormatName() {
        return FORMAT_NAME_ION;
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return false;
    }

    /*
    /***************************************************************
    /* Extended API
    /***************************************************************
     */

    public IonParser createParser(IonReader in) {
        return new IonParser(in, _system, _createContext(in, false), getCodec());
    }

    public IonParser createParser(IonValue value) {
        IonReader in = value.getSystem().newReader(value);
        return new IonParser(in, _system, _createContext(in, true), getCodec());
    }

    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt, IonWriter out) {
        return _createGenerator(writeCtxt,
                out, _createContext(out, false), out);
    }

    /*
    /***************************************************************
    /* Factory methods: parsers
    /***************************************************************
     */

    @Override
    public JsonParser createParser(File f) throws IOException {
        // true, since we create InputStream from File
        IOContext ctxt = _createContext(f, true);
        InputStream in = new FileInputStream(f);
        return _createParser(_decorate(in, ctxt), ctxt);
    }

    @Override
    public JsonParser createParser(URL url) throws IOException {
        // true, since we create InputStream from URL
        IOContext ctxt = _createContext(url, true);
        InputStream in = _optimizedStreamFromURL(url);
        return _createParser(_decorate(in, ctxt), ctxt);
    }

    @Override
    public JsonParser createParser(InputStream in) throws IOException {
        IOContext ctxt = _createContext(in, false);
        return _createParser(_decorate(in, ctxt), ctxt);
    }

    @Override
    public JsonParser createParser(Reader r) throws IOException {
        // false -> we do NOT own Reader (did not create it)
        IOContext ctxt = _createContext(r, false);
        return _createParser(_decorate(r, ctxt), ctxt);
    }

    @Override
    public JsonParser createParser(byte[] data) throws IOException {
        IOContext ctxt = _createContext(data, true);
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ctxt, data, 0, data.length);
            if (in != null) {
                return _createParser(in, ctxt);
            }
        }
        return _createParser(data, 0, data.length, ctxt);
    }

    @Override
    public JsonParser createParser(byte[] data, int offset, int len) throws IOException {
        IOContext ctxt = _createContext(data, true);
        if (_inputDecorator != null) {
            InputStream in = _inputDecorator.decorate(ctxt, data, offset, len);
            if (in != null) {
                return _createParser(in, ctxt);
            }
        }
        return _createParser(data, offset, len, ctxt);
    }

    @Override
    public JsonParser createParser(String content) throws IOException {
        return createParser(new StringReader(content));
    }

    @Override
    public JsonParser createParser(char[] content, int offset, int len) throws IOException {
        if (_inputDecorator != null) { // easier to just wrap in a Reader than extend InputDecorator
            return createParser(new CharArrayReader(content, offset, len));
        }
        return _createParser(content, offset, len, _createContext(content, true),
                // important: buffer is NOT recyclable, as it's from caller
                false);
    }

    @Override
    public JsonParser createParser(DataInput in) throws IOException {
        return _unsupported();
    }        
    
    /*
    /***************************************************************
    /* Factory methods: generators
    /***************************************************************
     */

    @Override
    public JsonGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException
    {
        return _createGenerator(EMPTY_WRITE_CONTEXT,
                out, enc, false);
    }

    @Override
    public JsonGenerator createGenerator(Writer out)
        throws IOException
    {
         // First things first: no binary writer for Writers:
        if (createBinaryWriters()) {
            throw new IOException("Can only create binary Ion writers that output to OutputStream, not Writer");
        }
        return _createGenerator(EMPTY_WRITE_CONTEXT,
                _system.newTextWriter(out), _createContext(out, false), out);
    }

    @Override
    public JsonGenerator createGenerator(File f, JsonEncoding enc)
        throws IOException
    {
        return _createGenerator(EMPTY_WRITE_CONTEXT,
                new FileOutputStream(f), enc, true);
    }

    @Override
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt,
            OutputStream out, JsonEncoding enc) throws IOException
    {
        return _createGenerator(writeCtxt, out, enc, false);
    }

    @Override
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt, Writer out)
        throws IOException
    {
         // First things first: no binary writer for Writers:
        if (createBinaryWriters()) {
            throw new IOException("Can only create binary Ion writers that output to OutputStream, not Writer");
        }
        return _createGenerator(writeCtxt,
                _system.newTextWriter(out), _createContext(out, false), out);
    }

    @Override
    public JsonGenerator createGenerator(ObjectWriteContext writeCtxt,
            File f, JsonEncoding enc)
        throws IOException
    {
        return _createGenerator(writeCtxt,
                new FileOutputStream(f), enc, true);
    }
    
    /*
    /***************************************************************
    /* Helper methods, parsers
    /***************************************************************
     */

    private JsonParser _createParser(InputStream in, IOContext ctxt)
        throws IOException
    {
        IonReader ion = _system.newReader(in);
        return new IonParser(ion, _system, ctxt, getCodec());
    }

    private JsonParser _createParser(Reader r, IOContext ctxt)
        throws IOException
    {
        return new IonParser(_system.newReader(r), _system, ctxt, getCodec());
    }

    private JsonParser _createParser(char[] data, int offset, int len, IOContext ctxt,
            boolean recyclable) throws IOException
    {
        return _createParser(new CharArrayReader(data, offset, len), ctxt);
    }

    private JsonParser _createParser(byte[] data, int offset, int len, IOContext ctxt)
        throws IOException
    {
        return new IonParser(_system.newReader(data, offset, len), _system, ctxt, getCodec());
    }

    /*
    /***************************************************************
    /* Helper methods, generators
    /***************************************************************
     */

    protected IonGenerator _createGenerator(ObjectWriteContext writeCtxt,
            OutputStream out, JsonEncoding enc, boolean isManaged)
         throws IOException
     {
        IonWriter ion;
        IOContext ctxt = _createContext(out, isManaged);
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
            /* In theory Ion package could take some advantage of getting OutputStream.
             * In practice we seem to be better off using Jackson's efficient buffering
             * encoder
             */
            ctxt.setEncoding(enc);
            // This is bit unfortunate, since out != dst now...
            Writer w = new CloseSafeUTF8Writer(ctxt, out);
            ion = _system.newTextWriter(w);
            dst = w;
        }
        return _createGenerator(writeCtxt, ion, ctxt, dst);
    }

    protected IonGenerator _createGenerator(ObjectWriteContext writeCtxt,
            IonWriter ion, IOContext ctxt, Closeable dst)
    {
        return new IonGenerator(writeCtxt.getGeneratorFeatures(_generatorFeatures),
                _objectCodec, ion, ctxt, dst);
    }
}
