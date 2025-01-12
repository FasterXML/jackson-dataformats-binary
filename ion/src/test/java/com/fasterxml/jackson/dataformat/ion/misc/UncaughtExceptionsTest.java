package com.fasterxml.jackson.dataformat.ion.misc;

import java.net.URL;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * A set of unit tests for reported issues where implementation does
 * not catch exceptions like {@link NullPointerException} where it should.
 */
public class UncaughtExceptionsTest
{
    private final IonObjectMapper MAPPER = IonObjectMapper.builder().build();

    // [dataformats-binary#302]
    @Test
    public void testUncaughtException302() throws Exception
    {
        URL poc = getClass().getResource("/data/issue-302.ion");
        try {
            MAPPER.readTree(poc);
            fail("Should not pass with invalid content");
        } catch (JsonProcessingException e) {
            verifyException(e, "Invalid embedded TIMESTAMP");
        }
    }

    void verifyException(Throwable e, String match)
    {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        if (!lmsg.contains(match.toLowerCase())) {
            fail("Expected an exception with a substrings ("+match+"): got one with message \""+msg+"\"");
        }
    }
}
