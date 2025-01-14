package tools.jackson.dataformat.cbor.parse;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.dataformat.cbor.*;

import static org.junit.jupiter.api.Assertions.fail;

public class TagParsing185Test extends CBORTestBase
{
    @Test
    public void testRecursiveTags() throws Exception
    {
        _testRecursiveTags(20000);
    }

    private void _testRecursiveTags(int levels)
    {
         byte[] data = new byte[levels * 2];
         for (int i = 0; i < levels; i++) {
              data[i * 2] = (byte)(CBORConstants.PREFIX_TYPE_TAG +
                        CBORConstants.TAG_DECIMAL_FRACTION);
              data[(i * 2) + 1] = (byte)(CBORConstants.PREFIX_TYPE_ARRAY +
                        2);
         }

         try (JsonParser p = cborParser(data)) {
             JsonToken t = p.nextToken();
             fail("Should not pass, got token: "+t);
         } catch (StreamReadException e) {
             verifyException(e, "Unexpected token");
             verifyException(e, "first part of 'bigfloat' value");
         }
    }
}
