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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
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
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.github.robtimus.filesystems.Messages;
import com.github.robtimus.filesystems.attribute.SimpleFileAttribute;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Directory;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Link;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Node;

@SuppressWarnings("nls")
class MemoryFileStoreTest {

    private MemoryFileStore store;
    private Directory root;

    private MemoryFileSystemProvider provider;
    private MemoryFileSystem fs;

    @BeforeEach
    void setupFileStore() {
        store = new MemoryFileStore();
        root = store.rootNode;

        provider = new MemoryFileSystemProvider(store);
        fs = new MemoryFileSystem(provider, store);
    }

    private MemoryPath createPath(String path) {
        return new MemoryPath(fs, path);
    }

    // Methods defined on FileStore

    @Test
    void testName() {
        assertEquals("/", store.name());
    }

    @Test
    void testType() {
        assertEquals("memory", store.type());
    }

    @Test
    void testIsReadOnly() {
        assertFalse(store.isReadOnly());
    }

    // Don't test getTotalSpace, getUsableSpace or getUnallocatedSpace, as these values are very volatile

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = { BasicFileAttributeView.class, MemoryFileAttributeView.class })
    void testSupportsFileAttributeView(Class<? extends FileAttributeView> type) {
        assertTrue(store.supportsFileAttributeView(type));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = { FileOwnerAttributeView.class, PosixFileAttributeView.class, DosFileAttributeView.class, AclFileAttributeView.class })
    void testSupportsFileAttributeViewNotSupported(Class<? extends FileAttributeView> type) {
        assertFalse(store.supportsFileAttributeView(type));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "basic", "memory" })
    void testSupportsFileAttributeView(String name) {
        assertTrue(store.supportsFileAttributeView(name));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "owner", "posix", "dos", "acl" })
    void testSupportsFileAttributeViewNotSupported(String name) {
        assertFalse(store.supportsFileAttributeView(name));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "totalSpace", "usableSpace", "unallocatedSpace" })
    void testGetAttribute(String attribute) throws IOException {
        assertNotNull(store.getAttribute(attribute));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "size", "owner" })
    void testGetAttributeNotSupported(String attribute) {
        assertThrows(UnsupportedOperationException.class, () -> store.getAttribute(attribute));
    }

    // The tests below do not call methods directly on store but instead on fs, provider or the result of createPath, to test the delegation as well.

    // MemoryFileStore.toRealPath

    @Test
    void testToRealPath() throws IOException {
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

    @Test
    void testToRealPathNotExisting() {
        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, path::toRealPath);
        assertEquals("/foo", exception.getFile());
    }

    @Test
    void testToRealPathBrokenLink() {
        root.add("foo", new Link("bar"));

        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, path::toRealPath);
        assertEquals("/bar", exception.getFile());
    }

    @Test
    void testToRealPathLinkLoop() {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        MemoryPath path = createPath("/foo");

        FileSystemException exception = assertThrows(FileSystemException.class, path::toRealPath);
        assertEquals("/foo", exception.getFile());
        assertEquals(MemoryMessages.maximumLinkDepthExceeded(), exception.getReason());
    }

    // MemoryFileStore.newInputStream

    @Test
    void testNewInputStream() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        try (InputStream input = provider.newInputStream(createPath("/foo/bar"))) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewInputStreamDeleteOnClose() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        OpenOption[] options = { StandardOpenOption.DELETE_ON_CLOSE };
        try (InputStream input = provider.newInputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertNull(foo.get("bar"));
    }

    @Test
    void testNewInputStreamNonExisting() {
        MemoryPath path = createPath("/foo/bar");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newInputStream(path));
        assertEquals("/foo/bar", exception.getFile());
        assertTrue(root.isEmpty());
    }

    @Test
    void testNewInputStreamDirectory() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newInputStream(path));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewInputStreamWithLinks() throws IOException {
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

    @Test
    void testNewInputStreamWithBrokenLink() {
        root.add("foo", new Link("bar"));

        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newInputStream(path));
        assertEquals("/bar", exception.getFile());
    }

    @Test
    void testNewInputStreamWithLinkLoop() {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        MemoryPath path = createPath("/foo");

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newInputStream(path));
        assertEquals("/foo", exception.getFile());
        assertEquals(MemoryMessages.maximumLinkDepthExceeded(), exception.getReason());
    }

    // MemoryFileStore.newOutputStream

    @Test
    void testNewOutputStreamExisting() throws IOException {
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
    void testNewOutputStreamExistingDeleteOnClose() throws IOException {
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
    void testNewOutputStreamExistingCreate() throws IOException {
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
    void testNewOutputStreamExistingCreateDeleteOnClose() throws IOException {
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

    @Test
    void testNewOutputStreamExistingCreateNew() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        MemoryPath path = createPath("/foo/bar");
        OpenOption[] options = { StandardOpenOption.CREATE_NEW };

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewOutputStreamExistingCreateNewDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        MemoryPath path = createPath("/foo/bar");
        OpenOption[] options = { StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE };

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewOutputStreamExistingReadOnly() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        OpenOption[] options = { StandardOpenOption.WRITE };

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewOutputStreamExistingReadOnlyDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        OpenOption[] options = { StandardOpenOption.DELETE_ON_CLOSE };

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewOutputStreamNonExistingNoCreate() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo/bar");
        OpenOption[] options = { StandardOpenOption.WRITE };

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo/bar", exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewOutputStreamNonExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewOutputStreamNonExistingCreateDeleteOnClose() throws IOException {
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
    void testNewOutputStreamNonExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE_NEW };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewOutputStreamNonExistingCreateNewDeleteOnClose() throws IOException {
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
    void testNewOutputStreamNonExistingCreateAndCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        OpenOption[] options = { StandardOpenOption.CREATE, StandardOpenOption.CREATE_NEW };
        try (OutputStream output = provider.newOutputStream(createPath("/foo/bar"), options)) {
            // don't do anything with the stream, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewOutputStreamNonExistingCreateNonExistingParent() {
        MemoryPath path = createPath("/foo/bar");
        OpenOption[] options = { StandardOpenOption.CREATE };

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo", exception.getMessage());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewOutputStreamNonExistingCreateNewNonExistingParent() {
        OpenOption[] options = { StandardOpenOption.CREATE_NEW };
        MemoryPath path = createPath("/foo/bar");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewOutputStreamNonExistingCreateReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        OpenOption[] options = { StandardOpenOption.CREATE };

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewOutputStreamNonExistingCreateNewReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        OpenOption[] options = { StandardOpenOption.CREATE_NEW };

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewOutputStreamDirectory() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");
        OpenOption[] options = { StandardOpenOption.WRITE };

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewOutputStreamDirectoryDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");
        OpenOption[] options = { StandardOpenOption.DELETE_ON_CLOSE };

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newOutputStream(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewOutputStreamWithLinks() throws IOException {
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
    void testNewOutputStreamBrokenWithLinkToExistingFolder() throws IOException {
        root.add("foo", new Link("bar"));

        try (OutputStream input = provider.newOutputStream(createPath("/foo"))) {
            // don't do anything with the stream, there's a separate test for that
        }
        assertThat(root.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewOutputStreamBrokenWithLinkToNonExistingFolder() {
        root.add("foo", new Link("bar/baz"));

        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newOutputStream(path));
        assertEquals("/bar", exception.getFile());
    }

    @Test
    void testNewOutputStreamWithLinkLoop() {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        MemoryPath path = createPath("/foo");

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newOutputStream(path));
        assertEquals("/foo", exception.getFile());
        assertEquals(MemoryMessages.maximumLinkDepthExceeded(), exception.getReason());
    }

    // MemoryFileStore.newByteChannel

    @Test
    void testNewByteChannelRead() throws IOException {
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
    void testNewByteChannelReadDeleteOnClose() throws IOException {
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
    void testNewByteChannelReadWithTruncate() throws IOException {
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
    void testNewByteChannelReadWithTruncateDeleteOnClose() throws IOException {
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
    void testNewByteChannelReadWithCreate() throws IOException {
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
    void testNewByteChannelReadWithCreateDeleteOnClose() throws IOException {
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
    void testNewByteChannelReadWithCreateNew() throws IOException {
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
    void testNewByteChannelReadWithCreateNewDeleteOnClose() throws IOException {
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

    @Test
    void testNewByteChannelReadNonExisting() {
        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewByteChannelReadNonExistingWithTruncate() {
        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.TRUNCATE_EXISTING);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewByteChannelReadNonExistingWithCreate() {
        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.CREATE);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewByteChannelReadNonExistingWithCreateNew() {
        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewByteChannelReadDirectory() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWithLinks() throws IOException {
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

    @Test
    void testNewByteChannelReadWithBrokenLink() {
        root.add("foo", new Link("bar"));

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());
    }

    @Test
    void testNewByteChannelReadWithLinkLoop() {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.noneOf(StandardOpenOption.class);

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(MemoryMessages.maximumLinkDepthExceeded(), exception.getReason());
    }

    @Test
    void testNewByteChannelWriteExisting() throws IOException {
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
    void testNewByteChannelWriteExistingDeleteOnClose() throws IOException {
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
    void testNewByteChannelWriteExistingCreate() throws IOException {
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
    void testNewByteChannelWriteExistingCreateDeleteOnClose() throws IOException {
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

    @Test
    void testNewByteChannelWriteExistingCreateNew() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewByteChannelWriteExistingCreateNewDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE);

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewByteChannelWriteExistingReadOnly() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewByteChannelWriteExistingReadOnlyDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewByteChannelWriteNonExistingNoCreate() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateDeleteOnClose() throws IOException {
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
    void testNewByteChannelWriteNonExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateNewDeleteOnClose() throws IOException {
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
    void testNewByteChannelWriteNonExistingCreateWithAttributes() throws IOException {
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
    void testNewByteChannelWriteNonExistingCreateNewWithAttributes() throws IOException {
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

    @Test
    void testNewByteChannelWriteNonExistingCreateWithInvalidAttributeView() {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("something:else", "foo"),
        };

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.newByteChannel(path, options, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("something").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateWithInvalidAttributes() {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("memory:other", "foo"),
        };

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.newByteChannel(path, options, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttribute("memory:other").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateNewWithInvalidAttributeView() {
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
        MemoryPath path = createPath("/foo/bar");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.newByteChannel(path, options, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("something").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateNewWithInvalidAttributes() {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("memory:other", "foo"),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        MemoryPath path = createPath("/foo/bar");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.newByteChannel(path, options, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttribute("memory:other").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateNonExistingParent() {
        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateNewNonExistingParent() {
        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateNewReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateReadAttribute() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        FileAttribute<Boolean> readOnly = new SimpleFileAttribute<>("memory:readOnly", true);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options, readOnly));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewByteChannelWriteNonExistingCreateNewReadOnlyAttribute() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        FileAttribute<Boolean> readOnly = new SimpleFileAttribute<>("memory:readOnly", true);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options, readOnly));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewByteChannelWriteDirectory() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteDirectoryDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelWriteWithLinks() throws IOException {
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
    void testNewByteChannelWriteBrokenWithLinkToExistingFolder() throws IOException {
        root.add("foo", new Link("bar"));

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }
        assertThat(root.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewByteChannelWriteBrokenWithLinkToNonExistingFolder() {
        root.add("foo", new Link("bar/baz"));

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/bar", exception.getFile());
    }

    @Test
    void testNewByteChannelWriteWithLinkLoop() {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.WRITE);

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(MemoryMessages.maximumLinkDepthExceeded(), exception.getReason());
    }

    @Test
    void testNewByteChannelReadWriteExisting() throws IOException {
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
    void testNewByteChannelReadWriteExistingDeleteOnClose() throws IOException {
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
    void testNewByteChannelReadWriteExistingCreate() throws IOException {
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
    void testNewByteChannelReadWriteExistingCreateDeleteOnClose() throws IOException {
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

    @Test
    void testNewByteChannelReadWriteExistingCreateNew() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewByteChannelReadWriteExistingCreateNewDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.DELETE_ON_CLOSE);

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewByteChannelReadWriteExistingReadOnly() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewByteChannelReadWriteExistingReadOnlyDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        bar.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testNewByteChannelReadWriteNonExistingNoCreate() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateDeleteOnClose() throws IOException {
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
    void testNewByteChannelReadWriteNonExistingCreateNew() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        try (SeekableByteChannel channel = provider.newByteChannel(createPath("/foo/bar"), options)) {
            // don't do anything with the channel, there's a separate test for that
        }

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("bar"), instanceOf(File.class));
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateNewDeleteOnClose() throws IOException {
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
    void testNewByteChannelReadWriteNonExistingCreateWithAttributes() throws IOException {
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
    void testNewByteChannelReadWriteNonExistingCreateNewWithAttributes() throws IOException {
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

    @Test
    void testNewByteChannelReadWriteNonExistingCreateWithInvalidAttributeView() {
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
        MemoryPath path = createPath("/foo/bar");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.newByteChannel(path, options, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("something").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateWithInvalidAttributes() {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("memory:other", "foo"),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        MemoryPath path = createPath("/foo/bar");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.newByteChannel(path, options, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttribute("memory:other").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateNewWithInvalidAttributeView() {
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
        MemoryPath path = createPath("/foo/bar");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.newByteChannel(path, options, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("something").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateNewWithInvalidAttributes() {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("memory:other", "foo"),
        };

        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        MemoryPath path = createPath("/foo/bar");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.newByteChannel(path, options, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttribute("memory:other").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateNonExistingParent() {

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateNewNonExistingParent() {
        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteNonExistingCreateNewReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteDirectory() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testNewByteChannelReadWriteDirectoryDeleteOnClose() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");
        Set<? extends OpenOption> options = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newByteChannel(path, options));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    // MemoryFileStore.newDirectoryStream

    @Test
    void testNewDirectoryStream() throws IOException {

        try (DirectoryStream<Path> stream = provider.newDirectoryStream(createPath("/"), entry -> true)) {
            assertNotNull(stream);
            // don't do anything with the stream, there's a separate test for that
        }
    }

    @Test
    void testNewDirectoryStreamNotExisting() {
        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newDirectoryStream(path, entry -> true));
        assertEquals("/foo", exception.getFile());
    }

    @Test
    void testNewDirectoryStreamNotDirectory() {
        root.add("foo", new File());

        MemoryPath path = createPath("/foo");

        NotDirectoryException exception = assertThrows(NotDirectoryException.class, () -> provider.newDirectoryStream(path, entry -> true));
        assertEquals("/foo", exception.getFile());
    }

    @Test
    void testNewDirectoryStreamWithLinks() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        bar.add("file", new File());

        foo.add("baz", new Link("bar"));
        root.add("baz", new Link("foo"));
        root.add("link", new Link("baz"));

        try (DirectoryStream<Path> stream = provider.newDirectoryStream(createPath("/link/baz"), entry -> true)) {
            assertNotNull(stream);
            Iterator<Path> iterator = stream.iterator();
            assertTrue(iterator.hasNext());
            assertEquals(createPath("/link/baz/file"), iterator.next());
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    void testNewDirectoryStreamWithBrokenLink() {
        root.add("foo", new Link("bar"));

        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.newDirectoryStream(path, entry -> true));
        assertEquals("/bar", exception.getFile());
    }

    @Test
    void testNewDirectoryStreamWithLinkLoop() {
        root.add("foo", new Link("bar"));
        root.add("bar", new Link("foo"));

        MemoryPath path = createPath("/foo");

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.newDirectoryStream(path, entry -> true));
        assertEquals("/foo", exception.getFile());
        assertEquals(MemoryMessages.maximumLinkDepthExceeded(), exception.getReason());
    }

    // MemoryFileStore.createDirectory

    @Test
    void testCreateDirectory() throws IOException {

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

    @Test
    void testCreateRoot() {
        MemoryPath path = createPath("/");

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.createDirectory(path));
        assertEquals("/", exception.getFile());
    }

    @Test
    void testCreateDirectoryWithAttributes() throws IOException {

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

    @Test
    void testCreateDirectoryWithInvalidAttributeView() {
        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("something:else", "foo"),
        };

        MemoryPath path = createPath("/foo");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> provider.createDirectory(path, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("something").getMessage(), exception.getMessage());

        assertNull(root.get("foo"));
    }

    @Test
    void testCreateDirectoryWithInvalidAttributes() {
        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("memory:other", "foo"),
        };

        MemoryPath path = createPath("/foo");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> provider.createDirectory(path, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttribute("memory:other").getMessage(), exception.getMessage());

        assertNull(root.get("foo"));
    }

    @Test
    void testCreateDirectoryReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());

        // content:
        // d /foo

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.createDirectory(path));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testCreateDirectoryNonExistingParent() {
        MemoryPath path = createPath("/foo/bar");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.createDirectory(path));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testCreateDirectoryExisting() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        MemoryPath path = createPath("/foo/bar");

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.createDirectory(path));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    // MemoryFileStore.createSymbolicLink

    @Test
    void testCreateSymbolicLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.createSymbolicLink(createPath("/link"), createPath("/foo/bar"));

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
        assertThat(root.get("link"), instanceOf(Link.class));

        Link link = (Link) root.get("link");
        assertEquals("/foo/bar", link.target);
    }

    @Test
    void testCreateSymbolicLinkCurrentDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.createSymbolicLink(createPath("/foo/link"), createPath("."));

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("link"), instanceOf(Link.class));

        Link link = (Link) foo.get("link");
        assertEquals(".", link.target);
        assertEquals(createPath("/foo"), createPath("/foo/link").toRealPath());
    }

    @Test
    void testCreateSymbolicLinkEmpty() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.createSymbolicLink(createPath("/foo/link"), createPath(""));

        assertSame(foo, root.get("foo"));
        assertThat(foo.get("link"), instanceOf(Link.class));

        Link link = (Link) foo.get("link");
        assertEquals("", link.target);
        assertEquals(createPath("/foo"), createPath("/foo/link").toRealPath());
    }

    @Test
    void testCreateSymbolicLinkExisting() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        MemoryPath link = createPath("/foo/bar");
        MemoryPath target = createPath("/bar");

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.createSymbolicLink(link, target));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testCreateSymbolicLinkWithAttributes() throws IOException {
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

    @Test
    void testCreateSymbolicLinkWithInvalidAttributeView() {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("something:else", "foo"),
        };

        MemoryPath link = createPath("/link");
        MemoryPath target = createPath("/foo/bar");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.createSymbolicLink(link, target, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("something").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
        assertNull(root.get("link"));
    }

    @Test
    void testCreateSymbolicLinkWithInvalidAttributes() {
        Directory foo = (Directory) root.add("foo", new Directory());

        FileAttribute<?>[] attributes = {
                new SimpleFileAttribute<>("basic:lastModifiedTime", FileTime.fromMillis(123456L)),
                new SimpleFileAttribute<>("basic:lastAccessTime", FileTime.fromMillis(1234567L)),
                new SimpleFileAttribute<>("basic:creationTime", FileTime.fromMillis(12345678L)),
                new SimpleFileAttribute<>("memory:readOnly", true),
                new SimpleFileAttribute<>("memory:hidden", true),
                new SimpleFileAttribute<>("memory:other", "foo"),
        };

        MemoryPath link = createPath("/link");
        MemoryPath target = createPath("/foo/bar");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.createSymbolicLink(link, target, attributes));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttribute("memory:other").getMessage(), exception.getMessage());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
        assertNull(root.get("link"));
    }

    @Test
    void testCreateSymbolicLinkNonExistingParent() {
        MemoryPath link = createPath("/foo/link");
        MemoryPath target = createPath("/bar");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.createSymbolicLink(link, target));
        assertEquals("/foo", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testCreateSymbolicLinkReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setReadOnly(true);

        MemoryPath link = createPath("/foo/link");
        MemoryPath target = createPath("/bar");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.createSymbolicLink(link, target));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    // MemoryFileStore.createLink

    @Test
    void testCreateLinkToFile() throws IOException {
        File foo = (File) root.add("foo", new File());

        provider.createLink(createPath("/bar"), createPath("foo"));

        assertSame(foo, root.get("foo"));
        assertSame(foo, root.get("bar"));
    }

    @Test
    void testCreateLinkToDirectory() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath link = createPath("/bar");
        MemoryPath existing = createPath("foo");

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.createLink(link, existing));
        assertEquals("/foo", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/foo").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertNull(root.get("bar"));
    }

    @Test
    void testCreateLinkToEmpty() {
        Directory foo = (Directory) root.add("foo", new Directory());

        MemoryPath link = createPath("/bar");
        MemoryPath existing = createPath("");

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.createLink(link, existing));
        assertEquals("/", exception.getFile());
        assertEquals(Messages.fileSystemProvider().isDirectory("/").getReason(), exception.getReason());

        assertSame(foo, root.get("foo"));
        assertNull(root.get("bar"));
    }

    @Test
    void testCreateLinkReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());
        Node bar = foo.add("bar", new File());
        root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        foo.setReadOnly(true);

        MemoryPath link = createPath("/foo/bar");
        MemoryPath existing = createPath("/baz");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.createLink(link, existing));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    @Test
    void testCreateLinkNonExistingParent() {
        root.add("baz", new File());

        MemoryPath link = createPath("/foo/bar");
        MemoryPath existing = createPath("/baz");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.createLink(link, existing));
        assertEquals("/foo", exception.getFile());

        assertNull(root.get("foo"));
    }

    @Test
    void testCreateLinkExisting() {
        File foo = (File) root.add("foo", new File());
        Node bar = root.add("bar", new Directory());

        MemoryPath link = createPath("/foo");
        MemoryPath existing = createPath("/bar");

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.createLink(link, existing));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, root.get("bar"));
    }

    @Test
    void testCreateLinkMissingExisting() {
        MemoryPath link = createPath("/foo");
        MemoryPath existing = createPath("/bar");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.createLink(link, existing));
        assertEquals("/bar", exception.getFile());

        assertTrue(root.isEmpty());
    }

    // MemoryFileStore.delete

    @Test
    void testDeleteNonExisting() {
        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.delete(path));
        assertEquals("/foo", exception.getFile());
    }

    @Test
    void testDeleteRoot() {
        MemoryPath path = createPath("/");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.delete(path));
        assertEquals("/", exception.getFile());
    }

    @Test
    void testDeleteFile() throws IOException {
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
    void testDeleteEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        provider.delete(createPath("/foo/bar"));

        assertSame(foo, root.get("foo"));
        assertNull(foo.get("bar"));
    }

    @Test
    void testDeleteNonEmptyDir() {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        Directory baz = (Directory) bar.add("baz", new Directory());

        // content:
        // d /foo
        // d /foo/bar
        // d /foo/bar/baz

        MemoryPath path = createPath("/foo/bar");

        DirectoryNotEmptyException exception = assertThrows(DirectoryNotEmptyException.class, () -> provider.delete(path));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertSame(baz, bar.get("baz"));
    }

    @Test
    void testDeleteLink() throws IOException {
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

    @Test
    void testDeleteReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.delete(path));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    // MemoryFileStore.deleteIfExists

    @Test
    void testDeleteIfExistsNonExisting() throws IOException {
        assertFalse(provider.deleteIfExists(createPath("/foo")));
    }

    @Test
    void testDeleteIfExistsRoot() {
        MemoryPath path = createPath("/");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.deleteIfExists(path));
        assertEquals("/", exception.getFile());
    }

    @Test
    void testDeleteIfExistsFile() throws IOException {
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
    void testDeleteIfExistsEmptyDir() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        assertTrue(provider.deleteIfExists(createPath("/foo/bar")));

        assertSame(foo, root.get("foo"));
        assertNull(foo.get("bar"));
    }

    @Test
    void testDeleteIfExistsNonEmptyDir() {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        Directory baz = (Directory) bar.add("baz", new Directory());

        // content:
        // d /foo
        // d /foo/bar
        // d /foo/bar/baz

        MemoryPath path = createPath("/foo/bar");

        DirectoryNotEmptyException exception = assertThrows(DirectoryNotEmptyException.class, () -> provider.deleteIfExists(path));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertSame(baz, bar.get("baz"));
    }

    @Test
    void testDeleteIfExistsLink() throws IOException {
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

    @Test
    void testDeleteIfExistsReadOnlyParent() {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());

        // content:
        // d /foo
        // d /foo/bar

        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.deleteIfExists(path));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
    }

    // MemoryFileStore.readSymbolicLink

    @Test
    void testReadSymbolicLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        foo.add("link", new Link("bar"));

        assertEquals(createPath("bar"), provider.readSymbolicLink(createPath("/foo/link")));
    }

    @Test
    void testReadSymbolicLinkFile() {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        foo.add("link", new Link("bar"));

        MemoryPath path = createPath("/foo/bar");

        NotLinkException exception = assertThrows(NotLinkException.class, () -> provider.readSymbolicLink(path));
        assertEquals("/foo/bar", exception.getFile());
    }

    @Test
    void testReadSymbolicLinkDirectory() {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());
        foo.add("link", new Link("bar"));

        MemoryPath path = createPath("/foo");

        NotLinkException exception = assertThrows(NotLinkException.class, () -> provider.readSymbolicLink(path));
        assertEquals("/foo", exception.getFile());
    }

    // MemoryFileStore.copy

    @Test
    void testCopySame() throws IOException {
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

    @Test
    void testCopyNonExisting() {
        Directory foo = (Directory) root.add("foo", new Directory());

        // content:
        // d /foo

        MemoryPath source = createPath("/foo/bar");
        MemoryPath target = createPath("/foo/baz");
        CopyOption[] options = {};

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.copy(source, target, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testCopyNonExistingTargetParent() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        MemoryPath source = createPath("/foo/bar");
        MemoryPath target = createPath("/baz/bar");
        CopyOption[] options = {};

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.copy(source, target, options));
        assertEquals("/baz", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertNull(root.get("baz"));
    }

    @Test
    void testCopyRoot() throws IOException {
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

    @Test
    void testCopyReplaceFile() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // d /foo/bar
        // f /baz

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo/bar");
        CopyOption[] options = {};

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.copy(source, target, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertSame(baz, root.get("baz"));
    }

    @Test
    void testCopyReplaceFileAllowed() throws IOException {
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

    @Test
    void testCopyReplaceNonEmptyDir() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo");
        CopyOption[] options = {};

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.copy(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertSame(baz, root.get("baz"));
    }

    @Test
    void testCopyReplaceNonEmptyDirAllowed() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo");
        CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };

        DirectoryNotEmptyException exception = assertThrows(DirectoryNotEmptyException.class, () -> provider.copy(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertSame(baz, root.get("baz"));
    }

    @Test
    void testCopyReplaceEmptyDir() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo");
        CopyOption[] options = {};

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.copy(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(baz, root.get("baz"));
    }

    @Test
    void testCopyReplaceEmptyDirAllowed() throws IOException {
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
    void testCopyFile() throws IOException {
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
    void testCopyFileWithAttributes() throws IOException {
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
    void testCopyEmptyDir() throws IOException {
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
    void testCopyEmptyDirWithAttributes() throws IOException {
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
    void testCopyNonEmptyDir() throws IOException {
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
    void testCopyNonEmptyDirWithAttributes() throws IOException {
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

    @Test
    void testCopyReadOnlyTargetParent() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        foo.setReadOnly(true);

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo/bar");
        CopyOption[] options = {};

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.copy(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(baz, root.get("baz"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testCopyLinkFollowLinks() throws IOException {
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
    void testCopyLinkNoFollowLinks() throws IOException {
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
    void testCopyLinkNoFollowLinksWithAttributes() throws IOException {
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
    void testCopyLinkBrokenNoFollowLinks() throws IOException {
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

    @Test
    void testCopyLinkLoopNoFollowLinks() {
        root.add("foo", new Directory());
        root.add("baz", new File());
        root.add("link1", new Link("link2"));
        root.add("link2", new Link("link1"));

        // content:
        // d /foo
        // f /baz
        // l /link1 -> /link2
        // l /link2 -> /link1

        MemoryPath source = createPath("/link2");
        MemoryPath target = createPath("/foo/bar");
        CopyOption[] options = { LinkOption.NOFOLLOW_LINKS };

        FileSystemException exception = assertThrows(FileSystemException.class, () -> provider.copy(source, target, options));
        assertEquals("/link2", exception.getFile());
        assertEquals(MemoryMessages.maximumLinkDepthExceeded(), exception.getReason());
    }

    // MemoryFileStore.move

    @Test
    void testMoveSame() throws IOException {
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

    @Test
    void testMoveNonExisting() {
        Directory foo = (Directory) root.add("foo", new Directory());

        // content:
        // d /foo

        MemoryPath source = createPath("/foo/bar");
        MemoryPath target = createPath("/foo/baz");
        CopyOption[] options = {};

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.move(source, target, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testMoveNonExistingTargetParent() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        MemoryPath source = createPath("/foo/bar");
        MemoryPath target = createPath("/baz/bar");
        CopyOption[] options = {};

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.move(source, target, options));
        assertEquals("/baz", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertNull(root.get("baz"));
    }

    @Test
    void testMoveEmptyRoot() {
        MemoryPath source = createPath("/");
        MemoryPath target = createPath("/baz");
        CopyOption[] options = {};

        DirectoryNotEmptyException exception = assertThrows(DirectoryNotEmptyException.class, () -> provider.move(source, target, options));
        assertEquals("/", exception.getFile());

        assertTrue(root.isEmpty());
    }

    @Test
    void testMoveNonEmptyRoot() {
        Directory foo = (Directory) root.add("foo", new Directory());

        // cotent:
        // d /foo

        MemoryPath source = createPath("/");
        MemoryPath target = createPath("/baz");
        CopyOption[] options = {};

        DirectoryNotEmptyException exception = assertThrows(DirectoryNotEmptyException.class, () -> provider.move(source, target, options));
        assertEquals("/", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertNull(root.get("baz"));
    }

    @Test
    void testMoveReplaceFile() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // d /foo/bar
        // f /baz

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo/bar");
        CopyOption[] options = {};

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.move(source, target, options));
        assertEquals("/foo/bar", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertSame(baz, root.get("baz"));
    }

    @Test
    void testMoveReplaceFileAllowed() throws IOException {
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

    @Test
    void testMoveReplaceNonEmptyDir() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo");
        CopyOption[] options = {};

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.move(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertSame(baz, root.get("baz"));
    }

    @Test
    void testMoveReplaceNonEmptyDirAllowed() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /foo/bar
        // f /baz

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo");
        CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };

        DirectoryNotEmptyException exception = assertThrows(DirectoryNotEmptyException.class, () -> provider.move(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertSame(baz, root.get("baz"));
    }

    @Test
    void testMoveReplaceEmptyDir() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo");
        CopyOption[] options = {};

        FileAlreadyExistsException exception = assertThrows(FileAlreadyExistsException.class, () -> provider.move(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(baz, root.get("baz"));
    }

    @Test
    void testMoveReplaceEmptyDirAllowed() throws IOException {
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
    void testMoveFile() throws IOException {
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
    void testMoveEmptyDir() throws IOException {
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
    void testMoveNonEmptyDir() throws IOException {
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
    void testMoveNonEmptyDirSameParent() throws IOException {
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

    @Test
    void testMoveReadOnlySourceParent() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File bar = (File) foo.add("bar", new File());

        // content:
        // d /foo
        // f /foo/bar

        foo.setReadOnly(true);

        MemoryPath source = createPath("/foo/bar");
        MemoryPath target = createPath("/baz");
        CopyOption[] options = {};

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.move(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(bar, foo.get("bar"));
        assertNull(root.get("baz"));
    }

    @Test
    void testMoveReadOnlyTargetParent() {
        Directory foo = (Directory) root.add("foo", new Directory());
        File baz = (File) root.add("baz", new File());

        // content:
        // d /foo
        // f /baz

        foo.setReadOnly(true);

        MemoryPath source = createPath("/baz");
        MemoryPath target = createPath("/foo/bar");
        CopyOption[] options = {};

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.move(source, target, options));
        assertEquals("/foo", exception.getFile());

        assertSame(foo, root.get("foo"));
        assertSame(baz, root.get("baz"));
        assertTrue(foo.isEmpty());
    }

    @Test
    void testMoveLink() throws IOException {
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
    void testIsSameFileEquals() throws IOException {
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
    void testIsSameFileExisting() throws IOException {
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

    @Test
    void testIsSameFileFirstNonExisting() {
        MemoryPath path = createPath("/foo");
        MemoryPath path2 = createPath("/");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.isSameFile(path, path2));
        assertEquals("/foo", exception.getFile());
    }

    @Test
    void testIsSameFileSecondNonExisting() {
        MemoryPath path = createPath("/");
        MemoryPath path2 = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.isSameFile(path, path2));
        assertEquals("/foo", exception.getFile());
    }

    // MemoryFileStore.isHidden

    @Test
    void testIsHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        foo.setHidden(true);
        assertTrue(provider.isHidden(createPath("/foo")));

        foo.setHidden(false);
        assertFalse(provider.isHidden(createPath("/foo")));
    }

    @Test
    void testIsHiddenNonExisting() {
        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.isHidden(path));
        assertEquals("/foo", exception.getFile());
    }

    // MemoryFileStore.getFileStore

    @Test
    void testGetFileStoreExisting() throws IOException {
        assertSame(store, provider.getFileStore(createPath("/")));
    }

    @Test
    void testGetFileStoreNonExisting() {
        MemoryPath path = createPath("/foo/bar");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.getFileStore(path));
        assertEquals("/foo/bar", exception.getFile());
    }

    // MemoryFileStore.checkAccess

    @Test
    void testCheckAccessNonExisting() {
        MemoryPath path = createPath("/foo/bar");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.checkAccess(path));
        assertEquals("/foo/bar", exception.getFile());
    }

    @Test
    void testCheckAccessNoModes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        provider.checkAccess(createPath("/foo/bar"));
    }

    @Test
    void testCheckAccessOnlyRead() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        provider.checkAccess(createPath("/foo/bar"), AccessMode.READ);
    }

    @Test
    void testCheckAccessOnlyWriteNotReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        provider.checkAccess(createPath("/foo/bar"), AccessMode.WRITE);
    }

    @Test
    void testCheckAccessOnlyWriteReadOnly() {
        Directory foo = (Directory) root.add("foo", new Directory());
        Directory bar = (Directory) foo.add("bar", new Directory());
        bar.setReadOnly(true);

        MemoryPath path = createPath("/foo/bar");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.checkAccess(path, AccessMode.WRITE));
        assertEquals("/foo/bar", exception.getFile());
    }

    @Test
    void testCheckAccessOnlyExecuteFile() {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new File());

        MemoryPath path = createPath("/foo/bar");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> provider.checkAccess(path, AccessMode.EXECUTE));
        assertEquals("/foo/bar", exception.getFile());
    }

    @Test
    void testCheckAccessOnlyExecuteDirectory() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());

        provider.checkAccess(createPath("/foo/bar"), AccessMode.EXECUTE);
    }

    @Test
    void testCheckAccessLink() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());
        Link link = (Link) foo.add("link", new Link("bar"));
        link.setReadOnly(true);

        provider.checkAccess(createPath("/foo/link"), AccessMode.WRITE);
    }

    @Test
    void testCheckAccessBrokenLink() {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.add("bar", new Directory());
        Link link = (Link) foo.add("link", new Link("baz"));
        link.setReadOnly(true);

        MemoryPath path = createPath("/foo/link");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.checkAccess(path, AccessMode.WRITE));
        assertEquals("/foo/baz", exception.getFile());
    }

    // MemoryFileStore.setTimes

    @Test
    void setTimesAllTimes() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        FileTime oldLastModified = foo.getLastModifiedTime();
        FileTime oldLastAccess = foo.getLastAccessTime();
        FileTime oldCreation = foo.getCreationTime();

        FileTime newLastModified = FileTime.fromMillis(oldLastModified.toMillis() + 5000);
        FileTime newLastAccess = FileTime.fromMillis(oldLastAccess.toMillis() + 10000);
        FileTime newCreation = FileTime.fromMillis(oldCreation.toMillis() + 15000);

        BasicFileAttributeView view = provider.getFileAttributeView(createPath("/foo"), BasicFileAttributeView.class);
        view.setTimes(newLastModified, newLastAccess, newCreation);

        assertEquals(newLastModified, foo.getLastModifiedTime());
        assertEquals(newLastAccess, foo.getLastAccessTime());
        assertEquals(newCreation, foo.getCreationTime());
    }

    @Test
    void setTimesOnlyLastModified() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        FileTime oldLastModified = foo.getLastModifiedTime();
        FileTime oldLastAccess = foo.getLastAccessTime();
        FileTime oldCreation = foo.getCreationTime();

        FileTime newLastModified = FileTime.fromMillis(oldLastModified.toMillis() + 5000);

        BasicFileAttributeView view = provider.getFileAttributeView(createPath("/foo"), BasicFileAttributeView.class);
        view.setTimes(newLastModified, null, null);

        assertEquals(newLastModified, foo.getLastModifiedTime());
        assertEquals(oldLastAccess, foo.getLastAccessTime());
        assertEquals(oldCreation, foo.getCreationTime());
    }

    @Test
    void setTimesOnlyLastAccess() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        FileTime oldLastModified = foo.getLastModifiedTime();
        FileTime oldLastAccess = foo.getLastAccessTime();
        FileTime oldCreation = foo.getCreationTime();

        FileTime newLastAccess = FileTime.fromMillis(oldLastAccess.toMillis() + 5000);

        BasicFileAttributeView view = provider.getFileAttributeView(createPath("/foo"), BasicFileAttributeView.class);
        view.setTimes(null, newLastAccess, null);

        assertEquals(oldLastModified, foo.getLastModifiedTime());
        assertEquals(newLastAccess, foo.getLastAccessTime());
        assertEquals(oldCreation, foo.getCreationTime());
    }

    @Test
    void setTimesOnlyCreate() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        FileTime oldLastModified = foo.getLastModifiedTime();
        FileTime oldLastAccess = foo.getLastAccessTime();
        FileTime oldCreation = foo.getCreationTime();

        FileTime newCreation = FileTime.fromMillis(oldCreation.toMillis() + 5000);

        BasicFileAttributeView view = provider.getFileAttributeView(createPath("/foo"), BasicFileAttributeView.class);
        view.setTimes(null, null, newCreation);

        assertEquals(oldLastModified, foo.getLastModifiedTime());
        assertEquals(oldLastAccess, foo.getLastAccessTime());
        assertEquals(newCreation, foo.getCreationTime());
    }

    // MemoryFileStore.setReadOnly

    @Test
    void testSetReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        assertFalse(foo.isReadOnly());

        MemoryFileAttributeView view = provider.getFileAttributeView(createPath("/foo"), MemoryFileAttributeView.class);

        view.setReadOnly(true);
        assertTrue(foo.isReadOnly());

        view.setReadOnly(false);
        assertFalse(foo.isReadOnly());
    }

    @Test
    void testSetReadOnlyNonExisting() {
        MemoryFileAttributeView view = provider.getFileAttributeView(createPath("/foo"), MemoryFileAttributeView.class);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> view.setReadOnly(true));
        assertEquals("/foo", exception.getFile());
    }

    // MemoryFileStore.setHidden

    @Test
    void testSetHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        assertFalse(foo.isHidden());

        MemoryFileAttributeView view = provider.getFileAttributeView(createPath("/foo"), MemoryFileAttributeView.class);

        view.setHidden(true);
        assertTrue(foo.isHidden());

        view.setHidden(false);
        assertFalse(foo.isHidden());
    }

    @Test
    void testSetHiddenNonExisting() {
        MemoryFileAttributeView view = provider.getFileAttributeView(createPath("/foo"), MemoryFileAttributeView.class);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> view.setHidden(true));
        assertEquals("/foo", exception.getFile());
    }

    // MemoryFileStore.readAttributes (MemoryFileAttributes variant)

    @Test
    void testReadAttributesDirectory() throws IOException {
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
    void testReadAttributesFile() throws IOException {
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

    @Test
    void testReadAttributesNonExisting() {
        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.readAttributes(path, MemoryFileAttributes.class));
        assertEquals("/foo", exception.getFile());
    }

    @Test
    void testReadAttributesLinkFollowLinks() throws IOException {
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
    void testReadAttributesLinkNoFollowLinks() throws IOException {
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

    @Test
    void testReadAttributesLinkBroken() {
        Link foo = (Link) root.add("foo", new Link("bar"));

        foo.setReadOnly(true);
        foo.setHidden(true);

        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.readAttributes(path, MemoryFileAttributes.class));
        assertEquals("/bar", exception.getFile());
    }

    // MemoryFileStore.readAttributes (map variant)

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "lastModifiedTime", "basic:lastModifiedTime", "memory:lastModifiedTime" })
    void testReadAttributesMapLastModifiedTime(String attributeName) throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("lastModifiedTime", foo.getLastModifiedTime());
        assertEquals(expected, attributes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "lastAccessTime", "basic:lastAccessTime", "memory:lastAccessTime" })
    void testReadAttributesMapLastAccessTime(String attributeName) throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("lastAccessTime", foo.getLastAccessTime());
        assertEquals(expected, attributes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "creationTime", "basic:creationTime", "memory:creationTime" })
    void testReadAttributesMapCreateTime(String attributeName) throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("creationTime", foo.getCreationTime());
        assertEquals(expected, attributes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "size", "basic:size", "memory:size" })
    void testReadAttributesMapSize(String attributeName) throws IOException {
        File foo = (File) root.add("foo", new File());
        foo.setContent(new byte[1024]);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("size", foo.getSize());
        assertEquals(expected, attributes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "isRegularFile", "basic:isRegularFile", "memory:isRegularFile" })
    void testReadAttributesMapIsRegularFile(String attributeName) throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("isRegularFile", foo.isRegularFile());
        assertEquals(expected, attributes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "isDirectory", "basic:isDirectory", "memory:isDirectory" })
    void testReadAttributesMapIsDirectory(String attributeName) throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "isSymbolicLink", "basic:isSymbolicLink", "memory:isSymbolicLink" })
    void testReadAttributesMapIsSymbolicLink(String attributeName) throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("isSymbolicLink", false);
        assertEquals(expected, attributes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "isOther", "basic:isOther", "memory:isOther" })
    void testReadAttributesMapIsOther(String attributeName) throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("isOther", false);
        assertEquals(expected, attributes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "fileKey", "basic:fileKey", "memory:fileKey" })
    void testReadAttributesMapFileKey(String attributeName) throws IOException {
        root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), attributeName);
        Map<String, ?> expected = Collections.singletonMap("fileKey", null);
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapNoTypeMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "lastModifiedTime,creationTime,isDirectory");
        Map<String, Object> expected = new HashMap<>();
        expected.put("lastModifiedTime", foo.getLastModifiedTime());
        expected.put("creationTime", foo.getCreationTime());
        expected.put("isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapNoTypeAll() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "*");
        Map<String, Object> expected = new HashMap<>();
        expected.put("lastModifiedTime", foo.getLastModifiedTime());
        expected.put("lastAccessTime", foo.getLastAccessTime());
        expected.put("creationTime", foo.getCreationTime());
        expected.put("size", foo.getSize());
        expected.put("isRegularFile", foo.isRegularFile());
        expected.put("isDirectory", foo.isDirectory());
        expected.put("isSymbolicLink", false);
        expected.put("isOther", false);
        expected.put("fileKey", null);
        assertEquals(expected, attributes);

        attributes = provider.readAttributes(createPath("/foo"), "lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapBasicMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:lastModifiedTime,creationTime,isDirectory");
        Map<String, Object> expected = new HashMap<>();
        expected.put("lastModifiedTime", foo.getLastModifiedTime());
        expected.put("creationTime", foo.getCreationTime());
        expected.put("isDirectory", foo.isDirectory());
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapBasicAll() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "basic:*");
        Map<String, Object> expected = new HashMap<>();
        expected.put("lastModifiedTime", foo.getLastModifiedTime());
        expected.put("lastAccessTime", foo.getLastAccessTime());
        expected.put("creationTime", foo.getCreationTime());
        expected.put("size", foo.getSize());
        expected.put("isRegularFile", foo.isRegularFile());
        expected.put("isDirectory", foo.isDirectory());
        expected.put("isSymbolicLink", false);
        expected.put("isOther", false);
        expected.put("fileKey", null);
        assertEquals(expected, attributes);

        attributes = provider.readAttributes(createPath("/foo"), "basic:lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapMemoryReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:readOnly");
        Map<String, ?> expected = Collections.singletonMap("readOnly", true);
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapMemoryHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setHidden(true);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:hidden");
        Map<String, ?> expected = Collections.singletonMap("hidden", true);
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapMemoryMultiple() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:lastModifiedTime,creationTime,readOnly");
        Map<String, Object> expected = new HashMap<>();
        expected.put("lastModifiedTime", foo.getLastModifiedTime());
        expected.put("creationTime", foo.getCreationTime());
        expected.put("readOnly", true);
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapMemoryAll() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        Map<String, Object> attributes = provider.readAttributes(createPath("/foo"), "memory:*");
        Map<String, Object> expected = new HashMap<>();
        expected.put("lastModifiedTime", foo.getLastModifiedTime());
        expected.put("lastAccessTime", foo.getLastAccessTime());
        expected.put("creationTime", foo.getCreationTime());
        expected.put("size", foo.getSize());
        expected.put("isRegularFile", foo.isRegularFile());
        expected.put("isDirectory", foo.isDirectory());
        expected.put("isSymbolicLink", false);
        expected.put("isOther", false);
        expected.put("fileKey", null);
        expected.put("readOnly", true);
        expected.put("hidden", false);
        assertEquals(expected, attributes);

        attributes = provider.readAttributes(createPath("/foo"), "memory:lastModifiedTime,*");
        assertEquals(expected, attributes);
    }

    @Test
    void testReadAttributesMapUnsupportedAttribute() {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> provider.readAttributes(path, "memory:lastModifiedTime,readOnly,dummy"));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttribute("dummy").getMessage(), exception.getMessage());
    }

    @Test
    void testReadAttributesMapUnsupportedAttributePrefix() {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.readAttributes(path, "dummy:lastModifiedTime"));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("dummy").getMessage(), exception.getMessage());
    }

    @Test
    void testReadAttributesMapUnsupportedType() {
        Directory foo = (Directory) root.add("foo", new Directory());
        foo.setReadOnly(true);

        MemoryPath path = createPath("/foo");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> provider.readAttributes(path, "zipfs:*"));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("zipfs").getMessage(), exception.getMessage());
    }

    // MemoryFileStore.setAttribute

    @Test
    void testSetAttributeLastModifiedTime() throws IOException {
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
    void testSetAttributeLastAccessTime() throws IOException {
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
    void testSetAttributeCreationTime() throws IOException {
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
    void testSetAttributeReadOnly() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.setAttribute(createPath("/foo"), "memory:readOnly", true);
        assertTrue(foo.isReadOnly());

        provider.setAttribute(createPath("/foo"), "memory:readOnly", false);
        assertFalse(foo.isReadOnly());
    }

    @Test
    void testSetAttributeHidden() throws IOException {
        Directory foo = (Directory) root.add("foo", new Directory());

        provider.setAttribute(createPath("/foo"), "memory:hidden", true);
        assertTrue(foo.isHidden());

        provider.setAttribute(createPath("/foo"), "memory:hidden", false);
        assertFalse(foo.isHidden());
    }

    @Test
    void testSetAttributeUnsupportedAttribute() {
        root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> provider.setAttribute(path, "memory:dummy", true));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttribute("dummy").getMessage(), exception.getMessage());
    }

    @Test
    void testSetAttributeUnsupportedView() {
        root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> provider.setAttribute(path, "zipfs:size", true));
        assertEquals(Messages.fileSystemProvider().unsupportedFileAttributeView("zipfs").getMessage(), exception.getMessage());
    }

    @Test
    void testSetAttributeInvalidValueType() {
        root.add("foo", new Directory());

        MemoryPath path = createPath("/foo");

        assertThrows(ClassCastException.class, () -> provider.setAttribute(path, "memory:hidden", 1));
    }

    @Test
    void testSetAttributeNonExisting() {
        MemoryPath path = createPath("/foo");

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> provider.setAttribute(path, "memory:hidden", true));
        assertEquals("/foo", exception.getFile());
    }

    @Test
    void testClear() {
        root.add("foo", new Directory());
        root.add("bar", new File());

        assertFalse(root.isEmpty());

        store.clear();

        assertTrue(root.isEmpty());
    }
}
