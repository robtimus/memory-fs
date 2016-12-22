/*
 * MemoryFileStoreFileOutputStreamTest.java
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import com.github.robtimus.filesystems.memory.MemoryFileStore.File;
import com.github.robtimus.filesystems.memory.MemoryFileStore.OnCloseAction;

@SuppressWarnings({ "nls", "javadoc" })
public class MemoryFileStoreFileOutputStreamTest {

    @Test
    public void testWriteSingle() throws IOException {
        File file = new File();

        try (OutputStream output = file.newOutputStream(false, null)) {
            output.write('H');
            output.write('e');
            output.write('l');
            output.write('l');
            output.write('o');
            assertArrayEquals("Hello".getBytes(), file.getContent());
        }
    }

    @Test
    public void testWriteBulk() throws IOException {
        File file = new File();

        try (OutputStream output = file.newOutputStream(false, null)) {
            output.write("Hello".getBytes());
            assertArrayEquals("Hello".getBytes(), file.getContent());
        }
    }

    @Test
    public void testOnClose() throws IOException {
        File file = new File();

        final AtomicInteger runCount = new AtomicInteger(0);
        final OnCloseAction onClose = new OnCloseAction() {
            @Override
            public void run() {
                runCount.incrementAndGet();
            }
        };
        try (OutputStream output = file.newOutputStream(false, onClose)) {
            output.close();
            output.close();
            output.close();
        }
        assertEquals(1, runCount.get());
    }
}
