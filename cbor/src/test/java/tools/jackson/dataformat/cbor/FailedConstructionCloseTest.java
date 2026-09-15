package tools.jackson.dataformat.cbor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.core.*;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.io.InputDecorator;
import tools.jackson.core.io.OutputDecorator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that streams Jackson itself opens -- for {@link File} /
 * {@link Path} sources and targets, or via {@link InputDecorator} for
 * {@code byte[]} sources -- get closed, exactly once, if construction of
 * parser or generator fails after the stream was opened.
 * Failure may occur in a user-provided decorator (before CBOR backend is
 * entered at all), in backend's own {@code _createParser()} /
 * {@code _createGenerator()}, or -- for parsers -- on the initial read
 * that {@link CBORParserBootstrapper} performs during construction.
 *<p>
 * CBOR inherits the handling from {@code BinaryTSFactory}; these tests
 * verify that the actual backend gets it (see
 * <a href="https://github.com/FasterXML/jackson-core/pull/1692">jackson-core#1692</a>
 * for generators and
 * <a href="https://github.com/FasterXML/jackson-core/pull/1693">jackson-core#1693</a>
 * for parsers).
 */
@SuppressWarnings("serial")
public class FailedConstructionCloseTest extends CBORTestBase
{
    private final static String DECORATOR_FAIL = "Test-induced decorator failure";

    private final static String CREATE_FAIL = "Test-induced construction failure";

    private final static String READ_FAIL = "Will not read, ever!";

    /**
     * Where construction is to fail.
     */
    enum Failure {
        /** No failure: sanity check that tracking works and nothing closes twice */
        NONE,
        /** Fail before backend sees stream at all (decorator failure) */
        IN_DECORATOR,
        /** Fail in backend's {@code _createParser()} / {@code _createGenerator()} */
        IN_CREATE,
        /** Fail on the initial read that CBOR bootstrapper does on construction */
        ON_READ
    }

    static class CloseTrackingInputStream extends FilterInputStream {
        public int closeCount;

        CloseTrackingInputStream(InputStream in) { super(in); }

        @Override
        public void close() throws IOException {
            ++closeCount;
            super.close();
        }
    }

    static class CloseTrackingOutputStream extends FilterOutputStream {
        public int closeCount;

        CloseTrackingOutputStream(OutputStream out) { super(out); }

        @Override
        public void close() throws IOException {
            ++closeCount;
            super.close();
        }
    }

    static class UnreadableInputStream extends InputStream {
        @Override
        public int read() throws IOException { throw new IOException(READ_FAIL); }
    }

    static class FailingInputDecorator extends InputDecorator {
        @Override
        public InputStream decorate(IOContext ctxt, InputStream in) {
            throw new IllegalStateException(DECORATOR_FAIL);
        }

        @Override
        public InputStream decorate(IOContext ctxt, byte[] src, int offset, int length) {
            throw new IllegalStateException(DECORATOR_FAIL);
        }

        @Override
        public Reader decorate(IOContext ctxt, Reader r) {
            throw new IllegalStateException(DECORATOR_FAIL);
        }
    }

    static class FailingOutputDecorator extends OutputDecorator {
        @Override
        public OutputStream decorate(IOContext ctxt, OutputStream out) {
            throw new IllegalStateException(DECORATOR_FAIL);
        }

        @Override
        public Writer decorate(IOContext ctxt, Writer w) {
            throw new IllegalStateException(DECORATOR_FAIL);
        }
    }

    /**
     * Decorator that creates {@link InputStream} for {@code byte[]} source: stream
     * is created by decorator, not caller, so factory must close it on failure.
     */
    static class ByteArraySourceDecorator extends InputDecorator {
        public final List<CloseTrackingInputStream> sources = new ArrayList<>();

        @Override
        public InputStream decorate(IOContext ctxt, InputStream in) { return in; }

        @Override
        public InputStream decorate(IOContext ctxt, byte[] src, int offset, int length) {
            CloseTrackingInputStream wrapped = new CloseTrackingInputStream(
                    new ByteArrayInputStream(src, offset, length));
            sources.add(wrapped);
            return wrapped;
        }

        @Override
        public Reader decorate(IOContext ctxt, Reader r) { return r; }
    }

    /**
     * {@link CBORFactory} that tracks streams it opens for {@link File} /
     * {@link Path} sources and targets, and optionally fails construction
     * at the requested point.
     */
    static class TrackingCBORFactory extends CBORFactory
    {
        public final List<CloseTrackingInputStream> sources = new ArrayList<>();
        public final List<CloseTrackingOutputStream> targets = new ArrayList<>();

        private final Failure _failure;

