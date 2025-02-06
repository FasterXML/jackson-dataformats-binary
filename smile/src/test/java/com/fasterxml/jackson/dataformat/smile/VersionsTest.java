package com.fasterxml.jackson.dataformat.smile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.Versioned;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersionsTest extends BaseTestForSmile
{
    @Test
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

