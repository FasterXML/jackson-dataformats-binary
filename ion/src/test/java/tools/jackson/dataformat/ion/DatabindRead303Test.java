package tools.jackson.dataformat.ion;

import java.net.URL;

import org.junit.Test;

import tools.jackson.core.exc.StreamReadException;

import static org.junit.Assert.*;

public class DatabindRead303Test
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
            // 19-Dec-2023, tatu: Looks like message depends on ion-java version,
            //     cannot easily verify
            // verifyException(e, "Value exceeds the length of its parent container");
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
