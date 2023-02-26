/*
 * CopyOptionsTest.java
 * Copyright 2019 Rob Spoor
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.Messages;

class CopyOptionsTest {

    @Nested
    class ForCopy {

        @Test
        void testWithNoOptions() {
            CopyOptions options = CopyOptions.forCopy();

            assertFalse(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithCopyAttributes() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.COPY_ATTRIBUTES);

            assertTrue(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithReplaceExisting() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING);

            assertFalse(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndCopyAttributes() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

            assertTrue(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithAtomicMove() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.ATOMIC_MOVE);

            assertFalse(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithCopyAttributesAndAtomicMove() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.ATOMIC_MOVE);

            assertTrue(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndAtomicMove() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            assertFalse(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndCopyAttributesAndAtomicMove() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.ATOMIC_MOVE);

            assertTrue(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithNoFollowLinks() {
            CopyOptions options = CopyOptions.forCopy(LinkOption.NOFOLLOW_LINKS);

            assertFalse(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithCopyAttributesNoFollowLinks() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);

            assertTrue(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);

            assertFalse(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndCopyAttributesAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES,
                    LinkOption.NOFOLLOW_LINKS);

            assertTrue(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithAtomicMoveAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);

            assertFalse(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithCopyAttributesAndAtomicMoveAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);

            assertTrue(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndAtomicMoveAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);

            assertFalse(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndCopyAttributesAndAtomicMoveAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);

            assertTrue(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithUnsupported() {
            testWithUnsupported(DummyOption.DUMMY);
        }

        private void testWithUnsupported(CopyOption option) {
            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> CopyOptions.forCopy(option));
            assertChainEquals(Messages.fileSystemProvider().unsupportedCopyOption(option), exception);
        }
    }

    @Nested
    class ForMove {

        @Test
        void testWithNoOptions() {
            CopyOptions options = CopyOptions.forMove();
            assertFalse(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithReplaceExisting() {
            CopyOptions options = CopyOptions.forMove(StandardCopyOption.REPLACE_EXISTING);

            assertFalse(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithAtomicMove() {
            CopyOptions options = CopyOptions.forMove(StandardCopyOption.ATOMIC_MOVE);

            assertFalse(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndAtomicMove() {
            CopyOptions options = CopyOptions.forMove(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            assertFalse(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithNoFollowLinks() {
            CopyOptions options = CopyOptions.forMove(LinkOption.NOFOLLOW_LINKS);

            assertFalse(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forMove(StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);

            assertFalse(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithAtomicMoveAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forMove(StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);

            assertFalse(options.copyAttributes);
            assertFalse(options.replaceExisting);
        }

        @Test
        void testWithReplaceExistingAndAtomicMoveAndNoFollowLinks() {
            CopyOptions options = CopyOptions.forMove(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);

            assertFalse(options.copyAttributes);
            assertTrue(options.replaceExisting);
        }

        @Test
        void testWithCopyAttributes() {
            testWithInvalid(StandardCopyOption.COPY_ATTRIBUTES);
        }

        private void testWithInvalid(CopyOption option) {
            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> CopyOptions.forMove(option));
            assertChainEquals(Messages.fileSystemProvider().unsupportedCopyOption(option), exception);
        }
    }

    enum DummyOption implements CopyOption {
        DUMMY
    }
}
