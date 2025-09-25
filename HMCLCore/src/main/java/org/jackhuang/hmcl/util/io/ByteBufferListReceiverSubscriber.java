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
package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/// @author Glavo
public final class ByteBufferListReceiverSubscriber implements Flow.Subscriber<List<ByteBuffer>> {

    private static final int DEFAULT_MAX_BUFFERS_IN_QUEUE = 4;
    private static final ByteBuffer LAST_BUFFER = ByteBuffer.allocate(0);
    private static final List<ByteBuffer> LAST_LIST = List.of(LAST_BUFFER);

    public static HttpResponse.BodySubscriber<Receiver> create() {
        return HttpResponse.BodySubscribers.fromSubscriber(new ByteBufferListReceiverSubscriber(), it -> it.new Receiver());
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final BlockingQueue<List<ByteBuffer>> buffers;
    private final AtomicBoolean subscribed = new AtomicBoolean();

    private volatile Flow.Subscription subscription;
    private volatile boolean closed;
    private volatile Throwable failed;

    private ByteBufferListReceiverSubscriber() {
        this(new ArrayBlockingQueue<>(DEFAULT_MAX_BUFFERS_IN_QUEUE));
    }

    private ByteBufferListReceiverSubscriber(BlockingQueue<List<ByteBuffer>> buffers) {
        this.buffers = buffers;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        assert this.subscription == null;
        this.subscription = subscription;

        try {
            if (!subscribed.compareAndSet(false, true)) {
                subscription.cancel();
            } else {
                boolean closed;

                lock.lock();
                try {
                    closed = this.closed;
                    if (!closed) {
                        this.subscription = subscription;
                    }
                } finally {
                    lock.unlock();
                }
                if (closed) {
                    subscription.cancel();
                    return;
                }
                subscription.request(Math.max(1, buffers.remainingCapacity() - 1));
            }
        } catch (Throwable t) {
            failed = t;
            try {
                close();
            } finally {
                onError(t);
            }
        }
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        try {
            if (!buffers.offer(item)) {
                throw new IllegalStateException("queue is full");
            }
        } catch (Throwable ex) {
            failed = ex;
            try {
                close();
            } finally {
                onError(ex);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        failed = Objects.requireNonNull(throwable);
        //noinspection ResultOfMethodCallIgnored
        buffers.offer(LAST_LIST);
    }

    @Override
    public void onComplete() {
        subscription = null;
        onNext(LAST_LIST);
    }

    public void close() {
        Flow.Subscription s;
        lock.lock();
        try {
            if (closed) return;
            closed = true;
            s = subscription;
            subscription = null;
        } finally {
            lock.unlock();
        }
        try {
            if (s != null) {
                s.cancel();
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            buffers.offer(LAST_LIST);
        }
    }

    public final class Receiver implements Closeable {
        private boolean finished = false;

        public @Nullable List<ByteBuffer> take() throws IOException, InterruptedException {
            if (closed || failed != null)
                throw new IOException("closed", failed);

            if (finished)
                return null;

            try {
                List<ByteBuffer> buffer = buffers.take();
                if (closed || failed != null)
                    throw new IOException("closed", failed);

                if (buffer == LAST_LIST) {
                    finished = true;
                    return null;
                }

                Flow.Subscription s = subscription;
                if (s != null)
                    s.request(1);

                return buffer;
            } catch (InterruptedException e) {
                close();
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        @Override
        public void close() {
            ByteBufferListReceiverSubscriber.this.close();
        }
    }
}
