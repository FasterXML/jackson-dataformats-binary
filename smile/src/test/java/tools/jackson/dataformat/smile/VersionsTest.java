package tools.jackson.dataformat.smile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import tools.jackson.core.Versioned;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.smile.databind.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersionsTest extends BaseTestForSmile
{
    @Test
    public void testMapperVersions() throws IOException
    {
        final ObjectMapper mapper = SmileMapper.shared();
        assertVersion(mapper.tokenStreamFactory());
        assertVersion(mapper.createGenerator(new ByteArrayOutputStream()));
        assertVersion(mapper.createParser(new byte[0]));
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void assertVersion(Versioned v)
    {
        assertEquals(PackageVersion.VERSION, v.version());
    }
}

