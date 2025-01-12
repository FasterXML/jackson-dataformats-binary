package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class ConcurrentAsyncTest extends AsyncTestBase
{
    @Test
    public void testConcurrentHandling() throws Exception
    {
        Map<String, Map<String, String>> tags = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            Map<String, String> value = new HashMap<>();
            for (int j = 0; j < 10; j++) {
                value.put("key_" + j, "val" + j);
            }
            tags.put("elt_" + i, value);
        }

        JsonFactory jsonFactory = new SmileFactory();
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectWriter objectWriter = objectMapper.writer().with(jsonFactory);
        jsonFactory.setCodec(objectMapper);
        byte[] json = objectWriter.writeValueAsBytes(tags);
        TypeReference<Map<String, Map<String, String>>> typeReference = new TypeReference<Map<String, Map<String, String>>>() {
        };

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<CompletableFuture<?>> futures = new ArrayList<>();

        // Exact count varies but this seems to be enough to produce the problem
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            JsonParser parser = jsonFactory.createNonBlockingByteArrayParser();
            ByteArrayFeeder inputFeeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    inputFeeder.feedInput(json, 0, json.length);
                    @SuppressWarnings("resource")
                    TokenBuffer tokenBuffer = new TokenBuffer(parser);
                    while (true) {
                        JsonToken token = parser.nextToken();
                        if (token == JsonToken.NOT_AVAILABLE || token == null) {
                            break;
                        }

                        tokenBuffer.copyCurrentEvent(parser);
                    }
                    return tokenBuffer.asParser(jsonFactory.getCodec()).readValueAs(typeReference);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    try {
                        inputFeeder.endOfInput();
                        parser.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get();
    }
}
