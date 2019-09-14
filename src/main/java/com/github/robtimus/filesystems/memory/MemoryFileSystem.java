/*
 * MemoryFileSystem.java
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import com.github.robtimus.filesystems.Messages;
import com.github.robtimus.filesystems.PathMatcherSupport;

/**
 * An in-memory file system.
 *
 * @author Rob Spoor
 */
final class MemoryFileSystem extends FileSystem {

    @SuppressWarnings("nls")
    private static final Set<String> SUPPORTED_FILE_ATTRIBUTE_VIEWS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("basic", "memory")));

    private final FileSystemProvider provider;
    private final Iterable<Path> rootDirectories;
    private final MemoryFileStore fileStore;
    private final Iterable<FileStore> fileStores;

    MemoryFileSystem(MemoryFileSystemProvider provider, MemoryFileStore fileStore) {
        this.provider = Objects.requireNonNull(provider);
        this.rootDirectories = Collections.<Path>singleton(new MemoryPath(this, "/")); //$NON-NLS-1$
        this.fileStore = Objects.requireNonNull(fileStore);
        this.fileStores = Collections.<FileStore>singleton(fileStore);
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        // does nothing
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/"; //$NON-NLS-1$
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return rootDirectories;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return fileStores;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return SUPPORTED_FILE_ATTRIBUTE_VIEWS;
    }

    @Override
    public Path getPath(String first, String... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String s : more) {
            sb.append("/").append(s); //$NON-NLS-1$
        }
        return new MemoryPath(this, sb.toString());
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        final Pattern pattern = PathMatcherSupport.toPattern(syntaxAndPattern);
        return new PathMatcher() {
            @Override
            public boolean matches(Path path) {
                return pattern.matcher(path.toString()).matches();
            }
        };
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw Messages.unsupportedOperation(FileSystem.class, "getUserPrincipalLookupService"); //$NON-NLS-1$
    }

    @Override
    public WatchService newWatchService() throws IOException {
        // FIXME: support watch service?
        throw Messages.unsupportedOperation(FileSystem.class, "newWatchService"); //$NON-NLS-1$
    }

    URI toUri(MemoryPath path) {
        return URI.create("memory:" + toAbsolutePath(path).normalize()); //$NON-NLS-1$
    }

    MemoryPath toAbsolutePath(MemoryPath path) {
        if (path.isAbsolute()) {
            return path;
        }
        return new MemoryPath(this, "/" + path.path()); //$NON-NLS-1$
    }

    MemoryPath toRealPath(MemoryPath path, LinkOption... options) throws IOException {
        return fileStore.toRealPath(path, options);
    }

    String toString(MemoryPath path) {
        return path.path();
    }

    byte[] getContent(MemoryPath path) throws IOException {
        return fileStore.getContent(path);
    }

    void setContent(MemoryPath path, byte[] content) throws IOException {
        fileStore.setContent(path, content);
    }

    InputStream newInputStream(MemoryPath path, OpenOption... options) throws IOException {
        return fileStore.newInputStream(path, options);
    }

    OutputStream newOutputStream(MemoryPath path, OpenOption... options) throws IOException {
        return fileStore.newOutputStream(path, options);
    }

    FileChannel newFileChannel(MemoryPath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return fileStore.newFileChannel(path, options, attrs);
    }

    SeekableByteChannel newByteChannel(MemoryPath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return fileStore.newByteChannel(path, options, attrs);
    }

    DirectoryStream<Path> newDirectoryStream(MemoryPath dir, Filter<? super Path> filter) throws IOException {
        return fileStore.newDirectoryStream(dir, filter);
    }

    void createDirectory(MemoryPath dir, FileAttribute<?>... attrs) throws IOException {
        fileStore.createDirectory(dir, attrs);
    }

    void delete(MemoryPath path) throws IOException {
        fileStore.delete(path);
    }

    boolean deleteIfExists(MemoryPath path) throws IOException {
        return fileStore.deleteIfExists(path);
    }

    void copy(MemoryPath source, MemoryPath target, CopyOption... options) throws IOException {
        fileStore.copy(source, target, options);
    }

    void move(MemoryPath source, MemoryPath target, CopyOption... options) throws IOException {
        fileStore.move(source, target, options);
    }

    boolean isSameFile(MemoryPath path, MemoryPath path2) throws IOException {
        return fileStore.isSameFile(path, path2);
    }

    boolean isHidden(MemoryPath path) throws IOException {
        return fileStore.isHidden(path);
    }

    FileStore getFileStore(MemoryPath path) throws IOException {
        return fileStore.getFileStore(path);
    }

    void checkAccess(MemoryPath path, AccessMode... modes) throws IOException {
        fileStore.checkAccess(path, modes);
    }

    void setTimes(MemoryPath path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        fileStore.setTimes(path, lastModifiedTime, lastAccessTime, createTime);
    }

    MemoryFileAttributes readAttributes(MemoryPath path) throws IOException {
        return fileStore.readAttributes(path);
    }

    void setReadOnly(MemoryPath path, boolean value) throws IOException {
        fileStore.setReadOnly(path, value);
    }

    void setHidden(MemoryPath path, boolean value) throws IOException {
        fileStore.setHidden(path, value);
    }

    Map<String, Object> readAttributes(MemoryPath path, String attributes, LinkOption... options) throws IOException {
        return fileStore.readAttributes(path, attributes, options);
    }

    void setAttribute(MemoryPath path, String attribute, Object value, LinkOption... options) throws IOException {
        fileStore.setAttribute(path, attribute, value, options);
    }
}
