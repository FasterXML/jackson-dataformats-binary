package tools.jackson.dataformat.protobuf.schema;

import java.io.*;
import java.net.URL;
import java.util.Objects;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.protobuf.ProtobufMapper;

/**
 * Class used for loading protobuf descriptors (from .desc files
 * or equivalent sources), to construct FileDescriptorSet.
 *
 * @since 2.9
 */
public class DescriptorLoader
{
    protected final static String DESCRIPTOR_PROTO = "/tools/jackson/dataformat/protobuf/schema/descriptor.proto";
    //protected final static String DESCRIPTOR_PROTO = "descriptor.proto";

    /**
     * Fully configured reader for {@link FileDescriptorSet} objects.
     */
    protected final ObjectReader _reader;

    /**
     * @param reader {@link ObjectReader} that is able to read protobuf input
     *    (that is, must have been created from {@link ProtobufMapper}, or regular
     *    mapper with {@link tools.jackson.dataformat.protobuf.ProtobufFactory}),
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
     * @param mapper {@link ObjectMapper} that can read protoc content.
     */
    public static DescriptorLoader construct(ObjectMapper mapper,
            ProtobufSchemaLoader schemaLoader) throws IOException
    {
        ProtobufSchema schema;
        final Class<?> ctxt = DescriptorLoader.class;
        final String resourceName = DESCRIPTOR_PROTO;
        try (InputStream in = ctxt.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException(String.format(
                        "Can not find resource `%s` within context '%s'",
                        resourceName, ctxt.getName()));
            }
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
        return _reader.readValue(Objects.requireNonNull(src));
    }

    public FileDescriptorSet load(File src) throws IOException
    {
        return _reader.readValue(Objects.requireNonNull(src));
    }

    /**
     * Note: passed {@link java.io.InputStream} will be closed by this method.
     */
    public FileDescriptorSet load(InputStream in) throws IOException
    {
        return _reader.readValue(Objects.requireNonNull(in));
    }

    /**
     * Note: passed {@link java.io.Reader} will be closed by this method.
     */
    public FileDescriptorSet load(Reader r) throws IOException
    {
        return _reader.readValue(Objects.requireNonNull(r));
    }
}
