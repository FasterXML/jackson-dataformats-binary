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

import java.io.CharArrayReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.TextBuffer;
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
public class IonFactory extends JsonFactory {
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
    ***************************************************************
    * Extended API
    ***************************************************************
     */

    /**
     * @since 2.7
     */
    public IonParser createParser(IonReader in) {
        return new IonParser(in, _system, _createContext(in, false), getCodec());
    }

    /**
     * @since 2.7
     */
    public IonParser createParser(IonValue value) {
        IonReader in = value.getSystem().newReader(value);
        return new IonParser(in, _system, _createContext(in, true), getCodec());
    }

    /**
     * @since 2.7
     */
    public JsonGenerator createGenerator(IonWriter out) {
        return _createGenerator(out, _createContext(out, false), out);
    }

    /**
     * @deprecated Since 2.7
     */
    @Deprecated
    public IonParser createJsonParser(IonReader in) {
        return new IonParser(in, _system, _createContext(in, false), getCodec());
    }

    /**
     * @deprecated Since 2.7
     */
    @Deprecated
    public IonParser createJsonParser(IonValue value) {
        IonReader in = value.getSystem().newReader(value);
        return new IonParser(in, _system, _createContext(in, true), getCodec());
    }

    /**
     * @deprecated Since 2.7
     */
    @Deprecated
    public JsonGenerator createJsonGenerator(IonWriter out) {
        return _createGenerator(out, _createContext(out, false), out);
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
        IonReader ion = _system.newReader(in);
        return new IonParser(ion, _system, ctxt, getCodec());
    }

    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt)
        throws IOException
    {
        return new IonParser(_system.newReader(r), _system, ctxt, getCodec());
    }

    @Override
    protected JsonParser _createParser(char[] data, int offset, int len, IOContext ctxt,
            boolean recyclable) throws IOException
    {
        return _createParser(new CharArrayReader(data, offset, len), ctxt);
    }

    @Override
    protected JsonParser _createParser(byte[] data, int offset, int len, IOContext ctxt)
        throws IOException
    {
        return new IonParser(_system.newReader(data, offset, len), _system, ctxt, getCodec());
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
        return _createGenerator(_system.newTextWriter(out), _createContext(out, false), out);
    }

    @Override
    public JsonGenerator createGenerator(File f, JsonEncoding enc)
        throws IOException
    {
        return _createGenerator(new FileOutputStream(f), enc, true);
    }

    /*
    ***************************************************************
    * Helper methods
    ***************************************************************
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
        return _createGenerator(ion, ctxt, dst);
    }

    protected IonGenerator _createGenerator(IonWriter ion, IOContext ctxt, Closeable dst)
    {
        return new IonGenerator(_generatorFeatures, _objectCodec, ion, ctxt, dst);
    }        
}
