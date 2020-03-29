/*
 * MemoryFileStoreFileChannelTest.java
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.OnCloseAction;

@SuppressWarnings({ "nls", "javadoc" })
public class MemoryFileStoreFileChannelTest {

    @Test
    public void testReadFromReadableChannelDestinationSmallerThanContent() throws IOException {
        final String content = "Hello World";
        assertEquals(11, content.length());
        final int destSize = 5;

        File file = new File();
        file.setContent(content.getBytes());

        try (SeekableByteChannel channel = file.newFileChannel(true, false, false, null)) {
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

        try (SeekableByteChannel channel = file.newFileChannel(true, false, false, null)) {
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

        try (SeekableByteChannel channel = file.newFileChannel(true, false, false, null)) {
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

        try (SeekableByteChannel channel = file.newFileChannel(false, true, false, null)) {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            channel.read(buffer);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testReadFromClosedChannel() throws IOException {

        File file = new File();

        @SuppressWarnings("resource")
        SeekableByteChannel channel = file.newFileChannel(false, true, false, null);
        channel.close();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        channel.read(buffer);
    }

    @Test
    public void testReadFromReadableChannelWithGivenPositionDestinationSmallerThanContent() throws IOException {
        final String content = "Hello World";
        assertEquals(11, content.length());
        final int destSize = 5;

        File file = new File();
        file.setContent(content.getBytes());

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            byte[] bytes = new byte[destSize];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(destSize, channel.read(buffer, 0));
            assertArrayEquals(Arrays.copyOf(content.getBytes(), destSize), bytes);
            assertEquals(0, channel.position());

            buffer.flip();
            assertEquals(-1, channel.read(buffer, content.length() + 10));
            assertEquals(0, channel.position());
        }
    }

    @Test
    public void testReadFromReadableChannelWithGivenPositionContentSmallerThanBuffer() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            byte[] bytes = new byte[content.length() + 1];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(content.length(), channel.read(buffer, 0));
            assertArrayEquals(content.getBytes(), Arrays.copyOf(bytes, content.length()));
            assertEquals(0, channel.position());

            buffer.flip();
            assertEquals(-1, channel.read(buffer, content.length() + 10));
            assertEquals(0, channel.position());
        }
    }

    @Test
    public void testReadFromReadableChannelWithGivenPositionContentLargerThanBuffer() throws IOException {
        final String content = repeatUntil("Hello World", 8192 + 10);

        File file = new File();
        file.setContent(content.getBytes());

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            byte[] bytes = new byte[content.length() + 1];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(content.length(), channel.read(buffer, 0));
            assertArrayEquals(content.getBytes(), Arrays.copyOf(bytes, content.length()));
            assertEquals(0, channel.position());

            buffer.flip();
            assertEquals(-1, channel.read(buffer, content.length() + 10));
            assertEquals(0, channel.position());
        }
    }

    @Test(expected = NonReadableChannelException.class)
    public void testReadFromWritableChannelWithGivenPosition() throws IOException {

        File file = new File();

        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            channel.read(buffer, 0);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testReadFromClosedChannelWithGivenPosition() throws IOException {

        File file = new File();

        @SuppressWarnings("resource")
        FileChannel channel = file.newFileChannel(false, true, false, null);
        channel.close();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        channel.read(buffer, 0);
    }

    @Test
    public void testReadScatteringFromReadableChannel() throws IOException {
        final String content = "Hello World";
        assertEquals(11, content.length());
        final int destSize = 5;

        File file = new File();
        file.setContent(content.getBytes());

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            assertEquals(0, channel.read(new ByteBuffer[0]));

            byte[] bytes1 = new byte[destSize];
            ByteBuffer buffer1 = ByteBuffer.wrap(bytes1);
            byte[] bytes2 = new byte[destSize];
            ByteBuffer buffer2 = ByteBuffer.wrap(bytes2);
            byte[] bytes3 = new byte[destSize];
            ByteBuffer buffer3 = ByteBuffer.wrap(bytes3);
            byte[] bytes4 = new byte[destSize];
            ByteBuffer buffer4 = ByteBuffer.wrap(bytes4);

            ByteBuffer[] buffers = { buffer1, buffer2, buffer3, buffer4 };

            assertEquals(content.length(), channel.read(buffers));
            assertArrayEquals(Arrays.copyOf(content.getBytes(), destSize), bytes1);
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), destSize, destSize * 2), bytes2);
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), destSize * 2, content.length()),
                    Arrays.copyOfRange(bytes3, 0, content.length() - destSize * 2));
            assertEquals(content.length(), channel.position());

            buffer1.flip();
            buffer2.flip();
            buffer3.flip();
            buffer4.flip();

            assertEquals(-1, channel.read(buffers));
            assertEquals(content.length(), channel.position());

            channel.position(0);
            assertEquals(0, channel.read(new ByteBuffer[] { ByteBuffer.allocate(0) }));
            assertEquals(0, channel.position());
        }
    }

    @Test
    public void testWriteToWritableChannel() throws IOException {
        final String content = "Hello World";
        final String newStart = "Goodbye";
        final String newContent = "Goodbyeorld";

        File file = new File();

        try (SeekableByteChannel channel = file.newFileChannel(false, true, false, null)) {
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

        try (SeekableByteChannel channel = file.newFileChannel(true, false, false, null)) {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            channel.write(buffer);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testWriteToClosedChannel() throws IOException {

        File file = new File();

        @SuppressWarnings("resource")
        SeekableByteChannel channel = file.newFileChannel(true, false, false, null);
        channel.close();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        channel.write(buffer);
    }

    @Test
    public void testWriteToWritableChannelWithGivenPosition() throws IOException {
        final String content = "Hello World";
        final String newStart = "Goodbye";
        final String newContent = "Goodbyeorld";

        File file = new File();

        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());
            channel.write(buffer, 0);
            assertArrayEquals(content.getBytes(), file.getContent());
            assertEquals(0, channel.position());

            buffer = ByteBuffer.wrap(newStart.getBytes());
            channel.write(buffer, 0);
            assertArrayEquals(newContent.getBytes(), file.getContent());
            assertEquals(0, channel.position());

            buffer.flip();
            channel.write(buffer, 100);
            assertArrayEquals(newContent.getBytes(), Arrays.copyOf(file.getContent(), newContent.length()));
            assertArrayEquals(new byte[100 - newContent.length()], Arrays.copyOfRange(file.getContent(), newContent.length(), 100));
            assertArrayEquals(newStart.getBytes(), Arrays.copyOfRange(file.getContent(), 100, 100 + newStart.length()));
            assertEquals(0, channel.position());
        }
    }

    @Test(expected = NonWritableChannelException.class)
    public void testWriteToReadableChannelWithGivenPosition() throws IOException {

        File file = new File();

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            ByteBuffer buffer = ByteBuffer.allocate(10);
            channel.write(buffer, 0);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testWriteToClosedChannelWithGivenPosition() throws IOException {

        File file = new File();

        @SuppressWarnings("resource")
        FileChannel channel = file.newFileChannel(true, false, false, null);
        channel.close();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        channel.write(buffer, 0);
    }

    @Test
    public void testWriteGatheringFromWritableChannel() throws IOException {
        final String content = "Hello World";
        assertEquals(11, content.length());
        final int destSize = 5;

        File file = new File();
        file.setContent(content.getBytes());

        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            assertEquals(0, channel.write(new ByteBuffer[0]));

            byte[] bytes1 = Arrays.copyOfRange(content.getBytes(), 0, destSize);
            ByteBuffer buffer1 = ByteBuffer.wrap(bytes1);
            byte[] bytes2 = Arrays.copyOfRange(content.getBytes(), destSize, destSize * 2);
            ByteBuffer buffer2 = ByteBuffer.wrap(bytes2);
            byte[] bytes3 = Arrays.copyOfRange(content.getBytes(), destSize * 2, content.length());
            ByteBuffer buffer3 = ByteBuffer.wrap(bytes3);
            byte[] bytes4 = new byte[0];
            ByteBuffer buffer4 = ByteBuffer.wrap(bytes4);

            ByteBuffer[] buffers = { buffer1, buffer2, buffer3, buffer4 };

            assertEquals(content.length(), channel.write(buffers));
            assertArrayEquals(content.getBytes(), file.getContent());
            assertEquals(content.length(), channel.position());

            channel.position(0);
            assertEquals(0, channel.write(buffers));
            assertArrayEquals(content.getBytes(), file.getContent());
            assertEquals(0, channel.position());
        }
    }

    @Test
    public void testReadWrite() throws IOException {
        final String content = "Hello World";

        File file = new File();

        try (SeekableByteChannel channel = file.newFileChannel(true, true, false, null)) {
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
    public void testTruncateWritableChannel() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        try (SeekableByteChannel channel = file.newFileChannel(false, true, true, null)) {
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

        try (SeekableByteChannel channel = file.newFileChannel(true, false, false, null)) {
            channel.truncate(1);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testTruncateClosedChannel() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        @SuppressWarnings("resource")
        SeekableByteChannel channel = file.newFileChannel(true, false, false, null);
        channel.close();
        channel.truncate(1);
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
        try (SeekableByteChannel channel = file.newFileChannel(true, false, false, onClose)) {
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

    @Test
    public void testTransferToOther() throws IOException {
        final String content = repeatUntil("Hello World", 8192 + 10);

        File file = new File();
        file.setContent(content.getBytes());

        File tempFile = new File();
        try (FileChannel channel = file.newFileChannel(true, false, false, null);
                FileChannel target = tempFile.newFileChannel(false, true, false, null)) {

            tempFile.setContent();
            assertEquals(0, channel.transferTo(content.length(), 10, target));
            assertEquals(0, target.size());
            assertEquals(0, channel.position());
            assertArrayEquals(new byte[0], tempFile.getContent());

            tempFile.setContent();
            assertEquals(content.length(), channel.transferTo(0, Integer.MAX_VALUE, target));
            assertEquals(content.length(), target.size());
            assertEquals(0, channel.position());
            assertArrayEquals(content.getBytes(), tempFile.getContent());

            tempFile.setContent();
            assertEquals(2048, channel.transferTo(1024, 2048, target));
            assertEquals(2048, target.size());
            assertEquals(0, channel.position());
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 1024, 3072), tempFile.getContent());

            tempFile.setContent();
            assertEquals(0, channel.transferTo(content.length(), 2048, target));
            assertEquals(0, target.size());
            assertEquals(0, channel.position());
            assertArrayEquals(new byte[0], tempFile.getContent());

            tempFile.setContent();
            assertEquals(0, channel.transferTo(content.length() + 1, 2048, target));
            assertEquals(0, target.size());
            assertEquals(0, channel.position());
            assertArrayEquals(new byte[0], tempFile.getContent());

            tempFile.setContent();
            assertEquals(0, channel.transferTo(0, 0, target));
            assertEquals(0, target.size());
            assertEquals(0, channel.position());
            assertArrayEquals(new byte[0], tempFile.getContent());
        }
    }

    @Test
    public void testTransferToGeneric() throws IOException {
        final String content = repeatUntil("Hello World", 8192 + 10);

        File file = new File();
        file.setContent(content.getBytes());

        Path tempFile = Files.createTempFile("transferTo", ".tmp");
        try (FileChannel channel = file.newFileChannel(true, false, false, null);
                FileChannel target = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {

            target.truncate(0);
            assertEquals(0, channel.transferTo(content.length(), 10, target));
            assertEquals(0, target.size());
            assertEquals(0, channel.position());
            channel.force(true);
            assertArrayEquals(new byte[0], Files.readAllBytes(tempFile));

            target.truncate(0);
            assertEquals(content.length(), channel.transferTo(0, Integer.MAX_VALUE, target));
            assertEquals(content.length(), target.size());
            assertEquals(0, channel.position());
            target.force(true);
            assertArrayEquals(content.getBytes(), Files.readAllBytes(tempFile));

            target.truncate(0);
            assertEquals(2048, channel.transferTo(1024, 2048, target));
            assertEquals(2048, target.size());
            assertEquals(0, channel.position());
            target.force(true);
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 1024, 3072), Files.readAllBytes(tempFile));

        } finally {
            Files.delete(tempFile);
        }
    }

    @Test(expected = NonReadableChannelException.class)
    public void testTransferToWritableChannel() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            channel.transferTo(0, Integer.MAX_VALUE, channel);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testTransferToClosedChannel() throws IOException {
        final String content = "Hello World";

        File file = new File();
        file.setContent(content.getBytes());

        @SuppressWarnings("resource")
        FileChannel channel = file.newFileChannel(true, false, false, null);
        channel.close();
        channel.transferTo(0, Integer.MAX_VALUE, channel);
    }

    @Test
    public void testTransferFromOther() throws IOException {
        final String content = repeatUntil("Hello World", 8192 + 10);

        File file = new File();

        File tempFile = new File();
        tempFile.setContent(content.getBytes());
        try (FileChannel channel = file.newFileChannel(false, true, false, null);
                FileChannel src = tempFile.newFileChannel(true, false, false, null)) {

            file.setContent();
            src.position(0);
            assertEquals(0, channel.transferFrom(src, content.length(), 10));
            assertEquals(0, channel.size());
            assertEquals(0, channel.position());
            assertArrayEquals(new byte[0], file.getContent());

            file.setContent();
            src.position(0);
            assertEquals(content.length(), channel.transferFrom(src, 0, Integer.MAX_VALUE));
            assertEquals(content.length(), channel.size());
            assertEquals(0, channel.position());
            assertArrayEquals(content.getBytes(), file.getContent());

            file.setContent(content.getBytes());
            src.position(0);
            assertEquals(2048, channel.transferFrom(src, 1024, 2048));
            assertEquals(content.length(), channel.size());
            assertEquals(0, channel.position());
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 0, 1024), Arrays.copyOfRange(file.getContent(), 0, 1024));
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 0, 2048), Arrays.copyOfRange(file.getContent(), 1024, 3072));
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 3072, content.length()),
                    Arrays.copyOfRange(file.getContent(), 3072, content.length()));

            file.setContent(content.getBytes());
            src.position(0);
            assertEquals(2048, channel.transferFrom(src, 8192, 2048));
            assertEquals(10240, channel.size());
            assertEquals(0, channel.position());
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 0, 8192), Arrays.copyOfRange(file.getContent(), 0, 8192));
            assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 0, 2048), Arrays.copyOfRange(file.getContent(), 8192, 10240));

            file.setContent(content.getBytes());
            src.position(content.length());
            assertEquals(0, channel.transferFrom(src, 1024, 2048));
            assertEquals(content.length(), channel.size());
            assertEquals(0, channel.position());
            assertArrayEquals(content.getBytes(), file.getContent());

            file.setContent();
            src.position(0);
            assertEquals(0, channel.transferFrom(src, 0, 0));
            assertEquals(0, channel.size());
            assertEquals(0, channel.position());
            assertArrayEquals(new byte[0], file.getContent());
        }
    }

    @Test
    public void testTransferFromGeneric() throws IOException {
        final String content = repeatUntil("Hello World", 8192 + 10);

        File file = new File();

        Path tempFile = Files.createTempFile("transferTo", ".tmp");
        try {
            Files.write(tempFile, content.getBytes());

            try (FileChannel channel = file.newFileChannel(false, true, false, null);
                    FileChannel src = FileChannel.open(tempFile, StandardOpenOption.READ)) {

                file.setContent();
                src.position(0);
                assertEquals(0, channel.transferFrom(src, content.length(), 10));
                assertEquals(0, channel.size());
                assertEquals(0, channel.position());
                assertArrayEquals(new byte[0], file.getContent());

                file.setContent();
                src.position(0);
                assertEquals(content.length(), channel.transferFrom(src, 0, Integer.MAX_VALUE));
                assertEquals(content.length(), channel.size());
                assertEquals(0, channel.position());
                assertArrayEquals(content.getBytes(), Files.readAllBytes(tempFile));

                file.setContent(content.getBytes());
                src.position(0);
                assertEquals(2048, channel.transferFrom(src, 1024, 2048));
                assertEquals(content.length(), src.size());
                assertEquals(0, channel.position());
                assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 0, 1024), Arrays.copyOfRange(file.getContent(), 0, 1024));
                assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 0, 2048), Arrays.copyOfRange(file.getContent(), 1024, 3072));
                assertArrayEquals(Arrays.copyOfRange(content.getBytes(), 3072, content.length()),
                        Arrays.copyOfRange(file.getContent(), 3072, content.length()));
            }
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test(expected = NonWritableChannelException.class)
    public void testTransferFromReadableChannel() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            channel.transferFrom(channel, 0, Integer.MAX_VALUE);
        }
    }

    @Test(expected = ClosedChannelException.class)
    public void testTransferFromClosedChannel() throws IOException {
        File file = new File();

        @SuppressWarnings("resource")
        FileChannel channel = file.newFileChannel(false, true, false, null);
        channel.close();
        channel.transferFrom(channel, 0, Integer.MAX_VALUE);
    }

    @Test
    public void testLockSharedReadable() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            @SuppressWarnings("resource")
            FileLock lock = channel.lock(0, Long.MAX_VALUE, true);
            assertTrue(lock.isValid());
            assertFalse(lock.isShared());
            lock.close();
            assertFalse(lock.isValid());
        }
    }

    @Test
    public void testLockNonSharedWritable() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            @SuppressWarnings("resource")
            FileLock lock = channel.lock(0, Long.MAX_VALUE, false);
            assertTrue(lock.isValid());
            assertFalse(lock.isShared());
            lock.close();
            assertFalse(lock.isValid());
        }
    }

    @Test(expected = NonReadableChannelException.class)
    public void testLockSharedNonReadable() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            try (FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {
                fail("FileChannel.lock should fail");
            }
        }
    }

    @Test(expected = NonWritableChannelException.class)
    public void testLockNonSharedNonWritable() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            try (FileLock lock = channel.lock(0, Long.MAX_VALUE, false)) {
                fail("FileChannel.lock should fail");
            }
        }
    }

    @Test
    public void testLockMultipleNonOverlapping() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(true, true, false, null);
                FileLock lock1 = channel.lock(0, 10, true);
                FileLock lock2 = channel.lock(10, Long.MAX_VALUE - 10, true)) {

            assertTrue(lock1.isValid());
            assertFalse(lock1.isShared());
            assertTrue(lock2.isValid());
            assertFalse(lock2.isShared());
        }
    }

    @Test(expected = OverlappingFileLockException.class)
    public void testLockMultipleOverlapping() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(true, true, false, null)) {
            try (FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {
                try (FileLock lock2 = channel.lock(0, 10, true)) {
                    fail("Second FileChannel.lock should fail");
                } finally {
                    assertTrue(lock.isValid());
                    assertFalse(lock.isShared());
                }
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    public void testLockMultipleChannelsNonOverlapping() throws IOException {
        File file = new File();

        try (FileChannel channel1 = file.newFileChannel(true, false, false, null);
                FileLock lock1 = channel1.lock(0, 10, true)) {

            assertTrue(lock1.isValid());
            assertFalse(lock1.isShared());

            FileLock lock;
            try (FileChannel channel2 = file.newFileChannel(true, false, false, null);
                    FileLock lock2 = channel2.lock(10, Long.MAX_VALUE - 10, true)) {

                assertTrue(lock2.isValid());
                assertFalse(lock2.isShared());

                lock = lock2;
            }

            assertTrue(lock1.isValid());
            assertFalse(lock1.isShared());

            assertFalse(lock.isValid());
            assertFalse(lock.isShared());
        }
    }

    @Test(expected = OverlappingFileLockException.class)
    public void testLockMultipleChannelsOverlapping() throws IOException {
        File file = new File();

        try (FileChannel channel1 = file.newFileChannel(true, false, false, null);
                FileLock lock = channel1.lock(0, Long.MAX_VALUE, true)) {

            assertTrue(lock.isValid());
            assertFalse(lock.isShared());

            try (FileChannel channel2 = file.newFileChannel(true, false, false, null);
                    FileLock lock2 = channel2.lock(0, 10, true)) {
                fail("Second FileChannel.lock should fail");
            } finally {
                assertTrue(lock.isValid());
                assertFalse(lock.isShared());
            }
        }
    }

    @Test
    public void testLockReleaseTwice() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            @SuppressWarnings("resource")
            FileLock lock = channel.lock(0, Long.MAX_VALUE, false);
            lock.close();
            lock.close();
        }
    }

    @Test(expected = ClosedChannelException.class)
    @SuppressWarnings("resource")
    public void testLockReleaseWhenChannelClosed() throws IOException {
        File file = new File();

        FileLock lock;
        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            lock = channel.lock(0, Long.MAX_VALUE, false);
        }
        lock.close();
    }

    @Test
    public void testTryLockSharedReadable() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(true, false, false, null)) {
            @SuppressWarnings("resource")
            FileLock lock = channel.tryLock(0, Long.MAX_VALUE, true);
            assertNotNull(lock);
            assertTrue(lock.isValid());
            assertFalse(lock.isShared());
            lock.close();
            assertFalse(lock.isValid());
        }
    }

    @Test
    public void testTryLockNonSharedWritable() throws IOException {
        File file = new File();

        try (FileChannel channel = file.newFileChannel(false, true, false, null)) {
            @SuppressWarnings("resource")
            FileLock lock = channel.lock(0, Long.MAX_VALUE, false);
            assertNotNull(lock);
            assertTrue(lock.isValid());
            assertFalse(lock.isShared());
            lock.close();
            assertFalse(lock.isValid());
        }
    }

    private String repeatUntil(String s, int minTotalCount) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < minTotalCount) {
            sb.append(s);
        }
        return sb.toString();
    }
}
