/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Download a file to cache repository.
 *
 * @author Glavo
 */
public final class CacheFileTask extends FetchTask<Path> {

    public CacheFileTask(@NotNull String uri) {
        this(NetworkUtils.toURI(uri));
    }

    public CacheFileTask(@NotNull URI uri) {
        super(List.of(uri));
        setName(uri.toString());

        if (!NetworkUtils.isHttpUri(uri))
            throw new IllegalArgumentException(uri.toString());
    }

    @Override
    protected EnumCheckETag shouldCheckETag() {
        // Check cache
        for (URI uri : uris) {
            try {
                setResult(repository.getCachedRemoteFile(uri, true));
                LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                return EnumCheckETag.CACHED;
            } catch (CacheRepository.CacheExpiredException e) {
                LOG.info("Cache expired for " + NetworkUtils.dropQuery(uri));
            } catch (IOException ignored) {
            }
        }
        return EnumCheckETag.CHECK_E_TAG;
    }

    @Override
    protected void useCachedResult(Path cache) {
        setResult(cache);
    }

    @Override
    protected Context getContext(HttpResponse<?> response, boolean checkETag, String bmclapiHash) throws IOException {
        assert checkETag;
        assert response != null;

        return new Context() {
            Path temp;
            FileChannel channel;

            @Override
            public void init() throws IOException {
                temp = Files.createTempFile("hmcl-download-", null);
                channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            @Override
            public void accept(List<ByteBuffer> buffers) throws IOException {
                if (channel == null)
                    return;

                //noinspection ResultOfMethodCallIgnored
                channel.write(buffers.toArray(ByteBuffer[]::new));
            }

            @Override
            public void onComplete(boolean success) throws IOException {
                if (channel == null)
                    return;

                try {
                    channel.close();
                } catch (IOException e) {
                    LOG.warning("Failed to close file: " + temp, e);
                }

                try {
                    if (success)
                        setResult(repository.cacheRemoteFile(response, temp));
                } finally {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException e) {
                        LOG.warning("Failed to delete file: " + temp, e);
                    }
                }
            }
        };
    }
}
