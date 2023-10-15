package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.lang.ref.SoftReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.fasterxml.jackson.core.util.RecyclerPool;
import com.fasterxml.jackson.core.util.RecyclerPool.BoundedPoolBase;
import com.fasterxml.jackson.core.util.RecyclerPool.ConcurrentDequePoolBase;
import com.fasterxml.jackson.core.util.RecyclerPool.LockFreePoolBase;

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

    /**
     * Accessor for getting the shared/global {@link NonRecyclingPool} instance
     * (due to design only one instance ever needed)
     *
     * @return Globally shared instance of {@link NonRecyclingPool}.
     */
    public static RecyclerPool<ApacheCodecRecycler> nonRecyclingPool() {
        return NonRecyclingPool.GLOBAL;
    }

    /**
     * Accessor for getting the shared/global {@link ConcurrentDequePool} instance.
     *
     * @return Globally shared instance of {@link NonRecyclingPool}.
     */
    public static RecyclerPool<ApacheCodecRecycler> sharedConcurrentDequePool() {
        return ConcurrentDequePool.GLOBAL;
    }

    /**
     * Accessor for constructing a new, non-shared {@link ConcurrentDequePool} instance.
     *
     * @return Globally shared instance of {@link NonRecyclingPool}.
     */
    public static RecyclerPool<ApacheCodecRecycler> newConcurrentDequePool() {
        return ConcurrentDequePool.construct();
    }

    /**
     * Accessor for getting the shared/global {@link LockFreePool} instance.
     *
     * @return Globally shared instance of {@link LockFreePool}.
     */
    public static RecyclerPool<ApacheCodecRecycler> sharedLockFreePool() {
        return LockFreePool.GLOBAL;
    }

    /**
     * Accessor for constructing a new, non-shared {@link LockFreePool} instance.
     *
     * @return Globally shared instance of {@link LockFreePool}.
     */
    public static RecyclerPool<ApacheCodecRecycler> newLockFreePool() {
        return LockFreePool.construct();
    }

    /**
     * Accessor for getting the shared/global {@link BoundedPool} instance.
     *
     * @return Globally shared instance of {@link BoundedPool}.
     */
    public static RecyclerPool<ApacheCodecRecycler> sharedBoundedPool() {
        return BoundedPool.GLOBAL;
    }

    /**
     * Accessor for constructing a new, non-shared {@link BoundedPool} instance.
     *
     * @param size Maximum number of values to pool
     *
     * @return Globally shared instance of {@link BoundedPool}.
     */
    public static RecyclerPool<ApacheCodecRecycler> newBoundedPool(int size) {
        return BoundedPool.construct(size);
    }

    /*
    /**********************************************************************
    /* Concrete RecyclerPool implementations for recycling BufferRecyclers
    /**********************************************************************
     */

    /**
     * {@link ThreadLocal}-based {@link RecyclerPool} implementation used for
     * recycling {@link ApacheCodecRecycler} instances:
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

    /**
     * Dummy {@link RecyclerPool} implementation that does not recycle
     * anything but simply creates new instances when asked to acquire items.
     */
    public static class NonRecyclingPool
        extends RecyclerPool.NonRecyclingPoolBase<ApacheCodecRecycler>
    {
        private static final long serialVersionUID = 1L;

        protected static final NonRecyclingPool GLOBAL = new NonRecyclingPool();

        protected NonRecyclingPool() { }

        @Override
        public ApacheCodecRecycler acquirePooled() {
            return new ApacheCodecRecycler();
        }

        // // // JDK serialization support

        protected Object readResolve() { return GLOBAL; }
    }

    /**
     * {@link RecyclerPool} implementation that uses
     * {@link ConcurrentLinkedDeque} for recycling instances.
     *<p>
     * Pool is unbounded: see {@link RecyclerPool} what this means.
     */
    public static class ConcurrentDequePool extends ConcurrentDequePoolBase<ApacheCodecRecycler>
    {
        private static final long serialVersionUID = 1L;

        protected static final ConcurrentDequePool GLOBAL = new ConcurrentDequePool(SERIALIZATION_SHARED);

        // // // Life-cycle (constructors, factory methods)

        protected ConcurrentDequePool(int serialization) {
            super(serialization);
        }

        public static ConcurrentDequePool construct() {
            return new ConcurrentDequePool(SERIALIZATION_NON_SHARED);
        }

        @Override
        public ApacheCodecRecycler createPooled() {
            return new ApacheCodecRecycler();
        }

        // // // JDK serialization support

        // Make sure to re-link to global/shared or non-shared.
        protected Object readResolve() {
            return _resolveToShared(GLOBAL).orElseGet(() -> construct());
        }
    }

    /**
     * {@link RecyclerPool} implementation that uses
     * a lock free linked list for recycling instances.
     *<p>
     * Pool is unbounded: see {@link RecyclerPool} for
     * details on what this means.
     */
    public static class LockFreePool extends LockFreePoolBase<ApacheCodecRecycler>
    {
        private static final long serialVersionUID = 1L;

        protected static final LockFreePool GLOBAL = new LockFreePool(SERIALIZATION_SHARED);

        // // // Life-cycle (constructors, factory methods)

        protected LockFreePool(int serialization) {
            super(serialization);
        }

        public static LockFreePool construct() {
            return new LockFreePool(SERIALIZATION_NON_SHARED);
        }
        
        @Override
        public ApacheCodecRecycler createPooled() {
            return new ApacheCodecRecycler();
        }

        // // // JDK serialization support

        // Make sure to re-link to global/shared or non-shared.
        protected Object readResolve() {
            return _resolveToShared(GLOBAL).orElseGet(() -> construct());
        }
    }

    /**
     * {@link RecyclerPool} implementation that uses
     * a bounded queue ({@link ArrayBlockingQueue} for recycling instances.
     * This is "bounded" pool since it will never hold on to more
     * {@link ApacheCodecRecycler} instances than its size configuration:
     * the default size is {@link BoundedPoolBase#DEFAULT_CAPACITY}.
     */
    public static class BoundedPool extends BoundedPoolBase<ApacheCodecRecycler>
    {
        private static final long serialVersionUID = 1L;

        protected static final BoundedPool GLOBAL = new BoundedPool(SERIALIZATION_SHARED);

        // // // Life-cycle (constructors, factory methods)

        protected BoundedPool(int capacityAsId) {
            super(capacityAsId);
        }

        public static BoundedPool construct(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be > 0, was: "+capacity);
            }
            return new BoundedPool(capacity);
        }

        @Override
        public ApacheCodecRecycler createPooled() {
            return new ApacheCodecRecycler();
        }

        // // // JDK serialization support

        // Make sure to re-link to global/shared or non-shared.
        protected Object readResolve() {
            return _resolveToShared(GLOBAL).orElseGet(() -> construct(_serialization));
        }
    }
}
