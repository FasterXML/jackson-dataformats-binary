package tools.jackson.dataformat.ion.failing;

import java.net.URL;

import org.junit.Test;

import tools.jackson.core.exc.StreamReadException;
import tools.jackson.dataformat.ion.IonObjectMapper;

import static org.junit.Assert.*;

public class UncaughtException303Test
{
    private final IonObjectMapper MAPPER = IonObjectMapper.builder().build();

    // [dataformats-binary#303]
    @Test
    public void testUncaughtException303() throws Exception
    {
        URL poc = getClass().getResource("/data/issue-303.ion");
        try {
            MAPPER.readTree(poc);
            fail("Should not pass with invalid content");
        } catch (StreamReadException e) {
            // !!! TODO: change to match what we actually expect
            verifyException(e, "MATCH MESSAGE");
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
