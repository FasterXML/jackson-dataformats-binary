package com.fasterxml.jackson.dataformat.protobuf.schema;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;

import java.io.InputStream;

public class DescriptorLoaderTest extends ProtobufTestBase
{
    public void testParsing() throws Exception
    {
        InputStream in = this.getClass().getResourceAsStream("/main.desc");
        FileDescriptorSet fds = DescriptorLoader.std.load(in);
        ProtobufSchema nps = fds.forType("main1");
        assertNotNull(nps);
    }

        ProtobufSchema schema = nativeSchema.forType("main1");
        assertNotNull(schema);
    }
}

