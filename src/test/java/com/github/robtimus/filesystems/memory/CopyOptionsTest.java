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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.Messages;

@SuppressWarnings("javadoc")
public class CopyOptionsTest {

    @Test
    public void testForCopy() {
        CopyOptions options = CopyOptions.forCopy();
        assertFalse(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.COPY_ATTRIBUTES);
        assertTrue(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING);
        assertFalse(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        assertTrue(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.ATOMIC_MOVE);
        assertFalse(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.ATOMIC_MOVE);
        assertTrue(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        assertFalse(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.ATOMIC_MOVE);
        assertTrue(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forCopy(LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
        assertTrue(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
        assertTrue(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);
        assertTrue(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forCopy(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.ATOMIC_MOVE,
                LinkOption.NOFOLLOW_LINKS);
        assertTrue(options.copyAttributes);
        assertTrue(options.replaceExisting);
    }

    @Test
    public void testForCopyWithInvalid() {
        testForCopyWithInvalid(DummyOption.DUMMY);
    }

    private void testForCopyWithInvalid(CopyOption option) {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> CopyOptions.forCopy(option));
        assertEquals(Messages.fileSystemProvider().unsupportedCopyOption(option).getMessage(), exception.getMessage());
    }

    @Test
    public void testForMove() {
        CopyOptions options = CopyOptions.forMove();
        assertFalse(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forMove(StandardCopyOption.REPLACE_EXISTING);
        assertFalse(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forMove(StandardCopyOption.ATOMIC_MOVE);
        assertFalse(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forMove(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        assertFalse(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forMove(LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forMove(StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.copyAttributes);
        assertTrue(options.replaceExisting);

        options = CopyOptions.forMove(StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.copyAttributes);
        assertFalse(options.replaceExisting);

        options = CopyOptions.forMove(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.copyAttributes);
        assertTrue(options.replaceExisting);
    }

    @Test
    public void testForMoveWithInvalid() {
        StandardCopyOption option = StandardCopyOption.COPY_ATTRIBUTES;
        testForMoveWithInvalid(option);
    }

    private void testForMoveWithInvalid(CopyOption option) {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> CopyOptions.forMove(option));
        assertEquals(Messages.fileSystemProvider().unsupportedCopyOption(option).getMessage(), exception.getMessage());
    }

    enum DummyOption implements CopyOption {
        DUMMY
    }
}
