/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.task;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jackhuang.hmcl.JavaFXLauncher;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public class FileDownloadTaskResumeTest {
    private static final byte[] CONTENT = new byte[8192];
    private static final int HALF = CONTENT.length / 2;

    static {
        for (int i = 0; i < CONTENT.length; i++) {
            CONTENT[i] = (byte) (i * 31);
        }
    }

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void initToolkit() {
        JavaFXLauncher.start();
        FetchTask.notifyInitialized();
    }

    @BeforeEach
    public void setUpRepository() {
        CacheRepository repository = new CacheRepository();
        repository.changeDirectory(tempDir.resolve("common"));
        CacheRepository.setInstance(repository);
    }

    @Test
    public void retriesResumeWithIntegrityCheck() throws Exception {
        String sha1 = DigestUtils.digestToString(CacheRepository.SHA1, CONTENT);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> rangeHeader = new AtomicReference<>();
        AtomicReference<String> ifRangeHeader = new AtomicReference<>();

        try (TestServer server = new TestServer(tempDir, "/integrity", exchange -> {
            int request = requests.incrementAndGet();
            rangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
            ifRangeHeader.set(exchange.getRequestHeaders().getFirst("If-Range"));
            if (request == 1) {
                sendFull(exchange, CONTENT, null, null, null);
                return;
            }

            assertEquals(2, request);
            sendPartial(exchange, CONTENT, HALF, null, null, null);
        })) {
            Path destination = tempDir.resolve("integrity.bin");
            FileDownloadTask task = new FaultyFileDownloadTask(server.uri("/integrity"), destination, HALF,
                    new FileDownloadTask.IntegrityCheck(CacheRepository.SHA1, sha1));
            task.setRetry(2);

            task.run();

            assertArrayEquals(CONTENT, Files.readAllBytes(destination));
            assertEquals(2, requests.get());
            assertEquals("bytes=" + HALF + "-", rangeHeader.get());
            assertNull(ifRangeHeader.get());
            assertFalse(Files.exists(partial(destination)));
        }
    }

    @Test
    public void retriesResumeWhenBmclapiHashIsStable() throws Exception {
        String sha1 = DigestUtils.digestToString(CacheRepository.SHA1, CONTENT);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> rangeHeader = new AtomicReference<>();
        AtomicReference<String> ifRangeHeader = new AtomicReference<>();

        try (TestServer server = new TestServer(tempDir, "/bmclapi", exchange -> {
            int request = requests.incrementAndGet();
            rangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
            ifRangeHeader.set(exchange.getRequestHeaders().getFirst("If-Range"));
            if (request == 1) {
                sendFull(exchange, CONTENT, null, null, sha1);
                return;
            }

            assertEquals(2, request);
            sendPartial(exchange, CONTENT, HALF, null, null, sha1);
        })) {
            Path destination = tempDir.resolve("bmclapi.bin");
            FileDownloadTask task = new FaultyFileDownloadTask(server.uri("/bmclapi"), destination, HALF);
            task.setRetry(2);

            task.run();

            assertArrayEquals(CONTENT, Files.readAllBytes(destination));
            assertEquals(2, requests.get());
            assertEquals("bytes=" + HALF + "-", rangeHeader.get());
            assertNull(ifRangeHeader.get());
        }
    }

    @Test
    public void retriesResumeWithIfRangeWhenValidatorExists() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> rangeHeader = new AtomicReference<>();
        AtomicReference<String> ifRangeHeader = new AtomicReference<>();

        try (TestServer server = new TestServer(tempDir, "/etag", exchange -> {
            int request = requests.incrementAndGet();
            rangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
            ifRangeHeader.set(exchange.getRequestHeaders().getFirst("If-Range"));
            if (request == 1) {
                sendFull(exchange, CONTENT, "\"v1\"", null, null);
                return;
            }

            assertEquals(2, request);
            sendPartial(exchange, CONTENT, HALF, "\"v1\"", null, null);
        })) {
            Path destination = tempDir.resolve("etag.bin");
            FileDownloadTask task = new FaultyFileDownloadTask(server.uri("/etag"), destination, HALF);
            task.setRetry(2);

            task.run();

            assertArrayEquals(CONTENT, Files.readAllBytes(destination));
            assertEquals(2, requests.get());
            assertEquals("bytes=" + HALF + "-", rangeHeader.get());
            assertEquals("\"v1\"", ifRangeHeader.get());
        }
    }

    @Test
    public void fallsBackToFullDownloadWhenRangeIsIgnored() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> secondRangeHeader = new AtomicReference<>();

        try (TestServer server = new TestServer(tempDir, "/fallback", exchange -> {
            int request = requests.incrementAndGet();
            if (request == 1) {
                sendFull(exchange, CONTENT, "\"v1\"", null, null);
                return;
            }

            secondRangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
            assertEquals(2, request);
            sendFull(exchange, CONTENT, "\"v1\"", null, null);
        })) {
            Path destination = tempDir.resolve("fallback.bin");
            FileDownloadTask task = new FaultyFileDownloadTask(server.uri("/fallback"), destination, HALF);
            task.setRetry(2);

            task.run();

            assertArrayEquals(CONTENT, Files.readAllBytes(destination));
            assertEquals(CONTENT.length, Files.size(destination));
            assertEquals(2, requests.get());
            assertEquals("bytes=" + HALF + "-", secondRangeHeader.get());
            assertFalse(Files.exists(partial(destination)));
        }
    }

    @Test
    public void finalizesExistingPartialFileOnRangeNotSatisfiable() throws Exception {
        String sha1 = DigestUtils.digestToString(CacheRepository.SHA1, CONTENT);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> secondRangeHeader = new AtomicReference<>();

        try (TestServer server = new TestServer(tempDir, "/range-416", exchange -> {
            int request = requests.incrementAndGet();
            if (request == 1) {
                sendTruncated(exchange, CONTENT, CONTENT.length, CONTENT.length + 8);
                return;
            }

            secondRangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
            assertEquals(2, request);
            exchange.getResponseHeaders().add("Content-Range", "bytes */" + CONTENT.length);
            exchange.sendResponseHeaders(416, -1);
            exchange.close();
        })) {
            Path destination = tempDir.resolve("range-416.bin");
            FileDownloadTask task = new FileDownloadTask(server.uri("/range-416"), destination,
                    new FileDownloadTask.IntegrityCheck(CacheRepository.SHA1, sha1));
            task.setRetry(2);

            task.run();

            assertArrayEquals(CONTENT, Files.readAllBytes(destination));
            assertEquals(2, requests.get());
            assertEquals("bytes=" + CONTENT.length + "-", secondRangeHeader.get());
            assertFalse(Files.exists(partial(destination)));
        }
    }

    private static Path partial(Path destination) {
        return destination.resolveSibling(destination.getFileName() + ".part");
    }

    private static void sendFull(HttpExchange exchange, byte[] content, String eTag, String lastModified, String bmclapiHash) throws IOException {
        sendHeaders(exchange, 200, content.length, eTag, lastModified, bmclapiHash, null);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static void sendPartial(HttpExchange exchange, byte[] content, int offset, String eTag, String lastModified, String bmclapiHash) throws IOException {
        byte[] partial = Arrays.copyOfRange(content, offset, content.length);
        sendHeaders(exchange, 206, partial.length, eTag, lastModified, bmclapiHash,
                "bytes " + offset + "-" + (content.length - 1) + "/" + content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(partial);
        }
    }

    private static void sendTruncated(HttpExchange exchange, byte[] content, int bytesWritten, int declaredLength) throws IOException {
        sendTruncated(exchange, content, bytesWritten, declaredLength, null, null, null);
    }

    private static void sendTruncated(HttpExchange exchange, byte[] content, int bytesWritten, int declaredLength,
                                      String eTag, String lastModified, String bmclapiHash) throws IOException {
        sendHeaders(exchange, 200, declaredLength, eTag, lastModified, bmclapiHash, null);
        OutputStream output = exchange.getResponseBody();
        output.write(content, 0, bytesWritten);
        output.close();
        exchange.close();
    }

    private static void sendHeaders(HttpExchange exchange, int statusCode, long contentLength,
                                    String eTag, String lastModified, String bmclapiHash, String contentRange) throws IOException {
        if (eTag != null) {
            exchange.getResponseHeaders().add("ETag", eTag);
        }
        if (lastModified != null) {
            exchange.getResponseHeaders().add("Last-Modified", lastModified);
        }
        if (bmclapiHash != null) {
            exchange.getResponseHeaders().add("x-bmclapi-hash", bmclapiHash);
        }
        if (contentRange != null) {
            exchange.getResponseHeaders().add("Content-Range", contentRange);
        }
        exchange.sendResponseHeaders(statusCode, contentLength);
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;

        private TestServer(Path tempDir, String path, ThrowingHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, exchange -> {
                try {
                    handler.handle(exchange);
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        private String uri(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class FaultyFileDownloadTask extends FileDownloadTask {
        private final int failAfterBytes;
        private boolean injectedFailure;

        private FaultyFileDownloadTask(String uri, Path path, int failAfterBytes) {
            this(uri, path, failAfterBytes, null);
        }

        private FaultyFileDownloadTask(String uri, Path path, int failAfterBytes, IntegrityCheck integrityCheck) {
            super(uri, path, integrityCheck);
            this.failAfterBytes = failAfterBytes;
        }

        @Override
        protected Context getContext(java.net.http.HttpResponse<?> response, boolean checkETag, String bmclapiHash) throws IOException {
            Context delegate = super.getContext(response, checkETag, bmclapiHash);
            if (injectedFailure) {
                return delegate;
            }

            return new Context() {
                private int written;

                @Override
                public void withResult(boolean success) {
                    delegate.withResult(success);
                }

                @Override
                public void write(byte[] buffer, int offset, int len) throws IOException {
                    int remaining = failAfterBytes - written;
                    if (remaining <= 0) {
                        injectedFailure = true;
                        throw new IOException("Injected test failure");
                    }

                    int current = Math.min(len, remaining);
                    delegate.write(buffer, offset, current);
                    written += current;

                    if (current < len || written >= failAfterBytes) {
                        injectedFailure = true;
                        throw new IOException("Injected test failure");
                    }
                }

                @Override
                public void close() throws IOException {
                    delegate.close();
                }
            };
        }
    }
}
