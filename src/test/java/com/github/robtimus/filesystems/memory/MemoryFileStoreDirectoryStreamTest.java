/*
 * MemoryFileStoreDirectoryStreamTest.java
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

import static com.github.robtimus.junit.support.ThrowableAssertions.assertChainEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.Messages;
import com.github.robtimus.filesystems.memory.MemoryFileStore.Directory;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;

@SuppressWarnings("nls")
class MemoryFileStoreDirectoryStreamTest {

    private MemoryFileStore fileStore;
    private Directory root;

    private MemoryFileSystem fs;

    @BeforeEach
    void setupFileStore() {
        fileStore = new MemoryFileStore();
        root = fileStore.rootNode;

        fs = new MemoryFileSystem(new MemoryFileSystemProvider(), fileStore);
    }

    private MemoryPath createPath(String path) {
        return new MemoryPath(fs, path);
    }

    @Test
    void testIterator() throws IOException {
        final int count = 100;

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            expected.add("file" + i);
            root.add("file" + i, new File());
        }
        Collections.sort(expected);

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/"), entry -> true)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertEquals(expected, names);
    }

    @Test
    void testFilteredIterator() throws IOException {
        final int count = 100;

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 2 == 1) {
                expected.add("file" + i);
            }
            root.add("file" + i, new File());
        }
        Collections.sort(expected);

        List<String> names = new ArrayList<>();
        Filter<Path> filter = new PatternFilter("file\\d*[13579]");
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/"), filter)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertEquals(expected, names);
    }

    @Test
    void testCloseWhileIterating() throws IOException {
        final int count = 100;

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            expected.add("file" + i);
            root.add("file" + i, new File());
        }
        Collections.sort(expected);
        expected = expected.subList(0, count / 2);

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/"), entry -> true)) {
            int index = 0;
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                if (++index == count / 2) {
                    stream.close();
                }
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertEquals(expected, names);
    }

    @Test
    void testIteratorAfterClose() throws IOException {
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/"), entry -> true)) {
            stream.close();
            IllegalStateException exception = assertThrows(IllegalStateException.class, stream::iterator);
            assertChainEquals(Messages.directoryStream().closed(), exception);
        }
    }

    @Test
    void testIteratorAfterIterator() throws IOException {
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/"), entry -> true)) {
            stream.iterator();
            IllegalStateException exception = assertThrows(IllegalStateException.class, stream::iterator);
            assertChainEquals(Messages.directoryStream().iteratorAlreadyReturned(), exception);
        }
    }

    @Test
    void testDeleteWhileIterating() throws IOException {
        final int count = 100;

        List<String> expected = new ArrayList<>();
        Directory foo = (Directory) root.add("foo", new Directory());
        for (int i = 0; i < count; i++) {
            expected.add("file" + i);
            foo.add("file" + i, new File());
        }
        Collections.sort(expected);

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/foo"), entry -> true)) {
            int index = 0;
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                if (++index < count / 2) {
                    root.remove("foo");
                }
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertEquals(expected, names);
    }

    @Test
    void testDeleteChildrenWhileIterating() throws IOException {
        final int count = 100;

        List<String> expected = new ArrayList<>();
        Directory foo = (Directory) root.add("foo", new Directory());
        for (int i = 0; i < count; i++) {
            expected.add("file" + i);
            foo.add("file" + i, new File());
        }
        Collections.sort(expected);

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/foo"), entry -> true)) {
            int index = 0;
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                if (++index < count / 2) {
                    for (int i = 0; i < count; i++) {
                        foo.remove("file" + i);
                    }
                    assertTrue(foo.isEmpty());
                }
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertEquals(expected, names);
    }

    @Test
    void testDeleteBeforeIterator() throws IOException {
        final int count = 100;

        List<String> expected = new ArrayList<>();
        Directory foo = (Directory) root.add("foo", new Directory());
        for (int i = 0; i < count; i++) {
            foo.add("file" + i, new File());
        }
        Collections.sort(expected);

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/foo"), entry -> true)) {
            root.remove("foo");
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertEquals(expected, names);
    }

    @Test
    void testThrowWhileIterating() throws IOException {
        root.add("foo", new File());

        Filter<Path> filter = entry -> {
            throw new IOException();
        };
        try (DirectoryStream<Path> stream = fileStore.newDirectoryStream(createPath("/"), filter)) {
            Iterator<Path> iterator = stream.iterator();
            // hasNext already uses the filter, and therefore already causes the exception to be thrown
            DirectoryIteratorException exception = assertThrows(DirectoryIteratorException.class, iterator::hasNext);
            assertThat(exception.getCause(), instanceOf(IOException.class));
        }
    }

    private static final class PatternFilter implements Filter<Path> {

        private final Pattern pattern;

        private PatternFilter(String regex) {
            pattern = Pattern.compile(regex);
        }

        @Override
        public boolean accept(Path entry) {
            return pattern.matcher(entry.getFileName().toString()).matches();
        }
    }
}
