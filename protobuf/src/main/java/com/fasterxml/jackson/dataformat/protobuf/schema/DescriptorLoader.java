package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;

/**
 * Class used for loading protobuf descriptors (from .desc files
 * or equivalent sources), to construct FileDescriptorSet.
 *
 * @since 2.9
 */
public class DescriptorLoader
{
    protected final static String DESCRIPTOR_PROTO = "/descriptor.proto";

    /**
     * Fully configured reader for {@link FileDescriptorSet} objects.
     */
    protected final ObjectReader _reader;

    /**
     * @param reader {@link ObjectReader} that is able to read protobuf input
     *    (that is, must have been created from {@link ProtobufMapper}, or regular
     *    mapper with {@link com.fasterxml.jackson.dataformat.protobuf.ProtobufFactory}),
     *    and has been configured with `protoc` definition of `descriptor.proro`
     */
    public DescriptorLoader(ObjectReader reader) {
        _reader = reader;
    }

    public static DescriptorLoader construct(ProtobufMapper mapper) throws IOException
    {
        return construct(mapper, mapper.schemaLoader());
    }

    /**
     * @param mapper {@link ObjectMapper} that can reader protoc content.
     */
    public static DescriptorLoader construct(ObjectMapper mapper,
            ProtobufSchemaLoader schemaLoader) throws IOException
    {
        ProtobufSchema schema;
        try (InputStream in = DescriptorLoader.class.getResourceAsStream(DESCRIPTOR_PROTO)) {
            schema = schemaLoader.load(in, "FileDescriptorSet");
        }
        return new DescriptorLoader(mapper.readerFor(FileDescriptorSet.class)
                .with(schema));
    }

    /*
    /**********************************************************************
    /* Public API
    /**********************************************************************
     */

    public FileDescriptorSet load(URL src) throws IOException
    {
        return _reader.readValue(src);
    }

    public FileDescriptorSet load(File src) throws IOException
    {
        return _reader.readValue(src);
    }

    /**
     * Note: passed {@link java.io.InputStream} will be closed by this method.
     */
    public FileDescriptorSet load(InputStream in) throws IOException
    {
        return _reader.readValue(in);
    }

    /**
     * Note: passed {@link java.io.Reader} will be closed by this method.
     */
    public FileDescriptorSet load(Reader r) throws IOException
    {
        return _reader.readValue(r);
    }
}
