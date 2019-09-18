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
import static org.junit.Assert.assertArrayEquals;
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
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import com.github.robtimus.filesystems.attribute.SimpleFileAttribute;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Directory;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Link;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Node;

@SuppressWarnings({ "nls", "javadoc" })
public class MemoryFileStoreTest {

    private MemoryFileStore store;
    private Directory root;

    private MemoryFileSystemProvider provider;
    private MemoryFileSystem fs;

    @Before
    public void setupFileStore() {
        store = new MemoryFileStore();
        root = store.rootNode;

        provider = new MemoryFileSystemProvider(store);
        fs = new MemoryFileSystem(provider, store);
    }

    private MemoryPath createPath(String path) {
        return new MemoryPath(fs, path);
    }

    // The tests below do not call methods directly on store but instead on fs, provider or the result of createPath, to test the delegation as well.

    // MemoryFileStore.toRealPath

    @Test
    public void testToRealPath() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        foo.add("baz", new Link("bar"));

        root.add("bar", new Directory());
        root.add("baz", new Link("foo"));
        root.add("link", new Link("baz"));
        foo.add("link2", new Link("/baz"));

        root.add("broken", new Link("not existing"));

        testToRealPath("/", "/");
        testToRealPath("/foo/bar", "/foo/bar");
        testToRealPath("/foo/../bar", "/bar");
        testToRealPath("/foo/./bar", "/foo/bar");

        testToRealPath("", "/");
        testToRealPath("foo/bar", "/foo/bar");
        testToRealPath("foo/../bar", "/bar");
        testToRealPath("foo/./bar", "/foo/bar");

        testToRealPath("/foo/baz", "/foo/bar");
        testToRealPath("/foo/baz", "/foo/baz", LinkOption.NOFOLLOW_LINKS);
        testToRealPath("/baz", "/foo");
        testToRealPath("/baz", "/baz", LinkOption.NOFOLLOW_LINKS);
        testToRealPath("/link", "/foo");
        testToRealPath("/link", "/link", LinkOption.NOFOLLOW_LINKS);
        testToRealPath("/foo/link2", "/foo");
        testToRealPath("/foo/link2", "/foo/link2", LinkOption.NOFOLLOW_LINKS);

