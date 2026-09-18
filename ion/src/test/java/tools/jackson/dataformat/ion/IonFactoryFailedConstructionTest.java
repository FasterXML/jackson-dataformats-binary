package tools.jackson.dataformat.ion;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.core.*;
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

    private File _tempIonFile(String name) throws IOException {
        Path p = _tempDir.resolve(name);
        Files.write(p, BINARY_INT_0);
        return p.toFile();
    }

    private IonSystem failingIonSystem() {
        return (IonSystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonSystem.class }, (proxy, method, args) -> {
                    if ("newReader".equals(method.getName())
                            && (args != null) && (args.length == 1)
                            && (args[0] instanceof InputStream)) {
                        return failingIonReader((InputStream) args[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private IonReader failingIonReader(InputStream in) {
        return (IonReader) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IonReader.class }, (proxy, method, args) -> {
                    if ("getType".equals(method.getName())) {
                        throw new IllegalStateException(CREATE_FAIL);
                    }
                    if ("close".equals(method.getName())) {
                        in.close();
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
}
