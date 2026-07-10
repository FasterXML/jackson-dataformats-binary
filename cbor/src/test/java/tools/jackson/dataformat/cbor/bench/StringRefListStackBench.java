package tools.jackson.dataformat.cbor.bench;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone micro-benchmark comparing {@code Stack}-backed vs {@code ArrayDeque}-backed
 * implementations of CBORParser's {@code StringRefListStack}, under a push/peek/pop
 * pattern representative of parsing nested CBOR structures with string references.
 *
 * Run directly, e.g.:
 *   javac -d /tmp/bench StringRefListStackBench.java
 *   java -cp /tmp/bench tools.jackson.dataformat.cbor.bench.StringRefListStackBench
 *
 * Note: with default JIT settings this shows no difference, because the stack instance
 * here is a fully method-local variable, so escape analysis proves it never leaves the
 * thread and elides {@code Stack}/{@code Vector}'s {@code synchronized} entirely. In
 * {@code CBORParser}, {@code _stringRefs} is an instance field touched from many large
 * methods, which defeats escape analysis in practice, leaving the synchronization (and
 * its {@code Vector.size()} calls inside push/pop/peek/empty) real and paid on every
 * call. To see that cost here, disable escape analysis:
 *   java -XX:-DoEscapeAnalysis -cp /tmp/bench tools.jackson.dataformat.cbor.bench.StringRefListStackBench
 * which shows Stack ~3.2-3.6x slower than ArrayDeque, single- and multi-threaded alike.
 */
public class StringRefListStackBench {

    static final class StringRefList {
        final int depth;
        final List<String> stringRefs = new ArrayList<>();
        StringRefList(int depth) { this.depth = depth; }
    }

    // --- Stack-backed variant (pre-change) ---
    static final class StackImpl {
        private final Stack<StringRefList> _stringRefs = new Stack<>();
        private int _nestedDepth = 0;

        void push(boolean hasNamespace) {
            if (hasNamespace) {
                _stringRefs.push(new StringRefList(_nestedDepth));
            }
            ++_nestedDepth;
        }

        void pop() {
            --_nestedDepth;
            if (!_stringRefs.empty() && _stringRefs.peek().depth == _nestedDepth) {
                _stringRefs.pop();
            }
        }

        StringRefList peek() {
            return _stringRefs.peek();
        }

        boolean empty() {
            return _stringRefs.empty();
        }
    }

    // --- ArrayDeque-backed variant (post-change) ---
    static final class ArrayDequeImpl {
        private final ArrayDeque<StringRefList> _stringRefs = new ArrayDeque<>();
        private int _nestedDepth = 0;

        void push(boolean hasNamespace) {
            if (hasNamespace) {
                _stringRefs.push(new StringRefList(_nestedDepth));
            }
            ++_nestedDepth;
        }

        void pop() {
            --_nestedDepth;
            if (!_stringRefs.isEmpty() && _stringRefs.peek().depth == _nestedDepth) {
                _stringRefs.pop();
            }
        }

        StringRefList peek() {
            return _stringRefs.peek();
        }

        boolean empty() {
            return _stringRefs.isEmpty();
        }
    }

    // Roughly 1-in-4 nesting levels open a string-ref namespace, matching typical CBOR
    // documents where only some containers carry the "stringref" tag.
    private static long runStack(int depth, int iterations) {
        StackImpl impl = new StackImpl();
        long sink = 0;
        for (int it = 0; it < iterations; it++) {
            for (int d = 0; d < depth; d++) {
                impl.push(d % 4 == 0);
                if (!impl.empty()) {
                    impl.peek().stringRefs.add("x");
                    sink += impl.peek().stringRefs.size();
                }
            }
            for (int d = 0; d < depth; d++) {
                impl.pop();
            }
        }
        return sink;
    }

    private static long runArrayDeque(int depth, int iterations) {
        ArrayDequeImpl impl = new ArrayDequeImpl();
        long sink = 0;
        for (int it = 0; it < iterations; it++) {
            for (int d = 0; d < depth; d++) {
                impl.push(d % 4 == 0);
                if (!impl.empty()) {
                    impl.peek().stringRefs.add("x");
                    sink += impl.peek().stringRefs.size();
                }
            }
            for (int d = 0; d < depth; d++) {
                impl.pop();
            }
        }
        return sink;
    }

    private static double time(Runnable r, int repeats) {
        // discard first run (JIT warmup), keep best-of-`repeats` for the rest
        r.run();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < repeats; i++) {
            long start = System.nanoTime();
            r.run();
            long elapsed = System.nanoTime() - start;
            best = Math.min(best, elapsed);
        }
        return best / 1_000_000.0;
    }

    // Each thread owns its own stack instance (mirrors real usage: one CBORParser,
    // one StringRefListStack, per thread) — measures per-thread synchronized-monitor
    // cost multiplied by concurrent core contention, not object sharing.
    private static double timeConcurrent(int threadCount, int depth, int iterations,
            boolean useStack, int repeats) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        long best = Long.MAX_VALUE;
        try {
            for (int r = 0; r < repeats + 1; r++) { // +1: discard first as warmup
                CountDownLatch ready = new CountDownLatch(threadCount);
                CountDownLatch go = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(threadCount);
                AtomicLong sink = new AtomicLong();
                for (int t = 0; t < threadCount; t++) {
                    pool.submit(() -> {
                        ready.countDown();
                        try {
                            go.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        sink.addAndGet(useStack ? runStack(depth, iterations)
                                : runArrayDeque(depth, iterations));
                        done.countDown();
                    });
                }
                ready.await();
                long start = System.nanoTime();
                go.countDown();
                done.await();
                long elapsed = System.nanoTime() - start;
                if (r > 0) { // ignore warmup round
                    best = Math.min(best, elapsed);
                }
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
        return best / 1_000_000.0;
    }

    public static void main(String[] args) throws InterruptedException {
        int depth = 32;
        int iterations = 200_000;
        int warmupRounds = 5;
        int measuredRuns = 10;

        // JIT warmup
        for (int i = 0; i < warmupRounds; i++) {
            runStack(depth, iterations);
            runArrayDeque(depth, iterations);
        }

        double stackMs = time(() -> runStack(depth, iterations), measuredRuns);
        double dequeMs = time(() -> runArrayDeque(depth, iterations), measuredRuns);

        System.out.println("=== single-threaded ===");
        System.out.printf("depth=%d iterations=%d%n", depth, iterations);
        System.out.printf("Stack     best time: %.2f ms%n", stackMs);
        System.out.printf("ArrayDeque best time: %.2f ms%n", dequeMs);
        System.out.printf("Speedup (Stack/ArrayDeque): %.2fx%n", stackMs / dequeMs);

        int availableCores = Runtime.getRuntime().availableProcessors();
        for (int threadCount : new int[] { 2, 4, availableCores }) {
            double stackConcurrentMs = timeConcurrent(threadCount, depth, iterations, true, measuredRuns);
            double dequeConcurrentMs = timeConcurrent(threadCount, depth, iterations, false, measuredRuns);

            System.out.println();
            System.out.printf("=== concurrent, %d threads (cores=%d) ===%n", threadCount, availableCores);
            System.out.printf("Stack     best time: %.2f ms%n", stackConcurrentMs);
            System.out.printf("ArrayDeque best time: %.2f ms%n", dequeConcurrentMs);
            System.out.printf("Speedup (Stack/ArrayDeque): %.2fx%n", stackConcurrentMs / dequeConcurrentMs);
        }
    }
}
