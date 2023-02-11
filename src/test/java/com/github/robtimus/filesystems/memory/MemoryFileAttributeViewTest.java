/*
 * MemoryFileAttributeViewTest.java
 * Copyright 2023 Rob Spoor
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.attribute.FileAttributeViewMetadata.Operation;

@SuppressWarnings("nls")
class MemoryFileAttributeViewTest {

    @Nested
    class Metadata {

        @Test
        void testViewType() {
            assertEquals(MemoryFileAttributeView.class, MemoryFileAttributeView.METADATA.viewType());
        }

        @Test
        void testViewName() {
            assertEquals(MemoryFileAttributeView.MEMORY_VIEW, MemoryFileAttributeView.METADATA.viewName());
        }

        @Test
        void testAttributeNames() {
            Set<String> expected = new HashSet<>();
            expected.add("lastModifiedTime");
            expected.add("lastAccessTime");
            expected.add("creationTime");
            expected.add("size");
            expected.add("isRegularFile");
            expected.add("isDirectory");
            expected.add("isSymbolicLink");
            expected.add("isOther");
            expected.add("fileKey");
            expected.add("readOnly");
            expected.add("hidden");

            assertEquals(expected, MemoryFileAttributeView.METADATA.attributeNames());
        }

        @Test
        void testAttributeNamesForRead() {
            Set<String> expected = new HashSet<>();
            expected.add("lastModifiedTime");
            expected.add("lastAccessTime");
            expected.add("creationTime");
            expected.add("size");
            expected.add("isRegularFile");
            expected.add("isDirectory");
            expected.add("isSymbolicLink");
            expected.add("isOther");
            expected.add("fileKey");
            expected.add("readOnly");
            expected.add("hidden");

            assertEquals(expected, MemoryFileAttributeView.METADATA.attributeNames(Operation.READ));
        }

        @Test
        void testAttributeNamesForWrite() {
            Set<String> expected = new HashSet<>();
            expected.add("lastModifiedTime");
            expected.add("lastAccessTime");
            expected.add("creationTime");
            expected.add("readOnly");
            expected.add("hidden");

            assertEquals(expected, MemoryFileAttributeView.METADATA.attributeNames(Operation.WRITE));
        }

        @Test
        void testAttributeType() {
            assertEquals(Boolean.class, MemoryFileAttributeView.METADATA.attributeType("readOnly"));
            assertEquals(Boolean.class, MemoryFileAttributeView.METADATA.attributeType("hidden"));
        }
    }
}
