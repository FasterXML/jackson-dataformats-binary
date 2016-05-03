package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;

import com.squareup.protoparser.ProtoFile;
import com.squareup.protoparser.ProtoParser;

/**
 * Class used for loading protobuf definitions (from .proto files
 * or equivalent sources), to construct schema needed for reading
 * or writing content.
 *<p>
 * Note that message name argument is optional if (and only if) desired
 * root type is the first Message type in definition; otherwise an
 * exception will be thrown.
 */
public class ProtobufSchemaLoader
    implements java.io.Serializable // since mapper has a reference
{
    private static final long serialVersionUID = 1L;

    private final static Charset UTF8 = Charset.forName("UTF-8");

    public final static String DEFAULT_SCHEMA_NAME = "Unnamed-protobuf-schema";

    /**
     * Standard loader instance that is usually used for loading protoc
     * schemas.
     */
    public final static ProtobufSchemaLoader std = new ProtobufSchemaLoader();
    
    public ProtobufSchemaLoader() { }

    /*
    /**********************************************************
    /* Public API
    /**********************************************************
     */
    
    public ProtobufSchema load(URL url) throws IOException {
        return loadNative(url).forFirstType();
    }

    /**
     * @param rootTypeName Name of message type in schema definition that is
     *   the root value to read/write
     */
    public ProtobufSchema load(URL url, String rootTypeName) throws IOException {
        return loadNative(url).forType(rootTypeName);
    }
    
    public ProtobufSchema load(File f) throws IOException {
        return loadNative(f).forFirstType();
    }

    /**
     * @param rootTypeName Name of message type in schema definition that is
     *   the root value to read/write
     */
    public ProtobufSchema load(File f, String rootTypeName) throws IOException {
        return loadNative(f).forType(rootTypeName);
    }
    
    /**
     * Method for loading and parsing a protoc definition from given
     * stream, assuming UTF-8 encoding.
     * Note that given {@link InputStream} will be closed before method returns.
     */
    public ProtobufSchema load(InputStream in) throws IOException {
        return loadNative(in, true).forFirstType();
    }

    /**
     * @param rootTypeName Name of message type in schema definition that is
     *   the root value to read/write
     */
    public ProtobufSchema load(InputStream in, String rootTypeName) throws IOException {
        return loadNative(in, true).forType(rootTypeName);
    }
    
    /**
     * Method for loading and parsing a protoc definition from given
     * stream, assuming UTF-8 encoding.
     * Note that given {@link Reader} will be closed before method returns.
     */
    public ProtobufSchema load(Reader r) throws IOException {
        return loadNative(r, true).forFirstType();
    }

    /**
     * @param rootTypeName Name of message type in schema definition that is
     *   the root value to read/write
     */
    public ProtobufSchema load(Reader r, String rootTypeName) throws IOException {
        return loadNative(r, true).forType(rootTypeName);
    }
    
    /**
     * Method for parsing given protoc schema definition, constructing
     * schema object Jackson can use.
     */
    public ProtobufSchema parse(String schemaAsString) throws IOException {
        return parseNative(schemaAsString).forFirstType();
    }

    /**
     * @param rootTypeName Name of message type in schema definition that is
     *   the root value to read/write
     */
    public ProtobufSchema parse(String schemaAsString, String rootTypeName) throws IOException {
        return parseNative(schemaAsString).forType(rootTypeName);
    }

    /*
    /**********************************************************
    /* Loading native schema instances
    /**********************************************************
     */

    public NativeProtobufSchema loadNative(File f) throws IOException {
        return NativeProtobufSchema.construct(_loadNative(f));
    }

    public NativeProtobufSchema loadNative(URL url) throws IOException {
        return NativeProtobufSchema.construct(_loadNative(url));
    }

    public NativeProtobufSchema parseNative(String schema) throws IOException {
        return NativeProtobufSchema.construct(_loadNative(schema));
    }
    
    public NativeProtobufSchema loadNative(InputStream in, boolean close) throws IOException {
        return NativeProtobufSchema.construct(_loadNative(in, close));
    }

    protected NativeProtobufSchema loadNative(Reader r, boolean close) throws IOException {
        return NativeProtobufSchema.construct(_loadNative(r, close));
    }
    
    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    public ProtoFile _loadNative(File f) throws IOException {
        return ProtoParser.parseUtf8(f);
    }

    public ProtoFile _loadNative(URL url) throws IOException {
        return _loadNative(url.openStream(), true);
    }

    public ProtoFile _loadNative(String schemaAsString) throws IOException {
        return ProtoParser.parse(DEFAULT_SCHEMA_NAME, schemaAsString);
    }
    
    public ProtoFile _loadNative(InputStream in, boolean close) throws IOException {
        return _loadNative(new InputStreamReader(in, UTF8), close);
    }
    
    protected ProtoFile _loadNative(Reader r, boolean close) throws IOException
    {
        try {
            return ProtoParser.parse(DEFAULT_SCHEMA_NAME, _readAll(r));
        } finally {
            if (close) {
                try { r.close(); } catch (IOException e) { }
            }
        }
    }
    
    protected String _readAll(Reader r) throws IOException
    {
        StringBuilder sb = new StringBuilder(1000);
        char[] buffer = new char[1000];
        int count;
        
        while ((count = r.read(buffer)) > 0) {
            sb.append(buffer, 0, count);
        }
        return sb.toString();
    }
    
    public void _throw(Exception e0) throws IOException
    {
        // First, peel it
        Throwable e = e0;
        while (e.getCause() != null) {
            e = e.getCause();
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        if (e instanceof IOException){ 
            throw (IOException) e;
        }
        throw new IOException(e.getMessage(), e);
    }
}
