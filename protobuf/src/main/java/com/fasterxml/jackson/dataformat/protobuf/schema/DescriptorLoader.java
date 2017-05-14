package com.fasterxml.jackson.dataformat.protobuf.schema;


import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Class used for loading protobuf descriptors (from .desc files
 * or equivalent sources), to construct native schema.
 * <p>
 * Note that proto name argument is optional if (and only if) desired
 * root file is the first file in definition; otherwise an
 * exception will be thrown.
 */
public class DescriptorLoader
{
    private final String DESCRIPTOR_PROTO = "/descriptor.proto";
    private ProtobufMapper descriptorMapper;
    private ProtobufSchema descriptorFileSchema;

    /**
     * Standard loader instance that is usually used for loading descriptor file.
     */
    public final static DescriptorLoader std = new DescriptorLoader();

    public DescriptorLoader() {}


    /**
     * Public API
     */

    public FileDescriptorSet load(URL url) throws IOException
    {
        return _loadFileDescriptorSet(url.openStream());
    }

    public FileDescriptorSet load(File f) throws IOException
    {
        return _loadFileDescriptorSet(new FileInputStream(f));
    }

    public FileDescriptorSet load(InputStream in) throws IOException
    {
        return _loadFileDescriptorSet(in);
    }

    public FileDescriptorSet fromBytes(byte[] descriptorBytes) throws IOException
    {
        return _loadFileDescriptorSet(new ByteArrayInputStream(descriptorBytes));
    }

    protected FileDescriptorSet _loadFileDescriptorSet(InputStream in) throws IOException
    {
        try {
            if (descriptorMapper == null) {
                createDescriptorMapper();
            }
            return descriptorMapper.readerFor(FileDescriptorSet.class)
                                   .with(descriptorFileSchema)
                                   .readValue(in);
        }
        finally {
            try {
                in.close();
            }
            catch (IOException e) {
            }
        }
    }

    private void createDescriptorMapper() throws IOException
    {
        // read Descriptor Proto
        descriptorMapper = new ProtobufMapper();
        InputStream in = getClass().getResourceAsStream(DESCRIPTOR_PROTO);
        descriptorFileSchema = ProtobufSchemaLoader.std.load(in, "FileDescriptorSet");
        in.close();
    }
}
