/*
 * OpenOptionsTest.java
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Test;
import com.github.robtimus.filesystems.Messages;

@SuppressWarnings({ "nls", "javadoc" })
public class OpenOptionsTest {

    @Test
    public void testForNewInputStream() {
        OpenOptions options = OpenOptions.forNewInputStream();
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.DELETE_ON_CLOSE);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.SPARSE);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, StandardOpenOption.SPARSE);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SPARSE);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SPARSE);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.SYNC);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, StandardOpenOption.SYNC);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SYNC);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SYNC);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.DSYNC);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, StandardOpenOption.DSYNC);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.DSYNC);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.DSYNC);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(LinkOption.NOFOLLOW_LINKS);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.DELETE_ON_CLOSE, LinkOption.NOFOLLOW_LINKS);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewInputStream(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE, LinkOption.NOFOLLOW_LINKS);
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);
    }

    @Test
    public void testForNewInputStreamWithInvalid() {
        testForNewInputStreamWithInvalid(StandardOpenOption.WRITE);
        testForNewInputStreamWithInvalid(StandardOpenOption.APPEND);
        testForNewInputStreamWithInvalid(StandardOpenOption.TRUNCATE_EXISTING);
        testForNewInputStreamWithInvalid(StandardOpenOption.CREATE);
        testForNewInputStreamWithInvalid(StandardOpenOption.CREATE_NEW);
    }

    private void testForNewInputStreamWithInvalid(OpenOption option) {
        try {
            OpenOptions.forNewInputStream(option);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            UnsupportedOperationException expected = Messages.fileSystemProvider().unsupportedOpenOption(option);
            assertEquals(expected.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testForNewOutStream() {
        OpenOptions options = OpenOptions.forNewOutputStream();
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertTrue(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.APPEND);
        assertFalse(options.read);
        assertTrue(options.write);
        assertTrue(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.DELETE_ON_CLOSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.APPEND, StandardOpenOption.DELETE_ON_CLOSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertTrue(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.TRUNCATE_EXISTING);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DELETE_ON_CLOSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DELETE_ON_CLOSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.SPARSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.SPARSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.APPEND, StandardOpenOption.SPARSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertTrue(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SPARSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SPARSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SPARSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.SYNC);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SYNC);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.SYNC);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.DSYNC);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.DSYNC);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.DSYNC);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.DELETE_ON_CLOSE, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE, LinkOption.NOFOLLOW_LINKS);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertTrue(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.DELETE_ON_CLOSE);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertTrue(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewOutputStream(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.CREATE_NEW);
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertTrue(options.create);
        assertTrue(options.createNew);
        assertFalse(options.deleteOnClose);
    }

    @Test
    public void testForNewOutputStreamWithInvalid() {
        testForNewOutputStreamWithInvalid(StandardOpenOption.READ);
    }

    private void testForNewOutputStreamWithInvalid(OpenOption option) {
        try {
            OpenOptions.forNewOutputStream(option);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            UnsupportedOperationException expected = Messages.fileSystemProvider().unsupportedOpenOption(option);
            assertEquals(expected.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testForNewOutputStreamWithIllegalCombinations() {
        testForNewOutputStreamWithIllegalCombination(StandardOpenOption.APPEND, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void testForNewOutputStreamWithIllegalCombination(OpenOption... options) {
        try {
            OpenOptions.forNewOutputStream(options);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            IllegalArgumentException expected = Messages.fileSystemProvider().illegalOpenOptionCombination(options);
            assertEquals(expected.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testForNewFileChannel() {
        OpenOptions options = OpenOptions.forNewFileChannel(EnumSet.noneOf(StandardOpenOption.class));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.READ));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.WRITE));
        assertFalse(options.read);
        assertTrue(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.APPEND));
        assertFalse(options.read);
        assertTrue(options.write);
        assertTrue(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.TRUNCATE_EXISTING));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.CREATE));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertTrue(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.CREATE_NEW));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertTrue(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.DELETE_ON_CLOSE));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertTrue(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.SPARSE));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.SYNC));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);

        options = OpenOptions.forNewFileChannel(EnumSet.of(StandardOpenOption.DSYNC));
        assertTrue(options.read);
        assertFalse(options.write);
        assertFalse(options.append);
        assertFalse(options.create);
        assertFalse(options.createNew);
        assertFalse(options.deleteOnClose);
    }

    @Test
    public void testForNewFileChannelWithInvalid() {
        testForNewFileChannelInvalid(DummyOption.DUMMY);
    }

    private void testForNewFileChannelInvalid(OpenOption option) {
        try {
            OpenOptions.forNewFileChannel(Collections.singleton(option));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            UnsupportedOperationException expected = Messages.fileSystemProvider().unsupportedOpenOption(option);
            assertEquals(expected.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testForNewFileChannelWithIllegalCombinations() {
        testForNewFileChannelWithIllegalCombination(StandardOpenOption.READ, StandardOpenOption.APPEND);
        testForNewFileChannelWithIllegalCombination(StandardOpenOption.APPEND, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void testForNewFileChannelWithIllegalCombination(StandardOpenOption... options) {
        try {
            OpenOptions.forNewFileChannel(EnumSet.of(options[0], options));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            IllegalArgumentException expected = Messages.fileSystemProvider().illegalOpenOptionCombination(options);
            assertEquals(expected.getMessage(), e.getMessage());
        }
    }

    enum DummyOption implements OpenOption {
        DUMMY
    }
}
