/*
 * MemoryFileSystemProviderTest.java
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.Messages;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Node;

@SuppressWarnings({ "nls", "javadoc" })
public class MemoryFileSystemProviderTest {

    private MemoryFileStore fileStore;

    private MemoryFileSystemProvider provider;
    private MemoryFileSystem fs;

    @BeforeEach
    public void setupProvider() {
        fileStore = spy(MemoryFileStore.class);

        provider = new MemoryFileSystemProvider(fileStore);
        fs = new MemoryFileSystem(provider, fileStore);
    }

    // support for Paths and Files

    @Test
    public void testPathsAndFilesSupport() throws IOException {

        Path path = Paths.get(URI.create("memory:/foo"));
        assertThat(path, instanceOf(MemoryPath.class));
        // as required by Paths.get
        assertEquals(path, path.toAbsolutePath());

        // the file does not exist yet
        assertFalse(Files.exists(path));

        Files.createFile(path);
        try {
            // the file now exists
            assertTrue(Files.exists(path));

            byte[] content = new byte[1024];
            new Random().nextBytes(content);
            try (OutputStream output = Files.newOutputStream(path)) {
                output.write(content);
            }

            // check the file directly
            Node node = MemoryFileStore.INSTANCE.rootNode.get("foo");
            assertThat(node, instanceOf(File.class));

            File file = (File) node;
            assertArrayEquals(content, file.getContent());

            try (InputStream input = Files.newInputStream(path)) {
                byte[] readContent = new byte[content.length];
                int len = input.read(readContent);
                assertEquals(readContent.length, len);
                assertArrayEquals(content, readContent);
            }

            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                byte[] readContent = new byte[content.length];
                ByteBuffer buffer = ByteBuffer.wrap(readContent);
                int len = channel.read(buffer);
                assertEquals(readContent.length, len);
                assertArrayEquals(content, readContent);
            }

        } finally {

            Files.delete(path);
            assertFalse(Files.exists(path));

            assertNull(MemoryFileStore.INSTANCE.rootNode.get("foo"));
        }
    }

    // MemoryFileSystemProvider.newFileSystem

    @Test
    public void testNewFileSystem() {
        assertThrows(FileSystemAlreadyExistsException.class, () -> provider.newFileSystem(URI.create("memory:foo"), Collections.emptyMap()));
    }

    // MemoryFileSystemProvider.getFileSystem

    @Test
    public void testGetFileSystem() {
        @SuppressWarnings("resource")
        FileSystem fileSystem = provider.getFileSystem(URI.create("memory:foo"));
        assertThat(fileSystem, instanceOf(MemoryFileSystem.class));
        assertEquals(Collections.singleton(fileStore), fileSystem.getFileStores());
    }

    // MemoryFileSystemProvider.getPath

    @Test
    public void testGetPath() {
        String[] inputs = {
                "/",
                "foo",
                "/foo",
                "foo/bar",
                "/foo/bar"
        };

        for (String input : inputs) {
            Path path = provider.getPath(URI.create("memory:" + input));
            assertThat(path, instanceOf(MemoryPath.class));
            assertEquals(input, ((MemoryPath) path).path());
        }
        for (String input : inputs) {
            Path path = provider.getPath(URI.create("MEMORY:" + input));
            assertThat(path, instanceOf(MemoryPath.class));
            assertEquals(input, ((MemoryPath) path).path());
        }
    }

    @Test
    public void testGetPathNoScheme() {
        URI uri = URI.create("/foo/bar");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> provider.getPath(uri));
        assertEquals(Messages.uri().notAbsolute(uri).getMessage(), exception.getMessage());
    }

    @Test
    public void testGetPathInvalidScheme() {
        URI uri = URI.create("https://www.github.com/");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> provider.getPath(uri));
        assertEquals(Messages.uri().invalidScheme(uri, "memory").getMessage(), exception.getMessage());
    }

    // MemoryFileSystemProvider.getFileAttributeView

    @Test
    public void testGetFileAttributeViewBasic() {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        BasicFileAttributeView view = provider.getFileAttributeView(path, BasicFileAttributeView.class);
        assertNotNull(view);
        assertEquals("basic", view.name());
    }

    @Test
    public void testGetFileAttributeViewMemory() {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        MemoryFileAttributeView view = provider.getFileAttributeView(path, MemoryFileAttributeView.class);
        assertNotNull(view);
        assertEquals("memory", view.name());
    }

    @Test
    public void testGetFileAttributeViewOther() {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        FileOwnerAttributeView view = provider.getFileAttributeView(path, FileOwnerAttributeView.class);
        assertNull(view);
    }

    @Test
    public void testGetFileAttributeViewReadAttributes() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        doReturn(null).when(fileStore).readAttributes(path, true);

        BasicFileAttributeView view = provider.getFileAttributeView(path, BasicFileAttributeView.class);
        assertNotNull(view);

        verify(fileStore, never()).readAttributes(any(MemoryPath.class), anyBoolean());

        view.readAttributes();

        verify(fileStore).readAttributes(path, true);
    }

    @Test
    public void testGetFileAttributeViewSetTimes() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        doNothing().when(fileStore).setTimes(eq(path), any(FileTime.class), any(FileTime.class), any(FileTime.class), anyBoolean());
        doNothing().when(fileStore).setTimes(path, null, null, null, true);

        BasicFileAttributeView view = provider.getFileAttributeView(path, BasicFileAttributeView.class);
        assertNotNull(view);

        verify(fileStore, never()).setTimes(any(MemoryPath.class), any(FileTime.class), any(FileTime.class), any(FileTime.class), anyBoolean());

        view.setTimes(null, null, null);

        verify(fileStore).setTimes(path, null, null, null, true);
    }

    @Test
    public void testGetFileAttributeViewSetReadOnly() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        doNothing().when(fileStore).setReadOnly(eq(path), anyBoolean(), anyBoolean());

        MemoryFileAttributeView view = provider.getFileAttributeView(path, MemoryFileAttributeView.class);
        assertNotNull(view);

        verify(fileStore, never()).setReadOnly(any(MemoryPath.class), anyBoolean(), anyBoolean());

        view.setReadOnly(true);

        verify(fileStore).setReadOnly(path, true, true);
    }

    @Test
    public void testGetFileAttributeViewSetHidden() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        doNothing().when(fileStore).setHidden(eq(path), anyBoolean(), anyBoolean());

        MemoryFileAttributeView view = provider.getFileAttributeView(path, MemoryFileAttributeView.class);
        assertNotNull(view);

        verify(fileStore, never()).setHidden(any(MemoryPath.class), anyBoolean(), anyBoolean());

        view.setHidden(true);

        verify(fileStore).setHidden(path, true, true);
    }

    @Test
    public void testReadAttributesBasic() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo");
        Files.createFile(path);
        try {
            BasicFileAttributes attributes = provider.readAttributes(path, BasicFileAttributes.class);
            assertNotNull(attributes);
            assertTrue(attributes.isRegularFile());
        } finally {
            Files.delete(path);
        }
    }

    @Test
    public void testReadAttributesMemory() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo");
        Files.createFile(path);
        try {
            MemoryFileAttributes attributes = provider.readAttributes(path, MemoryFileAttributes.class);
            assertNotNull(attributes);
            assertFalse(attributes.isHidden());
        } finally {
            Files.delete(path);
        }
    }

    @Test
    public void testReadAttributesOther() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo");
        Files.createFile(path);
        try {
            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                    () -> provider.readAttributes(path, DosFileAttributes.class));
            assertEquals(Messages.fileSystemProvider().unsupportedFileAttributesType(DosFileAttributes.class).getMessage(), exception.getMessage());
        } finally {
            Files.delete(path);
        }
    }

    @Test
    public void testGetContentFromString() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertArrayEquals(new byte[0], MemoryFileSystemProvider.getContent("/foo"));

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            Files.write(foo, newContent);

            assertArrayEquals(newContent, MemoryFileSystemProvider.getContent("/foo"));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    public void testGetContentExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertArrayEquals(new byte[0], MemoryFileSystemProvider.getContent(foo));

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            Files.write(foo, newContent);

            assertArrayEquals(newContent, MemoryFileSystemProvider.getContent(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    public void testGetContentNonExisting() {
        Path foo = Paths.get(URI.create("memory:/foo"));

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.getContent(foo));
        assertEquals(foo.toString(), exception.getFile());
    }

    @Test
    public void testGetContentDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);

        try {
            FileSystemException exception = assertThrows(FileSystemException.class, () -> MemoryFileSystemProvider.getContent(foo));
            assertEquals("/foo", exception.getFile());
            assertEquals(Messages.fileSystemProvider().isDirectory(foo.toString()).getReason(), exception.getReason());
        } finally {
            Files.delete(foo);
        }
    }

    @Test
    public void testGetContentLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);

            assertArrayEquals(new byte[0], MemoryFileSystemProvider.getContent(link));

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            Files.write(foo, newContent);

            assertArrayEquals(newContent, MemoryFileSystemProvider.getContent(link));

        } finally {
            Files.delete(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    public void testSetContentFromString() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            MemoryFileSystemProvider.setContent("/foo", newContent);

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    public void testSetContentExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            MemoryFileSystemProvider.setContent(foo, newContent);

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    public void testSetContentExistingReadOnly() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            Files.setAttribute(foo, "memory:readOnly", true);

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> MemoryFileSystemProvider.setContent(foo, newContent));
            assertEquals(foo.toString(), exception.getMessage());

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    public void testSetContentNonExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        assertFalse(Files.exists(foo));
        try {
            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            MemoryFileSystemProvider.setContent(foo, newContent);

            assertTrue(Files.isRegularFile(foo));

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    public void testSetContentNonExistingReadOnlyParent() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path bar = foo.resolve("bar");
        assertFalse(Files.exists(bar));
        Files.createDirectory(foo);
        try {
            Files.setAttribute(foo, "memory:readOnly", true);

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> MemoryFileSystemProvider.setContent(bar, newContent));
            assertEquals(foo.toString(), exception.getFile());

            assertFalse(Files.exists(bar));

        } finally {

            Files.delete(foo);
        }
    }

    @Test
    public void testSetContentNonExistingNonExistingParent() {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path bar = foo.resolve("bar");
        assertFalse(Files.exists(bar));

        byte[] newContent = new byte[100];
        new Random().nextBytes(newContent);

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.setContent(bar, newContent));
        assertEquals(foo.toString(), exception.getFile());

        assertFalse(Files.exists(bar));
        assertFalse(Files.exists(foo));
    }

    @Test
    public void testSetContentDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);
        try {
            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            FileSystemException exception = assertThrows(FileSystemException.class, () -> MemoryFileSystemProvider.setContent(foo, newContent));
            assertEquals("/foo", exception.getFile());
            assertEquals(Messages.fileSystemProvider().isDirectory(foo.toString()).getReason(), exception.getReason());

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    public void testSetContentLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });
            Files.createSymbolicLink(link, foo);

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            MemoryFileSystemProvider.setContent(link, newContent);

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    public void testClear() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);

        Path bar = foo.resolve("bar");
        Files.createFile(bar);

        Path baz = foo.resolveSibling("baz");
        Files.createFile(baz);

        assertTrue(Files.isDirectory(foo));
        assertTrue(Files.isRegularFile(bar));
        assertTrue(Files.isRegularFile(baz));

        MemoryFileSystemProvider.clear();

        assertFalse(Files.exists(foo));
        assertFalse(Files.exists(bar));
        assertFalse(Files.exists(baz));

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(foo.getParent())) {
            Iterator<Path> iterator = stream.iterator();
            assertFalse(iterator.hasNext());
        }
    }
}