        TrackingCBORFactory(CBORFactoryBuilder b, Failure failure) {
            super(b);
            _failure = failure;
        }

        @Override
        protected InputStream _fileInputStream(File f) {
            return _track(super._fileInputStream(f));
        }

        @Override
        protected InputStream _pathInputStream(Path p) {
            return _track(super._pathInputStream(p));
        }

        @Override
        protected OutputStream _fileOutputStream(File f) {
            return _track(super._fileOutputStream(f));
        }

        @Override
        protected OutputStream _pathOutputStream(Path p) {
            return _track(super._pathOutputStream(p));
        }

        private InputStream _track(InputStream in) {
            if (_failure == Failure.ON_READ) {
                in = new UnreadableInputStream();
            }
            CloseTrackingInputStream wrapped = new CloseTrackingInputStream(in);
            sources.add(wrapped);
            return wrapped;
        }

        private OutputStream _track(OutputStream out) {
            CloseTrackingOutputStream wrapped = new CloseTrackingOutputStream(out);
            targets.add(wrapped);
            return wrapped;
        }

        @Override
        protected CBORParser _createParser(ObjectReadContext readCtxt, IOContext ioCtxt,
                InputStream in) {
            if (_failure == Failure.IN_CREATE) {
                throw new IllegalStateException(CREATE_FAIL);
            }
            return super._createParser(readCtxt, ioCtxt, in);
        }

        @Override
        protected JsonGenerator _createGenerator(ObjectWriteContext writeCtxt,
                IOContext ioCtxt, OutputStream out) {
            if (_failure == Failure.IN_CREATE) {
                throw new IllegalStateException(CREATE_FAIL);
            }
            return super._createGenerator(writeCtxt, ioCtxt, out);
        }
    }

    @TempDir
    Path tempDir;

    /*
    /**********************************************************************
    /* Test methods: parser construction
    /**********************************************************************
     */

    @Test
    public void testParserFileSourceClosedOnDecoratorFailure() throws Exception {
        _verifyParserFileSourceClosed(Failure.IN_DECORATOR,
                IllegalStateException.class, DECORATOR_FAIL);
    }

    @Test
    public void testParserPathSourceClosedOnDecoratorFailure() throws Exception {
        _verifyParserPathSourceClosed(Failure.IN_DECORATOR,
                IllegalStateException.class, DECORATOR_FAIL);
    }

    @Test
    public void testParserFileSourceClosedOnCreateFailure() throws Exception {
        _verifyParserFileSourceClosed(Failure.IN_CREATE,
                IllegalStateException.class, CREATE_FAIL);
    }

    @Test
    public void testParserPathSourceClosedOnCreateFailure() throws Exception {
        _verifyParserPathSourceClosed(Failure.IN_CREATE,
                IllegalStateException.class, CREATE_FAIL);
    }

    // Failure inside the real backend: `CBORParserBootstrapper.constructParser()`
    // reads the first byte, so an unreadable stream fails without any override
    @Test
    public void testParserFileSourceClosedOnReadFailure() throws Exception {
        _verifyParserFileSourceClosed(Failure.ON_READ,
                JacksonException.class, READ_FAIL);
    }

    @Test
    public void testParserPathSourceClosedOnReadFailure() throws Exception {
        _verifyParserPathSourceClosed(Failure.ON_READ,
                JacksonException.class, READ_FAIL);
    }