        testToRealPath("/broken", "/broken", LinkOption.NOFOLLOW_LINKS);
    }

    private void testToRealPath(String path, String expected, LinkOption... linkOptions) throws IOException {
        MemoryPath expectedPath = createPath(expected);
        Path actual = createPath(path).toRealPath(linkOptions);
        assertEquals(expectedPath, actual);
    }

    @Test(expected = NoSuchFileException.class)
    public void testToRealPathNotExisting() throws IOException {
        createPath("/foo").toRealPath();
    }

    @Test(expected = NoSuchFileException.class)
    public void testToRealPathBrokenLink() throws IOException {
        root.add("foo", new Link("bar"));

        createPath("/foo").toRealPath();
    }

    @Test(expected = FileSystemException.class)
    public void testToRealPathLinkLoop() throws IOException {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        createPath("/foo").toRealPath();
    }

    // MemoryFileStore.newInputStream

    @Test
    public void testNewInputStream() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        try (InputStream input = provider.newInputStream(createPath("/foo/bar"))) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewInputStreamDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.DELETE_ON_CLOSE };
        try (InputStream input = provider.newInputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertNull(foo.get("bar"));
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewInputStreamNonExisting() throws IOException {
        try (InputStream input = provider.newInputStream(createPath("/foo/bar"))) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewInputStreamDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        try (InputStream input = provider.newInputStream(createPath("/foo"))) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testNewInputStreamWithLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        foo.add("baz", new Link("bar"));
        root.add("baz", new Link("foo"));
        root.add("link", new Link("baz"));

        try (InputStream input = provider.newInputStream(createPath("/link/baz"))) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertSame(bar, foo.get("bar"));
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewInputStreamWithBrokenLink() throws IOException {
        root.add("foo", new Link("bar"));

        try (InputStream input = provider.newInputStream(createPath("/foo"))) {
            // don't do anything with the stream, there's a separate test for that
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewInputStreamWithLinkLoop() throws IOException {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        try (InputStream input = provider.newInputStream(createPath("/foo"))) {
            // don't do anything with the stream, there's a separate test for that
        }
    }

    // MemoryFileStore.newOutputStream

    @Test
    public void testNewOutputStreamExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.WRITE };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewOutputStreamNonExistingCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewOutputStreamNonExistingCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
            assertThat(foo.get("bar"), instanceOf(File.class));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewOutputStreamNonExistingCreateAndCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE, StandardOpenOption.CREATE_NEW };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewOutputStreamNonExistingCreateNonExistingParent() throws IOException {

        OpenOption[] options = { StandardOpenOption.CREATE };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewOutputStreamNonExistingCreateNewNonExistingParent() throws IOException {

        OpenOption[] options = { StandardOpenOption.CREATE_NEW };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo"), options)) {
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
        try (OutputStream output = provider.newOutputStream(createPath("/foo"), options)) {
            // don't do anything with the stream, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testNewOutputStreamWithLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        foo.add("baz", new Link("bar"));
        root.add("baz", new Link("foo"));
        root.add("link", new Link("baz"));

        try (OutputStream input = provider.newOutputStream(createPath("/link/baz"))) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewOutputStreamBrokenWithLinkToExistingFolder() throws IOException {
        root.add("foo", new Link("bar"));

        try (OutputStream input = provider.newOutputStream(createPath("/foo"))) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertThat(root.get("bar"), instanceOf(File.class));
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewOutputStreamBrokenWithLinkToNonExistingFolder() throws IOException {
        root.add("foo", new Link("bar/baz"));

        try (OutputStream input = provider.newOutputStream(createPath("/foo"))) {
            // don't do anything with the stream, there's a separate test for that
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewOutputStreamWithLinkLoop() throws IOException {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        try (OutputStream input = provider.newOutputStream(createPath("/foo"))) {
            // don't do anything with the stream, there's a separate test for that
        }
    }

    // MemoryFileStore.newByteChannel

    @Test
    public void testNewByteChannelRead() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelReadWithTruncate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.TRUNCATE_EXISTING);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelReadWithTruncateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelReadWithCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelReadWithCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelReadWithCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelReadWithCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadNonExisting() throws IOException {

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadNonExistingWithTruncate() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.TRUNCATE_EXISTING);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadNonExistingWithCreate() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadNonExistingWithCreateNew() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelReadDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testNewByteChannelReadWithLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        foo.add("baz", new Link("bar"));
        root.add("baz", new Link("foo"));
        root.add("link", new Link("baz"));

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/link/baz"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }
        assertSame(bar, foo.get("bar"));
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadWithBrokenLink() throws IOException {
        root.add("foo", new Link("bar"));

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelReadWithLinkLoop() throws IOException {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }
    }

    @Test
    public void testNewByteChannelWriteExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewByteChannelWriteNonExistingCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewByteChannelWriteNonExistingCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, attributes)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, attributes)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, attributes)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, attributes)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelWriteNonExistingCreateNonExistingParent() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelWriteNonExistingCreateNewNonExistingParent() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelWriteNonExistingCreateReadAttribute() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        FileAttribute<Boolean> readOnly = new SimpleFileAttribute<>("memory:readOnly", true);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, readOnly)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertThat(foo.get("bar"), instanceOf(File.class));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelWriteNonExistingCreateNewReadOnlyAttribute() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        FileAttribute<Boolean> readOnly = new SimpleFileAttribute<>("memory:readOnly", true);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, readOnly)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertThat(foo.get("bar"), instanceOf(File.class));
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelWriteDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
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
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testNewByteChannelWriteWithLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        foo.add("baz", new Link("bar"));
        root.add("baz", new Link("foo"));
        root.add("link", new Link("baz"));

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/link/baz"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelWriteBrokenWithLinkToExistingFolder() throws IOException {
        root.add("foo", new Link("bar"));

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }
        assertThat(root.get("bar"), instanceOf(File.class));
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelWriteBrokenWithLinkToNonExistingFolder() throws IOException {
        root.add("foo", new Link("bar/baz"));

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelWriteWithLinkLoop() throws IOException {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }
    }

    @Test
    public void testNewByteChannelReadWriteExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelReadWriteExistingDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelReadWriteExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    public void testNewByteChannelReadWriteExistingCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertSame(bar, foo.get("bar"));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testNewByteChannelReadWriteExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testNewByteChannelReadWriteExistingCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelReadWriteExistingReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelReadWriteExistingReadOnlyDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadWriteNonExistingNoCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testNewByteChannelReadWriteNonExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewByteChannelReadWriteNonExistingCreateDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertThat(foo.get("bar"), instanceOf(File.class));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelReadWriteNonExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    public void testNewByteChannelReadWriteNonExistingCreateNewDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
            assertThat(foo.get("bar"), instanceOf(File.class));
        }

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    public void testNewByteChannelReadWriteNonExistingCreateWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", false),
                new SimpleFileAttribute<>("memory:hidden", true),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, attributes)) {
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
    public void testNewByteChannelReadWriteNonExistingCreateNewWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", false),
                new SimpleFileAttribute<>("memory:hidden", true),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, attributes)) {
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
    public void testNewByteChannelReadWriteNonExistingCreateWithInvalidAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("something:else", "foo"),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, attributes)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNewByteChannelReadWriteNonExistingCreateNewWithInvalidAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("something:else", "foo"),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options, attributes)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadWriteNonExistingCreateNonExistingParent() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewByteChannelReadWriteNonExistingCreateNewNonExistingParent() throws IOException {

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelReadWriteNonExistingCreateReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testNewByteChannelReadWriteNonExistingCreateNewReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelReadWriteDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = FileSystemException.class)
    public void testNewByteChannelReadWriteDirectoryDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    // MemoryFileStore.newDirectoryStream

    @Test
    public void testNewDirectoryStream() throws IOException {

        try (DirectoryStream<Path> stream = provider.newDirectoryStream(createPath("/"), AcceptAllFilter.INSTANCE)) {
            assertNotNull(stream);
            // don't do anything with the stream, there's a separate test for that
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewDirectoryStreamNotExisting() throws IOException {

        provider.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE);
    }

    @Test(expected = NotDirectoryException.class)
    public void testNewDirectoryStreamNotDirectory() throws IOException {

        root.add("foo", new File());

        provider.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE);
    }

    @Test
    public void testNewDirectoryStreamWithLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        bar.add("file", new File());

        foo.add("baz", new Link("bar"));
        root.add("baz", new Link("foo"));
        root.add("link", new Link("baz"));

        try (DirectoryStream<Path> stream = provider.newDirectoryStream(createPath("/link/baz"), AcceptAllFilter.INSTANCE)) {
            assertNotNull(stream);
            Iterator<Path> iterator = stream.iterator();
            assertTrue(iterator.hasNext());
            assertEquals(createPath("/link/baz/file"), iterator.next());
            assertFalse(iterator.hasNext());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testNewDirectoryStreamWithBrokenLink() throws IOException {
        root.add("foo", new Link("bar"));

        provider.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE);
    }

    @Test(expected = FileSystemException.class)
    public void testNewDirectoryStreamWithLinkLoop() throws IOException {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        provider.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE);
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

        provider.createDirectory(createPath("/foo"));
        assertThat(root.get("foo"), instanceOf(Directory.class));

        Directory foo = (Directory) root.get("foo");
        assertFalse(foo.isReadOnly());
        assertFalse(foo.isHidden());

        provider.createDirectory(createPath("/foo/bar"));
        assertThat(foo.get("bar"), instanceOf(Directory.class));

        Directory bar = (Directory) foo.get("bar");
        assertFalse(bar.isReadOnly());
        assertFalse(bar.isHidden());

        provider.createDirectory(createPath("bar"));
        assertThat(root.get("bar"), instanceOf(Directory.class));

        bar = (Directory) root.get("bar");
        assertFalse(bar.isReadOnly());
        assertFalse(bar.isHidden());
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testCreateRoot() throws IOException {
        provider.createDirectory(createPath("/"));
    }

    @Test
    public void testCreateDirectoryWithAttributes() throws IOException {

        provider.createDirectory(createPath("/foo"),
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
            provider.createDirectory(createPath("/foo"),
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
            provider.createDirectory(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testCreateDirectoryNonExistingParent() throws IOException {
        try {
            provider.createDirectory(createPath("/foo/bar"));
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
            provider.createDirectory(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    // MemoryFileStore.createSymbolicLink

    @Test
    public void testCreateSymbolicLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.createSymbolicLink(createPath("/link"), createPath("/foo/bar"));

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
        assertThat(root.get("link"), instanceOf(Link.class));

        Link link = (Link) root.get("link");
        assertEquals("/foo/bar", link.target);
    }

    @Test
    public void testCreateSymbolicLinkCurrentDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.createSymbolicLink(createPath("/foo/link"), createPath("."));

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("link"), instanceOf(Link.class));

        Link link = (Link) foo.get("link");
        assertEquals(".", link.target);
        assertEquals(createPath("/foo"), createPath("/foo/link").toRealPath());
    }

    @Test
    public void testCreateSymbolicLinkEmpty() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.createSymbolicLink(createPath("/foo/link"), createPath(""));

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("link"), instanceOf(Link.class));

        Link link = (Link) foo.get("link");
        assertEquals("", link.target);
        assertEquals(createPath("/foo"), createPath("/foo/link").toRealPath());
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testCreateSymbolicLinkExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        try {
            provider.createSymbolicLink(createPath("/foo/bar"), createPath("/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test
    public void testCreateSymbolicLinkWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", false),
                new SimpleFileAttribute<>("memory:hidden", true),
        };

        provider.createSymbolicLink(createPath("/link"), createPath("/foo/bar"), attributes);

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
        assertThat(root.get("link"), instanceOf(Link.class));

        Link link = (Link) root.get("link");
        assertEquals("/foo/bar", link.target);
        assertEquals(123456L, link.getLastModifiedTime().toMillis());
        assertEquals(1234567L, link.getLastAccessTime().toMillis());
        assertEquals(12345678L, link.getCreationTime().toMillis());
        assertFalse(link.isReadOnly());
        assertTrue(link.isHidden());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateSymbolicLinkWithInvalidAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("something:else", "foo"),
        };

        try {
            provider.createSymbolicLink(createPath("/link"), createPath("/foo/bar"), attributes);
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
            assertNull(root.get("link"));
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testCreateSymbolicLinkNonExistingParent() throws IOException {

        try {
            provider.createSymbolicLink(createPath("/foo/link"), createPath("/bar"));
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testCreateSymbolicLinkReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        try {
            provider.createSymbolicLink(createPath("/foo/link"), createPath("/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertTrue(foo.isEmpty());
        }
    }

    // MemoryFileStore.createLink

    @Test
    public void testCreateLinkToFile() throws IOException {
        File foo = (File) root.add("foo", new File());

        provider.createLink(createPath("/bar"), createPath("foo"));

        assertSame(foo, root.get("foo"));
        assertSame(foo, root.get("bar"));
    }

    @Test(expected = FileSystemException.class)
    public void testCreateLinkToDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        try {
            provider.createLink(createPath("/bar"), createPath("foo"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertNull(root.get("bar"));
        }
    }

    @Test(expected = FileSystemException.class)
    public void testCreateLinkToEmpty() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        try {
            provider.createLink(createPath("/bar"), createPath(""));
        } finally {
            assertSame(foo, root.get("foo"));
            assertNull(root.get("bar"));
        }
    }

    @Test(expected = AccessDeniedException.class)
    public void testCreateLinkReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Node bar = foo.add("bar", new File());
        root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        foo.setReadOnly(true);

        try {
            provider.createLink(createPath("/foo/bar"), createPath("/baz"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testCreateLinkNonExistingParent() throws IOException {
        root.add("baz", new File());

        try {
            provider.createLink(createPath("/foo/bar"), createPath("/baz"));
        } finally {
            assertNull(root.get("foo"));
        }
    }

    @Test(expected = FileAlreadyExistsException.class)
    public void testCreateLinkExisting() throws IOException {
        File foo = (File) root.add("foo", new File());
        Node bar = root.add("bar", new Directory());

        try {
            provider.createLink(createPath("/foo"), createPath("/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, root.get("bar"));
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testCreateLinkMissingExisting() throws IOException {

        try {
            provider.createLink(createPath("/foo"), createPath("/bar"));
        } finally {
            assertTrue(root.isEmpty());
        }
    }

    // MemoryFileStore.delete

    @Test(expected = NoSuchFileException.class)
    public void testDeleteNonExisting() throws IOException {
        provider.delete(createPath("/foo"));
    }

    @Test(expected = AccessDeniedException.class)
    public void testDeleteRoot() throws IOException {
        provider.delete(createPath("/"));
    }

    @Test
    public void testDeleteFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        provider.delete(createPath("/foo/bar"));

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

        provider.delete(createPath("/foo/bar"));

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
            provider.delete(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, bar.get("baz"));
        }
    }

    @Test
    public void testDeleteLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        bar.setContent("hello".getBytes());
        foo.add("link", new Link("bar"));

        assertArrayEquals("hello".getBytes(), store.getContent(createPath("/foo/link")));

        // content:
        // d /foo
        // f /foo/bar
        // l /foo/link -> /foo/bar

        provider.delete(createPath("/foo/link"));

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertNull(foo.get("link"));
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
            provider.delete(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    // MemoryFileStore.deleteIfExists

    @Test
    public void testDeleteIfExistsNonExisting() throws IOException {
        assertFalse(provider.deleteIfExists(createPath("/foo")));
    }

    @Test(expected = AccessDeniedException.class)
    public void testDeleteIfExistsRoot() throws IOException {
        provider.deleteIfExists(createPath("/"));
    }

    @Test
    public void testDeleteIfExistsFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        assertTrue(provider.deleteIfExists(createPath("/foo/bar")));

        assertSame(foo, root.get("foo"));
        assertNull(foo.get("bar"));
    }

    @Test
    public void testDeleteIfExistsEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        assertTrue(provider.deleteIfExists(createPath("/foo/bar")));

        assertSame(foo, root.get("foo"));
        assertNull(foo.get("bar"));
    }

    @Test(expected = DirectoryNotEmptyException.class)
    public void testDeleteIfExistsNonEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        Directory baz = (Directory) bar.add("baz", new Directory());

        // content:
        // d /foo
        // d /foo/bar
        // d /foo/bar/baz

        try {
            provider.deleteIfExists(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
            assertSame(baz, bar.get("baz"));
        }
    }

    @Test
    public void testDeleteIfExistsLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        bar.setContent("hello".getBytes());
        foo.add("link", new Link("bar"));

        assertArrayEquals("hello".getBytes(), store.getContent(createPath("/foo/link")));

        // content:
        // d /foo
        // f /foo/bar
        // l /foo/link -> /foo/bar

        provider.deleteIfExists(createPath("/foo/link"));

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertNull(foo.get("link"));
    }

    @Test(expected = AccessDeniedException.class)
    public void testDeleteIfExistsReadOnlyParent() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        foo.setReadOnly(true);

        try {
            provider.deleteIfExists(createPath("/foo/bar"));
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(bar, foo.get("bar"));
        }
    }

    // MemoryFileStore.readSymbolicLink

    @Test
    public void testReadSymbolicLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        foo.add("link", new Link("bar"));

        assertEquals(createPath("bar"), provider.readSymbolicLink(createPath("/foo/link")));
    }

    @Test(expected = NotLinkException.class)
    public void testReadSymbolicLinkFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        foo.add("link", new Link("bar"));

        provider.readSymbolicLink(createPath("/foo/bar"));
    }

    @Test(expected = NotLinkException.class)
    public void testReadSymbolicLinkDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        foo.add("link", new Link("bar"));

        provider.readSymbolicLink(createPath("/foo"));
    }

    // MemoryFileStore.copy

    @Test
    public void testCopySame() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        root.add("baz", bar);

        // content:
        // d /foo
        // d /foo/bar
        // d /baz

        CopyOption[] options = {};
        provider.copy(createPath("/"), createPath(""), options);
        provider.copy(createPath("/foo"), createPath("foo"), options);
        provider.copy(createPath("/foo/bar"), createPath("foo/bar"), options);
        provider.copy(createPath("/foo/bar"), createPath("/baz"), options);

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertTrue(bar.isEmpty());
        assertSame(bar, root.get("baz"));
    }

    @Test(expected = NoSuchFileException.class)
    public void testCopyNonExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        // content:
        // d /foo

        CopyOption[] options = {};
        try {
            provider.copy(createPath("/foo/bar"), createPath("/foo/baz"), options);
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
            provider.copy(createPath("/foo/bar"), createPath("/baz/bar"), options);
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
        provider.copy(createPath("/"), createPath("/foo/bar"), options);

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
            provider.copy(createPath("/baz"), createPath("/foo/bar"), options);
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
        provider.copy(createPath("/baz"), createPath("/foo/bar"), options);

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
            provider.copy(createPath("/baz"), createPath("/foo"), options);
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
            provider.copy(createPath("/baz"), createPath("/foo"), options);
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
            provider.copy(createPath("/baz"), createPath("/foo"), options);
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
        provider.copy(createPath("/baz"), createPath("/foo"), options);

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
        provider.copy(createPath("/baz"), createPath("/foo/bar"), options);

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
        provider.copy(createPath("/baz"), createPath("/foo/bar"), options);

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
        provider.copy(createPath("/baz"), createPath("/foo/bar"), options);

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
        provider.copy(createPath("/baz"), createPath("/foo/bar"), options);

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
        provider.copy(createPath("/baz"), createPath("/foo/bar"), options);

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
        provider.copy(createPath("/baz"), createPath("/foo/bar"), options);

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
            provider.copy(createPath("/baz"), createPath("/foo/bar"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(baz, root.get("baz"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testCopyLinkFollowLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());
        Link link = (Link) root.add("link", new Link("baz"));

        // content:
        // d /foo
        // f /baz
        // l /link -> /baz

        baz.setHidden(true);

        CopyOption[] options = {};
        provider.copy(createPath("/link"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(File.class));
        assertSame(foo, root.get("foo"));
        assertNotSame(baz, foo.get("bar"));
        assertSame(baz, root.get("baz"));
        assertFalse(foo.get("bar").isHidden());
        assertSame(link, root.get("link"));
    }

    @Test
    public void testCopyLinkNoFollowLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());
        Link link1 = (Link) root.add("link1", new Link("baz"));
        Link link2 = (Link) root.add("link2", new Link("link1"));

        // content:
        // d /foo
        // f /baz
        // l /link1 -> /baz
        // l /link2 -> /link1

        baz.setHidden(true);

        CopyOption[] options = { LinkOption.NOFOLLOW_LINKS };
        provider.copy(createPath("/link2"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(Link.class));
        assertSame(foo, root.get("foo"));
        assertSame(baz, root.get("baz"));
        assertFalse(foo.get("bar").isHidden());
        assertNotSame(baz, foo.get("link1"));
        assertSame(link1, root.get("link1"));
        assertNotSame(baz, foo.get("link2"));
        assertSame(link2, root.get("link2"));

        Link bar = (Link) foo.get("bar");
        assertEquals("baz", bar.target);
    }

    @Test
    public void testCopyLinkNoFollowLinksWithAttributes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());
        Link link = (Link) root.add("link", new Link("baz"));

        // content:
        // d /foo
        // f /baz
        // l /link -> /baz

        link.setHidden(true);

        CopyOption[] options = { StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS };
        provider.copy(createPath("/link"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(Link.class));
        assertSame(foo, root.get("foo"));
        assertSame(baz, root.get("baz"));
        assertNotSame(baz, foo.get("link"));
        assertSame(link, root.get("link"));

        Link bar = (Link) foo.get("bar");
        assertTrue(bar.isHidden());
        assertEquals(link.getLastModifiedTime(), bar.getLastModifiedTime());
        assertEquals(link.getLastAccessTime(), bar.getLastAccessTime());
        assertEquals(link.getCreationTime(), bar.getCreationTime());
    }

    @Test
    public void testCopyLinkBrokenNoFollowLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());
        Link link1 = (Link) root.add("link1", new Link("baz"));
        Link link2 = (Link) root.add("link2", new Link("link"));

        // content:
        // d /foo
        // f /baz
        // l /link1 -> /baz
        // l /link2 -> /link

        baz.setHidden(true);

        CopyOption[] options = { LinkOption.NOFOLLOW_LINKS };
        provider.copy(createPath("/link2"), createPath("/foo/bar"), options);

        assertThat(foo.get("bar"), instanceOf(Link.class));
        assertSame(foo, root.get("foo"));
        assertSame(baz, root.get("baz"));
        assertFalse(foo.get("bar").isHidden());
        assertNotSame(baz, foo.get("link1"));
        assertSame(link1, root.get("link1"));
        assertNotSame(baz, foo.get("link2"));
        assertSame(link2, root.get("link2"));

        Link bar = (Link) foo.get("bar");
        assertEquals("link", bar.target);
    }

    @Test(expected = FileSystemException.class)
    public void testCopyLinkLoopNoFollowLinks() throws IOException {
        root.add("foo", new Directory());
        root.add("baz", new File());
        root.add("link1", new Link("link2"));
        root.add("link2", new Link("link1"));

        // content:
        // d /foo
        // f /baz
        // l /link1 -> /link2
        // l /link2 -> /link1

        CopyOption[] options = { LinkOption.NOFOLLOW_LINKS };
        provider.copy(createPath("/link2"), createPath("/foo/bar"), options);
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
        provider.move(createPath("/"), createPath(""), options);
        provider.move(createPath("/foo"), createPath("foo"), options);
        provider.move(createPath("/foo/bar"), createPath("foo/bar"), options);

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
            provider.move(createPath("/foo/bar"), createPath("/foo/baz"), options);
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
            provider.move(createPath("/foo/bar"), createPath("/baz/bar"), options);
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
            provider.move(createPath("/"), createPath("/baz"), options);
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
            provider.move(createPath("/"), createPath("/baz"), options);
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
            provider.move(createPath("/baz"), createPath("/foo/bar"), options);
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
        provider.move(createPath("/baz"), createPath("/foo/bar"), options);

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
            provider.move(createPath("/baz"), createPath("/foo"), options);
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
            provider.move(createPath("/baz"), createPath("/foo"), options);
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
            provider.move(createPath("/baz"), createPath("/foo"), options);
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
        provider.move(createPath("/baz"), createPath("/foo"), options);

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
        provider.move(createPath("/baz"), createPath("/foo/bar"), options);

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
        provider.move(createPath("/baz"), createPath("/foo/bar"), options);

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
        provider.move(createPath("/baz"), createPath("/foo/bar"), options);

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
            provider.move(createPath("/foo"), createPath("/baz"), options);
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
            provider.move(createPath("/foo/bar"), createPath("/baz"), options);
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
            provider.move(createPath("/baz"), createPath("/foo/bar"), options);
        } finally {
            assertSame(foo, root.get("foo"));
            assertSame(baz, root.get("baz"));
            assertTrue(foo.isEmpty());
        }
    }

    @Test
    public void testMoveLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());
        Link link = (Link) root.add("link", new Link("baz"));

        // content:
        // d /foo
        // f /baz
        // l /link -> /baz

        CopyOption[] options = {};
        provider.move(createPath("/link"), createPath("/foo/bar"), options);

        assertSame(foo, root.get("foo"));
        assertSame(baz, root.get("baz"));
        assertSame(link, foo.get("bar"));
        assertNull(root.get("link"));
    }

    // MemoryFileStore.isSameFile

    @Test
    public void testIsSameFileEquals() throws IOException {
        assertTrue(provider.isSameFile(createPath("/"), createPath("/")));
        assertTrue(provider.isSameFile(createPath("/foo"), createPath("/foo")));
        assertTrue(provider.isSameFile(createPath("/foo/bar"), createPath("/foo/bar")));

        assertTrue(provider.isSameFile(createPath(""), createPath("")));
        assertTrue(provider.isSameFile(createPath("foo"), createPath("foo")));
        assertTrue(provider.isSameFile(createPath("foo/bar"), createPath("foo/bar")));

        assertTrue(provider.isSameFile(createPath(""), createPath("/")));
        assertTrue(provider.isSameFile(createPath("/"), createPath("")));
    }

    @Test
    public void testIsSameFileExisting() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        foo.add("link", new Link("bar"));

        assertTrue(provider.isSameFile(createPath("/"), createPath("")));
        assertTrue(provider.isSameFile(createPath("/foo"), createPath("foo")));
        assertTrue(provider.isSameFile(createPath("/foo/bar"), createPath("foo/bar")));

        assertTrue(provider.isSameFile(createPath(""), createPath("/")));
        assertTrue(provider.isSameFile(createPath("foo"), createPath("/foo")));
        assertTrue(provider.isSameFile(createPath("foo/bar"), createPath("/foo/bar")));

        assertTrue(provider.isSameFile(createPath("/foo/bar"), createPath("foo/link")));
        assertTrue(provider.isSameFile(createPath("/foo/bar"), createPath("/foo/link")));
        assertTrue(provider.isSameFile(createPath("foo/bar"), createPath("foo/link")));
        assertTrue(provider.isSameFile(createPath("foo/bar"), createPath("/foo/link")));
        assertTrue(provider.isSameFile(createPath("/foo/link"), createPath("foo/bar")));
        assertTrue(provider.isSameFile(createPath("/foo/link"), createPath("/foo/bar")));
        assertTrue(provider.isSameFile(createPath("foo/link"), createPath("foo/bar")));
        assertTrue(provider.isSameFile(createPath("foo/link"), createPath("/foo/bar")));

        assertFalse(provider.isSameFile(createPath("foo"), createPath("foo/bar")));
    }

    @Test(expected = NoSuchFileException.class)
    public void testIsSameFileFirstNonExisting() throws IOException {
        provider.isSameFile(createPath("/foo"), createPath("/"));
    }

    @Test(expected = NoSuchFileException.class)
    public void testIsSameFileSecondNonExisting() throws IOException {
        provider.isSameFile(createPath("/"), createPath("/foo"));
    }

    // MemoryFileStore.isHidden

    @Test
    public void testIsHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setHidden(true);
        assertTrue(provider.isHidden(createPath("/foo")));

        foo.setHidden(false);
        assertFalse(provider.isHidden(createPath("/foo")));
    }

    @Test(expected = NoSuchFileException.class)
    public void testIsHiddenNonExisting() throws IOException {
        provider.isHidden(createPath("/foo"));
    }

    // MemoryFileStore.getFileStore

    @Test
    public void testGetFileStoreExisting() throws IOException {
        assertSame(store, provider.getFileStore(createPath("/")));
    }

    @Test(expected = NoSuchFileException.class)
    public void testGetFileStoreNonExisting() throws IOException {
        provider.getFileStore(createPath("/foo/bar"));
    }

    // MemoryFileStore.checkAccess

    @Test(expected = NoSuchFileException.class)
    public void testCheckAccessNonExisting() throws IOException {
        provider.checkAccess(createPath("/foo/bar"));
    }

    @Test
    public void testCheckAccessNoModes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        provider.checkAccess(createPath("/foo/bar"));
    }

    @Test
    public void testCheckAccessOnlyRead() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        provider.checkAccess(createPath("/foo/bar"), AccessMode.READ);
    }

    @Test
    public void testCheckAccessOnlyWriteNotReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        provider.checkAccess(createPath("/foo/bar"), AccessMode.WRITE);
    }

    @Test(expected = AccessDeniedException.class)
    public void testCheckAccessOnlyWriteReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        bar.setReadOnly(true);

        provider.checkAccess(createPath("/foo/bar"), AccessMode.WRITE);
    }

    @Test(expected = AccessDeniedException.class)
    public void testCheckAccessOnlyExecuteFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        provider.checkAccess(createPath("/foo/bar"), AccessMode.EXECUTE);
    }

    @Test
    public void testCheckAccessOnlyExecuteDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        provider.checkAccess(createPath("/foo/bar"), AccessMode.EXECUTE);
    }

    @Test
    public void testCheckAccessLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());
        Link link = (Link) foo.add("link", new Link("bar"));
        link.setReadOnly(true);

        provider.checkAccess(createPath("/foo/link"), AccessMode.WRITE);
    }

    @Test(expected = NoSuchFileException.class)
    public void testCheckAccessBrokenLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());
        Link link = (Link) foo.add("link", new Link("baz"));
        link.setReadOnly(true);

        provider.checkAccess(createPath("/foo/link"), AccessMode.WRITE);
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

        createPath("/foo").setTimes(newLastModified, newLastAccess, newCreation, true);

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

        createPath("/foo").setTimes(newLastModified, null, null, true);

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

        createPath("/foo").setTimes(null, newLastAccess, null, true);

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

        createPath("/foo").setTimes(null, null, newCreation, true);

        assertEquals(oldLastModified, foo.getLastModifiedTime());
        assertEquals(oldLastAccess, foo.getLastAccessTime());
        assertEquals(newCreation, foo.getCreationTime());
    }

    // MemoryFileStore.setReadOnly

    @Test
    public void testSetReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        assertFalse(foo.isReadOnly());

        createPath("/foo").setReadOnly(true, true);
        assertTrue(foo.isReadOnly());

        createPath("/foo").setReadOnly(false, true);
        assertFalse(foo.isReadOnly());
    }

    @Test(expected = NoSuchFileException.class)
    public void testSetReadOnlyNonExisting() throws IOException {
        createPath("/foo").setReadOnly(true, true);
    }

    // MemoryFileStore.setHidden

    @Test
    public void testSetHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        assertFalse(foo.isHidden());

        createPath("/foo").setHidden(true, true);
        assertTrue(foo.isHidden());

        createPath("/foo").setHidden(false, true);
        assertFalse(foo.isHidden());
    }

    @Test(expected = NoSuchFileException.class)
    public void testSetHiddenNonExisting() throws IOException {
        createPath("/foo").setHidden(true, true);
    }

    // MemoryFileStore.readAttributes (MemoryFileAttributes variant)

    @Test
    public void testReadAttributesDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);
        foo.setHidden(true);

        MemoryFileAttributes attributes = createPath("/foo").readAttributes(true);

        assertEquals(foo.getLastModifiedTime(), attributes.lastModifiedTime());
        assertEquals(foo.getLastAccessTime(), attributes.lastAccessTime());
        assertEquals(foo.getCreationTime(), attributes.creationTime());
        assertFalse(attributes.isRegularFile());
        assertTrue(attributes.isDirectory());
        assertFalse(attributes.isSymbolicLink());
        assertFalse(attributes.isOther());
        assertEquals(0, attributes.size());
        assertNull(attributes.fileKey());

        assertTrue(attributes.isReadOnly());
        assertTrue(attributes.isHidden());
    }

    @Test
    public void testReadAttributesFile() throws IOException {
        File foo = (File) root.add("foo", new File());

        foo.setReadOnly(true);
        foo.setHidden(true);

        MemoryFileAttributes attributes = createPath("/foo").readAttributes(true);

        assertEquals(foo.getLastModifiedTime(), attributes.lastModifiedTime());
        assertEquals(foo.getLastAccessTime(), attributes.lastAccessTime());
        assertEquals(foo.getCreationTime(), attributes.creationTime());
        assertTrue(attributes.isRegularFile());
        assertFalse(attributes.isDirectory());
        assertFalse(attributes.isSymbolicLink());
        assertFalse(attributes.isOther());
        assertEquals(0, attributes.size());
        assertNull(attributes.fileKey());

        assertTrue(attributes.isReadOnly());
        assertTrue(attributes.isHidden());
    }

    @Test(expected = NoSuchFileException.class)
    public void testReadAttributesNonExisting() throws IOException {
        createPath("/foo").readAttributes(true);
    }

    @Test
    public void testReadAttributesLinkFollowLinks() throws IOException {
        Directory bar = (Directory) root.add("bar", new Directory());
        Link foo = (Link) root.add("foo", new Link("bar"));

        foo.setReadOnly(true);
        foo.setHidden(true);

        MemoryFileAttributes attributes = createPath("/foo").readAttributes(true);

        assertEquals(bar.getLastModifiedTime(), attributes.lastModifiedTime());
        assertEquals(bar.getLastAccessTime(), attributes.lastAccessTime());
        assertEquals(bar.getCreationTime(), attributes.creationTime());
        assertFalse(attributes.isRegularFile());
        assertTrue(attributes.isDirectory());
        assertFalse(attributes.isSymbolicLink());
        assertFalse(attributes.isOther());
        assertEquals(0, attributes.size());
        assertNull(attributes.fileKey());

        assertFalse(attributes.isReadOnly());
        assertFalse(attributes.isHidden());
    }

    @Test
    public void testReadAttributesLinkNoFollowLinks() throws IOException {
        Link foo = (Link) root.add("foo", new Link("bar"));

        foo.setReadOnly(true);
        foo.setHidden(true);

        MemoryFileAttributes attributes = createPath("/foo").readAttributes(false);

        assertEquals(foo.getLastModifiedTime(), attributes.lastModifiedTime());
        assertEquals(foo.getLastAccessTime(), attributes.lastAccessTime());
        assertEquals(foo.getCreationTime(), attributes.creationTime());
        assertFalse(attributes.isRegularFile());
        assertFalse(attributes.isDirectory());
        assertTrue(attributes.isSymbolicLink());
        assertFalse(attributes.isOther());
        assertEquals(0, attributes.size());
        assertNull(attributes.fileKey());

        assertTrue(attributes.isReadOnly());
        assertTrue(attributes.isHidden());
    }

    @Test(expected = NoSuchFileException.class)
    public void testReadAttributesLinkBroken() throws IOException {
        Link foo = (Link) root.add("foo", new Link("bar"));

        foo.setReadOnly(true);
        foo.setHidden(true);

        createPath("/foo").readAttributes(true);
    }

    // MemoryFileStore.readAttributes (map variant)

    @Test
    public void testReadAttributesMapNoTypeLastModifiedTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "lastModifiedTime");
        Map<String, ?> expected = Collections.singletonMap("basic:lastModifiedTime", foo.getLastModifiedTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeLastAccessTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "lastAccessTime");
        Map<String, ?> expected = Collections.singletonMap("basic:lastAccessTime", foo.getLastAccessTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeCreateTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "creationTime");
        Map<String, ?> expected = Collections.singletonMap("basic:creationTime", foo.getCreationTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeBasicSize() throws IOException {
        File foo = (File) root.add("foo", new File());
        foo.setContent(new byte[1024]);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "size");
        Map<String, ?> expected = Collections.singletonMap("basic:size", foo.getSize());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeIsRegularFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "isRegularFile");
        Map<String, ?> expected = Collections.singletonMap("basic:isRegularFile", foo.isRegularFile());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeIsDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "isDirectory");
        Map<String, ?> expected = Collections.singletonMap("basic:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeIsSymbolicLink() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "isSymbolicLink");
        Map<String, ?> expected = Collections.singletonMap("basic:isSymbolicLink", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeIsOther() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "isOther");
        Map<String, ?> expected = Collections.singletonMap("basic:isOther", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeFileKey() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "fileKey");
        Map<String, ?> expected = Collections.singletonMap("basic:fileKey", null);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "lastModifiedTime,creationTime,isDirectory");
        Map<String, Object> expected = new HashMap<>();
        expected.put("basic:lastModifiedTime", foo.getLastModifiedTime());
        expected.put("basic:creationTime", foo.getCreationTime());
        expected.put("basic:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapNoTypeAll() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "*");
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

        attributes = provider.readAttributes(createPath("/foo"), "basic:lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicLastModifiedTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:lastModifiedTime");
        Map<String, ?> expected = Collections.singletonMap("basic:lastModifiedTime", foo.getLastModifiedTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicLastAccessTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:lastAccessTime");
        Map<String, ?> expected = Collections.singletonMap("basic:lastAccessTime", foo.getLastAccessTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicCreateTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:creationTime");
        Map<String, ?> expected = Collections.singletonMap("basic:creationTime", foo.getCreationTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicSize() throws IOException {
        File foo = (File) root.add("foo", new File());
        foo.setContent(new byte[1024]);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:size");
        Map<String, ?> expected = Collections.singletonMap("basic:size", foo.getSize());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicIsRegularFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:isRegularFile");
        Map<String, ?> expected = Collections.singletonMap("basic:isRegularFile", foo.isRegularFile());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicIsDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:isDirectory");
        Map<String, ?> expected = Collections.singletonMap("basic:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicIsSymbolicLink() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:isSymbolicLink");
        Map<String, ?> expected = Collections.singletonMap("basic:isSymbolicLink", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicIsOther() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:isOther");
        Map<String, ?> expected = Collections.singletonMap("basic:isOther", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicFileKey() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:fileKey");
        Map<String, ?> expected = Collections.singletonMap("basic:fileKey", null);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:lastModifiedTime,creationTime,isDirectory");
        Map<String, Object> expected = new HashMap<>();
        expected.put("basic:lastModifiedTime", foo.getLastModifiedTime());
        expected.put("basic:creationTime", foo.getCreationTime());
        expected.put("basic:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapBasicAll() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:*");
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

        attributes = provider.readAttributes(createPath("/foo"), "basic:lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryLastModifiedTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:lastModifiedTime");
        Map<String, ?> expected = Collections.singletonMap("memory:lastModifiedTime", foo.getLastModifiedTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryLastAccessTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:lastAccessTime");
        Map<String, ?> expected = Collections.singletonMap("memory:lastAccessTime", foo.getLastAccessTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryCreateTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:creationTime");
        Map<String, ?> expected = Collections.singletonMap("memory:creationTime", foo.getCreationTime());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemorySize() throws IOException {
        File foo = (File) root.add("foo", new File());
        foo.setContent(new byte[1024]);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:size");
        Map<String, ?> expected = Collections.singletonMap("memory:size", foo.getSize());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryIsRegularFile() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:isRegularFile");
        Map<String, ?> expected = Collections.singletonMap("memory:isRegularFile", foo.isRegularFile());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryIsDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:isDirectory");
        Map<String, ?> expected = Collections.singletonMap("memory:isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryIsSymbolicLink() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:isSymbolicLink");
        Map<String, ?> expected = Collections.singletonMap("memory:isSymbolicLink", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryIsOther() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:isOther");
        Map<String, ?> expected = Collections.singletonMap("memory:isOther", false);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryFileKey() throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:fileKey");
        Map<String, ?> expected = Collections.singletonMap("memory:fileKey", null);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:readOnly");
        Map<String, ?> expected = Collections.singletonMap("memory:readOnly", true);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setHidden(true);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:hidden");
        Map<String, ?> expected = Collections.singletonMap("memory:hidden", true);
        assertEquals(expected, attributes);
    }

    @Test
    public void testReadAttributesMapMemoryMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:lastModifiedTime,creationTime,readOnly");
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

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:*");
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

        attributes = provider.readAttributes(createPath("/foo"), "memory:lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadAttributesMapUnsupportedAttribute() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        provider.readAttributes(createPath("/foo"), "memory:lastModifiedTime,readOnly,dummy");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReadAttributesMapUnsupportedAttributePrefix() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        provider.readAttributes(createPath("/foo"), "dummy:lastModifiedTime");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReadAttributesMapUnsupportedType() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        provider.readAttributes(createPath("/foo"), "zipfs:*");
    }

    // MemoryFileStore.setAttribute

    @Test
    public void testSetAttributeLastModifiedTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileTime lastModifiedTime = FileTime.fromMillis(123456L);

        provider.setAttribute(createPath("/foo"), "basic:lastModifiedTime", lastModifiedTime);
        assertEquals(lastModifiedTime, foo.getLastModifiedTime());

        lastModifiedTime = FileTime.fromMillis(1234567L);

        provider.setAttribute(createPath("/foo"), "memory:lastModifiedTime", lastModifiedTime);
        assertEquals(lastModifiedTime, foo.getLastModifiedTime());

        lastModifiedTime = FileTime.fromMillis(12345678L);

        provider.setAttribute(createPath("/foo"), "lastModifiedTime", lastModifiedTime);
        assertEquals(lastModifiedTime, foo.getLastModifiedTime());
    }

    @Test
    public void testSetAttributeLastAccessTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileTime lastAccessTime = FileTime.fromMillis(123456L);

        provider.setAttribute(createPath("/foo"), "basic:lastAccessTime", lastAccessTime);
        assertEquals(lastAccessTime, foo.getLastAccessTime());

        lastAccessTime = FileTime.fromMillis(1234567L);

        provider.setAttribute(createPath("/foo"), "memory:lastAccessTime", lastAccessTime);
        assertEquals(lastAccessTime, foo.getLastAccessTime());

        lastAccessTime = FileTime.fromMillis(12345678L);

        provider.setAttribute(createPath("/foo"), "lastAccessTime", lastAccessTime);
        assertEquals(lastAccessTime, foo.getLastAccessTime());
    }

    @Test
    public void testSetAttributeCreateTime() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileTime creationTime = FileTime.fromMillis(123456L);

        provider.setAttribute(createPath("/foo"), "basic:creationTime", creationTime);
        assertEquals(creationTime, foo.getCreationTime());

        creationTime = FileTime.fromMillis(1234567L);

        provider.setAttribute(createPath("/foo"), "memory:creationTime", creationTime);
        assertEquals(creationTime, foo.getCreationTime());

        creationTime = FileTime.fromMillis(12345678L);

        provider.setAttribute(createPath("/foo"), "creationTime", creationTime);
        assertEquals(creationTime, foo.getCreationTime());
    }

    @Test
    public void testSetAttributeReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.setAttribute(createPath("/foo"), "memory:readOnly", true);
        assertTrue(foo.isReadOnly());

        provider.setAttribute(createPath("/foo"), "memory:readOnly", false);
        assertFalse(foo.isReadOnly());
    }

    @Test
    public void testSetAttributeHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.setAttribute(createPath("/foo"), "memory:hidden", true);
        assertTrue(foo.isHidden());

        provider.setAttribute(createPath("/foo"), "memory:hidden", false);
        assertFalse(foo.isHidden());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetAttributeUnsupportedAttribute() throws IOException {
        root.add("foo", new Directory());

        provider.setAttribute(createPath("/foo"), "memory:dummy", true);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSetAttributeUnsupportedType() throws IOException {
        root.add("foo", new Directory());

        provider.setAttribute(createPath("/foo"), "zipfs:size", true);
    }

    @Test(expected = ClassCastException.class)
    public void testSetAttributeInvalidValueType() throws IOException {
        root.add("foo", new Directory());

        provider.setAttribute(createPath("/foo"), "memory:hidden", 1);
    }

    @Test(expected = NoSuchFileException.class)
    public void testSetAttributeNonExisting() throws IOException {
        provider.setAttribute(createPath("/foo"), "memory:hidden", true);
    }

    @Test
    public void testClear() {
        root.add("foo", new Directory());
        root.add("bar", new File());

        assertFalse(root.isEmpty());

        store.clear();

        assertTrue(root.isEmpty());
    }
}
