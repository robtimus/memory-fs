/*
 * MemoryFileAttributeView.java
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import com.github.robtimus.filesystems.attribute.FileAttributeViewMetadata;

/**
 * A file attribute view that provides a view of the in-memory file attributes.
 * <p>
 * A {@code MemoryFileAttributeView} is a {@link BasicFileAttributeView} that additionally supports access to the set of in-memory attribute flags
 * that are used to indicate if the file is read-only, or hidden.
 * <p>
 * Where dynamic access to file attributes is required, the attributes supported by this attribute view are as defined by
 * {@code BasicFileAttributeView}, and in addition, the following attributes are supported:
 * <blockquote>
 * <table class="striped">
 *   <caption style="display:none">Table columns</caption>
 *   <tr><th class="colFirst">Name</th><th class="colLast">Type</th></tr>
 *   <tr class="altColor"><td class="colFirst">readOnly</td><td class="colLast">{@link Boolean}</td></tr>
 *   <tr class="rowColor"><td class="colFirst">hidden</td><td class="colLast">{@link Boolean}</td></tr>
 * </table>
 * </blockquote>
 * The {@link Files#getAttribute getAttribute} method may be used to read any of these attributes, or any of the attributes defined by
 * {@link BasicFileAttributeView} as if by invoking the {@link #readAttributes()} method.
 * <p>
 * The {@link Files#setAttribute setAttribute} method may be used to update the file's last modified time, last access time or create time attributes
 * as defined by {@link BasicFileAttributeView}. It may also be used to update the in-memory attributes as if by invoking the
 * {@link #setReadOnly(boolean) setReadOnly}, and {@link #setHidden(boolean) setHidden} methods respectively.
 *
 * @author Rob Spoor
 */
public interface MemoryFileAttributeView extends BasicFileAttributeView {

    /**
     * The view name.
     *
     * @since 2.1
     */
    @SuppressWarnings("nls")
    String MEMORY_VIEW = "memory";

    /**
     * The name of the {@code readOnly} attribute.
     *
     * @since 2.1
     */
    @SuppressWarnings("nls")
    String READ_ONLY = "readOnly";

    /**
     * The name of the {@code hidden} attribute.
     *
     * @since 2.1
     */
    @SuppressWarnings("nls")
    String HIDDEN = "hidden";

    /**
     * Metadata for this view.
     *
     * @since 2.1
     */
    FileAttributeViewMetadata METADATA = FileAttributeViewMetadata.forView(MemoryFileAttributeView.class)
            .withAttributes(FileAttributeViewMetadata.BASIC)
            .withAttribute(READ_ONLY, Boolean.class)
            .withAttribute(HIDDEN, Boolean.class)
            .build();

    /**
     * Returns the name of the attribute view. Attribute views of this type have the name {@code "memory"}.
     */
    @Override
    String name();

    @Override
    MemoryFileAttributes readAttributes() throws IOException;

    /**
     * Updates the value of the read-only attribute.
     *
     * @param value The new value of the attribute.
     * @throws IOException If an I/O error occurs.
     */
    void setReadOnly(boolean value) throws IOException;

    /**
     * Updates the value of the hidden attribute.
     *
     * @param value The new value of the attribute.
     * @throws IOException If an I/O error occurs.
     */
    void setHidden(boolean value) throws IOException;
}
