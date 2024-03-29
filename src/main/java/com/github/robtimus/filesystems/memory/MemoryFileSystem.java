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

import static com.github.robtimus.filesystems.SimpleAbstractPath.ROOT_PATH;
import static com.github.robtimus.filesystems.SimpleAbstractPath.SEPARATOR;
import static com.github.robtimus.filesystems.memory.MemoryFileStore.VIEWS;
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
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private final FileSystemProvider provider;
    private final Iterable<Path> rootDirectories;
    private final MemoryFileStore fileStore;
    private final Iterable<FileStore> fileStores;

    MemoryFileSystem(MemoryFileSystemProvider provider, MemoryFileStore fileStore) {
        this.provider = Objects.requireNonNull(provider);
        this.rootDirectories = Collections.singleton(new MemoryPath(this, ROOT_PATH));
        this.fileStore = Objects.requireNonNull(fileStore);
        this.fileStores = Collections.singleton(fileStore);
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
        return SEPARATOR;
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
        return VIEWS.viewNames();
    }

    @Override
    public Path getPath(String first, String... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String s : more) {
            sb.append(SEPARATOR).append(s);
        }
        return new MemoryPath(this, sb.toString());
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        final Pattern pattern = PathMatcherSupport.toPattern(syntaxAndPattern);
        return path -> pattern.matcher(path.toString()).matches();
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
        return new MemoryPath(this, SEPARATOR + path.path());
    }

    MemoryPath toRealPath(MemoryPath path, boolean followLinks) throws IOException {
        return fileStore.toRealPath(path, followLinks);
    }

    String toString(MemoryPath path) {
        return path.path();
    }

    byte[] getContent(MemoryPath path) throws IOException {
        return fileStore.getContent(path);
    }

    Optional<byte[]> getContentIfExists(MemoryPath path) throws IOException {
        return fileStore.getContentIfExists(path);
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

    void createSymbolicLink(MemoryPath link, MemoryPath target, FileAttribute<?>... attrs) throws IOException {
        fileStore.createSymbolicLink(link, target, attrs);
    }

    void createLink(MemoryPath link, MemoryPath existing) throws IOException {
        fileStore.createLink(link, existing);
    }

    void delete(MemoryPath path) throws IOException {
        fileStore.delete(path);
    }

    boolean deleteIfExists(MemoryPath path) throws IOException {
        return fileStore.deleteIfExists(path);
    }

    MemoryPath readSymbolicLink(MemoryPath link) throws IOException {
        return fileStore.readSymbolicLink(link);
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

    <V extends FileAttributeView> V getFileAttributeView(MemoryPath path, Class<V> type, boolean followLinks) {
        return fileStore.getFileAttributeView(path, type, followLinks);
    }

    MemoryFileAttributes readAttributes(MemoryPath path, boolean followLinks) throws IOException {
        return fileStore.readAttributes(path, followLinks);
    }

    Map<String, Object> readAttributes(MemoryPath path, String attributes, boolean followLinks) throws IOException {
        return fileStore.readAttributes(path, attributes, followLinks);
    }

    void setAttribute(MemoryPath path, String attribute, Object value, boolean followLinks) throws IOException {
        fileStore.setAttribute(path, attribute, value, followLinks);
    }
}
