package com.fasterxml.jackson.dataformat.smile;

import java.io.*;

import com.fasterxml.jackson.core.Versioned;

/**
 * Tests to verify [JACKSON-278]
 */
public class VersionsTest extends BaseTestForSmile
{
    public void testMapperVersions() throws IOException
    {
        SmileFactory f = new SmileFactory();
        f.disable(SmileParser.Feature.REQUIRE_HEADER);
        assertVersion(f);
        assertVersion(f.createGenerator(new ByteArrayOutputStream()));
        assertVersion(f.createParser(new byte[0]));
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

