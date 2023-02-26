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

import static com.github.robtimus.junit.support.OptionalAssertions.assertIsEmpty;
import static com.github.robtimus.junit.support.OptionalAssertions.assertIsPresent;
import static com.github.robtimus.junit.support.ThrowableAssertions.assertChainEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
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
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.Messages;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Node;

@SuppressWarnings("nls")
class MemoryFileSystemProviderTest {

    private MemoryFileStore fileStore;

    private MemoryFileSystemProvider provider;
    private MemoryFileSystem fs;

    @BeforeEach
    void setupProvider() {
        fileStore = new MemoryFileStore();

        provider = new MemoryFileSystemProvider(fileStore);
        fs = new MemoryFileSystem(provider, fileStore);
    }

    // support for Paths and Files

    @Test
    void testPathsAndFilesSupport() throws IOException {

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
    void testNewFileSystem() {
        URI uri = URI.create("memory:foo");
        Map<String, ?> env = Collections.emptyMap();
        assertThrows(FileSystemAlreadyExistsException.class, () -> provider.newFileSystem(uri, env));
    }

    // MemoryFileSystemProvider.getFileSystem

    @Test
    void testGetFileSystem() {
        @SuppressWarnings("resource")
        FileSystem fileSystem = provider.getFileSystem(URI.create("memory:foo"));
        assertThat(fileSystem, instanceOf(MemoryFileSystem.class));
        assertEquals(Collections.singleton(fileStore), fileSystem.getFileStores());
    }

    // MemoryFileSystemProvider.getPath

    @Test
    void testGetPath() {
        String[] inputs = {
                "/",
                "foo",
                "/foo",
                "foo/bar",
                "/foo/bar",
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
    void testGetPathNoScheme() {
        URI uri = URI.create("/foo/bar");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> provider.getPath(uri));
        assertChainEquals(Messages.uri().notAbsolute(uri), exception);
    }

    @Test
    void testGetPathInvalidScheme() {
        URI uri = URI.create("https://www.github.com/");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> provider.getPath(uri));
        assertChainEquals(Messages.uri().invalidScheme(uri, "memory"), exception);
    }

    // MemoryFileSystemProvider.isSameFile

    @Test
    void testIsSameFileWithDifferentTypes() throws IOException {

        @SuppressWarnings("resource")
        FileSystem defaultFileSystem = FileSystems.getDefault();
        FileSystemProvider defaultProvider = defaultFileSystem.provider();

        MemoryPath path1 = new MemoryPath(fs, "pom.xml");
        Path path2 = Paths.get("pom.xml");

        assertFalse(provider.isSameFile(path1, path2));
        assertFalse(defaultProvider.isSameFile(path2, path1));
    }

    // MemoryFileSystemProvider.getFileAttributeView

    @Test
    void testGetFileAttributeViewBasic() {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        BasicFileAttributeView view = provider.getFileAttributeView(path, BasicFileAttributeView.class);
        assertNotNull(view);
        assertEquals("basic", view.name());
    }

    @Test
    void testGetFileAttributeViewMemory() {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        MemoryFileAttributeView view = provider.getFileAttributeView(path, MemoryFileAttributeView.class);
        assertNotNull(view);
        assertEquals("memory", view.name());
    }

    @Test
    void testGetFileAttributeViewOther() {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        FileOwnerAttributeView view = provider.getFileAttributeView(path, FileOwnerAttributeView.class);
        assertNull(view);
    }

    @Test
    void testGetFileAttributeViewReadAttributes() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        fileStore.createDirectory(path.getParent());
        fileStore.setContent(path, new byte[100]);

        BasicFileAttributeView view = provider.getFileAttributeView(path, BasicFileAttributeView.class);
        assertNotNull(view);

        BasicFileAttributes attributes = view.readAttributes();

        assertNotNull(attributes.creationTime());
        assertEquals(attributes.creationTime(), attributes.lastModifiedTime());
        assertEquals(attributes.creationTime(), attributes.lastAccessTime());
        assertTrue(attributes.isRegularFile());
        assertFalse(attributes.isDirectory());
        assertEquals(100, attributes.size());
    }

    @Test
    void testGetFileAttributeViewSetTimes() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        fileStore.createDirectory(path.getParent());
        fileStore.setContent(path, new byte[0]);

        MemoryFileAttributes attributes = fileStore.readAttributes(path, true);

