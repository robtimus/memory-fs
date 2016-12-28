/*
 * MemoryFileStoreTest.java
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

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import com.github.robtimus.filesystems.attribute.SimpleFileAttribute;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Directory;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Node;

@SuppressWarnings({ "nls", "javadoc" })
public class MemoryFileStoreTest {

    private MemoryFileStore fileStore;
    private Directory root;

    private MemoryFileSystem fs;

    @Before
    public void setupFileStore() {
        fileStore = new MemoryFileStore();
        root = fileStore.rootNode;

        fs = new MemoryFileSystem(new MemoryFileSystemProvider(fileStore), fileStore);
    }

    private MemoryPath createPath(String path) {
        return new MemoryPath(fs, path);
    }

    // MemoryFileStore.toRealPath

    @Test
    public void testToRealPath() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        root.add("bar", new Directory());

        testToRealPath("/", "/");
        testToRealPath("/foo/bar", "/foo/bar");
        testToRealPath("/foo/../bar", "/bar");
        testToRealPath("/foo/./bar", "/foo/bar");

        testToRealPath("", "/");
        testToRealPath("foo/bar", "/foo/bar");
        testToRealPath("foo/../bar", "/bar");
        testToRealPath("foo/./bar", "/foo/bar");
    }

    private void testToRealPath(String path, String expected) throws IOException {
        MemoryPath expectedPath = createPath(expected);
        Path actual = fileStore.toRealPath(createPath(path));
        assertEquals(expectedPath, actual);
    }

    @Test(expected = NoSuchFileException.class)
    public void testToRealPathNotExisting() throws IOException {
        fileStore.toRealPath(createPath("/foo"));
    }

    // MemoryFileStore.newInputStream

    @Test
    public void testNewInputStream() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        try (InputStream input = fileStore.newInputStream(createPath("/foo/bar"))) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewInputStreamDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.DELETE_ON_CLOSE };
        try (InputStream input = fileStore.newInputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertNull(foo.get("bar"));
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewInputStreamNonExisting() throws IOException {
        try (InputStream input = fileStore.newInputStream(createPath("/foo/bar"))) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewInputStreamDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        try (InputStream input = fileStore.newInputStream(createPath("/foo"))) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    // MemoryFileStore.newOutputStream

    @Test
    public void testNewOutputStreamExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.WRITE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewOutputStreamExistingDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewOutputStreamExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.CREATE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewOutputStreamExistingCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testNewOutputStreamExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.CREATE_NEW };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testNewOutputStreamExistingCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewOutputStreamExistingReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        OpenOption[] options = { StandardOpenOption.WRITE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewOutputStreamExistingReadOnlyDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        OpenOption[] options = { StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewOutputStreamNonExistingNoCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.WRITE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testNewOutputStreamNonExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewOutputStreamNonExistingCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
            assertThat(foo.get("bar"), instanceOf(File.class));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewOutputStreamNonExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE_NEW };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewOutputStreamNonExistingCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
            assertThat(foo.get("bar"), instanceOf(File.class));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewOutputStreamNonExistingCreateNonExistingParent() throws IOException {

        OpenOption[] options = { StandardOpenOption.CREATE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewOutputStreamNonExistingCreateNewNonExistingParent() throws IOException {

        OpenOption[] options = { StandardOpenOption.CREATE_NEW };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewOutputStreamNonExistingCreateReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        OpenOption[] options = { StandardOpenOption.CREATE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewOutputStreamNonExistingCreateNewReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        OpenOption[] options = { StandardOpenOption.CREATE_NEW };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewOutputStreamDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.WRITE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewOutputStreamDirectoryDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = fileStore.newOutputStream(createPath("/foo"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    // MemoryFileStore.newByteChannel

    @Test
    public void testNewByteChannelRead() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelReadDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadNonExisting() throws IOException {

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelReadDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testNewByteChannelWriteExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelWriteExistingDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelWriteExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelWriteExistingCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testNewByteChannelWriteExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testNewByteChannelWriteExistingCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelWriteExistingReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelWriteExistingReadOnlyDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelWriteNonExistingNoCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testNewByteChannelWriteNonExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewByteChannelWriteNonExistingCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertThat(foo.get("bar"), instanceOf(File.class));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelWriteNonExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewByteChannelWriteNonExistingCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertThat(foo.get("bar"), instanceOf(File.class));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelWriteNonExistingCreateWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", false),
                new SimpleFileAttribute<>("memory:hidden", true),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options, attributes)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));

        Node bar = foo.get("bar");
        assertEquals(123456L, bar.getLastModifiedTime().toMillis());
        assertEquals(1234567L, bar.getLastAccessTime().toMillis());
        assertEquals(12345678L, bar.getCreationTime().toMillis());
        assertFalse(bar.isReadOnly());
        assertTrue(bar.isHidden());
    }

    @Test
    public void testNewByteChannelWriteNonExistingCreateNewWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", false),
                new SimpleFileAttribute<>("memory:hidden", true),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options, attributes)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));

        Node bar = foo.get("bar");
        assertEquals(123456L, bar.getLastModifiedTime().toMillis());
        assertEquals(1234567L, bar.getLastAccessTime().toMillis());
        assertEquals(12345678L, bar.getCreationTime().toMillis());
        assertFalse(bar.isReadOnly());
        assertTrue(bar.isHidden());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNewByteChannelWriteNonExistingCreateWithInvalidAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("something:else", "foo"),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options, attributes)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNewByteChannelWriteNonExistingCreateNewWithInvalidAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("something:else", "foo"),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options, attributes)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelWriteNonExistingCreateNonExistingParent() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelWriteNonExistingCreateNewNonExistingParent() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelWriteNonExistingCreateReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelWriteNonExistingCreateNewReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelWriteDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelWriteDirectoryDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = fileStore.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    // MemoryFileStore.newDirectoryStream

    @Test
    public void testNewDirectoryStream() throws IOException {

        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/"), AcceptAllFilter.INSTANCE)) {
            assertNotNull(stream);
            // don't do anything with the stream, there's a separate test for that
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewDirectoryStreamNotExisting() throws IOException {

        fileStore.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE);
    }

    @Test(expected = NotDirectoryException.class)
    public void testNewDirectoryStreamNotDirectory() throws IOException {

        root.add("foo", new File());

        fileStore.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE);
    }

    private static final class AcceptAllFilter implements Filter<Path> {

        private static final AcceptAllFilter INSTANCE = new AcceptAllFilter();

        @Override
        public boolean accept(Path entry) {
            return true;
        }
    }

    // MemoryFileStore.createDirectory

    @Test
    public void testCreateDirectory() throws IOException {

        fileStore.createDirectory(createPath("/foo"));
        assertThat(root.get("foo"), instanceOf(Directory.class));

        Directory foo = (Directory) root.get("foo");
        assertFalse(foo.isReadOnly());
        assertFalse(foo.isHidden());

        fileStore.createDirectory(createPath("/foo/bar"));
        assertThat(foo.get("bar"), instanceOf(Directory.class));

        Directory bar = (Directory) foo.get("bar");
        assertFalse(bar.isReadOnly());
        assertFalse(bar.isHidden());

        fileStore.createDirectory(createPath("bar"));
        assertThat(root.get("bar"), instanceOf(Directory.class));

        bar = (Directory) root.get("bar");
        assertFalse(bar.isReadOnly());
        assertFalse(bar.isHidden());
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testCreateRoot() throws IOException {
        fileStore.createDirectory(createPath("/"));
    }

    @Test
    public void testCreateDirectoryWithAttributes() throws IOException {

        fileStore.createDirectory(createPath("/foo"),
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true)
        );
        assertThat(root.get("foo"), instanceOf(Directory.class));

        Node foo = root.get("foo");
        assertEquals(123456L, foo.getLastModifiedTime().toMillis());
        assertEquals(1234567L, foo.getLastAccessTime().toMillis());
        assertEquals(12345678L, foo.getCreationTime().toMillis());
        assertTrue(foo.isReadOnly());
        assertTrue(foo.isHidden());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateDirectoryWithInvalidAttributes() throws IOException {
        try {
            fileStore.createDirectory(createPath("/foo"),
                    new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                    new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                    new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                    new SimpleFileAttribute<>("memory:readOnly", true),
                    new SimpleFileAttribute<>("memory:hidden", true),
                    new SimpleFileAttribute<>("something:else", "foo")
            );
        } finally {
            assertNull(root.get("foo"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testCreateDirectoryReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        // content:
        // d /foo

        foo.setReadOnly(true);

        try {
            fileStore.createDirectory(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testCreateDirectoryNonExistingParent() throws IOException {
        try {
            fileStore.createDirectory(createPath("/foo/bar"));
        } finally {
            assertNull(root.get("foo"));
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testCreateDirectoryExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        try {
            fileStore.createDirectory(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    // MemoryFileStore.delete

    @Test(expected = NoSuchFileException.class)
    public void testDeleteNonExisting() throws IOException {
        fileStore.delete(createPath("/foo"));
    }

    @Test(expected = AccessDeniedException.class)
    public void testDeleteRoot() throws IOException {
        fileStore.delete(createPath("/"));
    }

    @Test
    public void testDeleteFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        fileStore.delete(createPath("/foo/bar"));

        assertSame(foo, root.get("foo"));
        assertNull(foo.get("bar"));
    }

    @Test
    public void testDeleteEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        fileStore.delete(createPath("/foo/bar"));

        assertSame(foo, root.get("foo"));
        assertNull(foo.get("bar"));
    }

    @Test(expected = DirectoryNotEmptyException.class)
    public void testDeleteNonEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        Directory baz = (Directory) bar.add("baz", new Directory());

        // content:
        // d /foo
        // d /foo/bar
        // d /foo/bar/baz

        try {
            fileStore.delete(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, bar.get("baz"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testDeleteReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        foo.setReadOnly(true);

        try {
            fileStore.delete(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    // MemoryFileStore.copy

    @Test
    public void testCopySame() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        CopyOption[] options = {};
        fileStore.copy(createPath("/"), createPath(""), options);
        fileStore.copy(createPath("/foo"), createPath("foo"), options);
        fileStore.copy(createPath("/foo/bar"), createPath("foo/bar"), options);

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertTrue(bar.isEmpty());
    }

    @Test(expected = NoSuchFileException.class)
    public void testCopyNonExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        // content:
        // d /foo

        CopyOption[] options = {};
        try {
            fileStore.copy(createPath("/foo/bar"), createPath("/foo/baz"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testCopyNonExistingTargetParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        CopyOption[] options = {};
        try {
            fileStore.copy(createPath("/foo/bar"), createPath("/baz/bar"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertNull(root.get("baz"));
        }
    }

    @Test
    public void testCopyRoot() throws IOException {
        // copying a directory (including the root) will not copy its contents, so copying the root is allowed
        Directory foo = (Directory) root.add("foo", new Directory());

        // content:
        // d /foo

        CopyOption[] options = {};
        fileStore.copy(createPath("/"), createPath("/foo/bar"), options);

        // added:
        // d /foo/bar

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(Directory.class));

        Directory bar = (Directory) foo.get("bar");

        assertNotSame(root, bar);
        assertTrue(bar.isEmpty());
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testCopyReplaceFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // d /foo/bar
        // f /baz

        CopyOption[] options = {};
        try {
            fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, root.get("baz"));
        }
    }

    @Test
    public void testCopyReplaceFileAllowed() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
        fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
        assertNotSame(bar, foo.get("bar"));
        assertNotSame(baz, foo.get("bar"));
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testCopyReplaceNonEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        CopyOption[] options = {};
        try {
            fileStore.copy(createPath("/baz"), createPath("/foo"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, root.get("baz"));
        }
    }

    @Test(expected = DirectoryNotEmptyException.class)
    public void testCopyReplaceNonEmptyDirAllowed() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
        try {
            fileStore.copy(createPath("/baz"), createPath("/foo"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, root.get("baz"));
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testCopyReplaceEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        CopyOption[] options = {};
        try {
            fileStore.copy(createPath("/baz"), createPath("/foo"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(baz, root.get("baz"));
        }
    }

    @Test
    public void testCopyReplaceEmptyDirAllowed() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
        fileStore.copy(createPath("/baz"), createPath("/foo"), options);

        assertThat(root.get("foo"), instanceOf(File.class));
        assertNotSame(foo, root.get("foo"));
        assertNotSame(baz, root.get("foo"));
    }

    @Test
    public void testCopyFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        baz.setHidden(true);

        CopyOption[] options = {};
        fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(File.class));
        assertSame(foo, root.get("foo"));
        assertNotSame(baz, foo.get("bar"));
        assertSame(baz, root.get("baz"));
        assertFalse(foo.get("bar").isHidden());
    }

    @Test
    public void testCopyFileWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        baz.setHidden(true);

        CopyOption[] options = { StandardCopyOption.COPY_ATTRIBUTES };
        fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(File.class));
        assertSame(foo, root.get("foo"));
        assertNotSame(baz, foo.get("bar"));
        assertSame(baz, root.get("baz"));

        File bar = (File) foo.get("bar");
        assertTrue(bar.isHidden());
        assertEquals(baz.getLastModifiedTime(), bar.getLastModifiedTime());
        assertEquals(baz.getLastAccessTime(), bar.getLastAccessTime());
        assertEquals(baz.getCreationTime(), bar.getCreationTime());
    }

    @Test
    public void testCopyEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory baz = (Directory) root.add("baz", new Directory());

        // content:
        // d /foo
        // d /baz

        baz.setHidden(true);

        CopyOption[] options = {};
        fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(Directory.class));
        assertSame(foo, root.get("foo"));
        assertNotSame(baz, foo.get("bar"));
        assertSame(baz, root.get("baz"));

        Directory bar = (Directory) foo.get("bar");
        assertTrue(bar.isEmpty());
        assertFalse(bar.isHidden());
    }

    @Test
    public void testCopyEmptyDirWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory baz = (Directory) root.add("baz", new Directory());

        // content:
        // d /foo
        // d /baz

        baz.setHidden(true);

        CopyOption[] options = { StandardCopyOption.COPY_ATTRIBUTES };
        fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(Directory.class));
        assertSame(foo, root.get("foo"));
        assertNotSame(baz, foo.get("bar"));
        assertSame(baz, root.get("baz"));

        Directory bar = (Directory) foo.get("bar");
        assertTrue(bar.isEmpty());
        assertTrue(bar.isHidden());
        assertEquals(baz.getLastModifiedTime(), bar.getLastModifiedTime());
        assertEquals(baz.getLastAccessTime(), bar.getLastAccessTime());
        assertEquals(baz.getCreationTime(), bar.getCreationTime());
    }

    @Test
    public void testCopyNonEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory baz = (Directory) root.add("baz", new Directory());
        baz.add("qux", new File());

        // content:
        // d /foo
        // d /baz
        // f /baz/qux

        baz.setHidden(true);

        CopyOption[] options = {};
        fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(Directory.class));
        assertSame(foo, root.get("foo"));
        assertNotSame(baz, foo.get("bar"));
        assertSame(baz, root.get("baz"));

        Directory bar = (Directory) foo.get("bar");
        assertTrue(bar.isEmpty());
        assertFalse(bar.isHidden());
    }

    @Test
    public void testCopyNonEmptyDirWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory baz = (Directory) root.add("baz", new Directory());
        baz.add("qux", new File());

        // content:
        // d /foo
        // d /baz
        // f /baz/qux

        baz.setHidden(true);

        CopyOption[] options = { StandardCopyOption.COPY_ATTRIBUTES };
        fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(Directory.class));
        assertSame(foo, root.get("foo"));
        assertNotSame(baz, foo.get("bar"));
        assertSame(baz, root.get("baz"));

        Directory bar = (Directory) foo.get("bar");
        assertTrue(bar.isEmpty());
        assertTrue(bar.isHidden());
        assertEquals(baz.getLastModifiedTime(), bar.getLastModifiedTime());
        assertEquals(baz.getLastAccessTime(), bar.getLastAccessTime());
        assertEquals(baz.getCreationTime(), bar.getCreationTime());
    }

    @Test(expected = AccessDeniedException.class)
    public void testCopyReadOnlyTargetParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        foo.setReadOnly(true);

        CopyOption[] options = {};
        try {
            fileStore.copy(createPath("/baz"), createPath("/foo/bar"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(baz, root.get("baz"));
            assertTrue(foo.isEmpty());
        }
    }

    // MemoryFileStore.move

    @Test
    public void testMoveSame() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        CopyOption[] options = {};
        fileStore.move(createPath("/"), createPath(""), options);
        fileStore.move(createPath("/foo"), createPath("foo"), options);
        fileStore.move(createPath("/foo/bar"), createPath("foo/bar"), options);

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertTrue(bar.isEmpty());
    }

    @Test(expected = NoSuchFileException.class)
    public void testMoveNonExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        // content:
        // d /foo

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/foo/bar"), createPath("/foo/baz"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testMoveNonExistingTargetParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/foo/bar"), createPath("/baz/bar"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertNull(root.get("baz"));
        }
    }

    @Test(expected = DirectoryNotEmptyException.class)
    public void testMoveEmptyRoot() throws IOException {

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/"), createPath("/baz"), options);
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = DirectoryNotEmptyException.class)
    public void testMoveNonEmptyRoot() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        // cotent:
        // d /foo

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/"), createPath("/baz"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertNull(root.get("baz"));
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testMoveReplaceFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // d /foo/bar
        // f /baz

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/baz"), createPath("/foo/bar"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, root.get("baz"));
        }
    }

    @Test
    public void testMoveReplaceFileAllowed() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
        fileStore.move(createPath("/baz"), createPath("/foo/bar"), options);

        assertSame(foo, root.get("foo"));
        assertSame(baz, foo.get("bar"));
        assertNull(root.get("baz"));
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testMoveReplaceNonEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/baz"), createPath("/foo"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, root.get("baz"));
        }
    }

    @Test(expected = DirectoryNotEmptyException.class)
    public void testMoveReplaceNonEmptyDirAllowed() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
        try {
            fileStore.move(createPath("/baz"), createPath("/foo"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, root.get("baz"));
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testMoveReplaceEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/baz"), createPath("/foo"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(baz, root.get("baz"));
        }
    }

    @Test
    public void testMoveReplaceEmptyDirAllowed() throws IOException {
        root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
        fileStore.move(createPath("/baz"), createPath("/foo"), options);

        assertSame(baz, root.get("foo"));
        assertNull(root.get("baz"));
    }

    @Test
    public void testMoveFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        CopyOption[] options = {};
        fileStore.move(createPath("/baz"), createPath("/foo/bar"), options);

        assertSame(foo, root.get("foo"));
        assertSame(baz, foo.get("bar"));
        assertNull(root.get("baz"));
    }

    @Test
    public void testMoveEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory baz = (Directory) root.add("baz", new Directory());

        // content:
        // d /foo
        // d /baz

        CopyOption[] options = {};
        fileStore.move(createPath("/baz"), createPath("/foo/bar"), options);

        assertSame(foo, root.get("foo"));
        assertSame(baz, foo.get("bar"));
        assertNull(root.get("baz"));
    }

    @Test
    public void testMoveNonEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory baz = (Directory) root.add("baz", new Directory());
        File qux = (File) baz.add("qux", new File());

        // content:
        // d /foo
        // d /baz
        // f /baz/qux

        CopyOption[] options = {};
        fileStore.move(createPath("/baz"), createPath("/foo/bar"), options);

        assertSame(foo, root.get("foo"));
        assertSame(baz, foo.get("bar"));
        assertSame(qux, baz.get("qux"));
    }

    @Test
    public void testMoveNonEmptyDirSameParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/foo"), createPath("/baz"), options);
        } finally {
            assertNull(root.get("foo"));
            assertSame(foo, root.get("baz"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testMoveReadOnlySourceParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        foo.setReadOnly(true);

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/foo/bar"), createPath("/baz"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertNull(root.get("baz"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testMoveReadOnlyTargetParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        foo.setReadOnly(true);

        CopyOption[] options = {};
        try {
            fileStore.move(createPath("/baz"), createPath("/foo/bar"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(baz, root.get("baz"));
            assertTrue(foo.isEmpty());
        }
    }

    // MemoryFileStore.isSameFile

    @Test
    public void testIsSameFileEquals() throws IOException {
        assertTrue(fileStore.isSameFile(createPath("/"), createPath("/")));
        assertTrue(fileStore.isSameFile(createPath("/foo"), createPath("/foo")));
        assertTrue(fileStore.isSameFile(createPath("/foo/bar"), createPath("/foo/bar")));

        assertTrue(fileStore.isSameFile(createPath(""), createPath("")));
        assertTrue(fileStore.isSameFile(createPath("foo"), createPath("foo")));
        assertTrue(fileStore.isSameFile(createPath("foo/bar"), createPath("foo/bar")));

        assertTrue(fileStore.isSameFile(createPath(""), createPath("/")));
        assertTrue(fileStore.isSameFile(createPath("/"), createPath("")));
    }

    @Test
    public void testIsSameFileExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        assertTrue(fileStore.isSameFile(createPath("/"), createPath("")));
        assertTrue(fileStore.isSameFile(createPath("/foo"), createPath("foo")));
        assertTrue(fileStore.isSameFile(createPath("/foo/bar"), createPath("foo/bar")));

        assertTrue(fileStore.isSameFile(createPath(""), createPath("/")));
        assertTrue(fileStore.isSameFile(createPath("foo"), createPath("/foo")));
        assertTrue(fileStore.isSameFile(createPath("foo/bar"), createPath("/foo/bar")));

        assertFalse(fileStore.isSameFile(createPath("foo"), createPath("foo/bar")));
    }

    @Test(expected = NoSuchFileException.class)
    public void testIsSameFileFirstNonExisting() throws IOException {
        fileStore.isSameFile(createPath("/foo"), createPath("/"));
    }

    @Test(expected = NoSuchFileException.class)
    public void testIsSameFileSecondNonExisting() throws IOException {
        fileStore.isSameFile(createPath("/"), createPath("/foo"));
    }

    // MemoryFileStore.isHidden

    @Test
    public void testIsHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setHidden(true);
        assertTrue(fileStore.isHidden(createPath("/foo")));

        foo.setHidden(false);
        assertFalse(fileStore.isHidden(createPath("/foo")));
    }

    @Test(expected = NoSuchFileException.class)
    public void testIsHiddenNonExisting() throws IOException {
        fileStore.isHidden(createPath("/foo"));
    }

    // MemoryFileStore.getFileStore

    @Test
    public void testGetFileStoreExisting() throws IOException {
        assertSame(fileStore, fileStore.getFileStore(createPath("/")));
    }

    @Test(expected = NoSuchFileException.class)
    public void testGetFileStoreNonExisting() throws IOException {
        fileStore.getFileStore(createPath("/foo/bar"));
    }

    // MemoryFileStore.checkAccess

    @Test(expected = NoSuchFileException.class)
    public void testCheckAccessNonExisting() throws IOException {
        fileStore.checkAccess(createPath("/foo/bar"));
    }

    @Test
    public void testCheckAccessNoModes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        fileStore.checkAccess(createPath("/foo/bar"));
    }

    @Test
    public void testCheckAccessOnlyRead() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        fileStore.checkAccess(createPath("/foo/bar"), AccessMode.READ);
    }

    @Test
    public void testCheckAccessOnlyWriteNotReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        fileStore.checkAccess(createPath("/foo/bar"), AccessMode.WRITE);
    }

    @Test(expected = AccessDeniedException.class)
    public void testCheckAccessOnlyWriteReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        bar.setReadOnly(true);

        fileStore.checkAccess(createPath("/foo/bar"), AccessMode.WRITE);
    }

    @Test(expected = AccessDeniedException.class)
    public void testCheckAccessOnlyExecuteFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        fileStore.checkAccess(createPath("/foo/bar"), AccessMode.EXECUTE);
    }

    @Test
    public void testCheckAccessOnlyExecuteDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        fileStore.checkAccess(createPath("/foo/bar"), AccessMode.EXECUTE);
    }

    // MemoryFileStore.setTimes

    @Test
    public void setTimesAllTimes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        FileTime oldLastModified = foo.getLastModifiedTime();
        FileTime oldLastAccess = foo.getLastAccessTime();
        FileTime oldCreation = foo.getCreationTime();

        FileTime newLastModified = FileTime.fromMillis(oldLastModified.toMillis() + 5000);
        FileTime newLastAccess = FileTime.fromMillis(oldLastAccess.toMillis() + 10000);
        FileTime newCreation = FileTime.fromMillis(oldCreation.toMillis() + 15000);

        fileStore.setTimes(createPath("/foo"), newLastModified, newLastAccess, newCreation);

        assertEquals(newLastModified, foo.getLastModifiedTime());
        assertEquals(newLastAccess, foo.getLastAccessTime());
        assertEquals(newCreation, foo.getCreationTime());
    }

    @Test
    public void setTimesOnlyLastModified() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        FileTime oldLastModified = foo.getLastModifiedTime();
        FileTime oldLastAccess = foo.getLastAccessTime();
        FileTime oldCreation = foo.getCreationTime();

        FileTime newLastModified = FileTime.fromMillis(oldLastModified.toMillis() + 5000);

        fileStore.setTimes(createPath("/foo"), newLastModified, null, null);

        assertEquals(newLastModified, foo.getLastModifiedTime());
        assertEquals(oldLastAccess, foo.getLastAccessTime());
        assertEquals(oldCreation, foo.getCreationTime());
    }

    @Test
    public void setTimesOnlyLastAccess() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        FileTime oldLastModified = foo.getLastModifiedTime();
        FileTime oldLastAccess = foo.getLastAccessTime();
        FileTime oldCreation = foo.getCreationTime();

        FileTime newLastAccess = FileTime.fromMillis(oldLastAccess.toMillis() + 5000);

        fileStore.setTimes(createPath("/foo"), null, newLastAccess, null);

        assertEquals(oldLastModified, foo.getLastModifiedTime());
        assertEquals(newLastAccess, foo.getLastAccessTime());
        assertEquals(oldCreation, foo.getCreationTime());
    }

    @Test
    public void setTimesOnlyCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        FileTime oldLastModified = foo.getLastModifiedTime();
        FileTime oldLastAccess = foo.getLastAccessTime();
        FileTime oldCreation = foo.getCreationTime();

        FileTime newCreation = FileTime.fromMillis(oldCreation.toMillis() + 5000);

        fileStore.setTimes(createPath("/foo"), null, null, newCreation);

        assertEquals(oldLastModified, foo.getLastModifiedTime());
        assertEquals(oldLastAccess, foo.getLastAccessTime());
        assertEquals(newCreation, foo.getCreationTime());
    }

    // MemoryFileStore.setReadOnly

    @Test
    public void testSetReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        assertFalse(foo.isReadOnly());

        fileStore.setReadOnly(createPath("/foo"), true);
        assertTrue(foo.isReadOnly());

        fileStore.setReadOnly(createPath("/foo"), false);
        assertFalse(foo.isReadOnly());
    }

    @Test(expected = NoSuchFileException.class)
    public void testSetReadOnlyNonExisting() throws IOException {
        fileStore.setReadOnly(createPath("/foo"), true);
    }

    // MemoryFileStore.setHidden

    @Test
    public void testSetHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        assertFalse(foo.isHidden());

        fileStore.setHidden(createPath("/foo"), true);
        assertTrue(foo.isHidden());

        fileStore.setHidden(createPath("/foo"), false);
        assertFalse(foo.isHidden());
    }

    @Test(expected = NoSuchFileException.class)
    public void testSetHiddenNonExisting() throws IOException {
        fileStore.setHidden(createPath("/foo"), true);
    }

    // MemoryFileStore.readAttributes (MemoryFileAttributes variant)

    @Test
    public void testReadAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);
        foo.setHidden(true);

        MemoryFileAttributes attributes = fileStore.readAttributes(createPath("/foo"));

        assertEquals(foo.getLastModifiedTime(), attributes.lastModifiedTime());
        assertEquals(foo.getLastAccessTime(), attributes.lastAccessTime());
        assertEquals(foo.getCreationTime(), attributes.creationTime());
        assertEquals(foo.isRegularFile(), attributes.isRegularFile());
        assertEquals(foo.isDirectory(), attributes.isDirectory());
        assertFalse(attributes.isSymbolicLink());
        assertFalse(attributes.isOther());
        assertEquals(0, attributes.size());
        assertNull(attributes.fileKey());

        assertTrue(attributes.isReadOnly());
        assertTrue(attributes.isHidden());
    }

    @Test(expected = NoSuchFileException.class)
    public void testReadAttributesNonExisting() throws IOException {
        fileStore.readAttributes(createPath("/foo"));
    }

    // MemoryFileStore.readAttributes (map variant)

    @Test
    public void testReadAttributesMapNoTypeLastModifiedTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "lastModifiedTime");
        Map<String, ?> expected = Collections.singletonMap("basic:lastModifiedTime", foo.getLastModifiedTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeLastAccessTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "lastAccessTime");
        Map<String, ?> expected = Collections.singletonMap("basic:lastAccessTime", foo.getLastAccessTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeCreateTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "creationTime");
        Map<String, ?> expected = Collections.singletonMap("basic:creationTime", foo.getCreationTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeBasicSize() throws IOException {
        File foo = (File) root.add("foo", new File());
        foo.setContent(new byte[1024]);

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "size");
        Map<String, ?> expected = Collections.singletonMap("basic:size", foo.getSize());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeIsRegularFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "isRegularFile");
        Map<String, ?> expected = Collections.singletonMap("basic:isRegularFile", foo.isRegularFile());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeIsDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "isDirectory");
        Map<String, ?> expected = Collections.singletonMap("basic:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeIsSymbolicLink() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "isSymbolicLink");
        Map<String, ?> expected = Collections.singletonMap("basic:isSymbolicLink", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeIsOther() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "isOther");
        Map<String, ?> expected = Collections.singletonMap("basic:isOther", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeFileKey() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "fileKey");
        Map<String, ?> expected = Collections.singletonMap("basic:fileKey", null);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "lastModifiedTime,creationTime,isDirectory");
        Map<String, Object> expected = new HashMap<>();
        expected.put("basic:lastModifiedTime", foo.getLastModifiedTime());
        expected.put("basic:creationTime", foo.getCreationTime());
        expected.put("basic:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeAll() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "*");
        Map<String, Object> expected = new HashMap<>();
        expected.put("basic:lastModifiedTime", foo.getLastModifiedTime());
        expected.put("basic:lastAccessTime", foo.getLastAccessTime());
        expected.put("basic:creationTime", foo.getCreationTime());
        expected.put("basic:size", foo.getSize());
        expected.put("basic:isRegularFile", foo.isRegularFile());
        expected.put("basic:isDirectory", foo.isDirectory());
        expected.put("basic:isSymbolicLink", false);
        expected.put("basic:isOther", false);
        expected.put("basic:fileKey", null);
        assertEquals(expected, attributes);

        attributes = fileStore.readAttributes(createPath("/foo"), "basic:lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicLastModifiedTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:lastModifiedTime");
        Map<String, ?> expected = Collections.singletonMap("basic:lastModifiedTime", foo.getLastModifiedTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicLastAccessTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:lastAccessTime");
        Map<String, ?> expected = Collections.singletonMap("basic:lastAccessTime", foo.getLastAccessTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicCreateTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:creationTime");
        Map<String, ?> expected = Collections.singletonMap("basic:creationTime", foo.getCreationTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicSize() throws IOException {
        File foo = (File) root.add("foo", new File());
        foo.setContent(new byte[1024]);

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:size");
        Map<String, ?> expected = Collections.singletonMap("basic:size", foo.getSize());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicIsRegularFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:isRegularFile");
        Map<String, ?> expected = Collections.singletonMap("basic:isRegularFile", foo.isRegularFile());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicIsDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:isDirectory");
        Map<String, ?> expected = Collections.singletonMap("basic:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicIsSymbolicLink() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:isSymbolicLink");
        Map<String, ?> expected = Collections.singletonMap("basic:isSymbolicLink", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicIsOther() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:isOther");
        Map<String, ?> expected = Collections.singletonMap("basic:isOther", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicFileKey() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:fileKey");
        Map<String, ?> expected = Collections.singletonMap("basic:fileKey", null);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:lastModifiedTime,creationTime,isDirectory");
        Map<String, Object> expected = new HashMap<>();
        expected.put("basic:lastModifiedTime", foo.getLastModifiedTime());
        expected.put("basic:creationTime", foo.getCreationTime());
        expected.put("basic:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicAll() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "basic:*");
        Map<String, Object> expected = new HashMap<>();
        expected.put("basic:lastModifiedTime", foo.getLastModifiedTime());
        expected.put("basic:lastAccessTime", foo.getLastAccessTime());
        expected.put("basic:creationTime", foo.getCreationTime());
        expected.put("basic:size", foo.getSize());
        expected.put("basic:isRegularFile", foo.isRegularFile());
        expected.put("basic:isDirectory", foo.isDirectory());
        expected.put("basic:isSymbolicLink", false);
        expected.put("basic:isOther", false);
        expected.put("basic:fileKey", null);
        assertEquals(expected, attributes);

        attributes = fileStore.readAttributes(createPath("/foo"), "basic:lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryLastModifiedTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:lastModifiedTime");
        Map<String, ?> expected = Collections.singletonMap("memory:lastModifiedTime", foo.getLastModifiedTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryLastAccessTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:lastAccessTime");
        Map<String, ?> expected = Collections.singletonMap("memory:lastAccessTime", foo.getLastAccessTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryCreateTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:creationTime");
        Map<String, ?> expected = Collections.singletonMap("memory:creationTime", foo.getCreationTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemorySize() throws IOException {
        File foo = (File) root.add("foo", new File());
        foo.setContent(new byte[1024]);

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:size");
        Map<String, ?> expected = Collections.singletonMap("memory:size", foo.getSize());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryIsRegularFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:isRegularFile");
        Map<String, ?> expected = Collections.singletonMap("memory:isRegularFile", foo.isRegularFile());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryIsDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:isDirectory");
        Map<String, ?> expected = Collections.singletonMap("memory:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryIsSymbolicLink() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:isSymbolicLink");
        Map<String, ?> expected = Collections.singletonMap("memory:isSymbolicLink", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryIsOther() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:isOther");
        Map<String, ?> expected = Collections.singletonMap("memory:isOther", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryFileKey() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:fileKey");
        Map<String, ?> expected = Collections.singletonMap("memory:fileKey", null);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:readOnly");
        Map<String, ?> expected = Collections.singletonMap("memory:readOnly", true);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setHidden(true);

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:hidden");
        Map<String, ?> expected = Collections.singletonMap("memory:hidden", true);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:lastModifiedTime,creationTime,readOnly");
        Map<String, Object> expected = new HashMap<>();
        expected.put("memory:lastModifiedTime", foo.getLastModifiedTime());
        expected.put("memory:creationTime", foo.getCreationTime());
        expected.put("memory:readOnly", true);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryAll() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        Map<String, Object> attributes = fileStore.readAttributes(createPath("/foo"), "memory:*");
        Map<String, Object> expected = new HashMap<>();
        expected.put("memory:lastModifiedTime", foo.getLastModifiedTime());
        expected.put("memory:lastAccessTime", foo.getLastAccessTime());
        expected.put("memory:creationTime", foo.getCreationTime());
        expected.put("memory:size", foo.getSize());
        expected.put("memory:isRegularFile", foo.isRegularFile());
        expected.put("memory:isDirectory", foo.isDirectory());
        expected.put("memory:isSymbolicLink", false);
        expected.put("memory:isOther", false);
        expected.put("memory:fileKey", null);
        expected.put("memory:readOnly", true);
        expected.put("memory:hidden", false);
        assertEquals(expected, attributes);

        attributes = fileStore.readAttributes(createPath("/foo"), "memory:lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadAttributesMapUnsupportedAttribute() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        fileStore.readAttributes(createPath("/foo"), "memory:lastModifiedTime,readOnly,dummy");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReadAttributesMapUnsupportedType() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        fileStore.readAttributes(createPath("/foo"), "zipfs:*");
    }

    // MemoryFileStore.setAttribute

    @Test
    public void testSetAttributeLastModifiedTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileTime lastModifiedTime = FileTime.fromMillis(123456L);

        fileStore.setAttribute(createPath("/foo"), "basic:lastModifiedTime", lastModifiedTime);
        assertEquals(lastModifiedTime, foo.getLastModifiedTime());

        lastModifiedTime = FileTime.fromMillis(1234567L);

        fileStore.setAttribute(createPath("/foo"), "memory:lastModifiedTime", lastModifiedTime);
        assertEquals(lastModifiedTime, foo.getLastModifiedTime());

        lastModifiedTime = FileTime.fromMillis(12345678L);

        fileStore.setAttribute(createPath("/foo"), "lastModifiedTime", lastModifiedTime);
        assertEquals(lastModifiedTime, foo.getLastModifiedTime());
    }

    @Test
    public void testSetAttributeLastAccessTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileTime lastAccessTime = FileTime.fromMillis(123456L);

        fileStore.setAttribute(createPath("/foo"), "basic:lastAccessTime", lastAccessTime);
        assertEquals(lastAccessTime, foo.getLastAccessTime());

        lastAccessTime = FileTime.fromMillis(1234567L);

        fileStore.setAttribute(createPath("/foo"), "memory:lastAccessTime", lastAccessTime);
        assertEquals(lastAccessTime, foo.getLastAccessTime());

        lastAccessTime = FileTime.fromMillis(12345678L);

        fileStore.setAttribute(createPath("/foo"), "lastAccessTime", lastAccessTime);
        assertEquals(lastAccessTime, foo.getLastAccessTime());
    }

    @Test
    public void testSetAttributeCreateTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileTime creationTime = FileTime.fromMillis(123456L);

        fileStore.setAttribute(createPath("/foo"), "basic:creationTime", creationTime);
        assertEquals(creationTime, foo.getCreationTime());

        creationTime = FileTime.fromMillis(1234567L);

        fileStore.setAttribute(createPath("/foo"), "memory:creationTime", creationTime);
        assertEquals(creationTime, foo.getCreationTime());

        creationTime = FileTime.fromMillis(12345678L);

        fileStore.setAttribute(createPath("/foo"), "creationTime", creationTime);
        assertEquals(creationTime, foo.getCreationTime());
    }

    @Test
    public void testSetAttributeReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        fileStore.setAttribute(createPath("/foo"), "memory:readOnly", true);
        assertTrue(foo.isReadOnly());

        fileStore.setAttribute(createPath("/foo"), "memory:readOnly", false);
        assertFalse(foo.isReadOnly());
    }

    @Test
    public void testSetAttributeHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        fileStore.setAttribute(createPath("/foo"), "memory:hidden", true);
        assertTrue(foo.isHidden());

        fileStore.setAttribute(createPath("/foo"), "memory:hidden", false);
        assertFalse(foo.isHidden());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetAttributeUnsupportedAttribute() throws IOException {
        root.add("foo", new Directory());

        fileStore.setAttribute(createPath("/foo"), "memory:dummy", true);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetAttributeUnsupportedType() throws IOException {
        root.add("foo", new Directory());

        fileStore.setAttribute(createPath("/foo"), "zipfs:size", true);
    }

    @Test(expected = ClassCastException.class)
    public void testSetAttributeInvalidValueType() throws IOException {
        root.add("foo", new Directory());

        fileStore.setAttribute(createPath("/foo"), "memory:hidden", 1);
    }

    @Test(expected = NoSuchFileException.class)
    public void testSetAttributeNonExisting() throws IOException {
        fileStore.setAttribute(createPath("/foo"), "memory:hidden", true);
    }
}
