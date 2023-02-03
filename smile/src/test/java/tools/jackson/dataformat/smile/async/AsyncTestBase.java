package tools.jackson.dataformat.smile.async;

import tools.jackson.core.JsonToken;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.smile.*;

abstract class AsyncTestBase extends BaseTestForSmile
{
    final static String SPACES = "                ";

    protected final static char UNICODE_2BYTES = (char) 167; // law symbol
    protected final static char UNICODE_3BYTES = (char) 0x4567;

    protected final static String UNICODE_SEGMENT = "["+UNICODE_2BYTES+"/"+UNICODE_3BYTES+"]";

    protected AsyncReaderWrapper asyncForBytes(ObjectMapper mapper,
            int bytesPerRead,
            byte[] bytes, int padding)
    {
        return asyncForBytes(mapper.reader(), bytesPerRead, bytes, padding);
    }

    protected AsyncReaderWrapper asyncForBytes(ObjectReader r,
            int bytesPerRead,
            byte[] bytes, int padding)
    {
        return new AsyncReaderWrapperForByteArray(r.createNonBlockingByteArrayParser(),
                bytesPerRead, bytes, padding);
    }

    protected static String spaces(int count)
    {
        return SPACES.substring(0, Math.min(SPACES.length(), count));
    }

    protected final JsonToken verifyStart(AsyncReaderWrapper reader)
    {
        assertToken(JsonToken.NOT_AVAILABLE, reader.currentToken());
        return reader.nextToken();
    }
}
