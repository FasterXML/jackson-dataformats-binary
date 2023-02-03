package com.fasterxml.jackson.dataformat.cbor.parse;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.dataformat.cbor.*;

public class TagParsing185Test extends CBORTestBase
{
    private final CBORFactory CBOR_F = new CBORFactory();

    public void testRecursiveTags() throws Exception
    {
        _testRecursiveTags(20000);
    }

    private void _testRecursiveTags(int levels) throws Exception
    {
         byte[] data = new byte[levels * 2];
         for (int i = 0; i < levels; i++) {
              data[i * 2] = (byte)(CBORConstants.PREFIX_TYPE_TAG +
                        CBORConstants.TAG_DECIMAL_FRACTION);
              data[(i * 2) + 1] = (byte)(CBORConstants.PREFIX_TYPE_ARRAY +
                        2);
         }

         JsonParser p = CBOR_F.createParser(data);
         JsonToken t;

         try {
             t = p.nextToken();
             fail("Should not pass, got token: "+t);
         } catch (JsonParseException e) {
             verifyException(e, "Unexpected token");
             verifyException(e, "first part of 'bigfloat' value");
         }
    }
}
