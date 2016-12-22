/*
 * MemoryFileAttributes.java
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

import java.nio.file.attribute.BasicFileAttributes;

/**
 * File attributes associated with a file in the in-memory file system.
 *
 * @author Rob Spoor
 */
public interface MemoryFileAttributes extends BasicFileAttributes {

    /**
     * Returns the value of the read-only attribute.
     * <p>
     * This attribute is used as a simple access control mechanism to prevent files from being deleted or updated.
     *
     * @return The value of the read-only attribute.
     */
    boolean isReadOnly();

    /**
     * Returns the value of the hidden attribute.
     * <p>
     * This attribute is used to indicate if the file is visible to users.
     *
     * @return The value of the hidden attribute.
     */
    boolean isHidden();
}
