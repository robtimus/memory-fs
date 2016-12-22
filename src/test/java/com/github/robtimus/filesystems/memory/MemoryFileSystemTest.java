/*
 * MemoryFileSystemTest.java
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

import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;

@SuppressWarnings({ "nls", "javadoc" })
public class MemoryFileSystemTest {

    // MemoryFileSystem.getPath

    @Test
    public void testGetPath() throws IOException {
        try (MemoryFileSystem fs = createFileSystem()) {
            testGetPath(fs, "/", "/");
            testGetPath(fs, "/foo/bar", "/", "/foo", "/bar");
            testGetPath(fs, "/foo/../bar", "/foo/", "../bar");
        }
    }

    private void testGetPath(MemoryFileSystem fs, String path, String first, String... more) {
        MemoryPath expected = new MemoryPath(fs, path);
        Path actual = fs.getPath(first, more);
        assertEquals(expected, actual);
    }

    // MemoryFileSystem.toAbsolutePath

    @Test
    public void testToAbsolutePath() throws IOException {
        try (MemoryFileSystem fs = createFileSystem()) {
            testToAbsolutePath(fs, "/", "/");
            testToAbsolutePath(fs, "/foo/bar", "/foo/bar");
            testToAbsolutePath(fs, "/foo/../bar", "/foo/../bar");

            testToAbsolutePath(fs, "", "/");
            testToAbsolutePath(fs, "foo/bar", "/foo/bar");
            testToAbsolutePath(fs, "foo/../bar", "/foo/../bar");
        }
    }

    private void testToAbsolutePath(MemoryFileSystem fs, String path, String expected) {
        MemoryPath expectedPath = new MemoryPath(fs, expected);
        Path actual = new MemoryPath(fs, path).toAbsolutePath();
        assertEquals(expectedPath, actual);
    }

    // helper

    private MemoryFileSystem createFileSystem() {
        return new MemoryFileSystem(new MemoryFileSystemProvider(), MemoryFileStore.INSTANCE);
    }
}
