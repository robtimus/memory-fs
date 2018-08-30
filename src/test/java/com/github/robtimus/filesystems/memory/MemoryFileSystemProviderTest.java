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

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Node;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({ "nls", "javadoc" })
public class MemoryFileSystemProviderTest {

    @Spy private MemoryFileStore fileStore;

    private MemoryFileSystemProvider provider;
    private MemoryFileSystem fs;

    @Before
    public void setupProvider() {
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

        } finally {

            Files.delete(path);
            assertFalse(Files.exists(path));

            assertNull(MemoryFileStore.INSTANCE.rootNode.get("foo"));
        }
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

    @Test(expected = IllegalArgumentException.class)
    public void testGetPathNoScheme() {
        provider.getPath(URI.create("/foo/bar"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPathInvalidScheme() {
        provider.getPath(URI.create("https://www.github.com/"));
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
    public void testGetFileAttributeViewReadAttributes() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        doReturn(null).when(fileStore).readAttributes(path);

        BasicFileAttributeView view = provider.getFileAttributeView(path, BasicFileAttributeView.class);
        assertNotNull(view);

        verify(fileStore, never()).readAttributes(any(MemoryPath.class));

        view.readAttributes();

        verify(fileStore).readAttributes(path);
    }

    @Test
    public void testGetFileAttributeViewSetTimes() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        doNothing().when(fileStore).setTimes(eq(path), any(FileTime.class), any(FileTime.class), any(FileTime.class));

        BasicFileAttributeView view = provider.getFileAttributeView(path, BasicFileAttributeView.class);
        assertNotNull(view);

        verify(fileStore, never()).setTimes(any(MemoryPath.class), any(FileTime.class), any(FileTime.class), any(FileTime.class));

        view.setTimes(null, null, null);

        verify(fileStore).setTimes(path, null, null, null);
    }

    @Test
    public void testGetFileAttributeViewSetReadOnly() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        doNothing().when(fileStore).setReadOnly(eq(path), anyBoolean());

        MemoryFileAttributeView view = provider.getFileAttributeView(path, MemoryFileAttributeView.class);
        assertNotNull(view);

        verify(fileStore, never()).setReadOnly(any(MemoryPath.class), anyBoolean());

        view.setReadOnly(true);

        verify(fileStore).setReadOnly(path, true);
    }

    @Test
    public void testGetFileAttributeViewSetHidden() throws IOException {
        MemoryPath path = new MemoryPath(fs, "/foo/bar");

        doNothing().when(fileStore).setHidden(eq(path), anyBoolean());

        MemoryFileAttributeView view = provider.getFileAttributeView(path, MemoryFileAttributeView.class);
        assertNotNull(view);

        verify(fileStore, never()).setHidden(any(MemoryPath.class), anyBoolean());

        view.setHidden(true);

        verify(fileStore).setHidden(path, true);
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

    @Test(expected = NoSuchFileException.class)
    public void testGetContentNonExisting() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));

        MemoryFileSystemProvider.getContent(foo);
    }

    @Test(expected = FileSystemException.class)
    public void testGetContentDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);

        try {
            MemoryFileSystemProvider.getContent(foo);
        } finally {
            Files.delete(foo);
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

    @Test(expected = AccessDeniedException.class)
    public void testSetContentExistingReadOnly() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createFile(foo);
        try {
            Files.write(foo, new byte[] { 1, 2, 3 });

            Files.setAttribute(foo, "memory:readOnly", true);

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            MemoryFileSystemProvider.setContent(foo, newContent);

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

    @Test(expected = AccessDeniedException.class)
    public void testSetContentNonExistingReadOnlyParent() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path bar = foo.resolve("bar");
        assertFalse(Files.exists(bar));
        Files.createDirectory(foo);
        try {
            Files.setAttribute(foo, "memory:readOnly", true);

            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            MemoryFileSystemProvider.setContent(bar, newContent);

        } finally {
            assertFalse(Files.exists(bar));

            Files.delete(foo);
        }
    }

    @Test(expected = NoSuchFileException.class)
    public void testSetContentNonExistingNonExistingParent() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Path bar = foo.resolve("bar");
        assertFalse(Files.exists(bar));
        try {
            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            MemoryFileSystemProvider.setContent(bar, newContent);

        } finally {
            assertFalse(Files.exists(bar));
            assertFalse(Files.exists(foo));
        }
    }

    @Test(expected = FileSystemException.class)
    public void testSetContentDirectory() throws IOException {
        Path foo = Paths.get(URI.create("memory:/foo"));
        Files.createDirectory(foo);
        try {
            byte[] newContent = new byte[100];
            new Random().nextBytes(newContent);

            MemoryFileSystemProvider.setContent(foo, newContent);

        } finally {
            Files.delete(foo);
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