    // Stream that decorator creates from `byte[]` source is Jackson's to close too
    @Test
    public void testParserByteArraySourceClosedOnCreateFailure() throws Exception {
        ByteArraySourceDecorator dec = new ByteArraySourceDecorator();
        TrackingCBORFactory f = new TrackingCBORFactory(
                CBORFactory.builder().inputDecorator(dec), Failure.IN_CREATE);
        final byte[] src = cborDoc("{\"a\":1}");
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createParser(ObjectReadContext.empty(), src));
        verifyException(e, CREATE_FAIL);
        _verifyClosedOnce(dec.sources, "InputStream from InputDecorator");
    }

    // Sanity check: on success, source is open until parser closed, then closed once
    @Test
    public void testParserFileSourceClosedOnceOnSuccess() throws Exception {
        TrackingCBORFactory f = _factory(Failure.NONE);
        try (JsonParser p = f.createParser(ObjectReadContext.empty(), _tempFile())) {
            assertEquals(1, f.sources.size());
            assertEquals(0, f.sources.get(0).closeCount);
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
        _verifyClosedOnce(f.sources, "InputStream for File");
    }

    /*
    /**********************************************************************
    /* Test methods: generator construction
    /**********************************************************************
     */

    @Test
    public void testGeneratorFileTargetClosedOnDecoratorFailure() throws Exception {
        _verifyGeneratorFileTargetClosed(Failure.IN_DECORATOR, DECORATOR_FAIL);
    }

    @Test
    public void testGeneratorPathTargetClosedOnDecoratorFailure() throws Exception {
        _verifyGeneratorPathTargetClosed(Failure.IN_DECORATOR, DECORATOR_FAIL);
    }

    @Test
    public void testGeneratorFileTargetClosedOnCreateFailure() throws Exception {
        _verifyGeneratorFileTargetClosed(Failure.IN_CREATE, CREATE_FAIL);
    }

    @Test
    public void testGeneratorPathTargetClosedOnCreateFailure() throws Exception {
        _verifyGeneratorPathTargetClosed(Failure.IN_CREATE, CREATE_FAIL);
    }

    // Sanity check: on success, target is open until generator closed, then closed
    // once, and content actually made it to the file
    @Test
    public void testGeneratorFileTargetClosedOnceOnSuccess() throws Exception {
        TrackingCBORFactory f = _factory(Failure.NONE);
        File dst = _tempFile();
        try (JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), dst, JsonEncoding.UTF8)) {
            assertEquals(1, f.targets.size());
            assertEquals(0, f.targets.get(0).closeCount);
            g.writeStartObject();
            g.writeNumberProperty("a", 1);
            g.writeEndObject();
        }
        _verifyClosedOnce(f.targets, "OutputStream for File");
        assertArrayEquals(cborDoc("{\"a\":1}"), Files.readAllBytes(dst.toPath()));
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private TrackingCBORFactory _factory(Failure failure) {
        CBORFactoryBuilder b = CBORFactory.builder();
        if (failure == Failure.IN_DECORATOR) {
            b = b.inputDecorator(new FailingInputDecorator())
                    .outputDecorator(new FailingOutputDecorator());
        }
        return new TrackingCBORFactory(b, failure);
    }

    private void _verifyParserFileSourceClosed(Failure failure,
            Class<? extends Exception> expType, String expFailMsg)
        throws Exception
    {
        TrackingCBORFactory f = _factory(failure);
        final File src = _tempFile();
        Exception e = assertThrows(expType,
                () -> f.createParser(ObjectReadContext.empty(), src));
        verifyException(e, expFailMsg);
        _verifyClosedOnce(f.sources, "InputStream for File");
    }

    private void _verifyParserPathSourceClosed(Failure failure,
            Class<? extends Exception> expType, String expFailMsg)
        throws Exception
    {
        TrackingCBORFactory f = _factory(failure);
        final Path src = _tempFile().toPath();
        Exception e = assertThrows(expType,
                () -> f.createParser(ObjectReadContext.empty(), src));
        verifyException(e, expFailMsg);
        _verifyClosedOnce(f.sources, "InputStream for Path");
    }

    private void _verifyGeneratorFileTargetClosed(Failure failure, String expFailMsg)
        throws Exception
    {
        TrackingCBORFactory f = _factory(failure);
        final File dst = _tempFile();
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createGenerator(ObjectWriteContext.empty(), dst, JsonEncoding.UTF8));
        verifyException(e, expFailMsg);
        _verifyClosedOnce(f.targets, "OutputStream for File");
    }

    private void _verifyGeneratorPathTargetClosed(Failure failure, String expFailMsg)
        throws Exception
    {
        TrackingCBORFactory f = _factory(failure);
        final Path dst = _tempFile().toPath();
        Exception e = assertThrows(IllegalStateException.class,
                () -> f.createGenerator(ObjectWriteContext.empty(), dst, JsonEncoding.UTF8));
        verifyException(e, expFailMsg);
        _verifyClosedOnce(f.targets, "OutputStream for Path");
    }

    private void _verifyClosedOnce(List<? extends Closeable> streams, String desc) {
        assertEquals(1, streams.size(), "Should have opened exactly one "+desc);
        Closeable s = streams.get(0);
        int closeCount = (s instanceof CloseTrackingInputStream)
                ? ((CloseTrackingInputStream) s).closeCount
                : ((CloseTrackingOutputStream) s).closeCount;
        // Exactly once: not closing leaks, closing twice may fail for non-idempotent
        // streams (and add bogus suppressed exceptions)
        assertEquals(1, closeCount, desc+" Jackson opened should have been closed exactly once");
    }

    private File _tempFile() throws IOException {
        Path p = Files.createTempFile(tempDir, "cbor-close-test", ".cbor");
        Files.write(p, cborDoc("{\"a\":1}"));
        return p.toFile();
    }
}
