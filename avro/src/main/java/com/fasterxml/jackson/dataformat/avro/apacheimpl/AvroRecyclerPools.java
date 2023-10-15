package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.lang.ref.SoftReference;

import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.RecyclerPool;

public final class AvroRecyclerPools
{
    /**
     * @return the default {@link RecyclerPool} implementation
     *   which is the thread local based one:
     *   basically alias to {@link #threadLocalPool()}).
     */
    public static RecyclerPool<ApacheCodecRecycler> defaultPool() {
        return threadLocalPool();
    }

    /**
     * Accessor for getting the shared/global {@link ThreadLocalPool} instance
     * (due to design only one instance ever needed)
     *
     * @return Globally shared instance of {@link ThreadLocalPool}
     */
    public static RecyclerPool<ApacheCodecRecycler> threadLocalPool() {
        return ThreadLocalPool.GLOBAL;
    }

    /*
    /**********************************************************************
    /* Concrete RecyclerPool implementations for recycling BufferRecyclers
    /**********************************************************************
     */

    /**
     * {@link ThreadLocal}-based {@link RecyclerPool} implementation used for
     * recycling {@link BufferRecycler} instances:
     * see {@link RecyclerPool.ThreadLocalPoolBase} for full explanation
     * of functioning.
     */
    public static class ThreadLocalPool
        extends RecyclerPool.ThreadLocalPoolBase<ApacheCodecRecycler>
    {
        private static final long serialVersionUID = 1L;

        protected static final ThreadLocalPool GLOBAL = new ThreadLocalPool();

        protected final static ThreadLocal<SoftReference<ApacheCodecRecycler>> _recycler
            = new ThreadLocal<SoftReference<ApacheCodecRecycler>>();

        private ThreadLocalPool() { }

        @Override
        public ApacheCodecRecycler acquirePooled() {
            SoftReference<ApacheCodecRecycler> ref = _recycler.get();
            ApacheCodecRecycler r = (ref == null) ? null : ref.get();

            if (r == null) {
                r = new ApacheCodecRecycler();
                _recycler.set(new SoftReference<>(r));
            }
            return r;
        }

        // // // JDK serialization support

        protected Object readResolve() { return GLOBAL; }
    }
    
}
