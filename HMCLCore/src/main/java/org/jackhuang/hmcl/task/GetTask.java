/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.ByteBufferUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * @author huangyuhui
 */
public final class GetTask extends FetchTask<String> {

    public GetTask(String uri) {
        this(NetworkUtils.toURI(uri));
    }

    public GetTask(URI url) {
        this(List.of(url));
        setName(url.toString());
    }

    public GetTask(List<URI> url) {
        super(url);
        setName(url.get(0).toString());
    }

    @Override
    protected EnumCheckETag shouldCheckETag() {
        return EnumCheckETag.CHECK_E_TAG;
    }

    @Override
    protected void useCachedResult(Path cachedFile) throws IOException {
        setResult(Files.readString(cachedFile));
    }

    @Override
    protected Context getContext(HttpResponse<?> response, boolean checkETag, String bmclapiHash) {
        return new Context() {
            private byte[] bytes;
            private int count;

            @Override
            public void init() throws IOException {
                long length = -1;
                if (response != null)
                    length = response.headers().firstValueAsLong("content-length").orElse(-1L);
                bytes = new byte[length <= 0 ? 8192 : (int) length];
            }

            @Override
            public void accept(List<ByteBuffer> buffers) throws IOException {
                if (bytes == null)
                    return;

                long remaining = ByteBufferUtils.getRemaining(buffers);
                if (remaining <= 0)
                    return;

                long minCap = count + remaining;
                if (minCap <= bytes.length) {
                    if (minCap > Integer.MAX_VALUE - 8)
                        throw new OutOfMemoryError("Too large min capacity: " + minCap);

                    int newSize = (int) Math.max(minCap, Math.min((long) bytes.length * 2, Integer.MAX_VALUE - 8));
                    bytes = new byte[newSize];
                }

                for (ByteBuffer buffer : buffers) {
                    int n = buffer.remaining();
                    buffer.get(bytes, count, n);
                    count += n;
                }
            }

            @Override
            public void onComplete(boolean success) throws IOException {
                if (!success) return;

                Charset charset = StandardCharsets.UTF_8;
                if (response != null)
                    charset = NetworkUtils.getCharsetFromContentType(response.headers().firstValue("content-type").orElse(null));

                String result = new String(bytes, charset);
                setResult(result);

                if (checkETag) {
                    repository.cacheText(response, result);
                }
            }
        };
    }

    public <T> Task<T> thenGetJsonAsync(Class<T> type) {
        return thenGetJsonAsync(TypeToken.get(type));
    }

    public <T> Task<T> thenGetJsonAsync(TypeToken<T> type) {
        return thenApplyAsync(jsonString -> JsonUtils.fromNonNullJson(jsonString, type));
    }
}
