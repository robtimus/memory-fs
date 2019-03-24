/*
 * MemoryFileStoreByteChannelTest.java
 * Copyright 2016 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.filesystems.memory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.OnCloseAction;

@SuppressWarnings({ "nls", "javadoc" })
public class MemoryFileStoreByteChannelTest {

    @Test
    public void testReadFromReadableChannelDestinationSmallerThanContent() throws IOException {
        final String content = "Hello World";
        assertEquals(11, content.length());
        final int destSize = 5;

        File file = new File();
        file.setContent(content.getBytes());

        try (SeekableByteChannel channel = file.newByteChannel(true, false, false, null)) {
            byte[] bytes = new byte[destSize];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(destSize, channel.read(buffer));
            assertArrayEquals(Arrays.copyOf(content.getBytes(), destSize), bytes);

            buffer.flip();
            assertEquals(destSize, channel.read(buffer));
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), destSize, destSize * 2), bytes);

            buffer.flip();
            assertEquals(1, channel.read(buffer));
            assertEquals(content.getBytes()[destSize * 2], bytes[0]);

            channel.position(0);
            buffer = ByteBuffer.wrap(bytes);
            assertEquals(destSize, channel.read(buffer));
            assertArrayEquals(Arrays.copyOf(content.getBytes(), destSize), bytes);

            channel.position(content.length() + 10);
            buffer.flip();
            assertEquals(-1, channel.read(buffer));
        }
    }

    @Test
    public void testReadFromReadableChannelContentSmallerThanBuffer() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        try (SeekableByteChannel channel = file.newByteChannel(true, false, false, null)) {
            byte[] bytes = new byte[content.length() + 1];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(content.length(), channel.read(buffer));
            assertArrayEquals(content.getBytes(), Arrays.copyOf(bytes, content.length()));
            assertEquals(content.length(), channel.position());
            assertEquals(-1, channel.read(buffer));

            channel.position(0);
            buffer.flip();
            assertEquals(content.length(), channel.read(buffer));
            assertArrayEquals(content.getBytes(), Arrays.copyOf(bytes, content.length()));
            assertEquals(content.length(), channel.position());
            assertEquals(-1, channel.read(buffer));

            channel.position(content.length() + 10);
            buffer.flip();
            assertEquals(-1, channel.read(buffer));
        }
    }

    @Test
    public void testReadFromReadableChannelContentLargerThanBuffer() throws IOException {
        final String content = repeatUntil("Hello World", 8192 + 10);

        File file = new File();
        file.setContent(content.getBytes());

        try (SeekableByteChannel channel = file.newByteChannel(true, false, false, null)) {
            byte[] bytes = new byte[content.length() + 1];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(content.length(), channel.read(buffer));
            assertArrayEquals(content.getBytes(), Arrays.copyOf(bytes, content.length()));
            assertEquals(content.length(), channel.position());
            assertEquals(-1, channel.read(buffer));

            channel.position(0);
            buffer.flip();
            assertEquals(content.length(), channel.read(buffer));
            assertArrayEquals(content.getBytes(), Arrays.copyOf(bytes, content.length()));
            assertEquals(content.length(), channel.position());
            assertEquals(-1, channel.read(buffer));

            channel.position(content.length() + 10);
            buffer.flip();
            assertEquals(-1, channel.read(buffer));
        }
    }

    @Test(expected = NonReadableChannelException.class)
    public void testReadFromWritableChannel() throws IOException {

        File file = new File();

        try (SeekableByteChannel channel = file.newByteChannel(false, true, false, null)) {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            channel.read(buffer);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testReadFromClosedChannel() throws IOException {

        File file = new File();

        @SuppressWarnings("resource")
        SeekableByteChannel channel = file.newByteChannel(false, true, false, null);
        channel.close();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        channel.read(buffer);
    }

    @Test
    public void testWriteToWritableChannel() throws IOException {
        final String content = "Hello World";
        final String newStart = "Goodbye";
        final String newContent = "Goodbyeorld";

        File file = new File();

        try (SeekableByteChannel channel = file.newByteChannel(false, true, false, null)) {
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());
            channel.write(buffer);
            assertArrayEquals(content.getBytes(), file.getContent());
            assertEquals(content.length(), channel.position());

            channel.position(0);
            buffer = ByteBuffer.wrap(newStart.getBytes());
            channel.write(buffer);
            assertArrayEquals(newContent.getBytes(), file.getContent());

            channel.position(100);
            buffer.flip();
            channel.write(buffer);
            assertArrayEquals(newContent.getBytes(), Arrays.copyOf(file.getContent(), newContent.length()));
            assertArrayEquals(new byte[100 - newContent.length()], Arrays.copyOfRange(file.getContent(), newContent.length(), 100));
            assertArrayEquals(newStart.getBytes(), Arrays.copyOfRange(file.getContent(), 100, 100 + newStart.length()));
        }
    }

    @Test(expected = NonWritableChannelException.class)
    public void testWriteToReadableChannel() throws IOException {

        File file = new File();

        try (SeekableByteChannel channel = file.newByteChannel(true, false, false, null)) {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            channel.write(buffer);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testWriteToClosedChannel() throws IOException {

        File file = new File();

        @SuppressWarnings("resource")
        SeekableByteChannel channel = file.newByteChannel(true, false, false, null);
        channel.close();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        channel.write(buffer);
    }

    @Test
    public void testTruncateWritableChannel() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        try (SeekableByteChannel channel = file.newByteChannel(false, true, true, null)) {
            channel.truncate(content.length() + 1);
            assertArrayEquals(content.getBytes(), file.getContent());
            channel.truncate(1);
            assertArrayEquals(content.substring(0, 1).getBytes(), file.getContent());
        }
    }

    @Test(expected = NonWritableChannelException.class)
    public void testTruncateReadableChannel() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        try (SeekableByteChannel channel = file.newByteChannel(true, false, false, null)) {
            channel.truncate(1);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testTruncateClosedChannel() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        @SuppressWarnings("resource")
        SeekableByteChannel channel = file.newByteChannel(true, false, false, null);
        channel.close();
        channel.truncate(1);
    }

    @Test
    public void testReadWrite() throws IOException {
        final String content = "Hello World";

        File file = new File();

        try (SeekableByteChannel channel = file.newByteChannel(true, true, false, null)) {
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());
            channel.write(buffer);
            assertArrayEquals(content.getBytes(), file.getContent());

            channel.position(6);

            byte[] bytes = new byte[content.length()];
            buffer = ByteBuffer.wrap(bytes);
            assertEquals(5, channel.read(buffer));
            assertArrayEquals(content.substring(6).getBytes(), Arrays.copyOf(bytes, 5));
        }
    }

    @Test
    public void testOnClose() throws IOException {
        File file = new File();

        final AtomicInteger runCount = new AtomicInteger(0);
        final OnCloseAction onClose = new OnCloseAction() {
            @Override
            public void run() {
                runCount.incrementAndGet();
            }
        };
        try (SeekableByteChannel channel = file.newByteChannel(true, false, false, onClose)) {
            assertTrue(channel.isOpen());
            channel.close();
            assertFalse(channel.isOpen());
            channel.close();
            assertFalse(channel.isOpen());
            channel.close();
            assertFalse(channel.isOpen());
        }
        assertEquals(1, runCount.get());
    }

    private String repeatUntil(String s, int minTotalCount) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < minTotalCount) {
            sb.append(s);
        }
        return sb.toString();
    }
}
