package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.io.*;
import org.apache.avro.util.WeakIdentityHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Simple helper class that contains extracted functionality for simple
 * encoder/decoder recycling.
 *
 * @since 2.8.7
 */
public final class CodecRecycler {

	private static final ThreadLocal<Map<Schema, Map<Schema, ResolvingDecoder>>> RESOLVER_CACHE;
	protected final static DecoderFactory DECODER_FACTORY = DecoderFactory.get();

	protected final static EncoderFactory ENCODER_FACTORY = EncoderFactory.get();

	protected final static ThreadLocal<SoftReference<CodecRecycler>> _recycler = new ThreadLocal<SoftReference<CodecRecycler>>();

	static {
		RESOLVER_CACHE = new ThreadLocal<Map<Schema, Map<Schema, ResolvingDecoder>>>() {
			protected Map<Schema, Map<Schema, ResolvingDecoder>> initialValue() {
				return new WeakIdentityHashMap<Schema, Map<Schema, ResolvingDecoder>>();
			}
		};
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
			Map<Schema, ResolvingDecoder> cache = RESOLVER_CACHE.get().get(actual);
			if (cache == null) {
				cache = new WeakIdentityHashMap<Schema, ResolvingDecoder>();
				RESOLVER_CACHE.get().put(actual, cache);
			}
			ResolvingDecoder resolver = cache.get(expected);
			if (resolver == null) {
				resolver = DecoderFactory.get().resolvingDecoder(Schema.applyAliases(actual, expected), expected, null);
				cache.put(expected, resolver);
			} 
			resolver.configure(src);
			return resolver;
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
