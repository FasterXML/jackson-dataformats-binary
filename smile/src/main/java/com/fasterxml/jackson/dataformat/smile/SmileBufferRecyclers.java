package com.fasterxml.jackson.dataformat.smile;

import java.lang.ref.SoftReference;

import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.RecyclerPool;

/**
 * Newly (in 2.16) added container class for {@link RecyclerPool}s available
 * for recycling Smile {@link SmileBufferRecycler} instances.
 *
 * @since 2.16
 */
public class SmileBufferRecyclers
{
    /**
     * @return the default {@link RecyclerPool} implementation
     *   which is the thread local based one:
     *   basically alias to {@link #threadLocalPool()}).
     */
    public static RecyclerPool<SmileBufferRecycler> defaultPool() {
        return threadLocalPool();
    }
    /**
     * Accessor for getting the shared/global {@link ThreadLocalPool} instance
     * (due to design only one instance ever needed)
     *
     * @return Globally shared instance of {@link ThreadLocalPool}
     */
    public static RecyclerPool<SmileBufferRecycler> threadLocalPool() {
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
        extends RecyclerPool.ThreadLocalPoolBase<SmileBufferRecycler>
    {
        private static final long serialVersionUID = 1L;

        /**
         * This <code>ThreadLocal</code> contains a {@link java.lang.ref.SoftReference}
         * to a {@link BufferRecycler} used to provide a low-cost
         * buffer recycling between reader and writer instances.
         */
        protected static final ThreadLocal<SoftReference<SmileBufferRecycler>> _recyclerRef
            = new ThreadLocal<>();

        protected static final ThreadLocalPool GLOBAL = new ThreadLocalPool();

        private ThreadLocalPool() { }

        @Override
        public SmileBufferRecycler acquirePooled() {
            SoftReference<SmileBufferRecycler> ref = _recyclerRef.get();
            SmileBufferRecycler br = (ref == null) ? null : ref.get();

            if (br == null) {
                br = new SmileBufferRecycler();
                ref = new SoftReference<>(br);
                _recyclerRef.set(ref);
            }
            return br;
        }

        // // // JDK serialization support

        protected Object readResolve() { return GLOBAL; }
    }

}
