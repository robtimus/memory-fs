/*
 * MemoryPath.java
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
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.github.robtimus.filesystems.LinkOptionSupport;
import com.github.robtimus.filesystems.Messages;
import com.github.robtimus.filesystems.SimpleAbstractPath;

/**
 * A path for in-memory file systems.
 *
 * @author Rob Spoor
 */
final class MemoryPath extends SimpleAbstractPath {

    private final MemoryFileSystem fs;

    MemoryPath(MemoryFileSystem fs, String path) {
        super(path);
        this.fs = Objects.requireNonNull(fs);
    }

    private MemoryPath(MemoryFileSystem fs, String path, boolean normalized) {
        super(path, normalized);
        this.fs = Objects.requireNonNull(fs);
    }

    @Override
    protected MemoryPath createPath(String path) {
        return new MemoryPath(fs, path, true);
    }

    @Override
    public MemoryFileSystem getFileSystem() {
        return fs;
    }

    @Override
    public MemoryPath getRoot() {
        return (MemoryPath) super.getRoot();
    }

    @Override
    public MemoryPath getFileName() {
        return (MemoryPath) super.getFileName();
    }

    @Override
    public MemoryPath getParent() {
        return (MemoryPath) super.getParent();
    }

    @Override
    public MemoryPath getName(int index) {
        return (MemoryPath) super.getName(index);
    }

    @Override
    public MemoryPath subpath(int beginIndex, int endIndex) {
        return (MemoryPath) super.subpath(beginIndex, endIndex);
    }

    @Override
    public MemoryPath normalize() {
        return (MemoryPath) super.normalize();
    }

    @Override
    public MemoryPath resolve(Path other) {
        return (MemoryPath) super.resolve(other);
    }

    @Override
    public MemoryPath resolve(String other) {
        return (MemoryPath) super.resolve(other);
    }

    @Override
    public MemoryPath resolveSibling(Path other) {
        return (MemoryPath) super.resolveSibling(other);
    }

    @Override
    public MemoryPath resolveSibling(String other) {
        return (MemoryPath) super.resolveSibling(other);
    }

    @Override
    public MemoryPath relativize(Path other) {
        return (MemoryPath) super.relativize(other);
    }

    @Override
    public URI toUri() {
        return fs.toUri(this);
    }

    @Override
    public MemoryPath toAbsolutePath() {
        return fs.toAbsolutePath(this);
    }

    @Override
    public MemoryPath toRealPath(LinkOption... options) throws IOException {
        boolean followLinks = LinkOptionSupport.followLinks(options);
        return fs.toRealPath(this, followLinks);
    }

    @Override
    public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
        // FIXME: support watch service?
        throw Messages.unsupportedOperation(Path.class, "register"); //$NON-NLS-1$
    }

    @Override
    public String toString() {
        return fs.toString(this);
    }

    byte[] getContent() throws IOException {
        return fs.getContent(this);
    }

    Optional<byte[]> getContentIfExists() throws IOException {
        return fs.getContentIfExists(this);
    }

    void setContent(byte[] content) throws IOException {
        fs.setContent(this, content);
    }

    InputStream newInputStream(OpenOption... options) throws IOException {
        return fs.newInputStream(this, options);
    }

    OutputStream newOutputStream(OpenOption... options) throws IOException {
        return fs.newOutputStream(this, options);
    }

    FileChannel newFileChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return fs.newFileChannel(this, options, attrs);
    }

    SeekableByteChannel newByteChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return fs.newByteChannel(this, options, attrs);
    }

    DirectoryStream<Path> newDirectoryStream(Filter<? super Path> filter) throws IOException {
        return fs.newDirectoryStream(this, filter);
    }

    void createDirectory(FileAttribute<?>... attrs) throws IOException {
        fs.createDirectory(this, attrs);
    }

    void createSymbolicLink(MemoryPath target, FileAttribute<?>... attrs) throws IOException {
        fs.createSymbolicLink(this, target, attrs);
    }

    void createLink(MemoryPath existing) throws IOException {
        fs.createLink(this, existing);
    }

    void delete() throws IOException {
        fs.delete(this);
    }

    boolean deleteIfExists() throws IOException {
        return fs.deleteIfExists(this);
    }

    MemoryPath readSymbolicLink() throws IOException {
        return fs.readSymbolicLink(this);
    }

    void copy(MemoryPath target, CopyOption... options) throws IOException {
        fs.copy(this, target, options);
    }

    void move(MemoryPath target, CopyOption... options) throws IOException {
        fs.move(this, target, options);
    }

    @SuppressWarnings("resource")
    boolean isSameFile(Path other) throws IOException {
        if (this.equals(other)) {
            return true;
        }
        if (other == null || getFileSystem() != other.getFileSystem()) {
            return false;
        }
        return fs.isSameFile(this, (MemoryPath) other);
    }

    boolean isHidden() throws IOException {
        return fs.isHidden(this);
    }

    FileStore getFileStore() throws IOException {
        return fs.getFileStore(this);
    }

    void checkAccess(AccessMode... modes) throws IOException {
        fs.checkAccess(this, modes);
    }

    <V extends FileAttributeView> V getFileAttributeView(Class<V> type, boolean followLinks) {
        return fs.getFileAttributeView(this, type, followLinks);
    }

    MemoryFileAttributes readAttributes(boolean followLinks) throws IOException {
        return fs.readAttributes(this, followLinks);
    }

    Map<String, Object> readAttributes(String attributes, boolean followLinks) throws IOException {
        return fs.readAttributes(this, attributes, followLinks);
    }

    void setAttribute(String attribute, Object value, boolean followLinks) throws IOException {
        fs.setAttribute(this, attribute, value, followLinks);
    }
}
