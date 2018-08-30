/*
 * MemoryFileSystemProvider.java
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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.github.robtimus.filesystems.Messages;

/**
 * A provider for in-memory file systems.
 *
 * @author Rob Spoor
 */
public final class MemoryFileSystemProvider extends FileSystemProvider {

    private final MemoryFileSystem fs;

    /**
     * Creates a new in-memory file system provider.
     */
    public MemoryFileSystemProvider() {
        this(MemoryFileStore.INSTANCE);
    }

    // package private for test purposes
    MemoryFileSystemProvider(MemoryFileStore fileStore) {
        fs = new MemoryFileSystem(this, fileStore);
    }

    /**
     * Returns the URI scheme that identifies this provider: {@code memory}.
     */
    @Override
    public String getScheme() {
        return "memory"; //$NON-NLS-1$
    }

    /**
     * Constructs a new {@code FileSystem} object identified by a URI.
     * <p>
     * Because there is only one single in-memory file system that is created automatically, this method will always throw a
     * {@link FileSystemAlreadyExistsException}.
     */
    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        checkURI(uri);
        throw new FileSystemAlreadyExistsException();
    }

    /**
     * Returns the single in-memory file system.
     * <p>
     * The URI must have a {@link URI#getScheme() scheme} equal to {@link #getScheme()}.
     */
    @Override
    public FileSystem getFileSystem(URI uri) {
        checkURI(uri);
        return fs;
    }

    /**
     * Return a {@code Path} object by converting the given {@link URI}.
     * <p>
     * The URI must have a {@link URI#getScheme() scheme} equal to {@link #getScheme()}.
     * Its scheme specific part will be used as the path (either relative or absolute) for the returned {@code Path} object.
     */
    @Override
    public Path getPath(URI uri) {
        checkURI(uri);
        return fs.getPath(uri.getSchemeSpecificPart());
    }

    private void checkURI(URI uri) {
        if (!uri.isAbsolute()) {
            throw Messages.uri().notAbsolute(uri);
        }
        if (!getScheme().equalsIgnoreCase(uri.getScheme())) {
            throw Messages.uri().invalidScheme(uri, getScheme());
        }
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return toMemoryPath(path).newInputStream(options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        return toMemoryPath(path).newOutputStream(options);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return toMemoryPath(path).newByteChannel(options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
        return toMemoryPath(dir).newDirectoryStream(filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        toMemoryPath(dir).createDirectory(attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        toMemoryPath(path).delete();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        toMemoryPath(source).copy(toMemoryPath(target), options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        toMemoryPath(source).move(toMemoryPath(target), options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return toMemoryPath(path).isSameFile(path2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return toMemoryPath(path).isHidden();
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return toMemoryPath(path).getFileStore();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        toMemoryPath(path).checkAccess(modes);
    }

    /**
     * Returns a file attribute view of a given type.
     * This method works in exactly the manner specified by the {@link Files#getFileAttributeView(Path, Class, LinkOption...)} method.
     * <p>
     * This provider supports {@link BasicFileAttributeView} and {@link MemoryFileAttributeView}.
     * All other classes will result in a {@code null} return value.
     */
    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        Objects.requireNonNull(type);
        if (type == BasicFileAttributeView.class) {
            return type.cast(new AttributeView("basic", toMemoryPath(path))); //$NON-NLS-1$
        }
        if (type == MemoryFileAttributeView.class) {
            return type.cast(new AttributeView("memory", toMemoryPath(path))); //$NON-NLS-1$
        }
        return null;
    }

    private static final class AttributeView implements MemoryFileAttributeView {

        private final String name;
        private final MemoryPath path;

        private AttributeView(String name, MemoryPath path) {
            this.name = Objects.requireNonNull(name);
            this.path = Objects.requireNonNull(path);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
            path.setTimes(lastModifiedTime, lastAccessTime, createTime);
        }

        @Override
        public MemoryFileAttributes readAttributes() throws IOException {
            return path.readAttributes();
        }

        @Override
        public void setReadOnly(boolean value) throws IOException {
            path.setReadOnly(value);
        }

        @Override
        public void setHidden(boolean value) throws IOException {
            path.setHidden(value);
        }
    }

    /**
     * Reads a file's attributes as a bulk operation.
     * This method works in exactly the manner specified by the {@link Files#readAttributes(Path, Class, LinkOption...)} method.
     * <p>
     * This provider supports {@link BasicFileAttributes} and {@link MemoryFileAttributes}.
     * All other classes will result in an {@link UnsupportedOperationException} to be thrown.
     */
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class || type == MemoryFileAttributes.class) {
            return type.cast(toMemoryPath(path).readAttributes());
        }
        throw Messages.fileSystemProvider().unsupportedFileAttributesType(type);
    }

    /**
     * Reads a set of file attributes as a bulk operation.
     * This method works in exactly the manner specified by the {@link Files#readAttributes(Path, String, LinkOption...)} method.
     * <p>
     * This provider supports views {@code basic} and {@code memory}, where {@code basic} will be used if no view is given.
     * All other views will result in an {@link UnsupportedOperationException} to be thrown.
     */
    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return toMemoryPath(path).readAttributes(attributes, options);
    }

    /**
     * Sets the value of a file attribute.
     * This method works in exactly the manner specified by the {@link Files#setAttribute(Path, String, Object, LinkOption...)} method.
     * <p>
     * This provider supports views {@code basic} and {@code memory}, where {@code basic} will be used if no view is given.
     * All other views will result in an {@link UnsupportedOperationException} to be thrown.
     */
    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        toMemoryPath(path).setAttribute(attribute, value, options);
    }

    /**
     * Returns the content of an existing file. This method is shorthand for the following: <pre>
     * URI uri = URI.create("memory:" + path);
     * return getContent(Paths.get(uri));</pre>
     *
     * @param path The path to the file to return the content of.
     * @return The content of the given file.
     * @throws NullPointerException If the given path is {@code null}.
     * @throws IllegalArgumentException If the path does not lead to a valid URI.
     * @throws IOException If an I/O error occurs.
     * @see #getContent(Path)
     * @since 1.1
     */
    public static byte[] getContent(String path) throws IOException {
        Objects.requireNonNull(path);
        URI uri = URI.create("memory:" + path); //$NON-NLS-1$
        return getContent(Paths.get(uri));
    }

    /**
     * Returns the content of an existing file.
     * <p>
     * This method works like {@link Files#readAllBytes(Path)} but is more optimized for in-memory paths.
     *
     * @param path The path to the file to return the content of.
     * @return The content of the given file.
     * @throws NullPointerException If the given path is {@code null}.
     * @throws ProviderMismatchException If the given path is not an in-memory path.
     * @throws IOException If an I/O error occurs.
     * @since 1.1
     */
    public static byte[] getContent(Path path) throws IOException {
        return toMemoryPath(path).getContent();
    }

    /**
     * Sets the content of a file. If the file does not exist it will be created. This method is shorthand for the following: <pre>
     * URI uri = URI.create("memory:" + path);
     * setContents(Paths.get(uri), content);</pre>
     *
     * @param path The path to the file to set the content of.
     * @param content The new content for the file.
     * @throws NullPointerException If the given path or content is {@code null}.
     * @throws IllegalArgumentException If the path does not lead to a valid URI.
     * @throws IOException If an I/O error occurs.
     * @see #setContent(Path, byte[])
     * @since 1.1
     */
    public static void setContent(String path, byte[] content) throws IOException {
        Objects.requireNonNull(path);
        URI uri = URI.create("memory:" + path); //$NON-NLS-1$
        setContent(Paths.get(uri), content);
    }

    /**
     * Sets the content of a file. If the file does not exist it will be created.
     * <p>
     * This method works like {@link Files#write(Path, byte[], OpenOption...)} with no options given but is more optimized for in-memory paths.
     * It does perform the necessary access checks on the file and/or its parent directory.
     *
     * @param path The path to the file to set the content of.
     * @param content The new content for the file.
     * @throws NullPointerException If the given path or content is {@code null}.
     * @throws ProviderMismatchException If the given path is not an in-memory path.
     * @throws IOException If an I/O error occurs.
     * @since 1.1
     */
    public static void setContent(Path path, byte[] content) throws IOException {
        Objects.requireNonNull(content);
        toMemoryPath(path).setContent(content);
    }

    private static MemoryPath toMemoryPath(Path path) {
        Objects.requireNonNull(path);
        if (path instanceof MemoryPath) {
            return (MemoryPath) path;
        }
        throw new ProviderMismatchException();
    }

    /**
     * Clears all stored in-memory files and directories. Afterwards only the root directory will exist.
     *
     * @since 1.1
     */
    public static void clear() {
        MemoryFileStore.INSTANCE.clear();
    }
}