        FileTime originalLastModifiedTime = attributes.lastModifiedTime();
        FileTime originalLastAccessTime = attributes.lastAccessTime();
        FileTime originalCreationTime = attributes.creationTime();

        BasicFileAttributeView view = provider.getFileAttributeView(path, BasicFileAttributeView.class);
        assertNotNull(view);

        view.setTimes(null, null, null);

        attributes = fileStore.readAttributes(path, true);

        assertEquals(originalLastModifiedTime, attributes.lastModifiedTime());
        assertEquals(originalLastAccessTime, attributes.lastAccessTime());
        assertEquals(originalCreationTime, attributes.creationTime());

        view.setTimes(FileTime.fromMillis(123456L), FileTime.fromMillis(1234567L), FileTime.fromMillis(12345678L));

        attributes = fileStore.readAttributes(path, true);

        assertEquals(123456L, attributes.lastModifiedTime().toMillis());
        assertEquals(1234567L, attributes.lastAccessTime().toMillis());
        assertEquals(12345678L, attributes.creationTime().toMillis());
    }

    @Test
    void testGetFileAttributeViewSetReadOnly() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        fileStore.createDirectory(path.getParent());
        fileStore.setContent(path, new byte[0]);

        MemoryFileAttributeView view = provider.getFileAttributeView(path, MemoryFileAttributeView.class);
        assertNotNull(view);

        assertFalse(fileStore.readAttributes(path, true).isReadOnly());

        view.setReadOnly(true);

        assertTrue(fileStore.readAttributes(path, true).isReadOnly());
    }

    @Test
    void testGetFileAttributeViewSetHidden() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        fileStore.createDirectory(path.getParent());
        fileStore.setContent(path, new byte[0]);

        MemoryFileAttributeView view = provider.getFileAttributeView(path, MemoryFileAttributeView.class);
        assertNotNull(view);

        assertFalse(fileStore.isHidden(path));

        view.setHidden(true);

        assertTrue(fileStore.isHidden(path));
    }

    @Test
    void testReadAttributesBasic() throws IOException {
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
    void testReadAttributesMemory() throws IOException {
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
    void testReadAttributesOther() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo");
        Files.createFile(path);
        try {
            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                    () -> provider.readAttributes(path, DosFileAttributes.class));
            assertChainEquals(Messages.fileSystemProvider().unsupportedFileAttributesType(DosFileAttributes.class), exception);
        } finally {
            Files.delete(path);
        }
    }

    @Test
    void testGetContentFromString() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertArrayEquals(new byte[0], MemoryFileSystemProvider.getContent("/foo"));

            byte[] newContent = randomBytes();

            Files.write(foo, newContent);

            assertArrayEquals(newContent, MemoryFileSystemProvider.getContent("/foo"));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertArrayEquals(new byte[0], MemoryFileSystemProvider.getContent(foo));

            byte[] newContent = randomBytes();

            Files.write(foo, newContent);

            assertArrayEquals(newContent, MemoryFileSystemProvider.getContent(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentNonExisting() {
        Path foo = Paths.get(URI.create("memory:/foo"));

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.getContent(foo));
        assertEquals(foo.toString(), exception.getFile());
    }

    @Test
    void testGetContentNonExistingParent() {
        Path bar = Paths.get(URI.create("memory:/foo/bar"));

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.getContent(bar));
        assertEquals(bar.toString(), exception.getFile());
    }

    @Test
    void testGetContentDirectory() throws IOException {
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
    void testGetContentLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);

            assertArrayEquals(new byte[0], MemoryFileSystemProvider.getContent(link));

            byte[] newContent = randomBytes();

            Files.write(foo, newContent);

            assertArrayEquals(newContent, MemoryFileSystemProvider.getContent(link));

        } finally {
            Files.delete(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testGetContentBrokenLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);
            Files.delete(foo);

            NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.getContent(foo));
            assertEquals(foo.toString(), exception.getFile());

        } finally {
            Files.deleteIfExists(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testGetContentIfExistsFromString() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertArrayEquals(new byte[0], assertIsPresent(MemoryFileSystemProvider.getContentIfExists("/foo")));

            byte[] newContent = randomBytes();

            Files.write(foo, newContent);

            assertArrayEquals(newContent, assertIsPresent(MemoryFileSystemProvider.getContentIfExists("/foo")));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentIfExistsExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertArrayEquals(new byte[0], assertIsPresent(MemoryFileSystemProvider.getContentIfExists(foo)));

            byte[] newContent = randomBytes();

            Files.write(foo, newContent);

            assertArrayEquals(newContent, assertIsPresent(MemoryFileSystemProvider.getContentIfExists(foo)));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentIfExistsNonExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));

        assertIsEmpty(MemoryFileSystemProvider.getContentIfExists(foo));
    }

    @Test
    void testGetContentIfExistsNonExistingParent() throws IOException {
        Path bar = Paths.get(URI.create("memory:/foo/bar"));

        assertIsEmpty(MemoryFileSystemProvider.getContentIfExists(bar));
    }

    @Test
    void testGetContentIfExistsDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);

        try {
            FileSystemException exception = assertThrows(FileSystemException.class, () -> MemoryFileSystemProvider.getContentIfExists(foo));
            assertEquals("/foo", exception.getFile());
            assertEquals(Messages.fileSystemProvider().isDirectory(foo.toString()).getReason(), exception.getReason());
        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentIfExistsLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);

            assertArrayEquals(new byte[0], assertIsPresent(MemoryFileSystemProvider.getContentIfExists(link)));

            byte[] newContent = randomBytes();

            Files.write(foo, newContent);

            assertArrayEquals(newContent, assertIsPresent(MemoryFileSystemProvider.getContentIfExists(link)));

        } finally {
            Files.delete(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testGetContentIfExistsBrokenLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);
            Files.delete(foo);

            assertIsEmpty(MemoryFileSystemProvider.getContentIfExists(foo));

        } finally {
            Files.deleteIfExists(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testGetContentAsStringFromString() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertEquals("", MemoryFileSystemProvider.getContentAsString("/foo"));

            String newContent = randomText();

            Files.write(foo, newContent.getBytes(StandardCharsets.UTF_8));

            assertEquals(newContent, MemoryFileSystemProvider.getContentAsString("/foo"));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentAsStringExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertEquals("", MemoryFileSystemProvider.getContentAsString(foo));

            String newContent = randomText();

            Files.write(foo, newContent.getBytes(StandardCharsets.UTF_8));

            assertEquals(newContent, MemoryFileSystemProvider.getContentAsString(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentAsStringNonExisting() {
        Path foo = Paths.get(URI.create("memory:/foo"));

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.getContentAsString(foo));
        assertEquals(foo.toString(), exception.getFile());
    }

    @Test
    void testGetContentAsStringNonExistingParent() {
        Path bar = Paths.get(URI.create("memory:/foo/bar"));

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.getContentAsString(bar));
        assertEquals(bar.toString(), exception.getFile());
    }

    @Test
    void testGetContentAsStringDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);

        try {
            FileSystemException exception = assertThrows(FileSystemException.class, () -> MemoryFileSystemProvider.getContentAsString(foo));
            assertEquals("/foo", exception.getFile());
            assertEquals(Messages.fileSystemProvider().isDirectory(foo.toString()).getReason(), exception.getReason());
        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentAsStringLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);

            assertEquals("", MemoryFileSystemProvider.getContentAsString(link));

            String newContent = randomText();

            Files.write(foo, newContent.getBytes(StandardCharsets.UTF_8));

            assertEquals(newContent, MemoryFileSystemProvider.getContentAsString(link));

        } finally {
            Files.delete(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testGetContentAsStringBrokenLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);
            Files.delete(foo);

            NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.getContentAsString(foo));
            assertEquals(foo.toString(), exception.getFile());

        } finally {
            Files.deleteIfExists(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testGetContentAsStringIfExistsFromString() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertEquals("", assertIsPresent(MemoryFileSystemProvider.getContentAsStringIfExists("/foo")));

            String newContent = randomText();

            Files.write(foo, newContent.getBytes(StandardCharsets.UTF_8));

            assertEquals(newContent, assertIsPresent(MemoryFileSystemProvider.getContentAsStringIfExists("/foo")));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentAsStringIfExistsExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            assertEquals("", assertIsPresent(MemoryFileSystemProvider.getContentAsStringIfExists(foo)));

            String newContent = randomText();

            Files.write(foo, newContent.getBytes(StandardCharsets.UTF_8));

            assertEquals(newContent, assertIsPresent(MemoryFileSystemProvider.getContentAsStringIfExists(foo)));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentAsStringIfExistsNonExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));

        assertIsEmpty(MemoryFileSystemProvider.getContentAsStringIfExists(foo));
    }

    @Test
    void testGetContentAsStringIfExistsNonExistingParent() throws IOException {
        Path bar = Paths.get(URI.create("memory:/foo/bar"));

        assertIsEmpty(MemoryFileSystemProvider.getContentAsStringIfExists(bar));
    }

    @Test
    void testGetContentAsStringIfExistsDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);

        try {
            FileSystemException exception = assertThrows(FileSystemException.class, () -> MemoryFileSystemProvider.getContentAsStringIfExists(foo));
            assertEquals("/foo", exception.getFile());
            assertEquals(Messages.fileSystemProvider().isDirectory(foo.toString()).getReason(), exception.getReason());
        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testGetContentAsStringIfExistsLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);

            assertEquals("", assertIsPresent(MemoryFileSystemProvider.getContentAsStringIfExists(link)));

            String newContent = randomText();

            Files.write(foo, newContent.getBytes(StandardCharsets.UTF_8));

            assertEquals(newContent, assertIsPresent(MemoryFileSystemProvider.getContentAsStringIfExists(link)));

        } finally {
            Files.delete(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testGetContentAsStringIfExistsBrokenLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);
            Files.delete(foo);

            assertIsEmpty(MemoryFileSystemProvider.getContentAsStringIfExists(foo));

        } finally {
            Files.deleteIfExists(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testSetContentFromString() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            byte[] newContent = randomBytes();

            MemoryFileSystemProvider.setContent("/foo", newContent);

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            byte[] newContent = randomBytes();

            MemoryFileSystemProvider.setContent(foo, newContent);

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentExistingReadOnly() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            Files.setAttribute(foo, "memory:readOnly", true);

            byte[] newContent = randomBytes();

            AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> MemoryFileSystemProvider.setContent(foo, newContent));
            assertEquals(foo.toString(), exception.getMessage());

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentNonExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        assertFalse(Files.exists(foo));
        try {
            byte[] newContent = randomBytes();

            MemoryFileSystemProvider.setContent(foo, newContent);

            assertTrue(Files.isRegularFile(foo));

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentNonExistingReadOnlyParent() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path bar = foo.resolve("bar");
        assertFalse(Files.exists(bar));
        Files.createDirectory(foo);
        try {
            Files.setAttribute(foo, "memory:readOnly", true);

            byte[] newContent = randomBytes();

            AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> MemoryFileSystemProvider.setContent(bar, newContent));
            assertEquals(foo.toString(), exception.getFile());

            assertFalse(Files.exists(bar));

        } finally {

            Files.delete(foo);
        }
    }

    @Test
    void testSetContentNonExistingNonExistingParent() {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path bar = foo.resolve("bar");
        assertFalse(Files.exists(bar));

        byte[] newContent = randomBytes();

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.setContent(bar, newContent));
        assertEquals(foo.toString(), exception.getFile());

        assertFalse(Files.exists(bar));
        assertFalse(Files.exists(foo));
    }

    @Test
    void testSetContentDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);
        try {
            byte[] newContent = randomBytes();

            FileSystemException exception = assertThrows(FileSystemException.class, () -> MemoryFileSystemProvider.setContent(foo, newContent));
            assertEquals("/foo", exception.getFile());
            assertEquals(Messages.fileSystemProvider().isDirectory(foo.toString()).getReason(), exception.getReason());

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });
            Files.createSymbolicLink(link, foo);

            byte[] newContent = randomBytes();

            MemoryFileSystemProvider.setContent(link, newContent);

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testSetContentBrokenLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);
            Files.delete(foo);

            byte[] newContent = randomBytes();

            MemoryFileSystemProvider.setContent(link, newContent);

            assertArrayEquals(newContent, Files.readAllBytes(foo));

        } finally {
            Files.deleteIfExists(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testSetContentAsStringFromString() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            String newContent = randomText();

            MemoryFileSystemProvider.setContentAsString("/foo", newContent);

            assertArrayEquals(newContent.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentAsStringExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            String newContent = randomText();

            MemoryFileSystemProvider.setContentAsString(foo, newContent);

            assertArrayEquals(newContent.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentAsStringExistingReadOnly() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            Files.setAttribute(foo, "memory:readOnly", true);

            String newContent = randomText();

            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> MemoryFileSystemProvider.setContentAsString(foo, newContent));
            assertEquals(foo.toString(), exception.getMessage());

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentAsStringNonExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        assertFalse(Files.exists(foo));
        try {
            String newContent = randomText();

            MemoryFileSystemProvider.setContentAsString(foo, newContent);

            assertTrue(Files.isRegularFile(foo));

            assertArrayEquals(newContent.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentAsStringNonExistingReadOnlyParent() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path bar = foo.resolve("bar");
        assertFalse(Files.exists(bar));
        Files.createDirectory(foo);
        try {
            Files.setAttribute(foo, "memory:readOnly", true);

            String newContent = randomText();

            AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                    () -> MemoryFileSystemProvider.setContentAsString(bar, newContent));
            assertEquals(foo.toString(), exception.getFile());

            assertFalse(Files.exists(bar));

        } finally {

            Files.delete(foo);
        }
    }

    @Test
    void testSetContentAsStringNonExistingNonExistingParent() {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path bar = foo.resolve("bar");
        assertFalse(Files.exists(bar));

        String newContent = randomText();

        NoSuchFileException exception = assertThrows(NoSuchFileException.class, () -> MemoryFileSystemProvider.setContentAsString(bar, newContent));
        assertEquals(foo.toString(), exception.getFile());

        assertFalse(Files.exists(bar));
        assertFalse(Files.exists(foo));
    }

    @Test
    void testSetContentAsStringDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);
        try {
            String newContent = randomText();

            FileSystemException exception = assertThrows(FileSystemException.class,
                    () -> MemoryFileSystemProvider.setContentAsString(foo, newContent));
            assertEquals("/foo", exception.getFile());
            assertEquals(Messages.fileSystemProvider().isDirectory(foo.toString()).getReason(), exception.getReason());

        } finally {
            Files.delete(foo);
        }
    }

    @Test
    void testSetContentAsStringLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });
            Files.createSymbolicLink(link, foo);

            String newContent = randomText();

            MemoryFileSystemProvider.setContentAsString(link, newContent);

            assertArrayEquals(newContent.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(foo));

        } finally {
            Files.delete(foo);
            Files.deleteIfExists(link);
        }
    }

    @Test
    void testSetContentAsStringBrokenLink() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path link = Paths.get(URI.create("memory:/link"));
        Files.createFile(foo);
        try {
            Files.createSymbolicLink(link, foo);
            Files.delete(foo);

            String newContent = randomText();

            MemoryFileSystemProvider.setContentAsString(link, newContent);

            assertArrayEquals(newContent.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(foo));

        } finally {
            Files.deleteIfExists(foo);
            Files.deleteIfExists(link);
        }
    }

    private byte[] randomBytes() {
        byte[] newContent = new byte[100];
        new Random().nextBytes(newContent);
        return newContent;
    }

    private String randomText() {
        String newContent = new Random().ints('A', 'Z' + 1)
                .limit(100)
                .mapToObj(c -> Character.toString((char) c))
                .collect(Collectors.joining());
        return newContent;
    }

    @Test
    void testClear() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);

        Path bar = foo.resolve("bar");
        Files.createFile(bar);

        Path baz = foo.resolveSibling("baz");
        Files.createFile(baz);

        assertTrue(Files.isDirectory(foo));
        assertTrue(Files.isRegularFile(bar));
        assertTrue(Files.isRegularFile(baz));

        MemoryFileAttributeView view = Files.getFileAttributeView(foo.getParent(), MemoryFileAttributeView.class);
        view.setReadOnly(true);
        view.setHidden(true);

        Map<String, ?> attributes = Files.readAttributes(foo.getParent(), "memory:readOnly,hidden");
        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put("readOnly", true);
        expectedAttributes.put("hidden", true);
        assertEquals(expectedAttributes, attributes);

        MemoryFileSystemProvider.clear();

        assertFalse(Files.exists(foo));
        assertFalse(Files.exists(bar));
        assertFalse(Files.exists(baz));

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(foo.getParent())) {
            Iterator<Path> iterator = stream.iterator();
            assertFalse(iterator.hasNext());
        }

        attributes = Files.readAttributes(foo.getParent(), "memory:readOnly,hidden");
        expectedAttributes.put("readOnly", false);
        expectedAttributes.put("hidden", false);
        assertEquals(expectedAttributes, attributes);
    }
}
