/*
 * CopyOptions.java
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

import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import com.github.robtimus.filesystems.Messages;

/**
 * A representation of possible copy options.
 *
 * @author Rob Spoor
 */
final class CopyOptions {

    final boolean replaceExisting;
    final boolean copyAttributes;
    final boolean followLinks;

    private CopyOptions(boolean replaceExisting, boolean copyAttributes, boolean followLinks) {
        this.replaceExisting = replaceExisting;
        this.copyAttributes = copyAttributes;
        this.followLinks = followLinks;
    }

    static CopyOptions forCopy(CopyOption... options) {

        boolean replaceExisting = false;
        boolean copyAttributes = false;
        boolean followLinks = true;

        for (CopyOption option : options) {
            if (option == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
            } else if (option == StandardCopyOption.COPY_ATTRIBUTES) {
                copyAttributes = true;
            } else if (option == LinkOption.NOFOLLOW_LINKS) {
                followLinks = false;
            } else if (!isIgnoredCopyOption(option)) {
                throw Messages.fileSystemProvider().unsupportedCopyOption(option);
            }
        }

        return new CopyOptions(replaceExisting, copyAttributes, followLinks);
    }

    static CopyOptions forMove(CopyOption... options) {
        boolean replaceExisting = false;
        boolean followLinks = true;

        for (CopyOption option : options) {
            if (option == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
            } else if (option == LinkOption.NOFOLLOW_LINKS) {
                followLinks = false;
            } else if (!isIgnoredCopyOption(option)) {
                throw Messages.fileSystemProvider().unsupportedCopyOption(option);
            }
        }

        return new CopyOptions(replaceExisting, false, followLinks);
    }

    private static boolean isIgnoredCopyOption(CopyOption option) {
        return option == StandardCopyOption.ATOMIC_MOVE;
    }
}
