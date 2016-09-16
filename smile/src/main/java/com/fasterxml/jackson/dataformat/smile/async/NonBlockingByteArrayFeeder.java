package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

public interface NonBlockingByteArrayFeeder extends NonBlockingInputFeeder
{
     /**
      * Method that can be called to feed more data, if (and only if)
      * {@link #needMoreInput} returns true.
      * 
      * @param data Byte array that contains data to feed: caller must ensure data remains
      *    stable until it is fully processed (which is true when {@link #needMoreInput}
      *    returns true)
      * @param offset Offset within array where input data to process starts
      * @param len Length of input data within array to process.
      * 
      * @throws IOException if the state is such that this method should not be called
      *   (has not yet consumed existing input data, or has been marked as closed)
      */
     public void feedInput(byte[] data, int offset, int len) throws IOException;
}
