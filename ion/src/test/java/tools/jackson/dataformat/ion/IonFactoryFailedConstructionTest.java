package tools.jackson.dataformat.ion;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.core.*;
import tools.jackson.core.io.ContentReference;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.io.InputDecorator;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.core.util.RecyclerPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IonFactoryFailedConstructionTest
{
    private final static ObjectReadContext EMPTY_READ_CTXT = ObjectReadContext.empty();
    private final static ObjectWriteContext EMPTY_WRITE_CTXT = ObjectWriteContext.empty();

    private final static String DECORATOR_FAIL = "Test-induced decorator failure";
    private final static String CREATE_FAIL = "Test-induced parser construction failure";
    private final static String GEN_CREATE_FAIL = "Test-induced generator construction failure";
    private final static String CTXT_FAIL = "Test-induced context creation failure";
    private final static String READER_FAIL = "Test-induced `IonReader` creation failure";
    private final static String CLOSE_FAIL = "Test-induced close failure";

    // 4-byte Ion 1.0 IVM followed by int 0.
    private static final byte[] BINARY_INT_0 = new byte[] {
            (byte) 0xE0, 0x01, 0x00, (byte) 0xEA, 0x20
    };

    @TempDir
    Path _tempDir;

    @Test
    void closesFileInputStreamOnDecoratorFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        TrackingIonFactory f = new TrackingIonFactory(IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .inputDecorator(new FailingInputDecorator()));

        assertEquals(0, pool.pooledCount());
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, _tempIonFile("input-file.ion")));
        assertEquals(DECORATOR_FAIL, e.getMessage());

        assertEquals(1, f.inputs.size());
        assertEquals(1, f.inputs.get(0).closeCount);
        assertEquals(1, pool.pooledCount());
    }

    @Test
    void closesPathInputStreamOnDecoratorFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        TrackingIonFactory f = new TrackingIonFactory(IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .inputDecorator(new FailingInputDecorator()));

        assertEquals(0, pool.pooledCount());
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, _tempIonFile("input-path.ion").toPath()));
        assertEquals(DECORATOR_FAIL, e.getMessage());

        assertEquals(1, f.inputs.size());
        assertEquals(1, f.inputs.get(0).closeCount);
        assertEquals(1, pool.pooledCount());
    }

    @Test
    void closesIonReaderAndReleasesContextsOnParserConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        TrackingIonFactory f = new TrackingIonFactory(IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .ionSystem(failingIonSystem()));

        assertEquals(0, pool.pooledCount());
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, _tempIonFile("input-create-fail.ion")));
        assertEquals(CREATE_FAIL, e.getMessage());

        assertEquals(1, f.inputs.size());
        assertEquals(1, f.inputs.get(0).closeCount);
        assertEquals(2, pool.pooledCount());
    }

    // [dataformats-binary#780]: caller-provided `InputStream` must NOT be closed
    // even if construction fails after `IonReader` has been created
    @Test
    void leavesCallerProvidedInputStreamOpenOnParserConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        IonFactory f = IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .ionSystem(failingIonSystem())
                .build();

        CloseTrackingInputStream in = new CloseTrackingInputStream(
                new ByteArrayInputStream(BINARY_INT_0));
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, in));
        assertEquals(CREATE_FAIL, e.getMessage());

        assertEquals(0, in.closeCount);
    }

    @Test
    void closesFileOutputStreamOnGeneratorConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        TrackingIonFactory f = new TrackingIonFactory(IonFactory.builderForTextualWriters()
                .recyclerPool(pool));

        assertEquals(0, pool.pooledCount());
        assertThrows(JacksonException.class,
                () -> f.createGenerator(EMPTY_WRITE_CTXT,
                        _tempDir.resolve("output-file.ion").toFile(),
                        JsonEncoding.UTF16_BE));

        assertEquals(1, f.outputs.size());
        assertEquals(1, f.outputs.get(0).closeCount);
        assertEquals(1, pool.pooledCount());
    }

    @Test
    void closesPathOutputStreamOnGeneratorConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        TrackingIonFactory f = new TrackingIonFactory(IonFactory.builderForTextualWriters()
                .recyclerPool(pool));

        assertEquals(0, pool.pooledCount());
        assertThrows(JacksonException.class,
                () -> f.createGenerator(EMPTY_WRITE_CTXT,
                        _tempDir.resolve("output-path.ion"),
                        JsonEncoding.UTF16_BE));

        assertEquals(1, f.outputs.size());
        assertEquals(1, f.outputs.get(0).closeCount);
        assertEquals(1, pool.pooledCount());
    }

    // [dataformats-binary#780]: `IonWriter` created before failure must be closed
    // (which closes the factory-created `OutputStream` as well)
    @Test
    void closesIonWriterOnGeneratorConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        AtomicInteger writerCloseCount = new AtomicInteger();
        GeneratorFailingIonFactory f = new GeneratorFailingIonFactory(
                IonFactory.builderForBinaryWriters()
                    .recyclerPool(pool)
                    .ionSystem(writerTrackingIonSystem(writerCloseCount)));

        assertEquals(0, pool.pooledCount());
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createGenerator(EMPTY_WRITE_CTXT,
                        _tempDir.resolve("output-writer-fail.ion").toFile(),
                        JsonEncoding.UTF8));
        assertEquals(GEN_CREATE_FAIL, e.getMessage());

        assertEquals(1, writerCloseCount.get());
        assertEquals(1, f.outputs.size());
        assertEquals(1, f.outputs.get(0).closeCount);
        assertEquals(1, pool.pooledCount());
    }

    // [dataformats-binary#780]: caller-provided `OutputStream`, on the other hand,
    // must NOT be closed on failed construction
    @Test
    void leavesCallerProvidedOutputStreamOpenOnGeneratorConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        AtomicInteger writerCloseCount = new AtomicInteger();
        GeneratorFailingIonFactory f = new GeneratorFailingIonFactory(
                IonFactory.builderForBinaryWriters()
                    .recyclerPool(pool)
                    .ionSystem(writerTrackingIonSystem(writerCloseCount)));

        CloseTrackingOutputStream out = new CloseTrackingOutputStream(
                new ByteArrayOutputStream());
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createGenerator(EMPTY_WRITE_CTXT, out, JsonEncoding.UTF8));
        assertEquals(GEN_CREATE_FAIL, e.getMessage());

        assertEquals(0, writerCloseCount.get());
        assertEquals(0, out.closeCount);
        assertEquals(1, pool.pooledCount());
    }

    // [dataformats-binary#780]: `IOContext` of non-`File`/`Path` sources was never
    // released, neither on success...
    @Test
    void releasesContextsForInputStreamSource() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        IonFactory f = IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .build();

        JsonParser p = f.createParser(EMPTY_READ_CTXT,
                new ByteArrayInputStream(BINARY_INT_0));
        // outer context released right away, parser's own one on close:
        assertEquals(1, pool.pooledCount());
        p.close();
        assertEquals(2, pool.pooledCount());
    }

    // ... nor on failure
    @Test
    void releasesContextOnDecoratorFailureForInputStreamSource() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        IonFactory f = IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .inputDecorator(new FailingInputDecorator())
                .build();

        CloseTrackingInputStream in = new CloseTrackingInputStream(
                new ByteArrayInputStream(BINARY_INT_0));
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, in));
        assertEquals(DECORATOR_FAIL, e.getMessage());

        assertEquals(0, in.closeCount);
        assertEquals(1, pool.pooledCount());
    }

    // [dataformats-binary#780]: `InputStream` created by `InputDecorator` for `byte[]`
    // input is ours, not caller's, so it must be closed on failed construction
    @Test
    void closesDecoratorCreatedStreamOnByteArrayParserConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        CloseTrackingInputStream decorated = new CloseTrackingInputStream(
                new ByteArrayInputStream(BINARY_INT_0));
        IonFactory f = IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .ionSystem(failingIonSystem())
                .inputDecorator(new StreamProvidingInputDecorator(decorated))
                .build();

        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, BINARY_INT_0));
        assertEquals(CREATE_FAIL, e.getMessage());

        assertEquals(1, decorated.closeCount);
        assertEquals(2, pool.pooledCount());
    }

    // [dataformats-binary#780]: `createGenerator(Writer)` had no failure handling at all
    @Test
    void releasesContextOnWriterGeneratorConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        GeneratorFailingIonFactory f = new GeneratorFailingIonFactory(
                IonFactory.builderForTextualWriters()
                    .recyclerPool(pool));

        CloseTrackingWriter w = new CloseTrackingWriter(new StringWriter());
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createGenerator(EMPTY_WRITE_CTXT, w));
        assertEquals(GEN_CREATE_FAIL, e.getMessage());

        // caller-provided `Writer`: left open, but context must be released
        assertEquals(0, w.closeCount);
        assertEquals(1, pool.pooledCount());
    }

    // [dataformats-binary#780]: `File`/`Path` generator paths delegate cleanup of the
    // stream to `_createGenerator()`, so failures in context creation must be covered too
    @Test
    void closesFileOutputStreamOnContextCreationFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        TrackingIonFactory f = new ContentReferenceFailingIonFactory(
                IonFactory.builderForTextualWriters()
                    .recyclerPool(pool));

        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createGenerator(EMPTY_WRITE_CTXT,
                        _tempDir.resolve("output-ctxt-fail.ion").toFile(),
                        JsonEncoding.UTF8));
        assertEquals(CTXT_FAIL, e.getMessage());

        assertEquals(1, f.outputs.size());
        assertEquals(1, f.outputs.get(0).closeCount);
    }

    // [dataformats-binary#780]: extended API -- caller-provided `IonReader` must be
    // left alone, but `IOContext` still released
    @Test
    void leavesCallerProvidedIonReaderOpenOnParserConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        IonFactory f = IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .build();
        AtomicInteger readerCloseCount = new AtomicInteger();
        IonReader r = failingIonReader(null, readerCloseCount);

        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, r));
        assertEquals(CREATE_FAIL, e.getMessage());

        assertEquals(0, readerCloseCount.get());
        assertEquals(1, pool.pooledCount());
    }

    // ... whereas `IonReader` we create over `IonValue` is ours to close
    @Test
    void closesIonReaderCreatedForIonValueOnParserConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        IonFactory f = IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .build();
        AtomicInteger readerCloseCount = new AtomicInteger();

        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, failingIonValue(readerCloseCount)));
        assertEquals(CREATE_FAIL, e.getMessage());

        assertEquals(1, readerCloseCount.get());
        assertEquals(1, pool.pooledCount());
    }

    // [dataformats-binary#780]: extended API -- caller-provided `IonWriter` likewise
    @Test
    void leavesCallerProvidedIonWriterOpenOnGeneratorConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        GeneratorFailingIonFactory f = new GeneratorFailingIonFactory(
                IonFactory.builderForTextualWriters()
                    .recyclerPool(pool));
        AtomicInteger writerCloseCount = new AtomicInteger();
        IonWriter w = countingIonWriter(
                IonSystemBuilder.standard().build().newTextWriter(new StringWriter()),
                writerCloseCount);

        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createGenerator(EMPTY_WRITE_CTXT, w));
        assertEquals(GEN_CREATE_FAIL, e.getMessage());

        assertEquals(0, writerCloseCount.get());
        assertEquals(1, pool.pooledCount());
    }

    // [dataformats-binary#780]: `Reader` we create over `char[]` / `String` is ours,
    // so the `IonReader` over it gets closed on failed construction
    @Test
    void closesIonReaderOnCharArrayParserConstructionFailure() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        AtomicInteger readerCloseCount = new AtomicInteger();
        IonFactory f = IonFactory.builderForTextualWriters()
                .recyclerPool(pool)
                .ionSystem(failingIonSystem(readerCloseCount))
                .build();

        char[] doc = "0".toCharArray();
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, doc, 0, doc.length));
        assertEquals(CREATE_FAIL, e.getMessage());
        assertEquals(1, readerCloseCount.get());

        readerCloseCount.set(0);
        e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, "0"));
        assertEquals(CREATE_FAIL, e.getMessage());
        assertEquals(1, readerCloseCount.get());
    }

    // [dataformats-binary#798]: closing `IonReader` cascades to the decorated stream;
    // if that close fails, stream Jackson opened for `File` must not be left leaked
    @Test
    void closesFileInputStreamWhenDecoratedStreamCloseFails() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        CloseFailingInputDecorator dec = new CloseFailingInputDecorator();
        TrackingIonFactory f = new TrackingIonFactory(IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .inputDecorator(dec)
                .ionSystem(failingIonSystem()));

        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT, _tempIonFile("input-close-fail.ion")));
        assertEquals(CREATE_FAIL, e.getMessage());

        // Outermost resource closed first, and it fails...
        assertEquals(1, dec.decorated.closeCount);
        assertEquals(1, e.getSuppressed().length);
        assertEquals(CLOSE_FAIL, e.getSuppressed()[0].getMessage());
        // ... so what Jackson opened gets closed directly
        assertEquals(1, f.inputs.size());
        assertEquals(1, f.inputs.get(0).closeCount);
        assertEquals(2, pool.pooledCount());
    }

    // [dataformats-binary#798]: same, but for failure before `IonReader` exists, where
    // decorated stream itself is the outermost resource
    @Test
    void closesFileInputStreamWhenDecoratedStreamCloseFailsBeforeIonReader() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        CloseFailingInputDecorator dec = new CloseFailingInputDecorator();
        TrackingIonFactory f = new TrackingIonFactory(IonFactory.builderForBinaryWriters()
                .recyclerPool(pool)
                .inputDecorator(dec)
                .ionSystem(readerFailingIonSystem()));

        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(EMPTY_READ_CTXT,
                        _tempIonFile("input-close-fail-early.ion").toPath()));
        assertEquals(READER_FAIL, e.getMessage());

        assertEquals(1, dec.decorated.closeCount);
        assertEquals(1, e.getSuppressed().length);
        assertEquals(1, f.inputs.size());
        assertEquals(1, f.inputs.get(0).closeCount);
        // no parser-owned context created yet, so just the one
        assertEquals(1, pool.pooledCount());
    }

    // [dataformats-binary#798]: and same on generator side, where closing `IonWriter`
    // is what normally cascades to the target Jackson opened
    @Test
    void closesFileOutputStreamWhenIonWriterCloseFails() throws Exception
    {
        RecyclerPool<BufferRecycler> pool = JsonRecyclerPools.newBoundedPool(5);
        GeneratorFailingIonFactory f = new GeneratorFailingIonFactory(
                IonFactory.builderForBinaryWriters()
                    .recyclerPool(pool)
                    .ionSystem(closeFailingWriterIonSystem()));

        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createGenerator(EMPTY_WRITE_CTXT,
                        _tempDir.resolve("output-close-fail.ion").toFile(),
                        JsonEncoding.UTF8));
        assertEquals(GEN_CREATE_FAIL, e.getMessage());

        assertEquals(1, e.getSuppressed().length);
        assertEquals(CLOSE_FAIL, e.getSuppressed()[0].getMessage());
        assertEquals(1, f.outputs.size());
        assertEquals(1, f.outputs.get(0).closeCount);
        assertEquals(1, pool.pooledCount());
    }

    private File _tempIonFile(String name) throws IOException {
        Path p = _tempDir.resolve(name);
        Files.write(p, BINARY_INT_0);
        return p.toFile();
    }

    private IonSystem failingIonSystem() {
        return failingIonSystem(null);
    }

    private IonSystem failingIonSystem(AtomicInteger closeCount) {
        return (IonSystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonSystem.class }, (proxy, method, args) -> {
                    if ("newReader".equals(method.getName())
                            && (args != null) && (args.length == 1)) {
                        Object src = args[0];
                        return failingIonReader((src instanceof Closeable)
                                ? (Closeable) src : null, closeCount);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private IonSystem readerFailingIonSystem() {
        return (IonSystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonSystem.class }, (proxy, method, args) -> {
                    if ("newReader".equals(method.getName())) {
                        throw new IllegalStateException(READER_FAIL);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private IonSystem closeFailingWriterIonSystem() {
        final IonSystem delegate = IonSystemBuilder.standard().build();
        return (IonSystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonSystem.class }, (proxy, method, args) -> {
                    Object result = _invoke(delegate, method, args);
                    if (result instanceof IonWriter) {
                        result = closeFailingIonWriter((IonWriter) result);
                    }
                    return result;
                });
    }

    // `IonWriter` that fails to close, leaving underlying target open
    private IonWriter closeFailingIonWriter(IonWriter delegate) {
        return (IonWriter) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonWriter.class }, (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        throw new IOException(CLOSE_FAIL);
                    }
                    return _invoke(delegate, method, args);
                });
    }

    private IonValue failingIonValue(AtomicInteger readerCloseCount) {
        final IonSystem ionSystem = failingIonSystem(readerCloseCount);
        return (IonValue) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonValue.class }, (proxy, method, args) -> {
                    if ("getSystem".equals(method.getName())) {
                        return ionSystem;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private IonSystem writerTrackingIonSystem(AtomicInteger closeCount) {
        final IonSystem delegate = IonSystemBuilder.standard().build();
        return (IonSystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonSystem.class }, (proxy, method, args) -> {
                    Object result = _invoke(delegate, method, args);
                    if (result instanceof IonWriter) {
                        result = countingIonWriter((IonWriter) result, closeCount);
                    }
                    return result;
                });
    }

    private IonWriter countingIonWriter(IonWriter delegate, AtomicInteger closeCount) {
        return (IonWriter) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonWriter.class }, (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        closeCount.incrementAndGet();
                    }
                    return _invoke(delegate, method, args);
                });
    }

    private static Object _invoke(Object delegate, Method method, Object[] args)
        throws Throwable
    {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    // NOTE: mock deliberately mirrors the real ion-java contract, in which
    // `IonReader.close()` cascades to the underlying `InputStream` / `Reader`
    // (see `IonCursorBinary.close()`, `UnifiedInputStreamX.close()`); production
    // cleanup relies on that cascade
    private IonReader failingIonReader(Closeable toClose, AtomicInteger closeCount) {
        return (IonReader) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonReader.class }, (proxy, method, args) -> {
                    if ("getType".equals(method.getName())) {
                        throw new IllegalStateException(CREATE_FAIL);
                    }
                    if ("close".equals(method.getName())) {
                        if (closeCount != null) {
                            closeCount.incrementAndGet();
                        }
                        if (toClose != null) {
                            toClose.close();
                        }
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Byte.TYPE) {
            return (byte) 0;
        }
        if (type == Short.TYPE) {
            return (short) 0;
        }
        if (type == Integer.TYPE) {
            return 0;
        }
        if (type == Long.TYPE) {
            return 0L;
        }
        if (type == Float.TYPE) {
            return 0F;
        }
        if (type == Double.TYPE) {
            return 0D;
        }
        if (type == Character.TYPE) {
            return '\0';
        }
        return null;
    }

    static class FailingInputDecorator extends InputDecorator
    {
        private static final long serialVersionUID = 1L;

        @Override
        public InputStream decorate(IOContext ctxt, InputStream in) {
            throw new IllegalStateException(DECORATOR_FAIL);
        }

        @Override
        public InputStream decorate(IOContext ctxt, byte[] src, int offset, int length) {
            return null;
        }

        @Override
        public Reader decorate(IOContext ctxt, Reader r) {
            return r;
        }
    }

    static class TrackingIonFactory extends IonFactory
    {
        private static final long serialVersionUID = 1L;

        public final List<CloseTrackingInputStream> inputs = new ArrayList<>();
        public final List<CloseTrackingOutputStream> outputs = new ArrayList<>();

        TrackingIonFactory(IonFactoryBuilder b) {
            super(b);
        }

        @Override
        protected InputStream _fileInputStream(File f) throws JacksonException {
            return _track(super._fileInputStream(f));
        }

        @Override
        protected InputStream _pathInputStream(Path p) throws JacksonException {
            return _track(super._pathInputStream(p));
        }

        private InputStream _track(InputStream in) {
            CloseTrackingInputStream wrapped = new CloseTrackingInputStream(in);
            inputs.add(wrapped);
            return wrapped;
        }

        @Override
        protected OutputStream _fileOutputStream(File f) throws JacksonException {
            return _track(super._fileOutputStream(f));
        }

        @Override
        protected OutputStream _pathOutputStream(Path p) throws JacksonException {
            return _track(super._pathOutputStream(p));
        }

        private OutputStream _track(OutputStream out) {
            CloseTrackingOutputStream wrapped = new CloseTrackingOutputStream(out);
            outputs.add(wrapped);
            return wrapped;
        }
    }

    static class ContentReferenceFailingIonFactory extends TrackingIonFactory
    {
        private static final long serialVersionUID = 1L;

        ContentReferenceFailingIonFactory(IonFactoryBuilder b) {
            super(b);
        }

        @Override
        protected ContentReference _createContentReference(Object contentRef) {
            if (contentRef instanceof OutputStream) {
                throw new IllegalStateException(CTXT_FAIL);
            }
            return super._createContentReference(contentRef);
        }
    }

    static class GeneratorFailingIonFactory extends TrackingIonFactory
    {
        private static final long serialVersionUID = 1L;

        GeneratorFailingIonFactory(IonFactoryBuilder b) {
            super(b);
        }

        // Fails after both `IonWriter` and the actual output target exist
        @Override
        protected IonGenerator _createGenerator(ObjectWriteContext writeCtxt,
                IOContext ioCtxt, IonWriter ion, boolean ionWriterIsManaged, Closeable dst) {
            throw new IllegalStateException(GEN_CREATE_FAIL);
        }
    }

    static class StreamProvidingInputDecorator extends InputDecorator
    {
        private static final long serialVersionUID = 1L;

        private final InputStream _toProvide;

        StreamProvidingInputDecorator(InputStream toProvide) {
            _toProvide = toProvide;
        }

        @Override
        public InputStream decorate(IOContext ctxt, InputStream in) {
            return in;
        }

        @Override
        public InputStream decorate(IOContext ctxt, byte[] src, int offset, int length) {
            return _toProvide;
        }

        @Override
        public Reader decorate(IOContext ctxt, Reader r) {
            return r;
        }
    }

    // Decorator whose wrapper fails to close, leaving what it wraps open
    static class CloseFailingInputDecorator extends InputDecorator
    {
        private static final long serialVersionUID = 1L;

        public CloseFailingInputStream decorated;

        @Override
        public InputStream decorate(IOContext ctxt, InputStream in) {
            decorated = new CloseFailingInputStream(in);
            return decorated;
        }

        @Override
        public InputStream decorate(IOContext ctxt, byte[] src, int offset, int length) {
            return null;
        }

        @Override
        public Reader decorate(IOContext ctxt, Reader r) {
            return r;
        }
    }

    static class CloseFailingInputStream extends FilterInputStream
    {
        public int closeCount;

        CloseFailingInputStream(InputStream in) {
            super(in);
        }

        // NOTE: deliberately does NOT close what it wraps
        @Override
        public void close() throws IOException {
            ++closeCount;
            throw new IOException(CLOSE_FAIL);
        }
    }

    static class CloseTrackingInputStream extends FilterInputStream
    {
        public int closeCount;

        CloseTrackingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            ++closeCount;
            super.close();
        }
    }

    static class CloseTrackingOutputStream extends FilterOutputStream
    {
        public int closeCount;

        CloseTrackingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            ++closeCount;
            super.close();
        }
    }

    static class CloseTrackingWriter extends FilterWriter
    {
        public int closeCount;

        CloseTrackingWriter(Writer w) {
            super(w);
        }

        @Override
        public void close() throws IOException {
            ++closeCount;
            super.close();
        }
    }
}
