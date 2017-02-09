package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.ResolvingDecoder;
import org.apache.avro.io.parsing.Symbol;
import org.apache.avro.util.WeakIdentityHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Simple helper class that contains extracted functionality for simple
 * encoder/decoder recycling.
 *
 * @since 2.8.7
 */
public final class CodecRecycler {

	private static final ThreadLocal<Map<Schema, Map<Schema, Symbol>>> SYMBOL_CACHE;
	protected final static DecoderFactory DECODER_FACTORY = DecoderFactory.get();
	protected final static EncoderFactory ENCODER_FACTORY = EncoderFactory.get();
	protected final static Constructor<ResolvingDecoder> RESOLVING_DECODER_CONSTRUCTOR;

	protected final static ThreadLocal<SoftReference<CodecRecycler>> _recycler = new ThreadLocal<SoftReference<CodecRecycler>>();

	static {
		SYMBOL_CACHE = new ThreadLocal<Map<Schema, Map<Schema, Symbol>>>() {
			protected Map<Schema, Map<Schema, Symbol>> initialValue() {
				return new WeakIdentityHashMap<Schema, Map<Schema, Symbol>>();
			}
		};
		try {
			RESOLVING_DECODER_CONSTRUCTOR = ResolvingDecoder.class.getDeclaredConstructor(Object.class, Decoder.class);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException(e);
		}
		RESOLVING_DECODER_CONSTRUCTOR.setAccessible(true);
	}

	private BinaryDecoder decoder;
	private BinaryEncoder encoder;

	private CodecRecycler() {
	}

	/*
	 * /********************************************************** /* Public API
	 * /**********************************************************
	 */

	public static BinaryDecoder decoder(InputStream in, boolean buffering) {
		BinaryDecoder prev = _recycler().claimDecoder();
		return buffering ? DECODER_FACTORY.binaryDecoder(in, prev) : DECODER_FACTORY.directBinaryDecoder(in, prev);
	}

	public static BinaryDecoder decoder(byte[] buffer, int offset, int len) {
		BinaryDecoder prev = _recycler().claimDecoder();
		return DECODER_FACTORY.binaryDecoder(buffer, offset, len, prev);
	}

	public static ResolvingDecoder convertingDecoder(Decoder src, Schema actual, Schema expected)
			throws JsonProcessingException {
		try {
			Map<Schema, Symbol> cache = SYMBOL_CACHE.get().get(actual);
			if (cache == null) {
				cache = new WeakIdentityHashMap<Schema, Symbol>();
				SYMBOL_CACHE.get().put(actual, cache);
			}
			Symbol symbol = cache.get(expected);
			if (symbol == null) {
				symbol = (Symbol) ResolvingDecoder.resolve(actual, expected);
				cache.put(expected, symbol);
			}
			
			try {
				return RESOLVING_DECODER_CONSTRUCTOR.newInstance(symbol, src);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new IllegalStateException(e);
			}
		} catch (IOException e) {
			throw new BadSchemaException(
					"Failed to create reader/writer-resolving Avro schema handler: " + e.getMessage(), e);
		}
	}

	public static BinaryEncoder encoder(OutputStream out, boolean buffering) {
		BinaryEncoder prev = _recycler().claimEncoder();
		return buffering ? ENCODER_FACTORY.binaryEncoder(out, prev) : ENCODER_FACTORY.directBinaryEncoder(out, prev);
	}

	public static void release(BinaryDecoder dec) {
		_recycler().decoder = (BinaryDecoder) dec;
	}

	public static void release(BinaryEncoder enc) {
		_recycler().encoder = enc;
	}

	/*
	 * /********************************************************** /* Internal
	 * per-instance methods
	 * /**********************************************************
	 */

	private static CodecRecycler _recycler() {
		SoftReference<CodecRecycler> ref = _recycler.get();
		CodecRecycler r = (ref == null) ? null : ref.get();

		if (r == null) {
			r = new CodecRecycler();
			_recycler.set(new SoftReference<CodecRecycler>(r));
		}
		return r;
	}

	private BinaryDecoder claimDecoder() {
		BinaryDecoder d = decoder;
		decoder = null;
		return d;
	}

	private BinaryEncoder claimEncoder() {
		BinaryEncoder e = encoder;
		encoder = null;
		return e;
	}

	/*
	 * /********************************************************** /* Helper
	 * class /**********************************************************
	 */

	public static class BadSchemaException extends JsonProcessingException {
		private static final long serialVersionUID = 1L;

		public BadSchemaException(String msg, Throwable src) {
			super(msg, src);
		}
	}
}
