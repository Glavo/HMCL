/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests immutable snapshot publication and serialized repository mutations.
@NotNullByDefault
public final class DefaultGameRepositoryTest {
    /// Verifies that a save replaces the current value without mutating a previously returned one.
    @Test
    public void savePublishesReplacementInstance(@TempDir Path temporaryDirectory)
            throws Exception {
        GameInstanceID instanceId = new GameInstanceID("snapshot");
        GameInstanceManifest originalManifest =
                new GameInstanceManifest(instanceId).withMainClass("example.Original");
        writeManifest(temporaryDirectory, originalManifest);

        DefaultGameRepository repository =
                new DefaultGameRepository(temporaryDirectory);
        repository.refresh();

        GameInstance originalInstance = repository.getInstance(instanceId).orElseThrow();
        @Unmodifiable List<? extends GameInstance> originalList =
                repository.getInstances();
        assertThrows(UnsupportedOperationException.class, originalList::clear);

        repository.saveAsync(originalManifest.withMainClass("example.Replacement")).run();

        GameInstance replacementInstance =
                repository.getInstance(instanceId).orElseThrow();
        assertNotSame(originalInstance, replacementInstance);
        assertEquals("example.Original", originalInstance.getManifest().mainClass());
        assertEquals("example.Replacement", replacementInstance.getManifest().mainClass());
        assertEquals("example.Original", originalList.getFirst().getManifest().mainClass());
    }

    /// Stresses concurrent readers while save, refresh, and rename publish replacement snapshots.
    @Test
    public void concurrentReadersObserveCompleteSnapshots(@TempDir Path temporaryDirectory)
            throws Exception {
        GameInstanceID firstId = new GameInstanceID("first");
        GameInstanceID secondId = new GameInstanceID("second");
        writeManifest(
                temporaryDirectory,
                new GameInstanceManifest(firstId).withMainClass("example.Initial"));

        DefaultGameRepository repository =
                new DefaultGameRepository(temporaryDirectory);
        repository.refresh();

        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicBoolean finished = new AtomicBoolean();
        AtomicReference<@Nullable Throwable> readerFailure =
                new AtomicReference<>();
        try {
            CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
                GameInstanceID currentId = firstId;
                try {
                    for (int index = 0; index < 40; index++) {
                        GameInstance currentInstance =
                                repository.getInstance(currentId).orElseThrow();
                        repository.saveAsync(
                                currentInstance.getManifest()
                                        .withMainClass("example.Version" + index)).run();

                        GameInstanceID targetId =
                                currentId.equals(firstId) ? secondId : firstId;
                        if (!repository.renameInstance(currentId, targetId)) {
                            throw new AssertionError(
                                    "Rename failed at iteration " + index);
                        }
                        currentId = targetId;

                        if (index % 4 == 0) {
                            repository.refresh();
                        }
                    }
                } catch (Throwable throwable) {
                    readerFailure.compareAndSet(null, throwable);
                } finally {
                    finished.set(true);
                }
            }, executor);

            CompletableFuture<?>[] readers = new CompletableFuture<?>[4];
            for (int readerIndex = 0;
                 readerIndex < readers.length;
                 readerIndex++) {
                readers[readerIndex] = CompletableFuture.runAsync(() -> {
                    try {
                        while (!finished.get()) {
                            for (GameInstance instance : repository.getInstances()) {
                                assertEquals(
                                        instance.getId(),
                                        instance.getManifest().id());
                                assertEquals(
                                        instance.getId(),
                                        instance.getResolvedManifest().unresolved().id());
                                assertEquals(
                                        instance.getId(),
                                        instance.getResolvedManifest().launchManifest().id());
                                assertEquals(
                                        instance.getId(),
                                        instance.getResolvedManifest().standaloneManifest().id());
                                assertTrue(instance.getInstanceRoot()
                                        .endsWith(instance.getId().id()));
                                assertNotNull(instance.getRunDirectory());
                            }
                        }
                    } catch (Throwable throwable) {
                        readerFailure.compareAndSet(null, throwable);
                        finished.set(true);
                    }
                }, executor);
            }

            writer.get(30, TimeUnit.SECONDS);
            CompletableFuture.allOf(readers).get(30, TimeUnit.SECONDS);
        } finally {
            finished.set(true);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertNull(readerFailure.get());
        assertEquals(1, repository.getInstanceCount());
        assertFalse(repository.getInstances().isEmpty());
    }

    /// Writes one manifest at its conventional repository path.
    ///
    /// @param baseDirectory the repository base directory
    /// @param manifest      the manifest to write
    private static void writeManifest(
            Path baseDirectory,
            GameInstanceManifest manifest) throws Exception {
        GameRepositoryLayout layout =
                new DefaultGameRepositoryLayout(baseDirectory);
        Path json = layout.getInstanceJson(manifest.id());
        Files.createDirectories(json.getParent());
        JsonUtils.writeToJsonFile(json, manifest);
    }
}
