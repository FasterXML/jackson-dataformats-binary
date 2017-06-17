/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.fasterxml.jackson.dataformat.ion.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.io.UTF8Writer;

/**
 * This is simply a wrapper around the {@link UTF8Writer} to prevent it from throwing 
 * an NPE after closing. It would be easier to subclass it, but {@link UTF8Writer} is 
 * final, so we have to use delegation.
 */
public class CloseSafeUTF8Writer extends Writer {

    UTF8Writer wrapped;
    boolean closed = false;
    Object closedLatch = new Object();

    public CloseSafeUTF8Writer(IOContext ctxt, OutputStream out) {
        wrapped = new UTF8Writer(ctxt,out);
    }

    @Override
    public void close() throws IOException {
        synchronized(closedLatch) {
            if(!closed) {
                closed = true;
                wrapped.close();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        synchronized(closedLatch) {
            if(!closed) {
                wrapped.flush();
            }
        }
    }

    @Override
    public Writer append(char c) throws IOException {
        return wrapped.append(c);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        wrapped.write(cbuf);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        wrapped.write(cbuf, off, len);
    }
    
    @Override
    public void write(int c) throws IOException {
        wrapped.write(c);
    }

    @Override
    public void write(String str) throws IOException {
        wrapped.write(str);
    }

    @Override
    public void write(String str, int off, int len)  throws IOException {
        wrapped.write(str, off, len);
    }
    
    
    
}
