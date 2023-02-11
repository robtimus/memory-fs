/*
 * MemoryFileStore.java
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
import static com.github.robtimus.filesystems.attribute.FileAttributeConstants.BASIC_VIEW;
import static com.github.robtimus.filesystems.attribute.FileAttributeConstants.CREATION_TIME;
import static com.github.robtimus.filesystems.attribute.FileAttributeConstants.LAST_ACCESS_TIME;
import static com.github.robtimus.filesystems.attribute.FileAttributeConstants.LAST_MODIFIED_TIME;
import static com.github.robtimus.filesystems.attribute.FileAttributeSupport.getAttributeName;
import static com.github.robtimus.filesystems.attribute.FileAttributeSupport.getAttributeNames;
import static com.github.robtimus.filesystems.attribute.FileAttributeSupport.getViewName;
import static com.github.robtimus.filesystems.attribute.FileAttributeSupport.populateAttributeMap;
import static com.github.robtimus.filesystems.memory.MemoryFileAttributeView.HIDDEN;
import static com.github.robtimus.filesystems.memory.MemoryFileAttributeView.MEMORY_VIEW;
import static com.github.robtimus.filesystems.memory.MemoryFileAttributeView.READ_ONLY;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import com.github.robtimus.filesystems.AbstractDirectoryStream;
import com.github.robtimus.filesystems.Messages;
import com.github.robtimus.filesystems.attribute.FileAttributeSupport;
import com.github.robtimus.filesystems.attribute.FileAttributeViewMetadata;

/**
 * The in-memory file system store.
 *
 * @author Rob Spoor
 */
// not final for test purposes
class MemoryFileStore extends FileStore {

    // TODO: remove these two and their usages as part of the next major release
    @SuppressWarnings("nls")
    private static final String PREFIX_ATTRIBUTES_PROPERTY = MemoryFileStore.class.getPackage().getName() + ".prefixAttributes";
    private static final boolean PREFIX_ATTRIBUTES = Boolean.getBoolean(PREFIX_ATTRIBUTES_PROPERTY);

    static final MemoryFileStore INSTANCE = new MemoryFileStore();

    final Directory rootNode;

    // package private for test purposes
    MemoryFileStore() {
        rootNode = new Directory();
    }

    @Override
    public String name() {
        return ROOT_PATH;
    }

    @Override
    public String type() {
        return "memory"; //$NON-NLS-1$
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public synchronized long getTotalSpace() throws IOException {
        return Runtime.getRuntime().maxMemory();
    }

    @Override
    public long getUsableSpace() throws IOException {
        return Runtime.getRuntime().freeMemory();
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        return Runtime.getRuntime().freeMemory();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return type == BasicFileAttributeView.class || type == MemoryFileAttributeView.class;
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return BASIC_VIEW.equals(name) || MEMORY_VIEW.equals(name);
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        Objects.requireNonNull(type);
        return null;
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        if ("totalSpace".equals(attribute)) { //$NON-NLS-1$
            return getTotalSpace();
        }
        if ("usableSpace".equals(attribute)) { //$NON-NLS-1$
            return getUsableSpace();
        }
        if ("unallocatedSpace".equals(attribute)) { //$NON-NLS-1$
            return getUnallocatedSpace();
        }
        throw Messages.fileStore().unsupportedAttribute(attribute);
    }

    private MemoryPath normalize(MemoryPath path) {
        return path.toAbsolutePath().normalize();
    }

    private void assertIsAbsolute(MemoryPath path) {
        assert path.isAbsolute() : "path must be absolute"; //$NON-NLS-1$
    }

    private Directory findParentNode(MemoryPath path) throws FileSystemException {
        assertIsAbsolute(path);

        int nameCount = path.getNameCount();
        if (nameCount == 0) {
            // root path has no parent node
            return null;
        }
        Directory parent = rootNode;
        for (int i = 0; i < nameCount - 1; i++) {
            Node node = parent.get(path.nameAt(i));
            if (node instanceof Link) {
                MemoryPath resolvedPath = resolveLink((Link) node, path.subpath(0, i + 1));
                Directory resolvedParent = findParentNode(resolvedPath);
                node = findNode(resolvedParent, resolvedPath);
            }
            if (!(node instanceof Directory)) {
                return null;
            }
            parent = (Directory) node;
        }
        return parent;
    }

    private Directory getExistingParentNode(MemoryPath path) throws FileSystemException {
        Directory parent = findParentNode(path);
        if (parent == null) {
            throw new NoSuchFileException(path.parentPath());
        }
        return parent;
    }

    private Node findNode(Directory parent, MemoryPath path) {
        assertIsAbsolute(path);

        if (parent == null) {
            return path.getNameCount() == 0 ? rootNode : null;
        }
        return parent.get(path.fileName());
    }

    private Node findExistingNode(MemoryPath path) throws FileSystemException {
        assertIsAbsolute(path);

        if (path.getNameCount() == 0) {
            return rootNode;
        }

        Directory parent = findParentNode(path);
        return parent != null ? parent.get(path.fileName()) : null;
    }

    private Node getExistingNode(MemoryPath path) throws FileSystemException {
        Node node = findExistingNode(path);
        if (node == null) {
            throw new NoSuchFileException(path.path());
        }
        return node;
    }

    private Node getExistingNode(MemoryPath path, boolean followLinks) throws FileSystemException {
        Node node = getExistingNode(path);
        if (followLinks && node instanceof Link) {
            MemoryPath resolvedPath = resolveLink((Link) node, path);
            node = getExistingNode(resolvedPath);
        }
        return node;
    }

    private File validateIsNotDirectory(Node node, MemoryPath normalizedPath) throws FileSystemException {
        if (node instanceof Directory) {
            throw Messages.fileSystemProvider().isDirectory(normalizedPath.path());
        }
        return (File) node;
    }

    private MemoryPath resolveLink(Link link, MemoryPath path) throws FileSystemException {
        MemoryPath currentPath = path;
        Link currentLink = link;
        int depth = 0;
        while (depth < Link.MAX_DEPTH) {
            MemoryPath targetPath = normalize(currentPath.resolveSibling(currentLink.target));
            Directory parent = findParentNode(targetPath);
            Node node = findNode(parent, targetPath);
            if (!(node instanceof Link)) {
                return targetPath;
            }
            currentPath = targetPath;
            currentLink = (Link) node;
            depth++;
        }
        throw new FileSystemException(path.path(), null, MemoryMessages.maximumLinkDepthExceeded());
    }

    private Link resolveLastLink(Link link, MemoryPath path) throws FileSystemException {
        MemoryPath currentPath = path;
        Link currentLink = link;
        int depth = 0;
        while (depth < Link.MAX_DEPTH) {
            MemoryPath targetPath = normalize(currentPath.resolveSibling(currentLink.target));
            Directory parent = findParentNode(targetPath);
            Node node = findNode(parent, targetPath);
            if (!(node instanceof Link)) {
                // currentLink is the last working link, return it
                return currentLink;
            }
            currentPath = targetPath;
            currentLink = (Link) node;
            depth++;
        }
        throw new FileSystemException(path.path(), null, MemoryMessages.maximumLinkDepthExceeded());
    }

    MemoryPath toRealPath(MemoryPath path, boolean followLinks) throws IOException {
        MemoryPath currentPath = normalize(path);
        Node node = getExistingNode(currentPath);
        if (followLinks && node instanceof Link) {
            Link currentLink = (Link) node;
            int depth = 0;
            while (depth < Link.MAX_DEPTH) {
                MemoryPath targetPath = normalize(currentPath.resolveSibling(currentLink.target));
                node = getExistingNode(targetPath);
                if (!(node instanceof Link)) {
                    return targetPath;
                }
                currentPath = targetPath;
                currentLink = (Link) node;
                depth++;
            }
            throw new FileSystemException(path.path(), null, MemoryMessages.maximumLinkDepthExceeded());
        }
        return currentPath;
    }

    synchronized byte[] getContent(MemoryPath path) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        Node node = getExistingNode(normalizedPath, true);
        File file = validateIsNotDirectory(node, normalizedPath);
        return file.getContent();
    }

    synchronized Optional<byte[]> getContentIfExists(MemoryPath path) throws FileSystemException {
        MemoryPath normalizedPath = normalize(path);
        MemoryPath resolvedPath = normalizedPath;
        Node node = findExistingNode(resolvedPath);
        while (node instanceof Link) {
            resolvedPath = resolveLink((Link) node, resolvedPath);
            node = findExistingNode(resolvedPath);
        }
        File file = validateIsNotDirectory(node, resolvedPath);
        return Optional.ofNullable(file)
                .map(File::getContent);
    }

    synchronized void setContent(MemoryPath path, byte[] content) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        Directory parent = getExistingParentNode(normalizedPath);
        Node node = findNode(parent, normalizedPath);
        MemoryPath fileToSave = normalizedPath;
        if (node instanceof Link) {
            MemoryPath resolvedPath = resolveLink((Link) node, normalizedPath);
            parent = getExistingParentNode(resolvedPath);
            node = findNode(parent, resolvedPath);
            fileToSave = resolvedPath;
        }
        File file = validateIsNotDirectory(node, normalizedPath);

        if (file == null) {
            validateTarget(parent, fileToSave, normalizedPath, true);

            file = new File();
            parent.add(fileToSave.fileName(), file);
        }
        if (file.isReadOnly()) {
            throw new AccessDeniedException(normalizedPath.path());
        }
        file.setContent(content);
    }

    synchronized InputStream newInputStream(MemoryPath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewInputStream(options);
        assert openOptions.read;

        MemoryPath normalizedPath = normalize(path);
        Node node = getExistingNode(normalizedPath, true);
        File file = validateIsNotDirectory(node, normalizedPath);
        OnCloseAction onClose = openOptions.deleteOnClose ? () -> delete(normalizedPath) : null;

        return file.newInputStream(onClose);
    }

    synchronized OutputStream newOutputStream(MemoryPath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewOutputStream(options);
        assert openOptions.write;

        MemoryPath normalizedPath = normalize(path);
        Directory parent = getExistingParentNode(normalizedPath);
        Node node = findNode(parent, normalizedPath);
        MemoryPath fileToSave = normalizedPath;
        if (node instanceof Link) {
            MemoryPath resolvedPath = resolveLink((Link) node, normalizedPath);
            parent = getExistingParentNode(resolvedPath);
            node = findNode(parent, resolvedPath);
            fileToSave = resolvedPath;
        }
        File file = validateIsNotDirectory(node, normalizedPath);
        OnCloseAction onClose = openOptions.deleteOnClose ? () -> delete(normalizedPath) : null;

        if (file == null) {
            if (!openOptions.create && !openOptions.createNew) {
                throw new NoSuchFileException(normalizedPath.path());
            }

            validateTarget(parent, fileToSave, normalizedPath, openOptions.create && !openOptions.createNew);

            file = new File();
            parent.add(fileToSave.fileName(), file);

        } else if (openOptions.createNew) {
            throw new FileAlreadyExistsException(normalizedPath.path());
        }
        if (file.isReadOnly()) {
            throw new AccessDeniedException(normalizedPath.path());
        }
        return file.newOutputStream(openOptions.append, onClose);
    }

    synchronized FileChannel newFileChannel(MemoryPath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewFileChannel(options);

        MemoryPath normalizedPath = normalize(path);
        Directory parent = getExistingParentNode(normalizedPath);
        Node node = findNode(parent, normalizedPath);
        MemoryPath fileToSave = normalizedPath;
        if (node instanceof Link) {
            MemoryPath resolvedPath = resolveLink((Link) node, normalizedPath);
            parent = getExistingParentNode(resolvedPath);
            node = findNode(parent, resolvedPath);
            fileToSave = resolvedPath;
        }
        File file = validateIsNotDirectory(node, normalizedPath);
        OnCloseAction onClose = openOptions.deleteOnClose ? () -> delete(normalizedPath) : null;

        if (openOptions.read && !openOptions.write) {
            // read-only mode; append is not allowed, and truncateExisting, createNew and create should be ignored
            if (file == null) {
                throw new NoSuchFileException(normalizedPath.path());
            }

            return file.newFileChannel(true, false, false, onClose);
        }

        // either write-only mode, or read-write mode
        if (file == null) {
            if (!openOptions.create && !openOptions.createNew) {
                throw new NoSuchFileException(normalizedPath.path());
            }

            validateTarget(parent, fileToSave, normalizedPath, openOptions.create && !openOptions.createNew);

            file = new File();

            // creating the file channel will update some of the attributes; therefore, set the attributes afterwards

            FileChannel channel = file.newFileChannel(openOptions.read, openOptions.write, openOptions.append, onClose);

            setAttributes(file, channel, attrs);

            parent.add(fileToSave.fileName(), file);

            if (file.isReadOnly()) {
                throw new AccessDeniedException(normalizedPath.path());
            }

            return channel;
        }

        if (openOptions.createNew) {
            throw new FileAlreadyExistsException(normalizedPath.path());
        }
        if (file.isReadOnly()) {
            throw new AccessDeniedException(normalizedPath.path());
        }
        return file.newFileChannel(openOptions.read, openOptions.write, openOptions.append, onClose);
    }

    private void setAttributes(Node node, FileAttribute<?>... attrs) {
        for (FileAttribute<?> attribute : attrs) {
            try {
                setAttribute(node, attribute.name(), attribute.value());
            } catch (IllegalArgumentException e) {
                throw new UnsupportedOperationException(e.getMessage());
            }
        }
    }

    private void setAttributes(File file, FileChannel channel, FileAttribute<?>... attrs) throws IOException {
        try {
            setAttributes(file, attrs);
        } catch (final Exception e) {
            channel.close();
            throw e;
        }
    }

    synchronized SeekableByteChannel newByteChannel(MemoryPath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {

        return newFileChannel(path, options, attrs);
    }

    synchronized DirectoryStream<Path> newDirectoryStream(MemoryPath path, Filter<? super Path> filter) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        // only retrieve the node to check if it's an existing directory
        Node node = getExistingNode(normalizedPath, true);
        if (!(node instanceof Directory)) {
            throw new NotDirectoryException(normalizedPath.path());
        }
        Objects.requireNonNull(filter);
        return new MemoryPathDirectoryStream(normalizedPath, filter);
    }

    private final class MemoryPathDirectoryStream extends AbstractDirectoryStream<Path> {

        private final MemoryPath path;
        private Iterator<String> names;

        private MemoryPathDirectoryStream(MemoryPath path, DirectoryStream.Filter<? super Path> filter) {
            super(filter);
            this.path = Objects.requireNonNull(path);
        }

        @Override
        protected void setupIteration() {
            // at this point node should be a Directory, but it could have changed between creating this stream and the iteration
            Node node = null;
            try {
                Directory parent = findParentNode(path);
                node = findNode(parent, path);
                if (node instanceof Link) {
                    MemoryPath resolvedPath = resolveLink((Link) node, path);
                    parent = findParentNode(resolvedPath);
                    node = findNode(parent, resolvedPath);
                }
            } catch (@SuppressWarnings("unused") FileSystemException e) {
                // linking became too deep, ignore
            }
            if (node instanceof Directory) {
                Directory directory = (Directory) node;
                names = new ArrayList<>(directory.children.keySet()).iterator();
            } else {
                names = Collections.emptyIterator();
            }
        }

        @Override
        protected Path getNext() {
            if (names.hasNext()) {
                String name = names.next();
                return path.resolve(name);
            }
            return null;
        }
    }

    synchronized void createDirectory(MemoryPath dir, FileAttribute<?>... attrs) throws IOException {
        MemoryPath normalizedDir = normalize(dir);
        if (normalizedDir.getNameCount() == 0) {
            throw new FileAlreadyExistsException(normalizedDir.path());
        }

        Directory parent = getExistingParentNode(normalizedDir);
        validateTarget(parent, normalizedDir, false);

        Directory directory = new Directory();

        setAttributes(directory, attrs);

        parent.add(normalizedDir.fileName(), directory);
    }

    synchronized void createSymbolicLink(MemoryPath link, MemoryPath target, FileAttribute<?>... attrs) throws IOException {
        MemoryPath normalizedLink = normalize(link);
        // don't normalize target, it will be used as-is as the target of the link

        Directory parent = getExistingParentNode(normalizedLink);
        validateTarget(parent, normalizedLink, false);

        Node node = new Link(target.path());

        setAttributes(node, attrs);

        parent.add(normalizedLink.fileName(), node);
    }

    synchronized void createLink(MemoryPath link, MemoryPath existing) throws IOException {
        MemoryPath normalizedLink = normalize(link);
        MemoryPath normalizedExisting = normalize(existing);

        Directory parent = getExistingParentNode(normalizedLink);
        validateTarget(parent, normalizedLink, false);

        Node node = getExistingNode(normalizedExisting);
        // don't follow symbolic links; creating a hard link of a symbolic link copies the symbolic link
        if (node instanceof Directory) {
            throw Messages.fileSystemProvider().isDirectory(normalizedExisting.path());
        }
        parent.add(normalizedLink.fileName(), node);
    }

    synchronized void delete(MemoryPath path) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        // don't follow symbolic links; the symbolic link itself must be deleted
        Node node = getExistingNode(normalizedPath);
        deleteNode(node, normalizedPath);
    }

    synchronized boolean deleteIfExists(MemoryPath path) throws IOException {
        MemoryPath normalizedPath = normalize(path);

        Directory parent = findParentNode(normalizedPath);
        Node node = findNode(parent, normalizedPath);
        // don't follow symbolic links; the symbolic link itself must be deleted
        if (node != null) {
            deleteNode(node, normalizedPath);
            return true;
        }
        return false;
    }

    private void deleteNode(Node node, MemoryPath path) throws IOException {
        if (isNonEmptyDirectory(node)) {
            throw new DirectoryNotEmptyException(path.path());
        }
        if (node == rootNode) {
            throw new AccessDeniedException(path.path());
        }
        if (node.parent.isReadOnly()) {
            throw new AccessDeniedException(path.parentPath());
        }
        node.parent.remove(path.fileName());
    }

    @SuppressWarnings("resource")
    synchronized MemoryPath readSymbolicLink(MemoryPath link) throws IOException {
        MemoryPath normalizedLink = normalize(link);

        Node node = getExistingNode(normalizedLink);
        if (!(node instanceof Link)) {
            throw new NotLinkException(normalizedLink.path());
        }
        return new MemoryPath(link.getFileSystem(), ((Link) node).target);
    }

    synchronized void copy(MemoryPath source, MemoryPath target, CopyOption... options) throws IOException {
        CopyOptions copyOptions = CopyOptions.forCopy(options);

        MemoryPath normalizedSource = normalize(source);
        MemoryPath normalizedTarget = normalize(target);

        Node sourceNode = getExistingNode(normalizedSource, copyOptions.followLinks);

        Directory targetDirectory = findParentNode(normalizedTarget);
        Node targetNode = findNode(targetDirectory, normalizedTarget);
        // don't follow symbolic links for the target

        if (sourceNode == targetNode) {
            // non-op, don't do a thing
            return;
        }

        if (sourceNode instanceof Link) {
            // copyOptions.followLinks is false
            sourceNode = resolveLastLink((Link) sourceNode, normalizedSource);
        }

        validateTarget(targetDirectory, normalizedTarget, copyOptions.replaceExisting);

        targetNode = sourceNode.copy(copyOptions.copyAttributes);

        targetDirectory.add(normalizedTarget.fileName(), targetNode);
    }

    synchronized void move(MemoryPath source, MemoryPath target, CopyOption... options) throws IOException {
        CopyOptions copyOptions = CopyOptions.forMove(options);

        MemoryPath normalizedSource = normalize(source);
        MemoryPath normalizedTarget = normalize(target);

        Node sourceNode = getExistingNode(normalizedSource);
        // the link itself, and not the target, must be moved

        Directory targetDirectory = findParentNode(normalizedTarget);
        Node targetNode = findNode(targetDirectory, normalizedTarget);
        // don't follow symbolic links for the target

        if (sourceNode == targetNode) {
            // non-op, don't do a thing
            return;
        }

        if (sourceNode == rootNode) {
            // cannot move or rename the root
            throw new DirectoryNotEmptyException(normalizedSource.path());
        }

        Directory sourceDirectory = sourceNode.parent;

        if (sourceDirectory.isReadOnly()) {
            throw new AccessDeniedException(normalizedSource.parentPath());
        }
        validateTarget(targetDirectory, normalizedTarget, copyOptions.replaceExisting);

        sourceDirectory.remove(normalizedSource.fileName());
        targetDirectory.add(normalizedTarget.fileName(), sourceNode);
    }

    private void validateTarget(Directory targetDirectory, MemoryPath target, boolean replaceExisting) throws IOException {
        validateTarget(targetDirectory, target, target, replaceExisting);
    }

    private void validateTarget(Directory targetDirectory, MemoryPath target, MemoryPath originalTarget, boolean replaceExisting) throws IOException {
        if (targetDirectory == null) {
            throw new NoSuchFileException(originalTarget.parentPath());
        }
        if (targetDirectory.isReadOnly()) {
            throw new AccessDeniedException(originalTarget.parentPath());
        }

        Node targetNode = targetDirectory.get(target.fileName());
        if (targetNode != null && !replaceExisting) {
            throw new FileAlreadyExistsException(originalTarget.path());
        }
        if (isNonEmptyDirectory(targetNode)) {
            throw new DirectoryNotEmptyException(originalTarget.path());
        }
    }

    private boolean isNonEmptyDirectory(Node node) {
        return node instanceof Directory && !((Directory) node).isEmpty();
    }

    synchronized boolean isSameFile(MemoryPath path, MemoryPath path2) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        MemoryPath normalizedPath2 = normalize(path2);
        if (normalizedPath.equals(normalizedPath2)) {
            return true;
        }

        return getExistingNode(normalizedPath, true) == getExistingNode(normalizedPath2, true);
    }

    synchronized boolean isHidden(MemoryPath path) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        Node node = getExistingNode(normalizedPath);
        // don't follow symbolic links
        return node.isHidden();
    }

    synchronized FileStore getFileStore(MemoryPath path) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        getExistingNode(normalizedPath);
        // don't follow symbolic links
        return this;
    }

    synchronized void checkAccess(MemoryPath path, AccessMode... modes) throws IOException {
        boolean w = false;
        boolean x = false;
        for (AccessMode mode : modes) {
            if (mode == AccessMode.WRITE) {
                w = true;
            } else if (mode == AccessMode.EXECUTE) {
                x = true;
            }
        }

        MemoryPath normalizedPath = normalize(path);
        // symbolic links should be followed
        Node node = getExistingNode(normalizedPath, true);
        boolean isReadOnly = node.isReadOnly();
        if (w && isReadOnly) {
            throw new AccessDeniedException(normalizedPath.path());
        }
        if (x && !node.isDirectory()) {
            throw new AccessDeniedException(normalizedPath.path());
        }
    }

    <V extends FileAttributeView> V getFileAttributeView(MemoryPath path, Class<V> type, boolean followLinks) {
        Objects.requireNonNull(type);
        if (type == BasicFileAttributeView.class) {
            return type.cast(new MemoryAttributeView(BASIC_VIEW, path, followLinks));
        }
        if (type == MemoryFileAttributeView.class) {
            return type.cast(new MemoryAttributeView(MEMORY_VIEW, path, followLinks));
        }
        return null;
    }

    private final class MemoryAttributeView implements MemoryFileAttributeView {

        private final String name;
        private final MemoryPath path;
        private final boolean followLinks;

        private MemoryAttributeView(String name, MemoryPath path, boolean followLinks) {
            this.name = Objects.requireNonNull(name);
            this.path = Objects.requireNonNull(path);
            this.followLinks = followLinks;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
            MemoryFileStore.this.setTimes(path, lastModifiedTime, lastAccessTime, createTime, followLinks);
        }

        @Override
        public MemoryFileAttributes readAttributes() throws IOException {
            return MemoryFileStore.this.readAttributes(path, followLinks);
        }

        @Override
        public void setReadOnly(boolean value) throws IOException {
            MemoryFileStore.this.setReadOnly(path, value, followLinks);
        }

        @Override
        public void setHidden(boolean value) throws IOException {
            MemoryFileStore.this.setHidden(path, value, followLinks);
        }
    }

    synchronized void setTimes(MemoryPath path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime, boolean followLinks)
            throws IOException {

        MemoryPath normalizedPath = normalize(path);
        Node node = getExistingNode(normalizedPath, followLinks);

        if (lastModifiedTime != null) {
            node.setLastModifiedTime(lastModifiedTime);
        }
        if (lastAccessTime != null) {
            node.setLastAccessTime(lastAccessTime);
        }
        if (createTime != null) {
            node.setCreationTime(createTime);
        }
    }

    synchronized void setReadOnly(MemoryPath path, boolean value, boolean followLinks) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        Node node = getExistingNode(normalizedPath, followLinks);
        node.setReadOnly(value);
    }

    synchronized void setHidden(MemoryPath path, boolean value, boolean followLinks) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        Node node = getExistingNode(normalizedPath, followLinks);
        node.setHidden(value);
    }

    synchronized MemoryFileAttributes readAttributes(MemoryPath path, boolean followLinks) throws IOException {
        MemoryPath normalizedPath = normalize(path);
        Node node = getExistingNode(normalizedPath, followLinks);
        return node.getAttributes();
    }

    synchronized Map<String, Object> readAttributes(MemoryPath path, String attributes, boolean followLinks) throws IOException {
        String viewName = getViewName(attributes);
        FileAttributeViewMetadata metadata = getMetadata(viewName);
        Set<String> attributeNames = getAttributeNames(attributes, metadata);

        MemoryFileAttributes fileAttributes = readAttributes(path, followLinks);

        Map<String, Object> result = new HashMap<>();
        populateAttributeMap(result, fileAttributes, attributeNames);
        populateAttributeMap(result, READ_ONLY, attributeNames, fileAttributes::isReadOnly);
        populateAttributeMap(result, HIDDEN, attributeNames, fileAttributes::isHidden);
        return prefixAttributesIfNeeded(result, metadata);
    }

    private FileAttributeViewMetadata getMetadata(String viewName) {
        switch (viewName) {
            case BASIC_VIEW:
                return FileAttributeViewMetadata.BASIC;
            case MEMORY_VIEW:
                return MemoryFileAttributeView.METADATA;
            default:
                throw Messages.fileSystemProvider().unsupportedFileAttributeView(viewName);
        }
    }

    private Map<String, Object> prefixAttributesIfNeeded(Map<String, Object> attributes, FileAttributeViewMetadata metadata) {
        return PREFIX_ATTRIBUTES
                ? prefixAttributes(attributes, metadata)
                : attributes;
    }

    static Map<String, Object> prefixAttributes(Map<String, Object> attributes, FileAttributeViewMetadata metadata) {
        String prefix = metadata.viewName() + ":"; //$NON-NLS-1$
        return attributes.entrySet().stream()
                .collect(Collectors.toMap(e -> prefix + e.getKey(), Map.Entry::getValue));
    }

    synchronized void setAttribute(MemoryPath path, String attribute, Object value, boolean followLinks) throws IOException {
        String viewName = getViewName(attribute);
        String attributeName = getAttributeName(attribute);

        switch (viewName) {
            case BASIC_VIEW:
                setBasicAttribute(path, attributeName, value, followLinks);
                break;
            case MEMORY_VIEW:
                setMemoryAttributes(path, attributeName, value, followLinks);
                break;
            default:
                throw Messages.fileSystemProvider().unsupportedFileAttributeView(viewName);
        }
    }

    private void setBasicAttribute(MemoryPath path, String attribute, Object value, boolean followLinks) throws IOException {
        BasicFileAttributeView view = getFileAttributeView(path, BasicFileAttributeView.class, followLinks);
        FileAttributeSupport.setAttribute(attribute, value, view);
    }

    private void setMemoryAttributes(MemoryPath path, String attribute, Object value, boolean followLinks) throws IOException {
        MemoryFileAttributeView view = getFileAttributeView(path, MemoryFileAttributeView.class, followLinks);
        switch (attribute) {
            case READ_ONLY:
                view.setReadOnly((boolean) value);
                break;
            case HIDDEN:
                view.setHidden((boolean) value);
                break;
            default:
                FileAttributeSupport.setAttribute(attribute, value, view);
                break;
        }
    }

    private void setAttribute(Node node, String attribute, Object value) {
        final String colon = ":"; //$NON-NLS-1$
        switch (attribute) {
            case LAST_MODIFIED_TIME:
            case BASIC_VIEW + colon + LAST_MODIFIED_TIME:
            case MEMORY_VIEW + colon + LAST_MODIFIED_TIME:
                node.setLastModifiedTime((FileTime) value);
                break;
            case LAST_ACCESS_TIME:
            case BASIC_VIEW + colon + LAST_ACCESS_TIME:
            case MEMORY_VIEW + colon + LAST_ACCESS_TIME:
                node.setLastAccessTime((FileTime) value);
                break;
            case CREATION_TIME:
            case BASIC_VIEW + colon + CREATION_TIME:
            case MEMORY_VIEW + colon + CREATION_TIME:
                node.setCreationTime((FileTime) value);
                break;
            case MEMORY_VIEW + colon + READ_ONLY:
                node.setReadOnly((Boolean) value);
                break;
            case MEMORY_VIEW + colon + HIDDEN:
                node.setHidden((Boolean) value);
                break;
            default:
                throw Messages.fileSystemProvider().unsupportedFileAttribute(attribute);
        }
    }

    void clear() {
        rootNode.clear();
    }

    abstract static class Node {
        private Directory parent;

        private long lastModifiedTimestamp;
        private long lastAccessTimestamp;
        private long creationTimestamp;

        private FileTime lastModifiedFileTime;
        private FileTime lastAccessFileTime;
        private FileTime creationFileTime;

        private boolean readOnly;
        private boolean hidden;

        private MemoryFileAttributes attributes;

        Node() {
            final long now = System.currentTimeMillis();
            creationTimestamp = now;
            lastModifiedTimestamp = now;
            lastAccessTimestamp = now;
        }

        abstract boolean isRegularFile();

        abstract boolean isDirectory();

        abstract boolean isSymbolicLink();

        abstract long getSize();

        abstract Node copy(boolean copyAttributes);

        synchronized void copyAttributes(Node target) {
            target.lastModifiedTimestamp = lastModifiedTimestamp;
            target.lastAccessTimestamp = lastAccessTimestamp;
            target.creationTimestamp = creationTimestamp;

            target.lastModifiedFileTime = lastModifiedFileTime;
            target.lastAccessFileTime = lastAccessFileTime;
            target.creationFileTime = creationFileTime;

            target.readOnly = readOnly;
            target.hidden = hidden;
        }

        synchronized void updateLastAccessTime() {
            final long now = System.currentTimeMillis();
            setLastAccessTime(now);
        }

        synchronized void updateLastModifiedAndAccessTimes() {
            final long now = System.currentTimeMillis();
            setLastModifiedTime(now);
            setLastAccessTime(now);
        }

        synchronized FileTime getLastModifiedTime() {
            if (lastModifiedFileTime == null) {
                lastModifiedFileTime = FileTime.fromMillis(lastModifiedTimestamp);
            }
            return lastModifiedFileTime;
        }

        synchronized void setLastModifiedTime(long lastModifiedTimestamp) {
            this.lastModifiedTimestamp = lastModifiedTimestamp;
            lastModifiedFileTime = null;
        }

        synchronized void setLastModifiedTime(FileTime lastModifiedFileTime) {
            lastModifiedTimestamp = lastModifiedFileTime.toMillis();
            this.lastModifiedFileTime = lastModifiedFileTime;
        }

        synchronized FileTime getLastAccessTime() {
            if (lastAccessFileTime == null) {
                lastAccessFileTime = FileTime.fromMillis(lastAccessTimestamp);
            }
            return lastAccessFileTime;
        }

        synchronized void setLastAccessTime(long lastAccessTimestamp) {
            this.lastAccessTimestamp = lastAccessTimestamp;
            lastAccessFileTime = null;
        }

        synchronized void setLastAccessTime(FileTime lastAccessFileTime) {
            lastAccessTimestamp = lastAccessFileTime.toMillis();
            this.lastAccessFileTime = lastAccessFileTime;
        }

        synchronized FileTime getCreationTime() {
            if (creationFileTime == null) {
                creationFileTime = FileTime.fromMillis(creationTimestamp);
            }
            return creationFileTime;
        }

        synchronized void setCreationTime(long creationTimestamp) {
            this.creationTimestamp = creationTimestamp;
            creationFileTime = null;
        }

        synchronized void setCreationTime(FileTime creationFileTime) {
            creationTimestamp = creationFileTime.toMillis();
            this.creationFileTime = creationFileTime;
        }

        synchronized boolean isReadOnly() {
            return readOnly;
        }

        synchronized void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        synchronized boolean isHidden() {
            return hidden;
        }

        synchronized void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        synchronized MemoryFileAttributes getAttributes() {
            if (attributes == null) {
                attributes = new FileAttributes();
            }
            return attributes;
        }

        synchronized void resetAttributes() {
            setReadOnly(false);
            setHidden(false);
        }

        private final class FileAttributes implements MemoryFileAttributes {

            @Override
            public FileTime lastModifiedTime() {
                return Node.this.getLastModifiedTime();
            }

            @Override
            public FileTime lastAccessTime() {
                return Node.this.getLastAccessTime();
            }

            @Override
            public FileTime creationTime() {
                return Node.this.getCreationTime();
            }

            @Override
            public boolean isRegularFile() {
                return Node.this.isRegularFile();
            }

            @Override
            public boolean isDirectory() {
                return Node.this.isDirectory();
            }

            @Override
            public boolean isSymbolicLink() {
                return Node.this.isSymbolicLink();
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public long size() {
                return Node.this.getSize();
            }

            @Override
            public Object fileKey() {
                return null;
            }

            @Override
            public boolean isReadOnly() {
                return Node.this.isReadOnly();
            }

            @Override
            public boolean isHidden() {
                return Node.this.isHidden();
            }
        }
    }

    static final class Directory extends Node {
        private final Map<String, Node> children = new TreeMap<>();

        @Override
        boolean isRegularFile() {
            return false;
        }

        @Override
        boolean isDirectory() {
            return true;
        }

        @Override
        boolean isSymbolicLink() {
            return false;
        }

        @Override
        long getSize() {
            return 0;
        }

        @Override
        synchronized Node copy(boolean copyAttributes) {
            Directory copy = new Directory();
            // children aren't copied, as specified by the contract of Files.copy
            if (copyAttributes) {
                copyAttributes(copy);
            }
            return copy;
        }

        synchronized boolean isEmpty() {
            return children.isEmpty();
        }

        synchronized Node get(String name) {
            Node node = children.get(name);
            if (node != null) {
                updateLastAccessTime();
            }
            return node;
        }

        synchronized Node add(String name, Node node) {
            Node oldNode = children.put(name, node);
            if (oldNode != null) {
                oldNode.parent = null;
            }
            node.parent = this;

            updateLastModifiedAndAccessTimes();

            return node;
        }

        synchronized Node remove(String name) {
            Node node = children.remove(name);
            if (node != null) {
                node.parent = null;

                updateLastModifiedAndAccessTimes();
            }
            return node;
        }

        synchronized void clear() {
            resetAttributes();

            if (!children.isEmpty()) {
                children.clear();

                updateLastModifiedAndAccessTimes();
            }
        }
    }

    static final class File extends Node {
        private final List<Byte> content = new ArrayList<>();
        private LockTable lockTable;

        @Override
        boolean isRegularFile() {
            return true;
        }

        @Override
        boolean isDirectory() {
            return false;
        }

        @Override
        boolean isSymbolicLink() {
            return false;
        }

        @Override
        synchronized long getSize() {
            return content.size();
        }

        @Override
        synchronized Node copy(boolean copyAttributes) {
            File copy = new File();
            copy.content.addAll(content);
            if (copyAttributes) {
                copyAttributes(copy);
            }
            return copy;
        }

        synchronized InputStream newInputStream(OnCloseAction onClose) {
            updateLastAccessTime();
            return new ContentInputStream(this, onClose);
        }

        synchronized OutputStream newOutputStream(boolean append, OnCloseAction onClose) {
            if (!append) {
                content.clear();
            }
            updateLastModifiedAndAccessTimes();
            return new ContentOutputStream(this, onClose);
        }

        synchronized FileChannel newFileChannel(boolean readable, boolean writable, boolean append, OnCloseAction onClose) {
            // assert that append => writable
            assert !append || writable : "append is only allowed if writable is true"; //$NON-NLS-1$
            ContentFileChannel channel = new ContentFileChannel(this, readable, writable, onClose);
            if (append) {
                synchronized (channel) {
                    channel.position = content.size();
                }
                updateLastModifiedAndAccessTimes();
            } else if (writable) {
                content.clear();
                updateLastModifiedAndAccessTimes();
            } else {
                updateLastAccessTime();
            }
            return channel;
        }

        synchronized LockTable lockTable() {
            if (lockTable == null) {
                lockTable = new LockTable();
            }
            return lockTable;
        }

        // for test purposes
        synchronized byte[] getContent() {
            byte[] result = new byte[content.size()];
            int i = 0;
            for (Byte b : content) {
                result[i++] = b;
            }
            updateLastAccessTime();
            return result;
        }

        // for test purposes
        synchronized void setContent(byte... newContent) {
            content.clear();
            for (byte b : newContent) {
                content.add(b);
            }
            updateLastModifiedAndAccessTimes();
        }

        private static final class ContentInputStream extends InputStream {

            private final File file;
            private final OnCloseAction onClose;

            private int pos = 0;
            private int mark = 0;

            private boolean open = true;

            private ContentInputStream(File file, OnCloseAction onClose) {
                this.file = file;
                this.onClose = onClose;
            }

            @Override
            public synchronized void close() throws IOException {
                if (open) {
                    open = false;
                    if (onClose != null) {
                        onClose.run();
                    }
                }
            }

            @Override
            public int read() throws IOException {
                synchronized (file) {
                    int result = pos < file.content.size() ? file.content.get(pos++) & 0xFF : -1;
                    file.updateLastAccessTime();
                    return result;
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                Objects.requireNonNull(b);
                if (off < 0 || len < 0 || len > b.length - off) {
                    throw new IndexOutOfBoundsException();
                }

                synchronized (file) {
                    int size = file.content.size();
                    if (pos >= size) {
                        return -1;
                    }
                    int available = size - pos;
                    if (len > available) {
                        len = available;
                    }
                    if (len <= 0) {
                        return 0;
                    }
                    for (int i = 0; i < len; i++, pos++) {
                        b[off + i] = file.content.get(pos);
                    }
                    file.updateLastAccessTime();
                    return len;
                }
            }

            @Override
            public long skip(long n) throws IOException {
                synchronized (file) {
                    long k = file.content.size() - pos;
                    if (n < k) {
                        k = n < 0 ? 0 : n;
                    }
                    pos += k;
                    file.updateLastAccessTime();
                    return k;
                }
            }

            @Override
            public int available() throws IOException {
                synchronized (file) {
                    int result = Math.max(0, file.content.size() - pos);
                    file.updateLastAccessTime();
                    return result;
                }
            }

            @Override
            public synchronized void mark(int readlimit) {
                mark = pos;
            }

            @Override
            public synchronized void reset() throws IOException {
                pos = mark;
            }

            @Override
            public boolean markSupported() {
                return true;
            }
        }

        private static final class ContentOutputStream extends OutputStream {

            private final File file;
            private final OnCloseAction onClose;

            private boolean open = true;

            private ContentOutputStream(File file, OnCloseAction onClose) {
                this.file = file;
                this.onClose = onClose;
            }

            @Override
            public synchronized void close() throws IOException {
                if (open) {
                    open = false;
                    if (onClose != null) {
                        onClose.run();
                    }
                }
            }

            @Override
            public void write(int b) throws IOException {
                synchronized (file) {
                    file.content.add((byte) b);
                    file.updateLastModifiedAndAccessTimes();
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (off < 0 || off > b.length || len < 0 || off + len - b.length > 0) {
                    throw new IndexOutOfBoundsException();
                }
                synchronized (file) {
                    for (int i = 0; i < len; i++) {
                        file.content.add(b[off + i]);
                    }
                    file.updateLastModifiedAndAccessTimes();
                }
            }
        }

        private static final class ContentFileChannel extends FileChannel {

            private final File file;
            private final boolean readable;
            private final boolean writeable;
            private final OnCloseAction onClose;

            private int position = 0;

            private ContentFileChannel(File file, boolean readable, boolean writable, OnCloseAction onClose) {
                this.file = file;
                this.readable = readable;
                this.writeable = writable;
                this.onClose = onClose;
            }

            private void checkReadable() {
                if (!readable) {
                    throw new NonReadableChannelException();
                }
            }

            private void checkWritable() {
                if (!writeable) {
                    throw new NonWritableChannelException();
                }
            }

            private void checkOpen() throws ClosedChannelException {
                if (!isOpen()) {
                    throw new ClosedChannelException();
                }
            }

            @Override
            protected void implCloseChannel() throws IOException {
                file.lockTable().invalidateAndRemoveAll(this);
                if (onClose != null) {
                    onClose.run();
                }
            }

            @Override
            public synchronized int read(ByteBuffer dst) throws IOException {
                int read = read(dst, position);
                if (read > 0) {
                    position += read;
                }
                return read;
            }

            @Override
            public synchronized int read(ByteBuffer dst, long pos) throws IOException {
                checkOpen();
                checkReadable();

                synchronized (file) {
                    int size = file.content.size();
                    if (pos >= size) {
                        return -1;
                    }
                    int available = size - (int) pos;
                    int len = dst.remaining();
                    if (len > available) {
                        len = available;
                    }
                    if (len <= 0) {
                        return 0;
                    }
                    int totalRead = 0;
                    byte[] buffer = new byte[8192];
                    while (totalRead < len) {
                        int bytesToRead = Math.min(len - totalRead, buffer.length);
                        for (int i = 0; i < bytesToRead && pos <= Integer.MAX_VALUE; i++, pos++) {
                            buffer[i] = file.content.get((int) pos);
                        }
                        dst.put(buffer, 0, bytesToRead);
                        totalRead += bytesToRead;
                    }
                    file.updateLastAccessTime();
                    return totalRead;
                }
            }

            @Override
            public synchronized long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
                int totalRead = 0;
                for (int i = 0; i < length; i++) {
                    int read = read(dsts[offset + i]);
                    if (read == -1) {
                        return totalRead == 0 ? -1 : totalRead;
                    }
                    totalRead += read;
                }
                return totalRead;
            }

            @Override
            public synchronized int write(ByteBuffer src) throws IOException {
                int written = write(src, position);
                if (written > 0) {
                    position += written;
                }
                return written;
            }

            @Override
            public synchronized int write(ByteBuffer src, long pos) throws IOException {
                checkOpen();
                checkWritable();

                synchronized (file) {
                    if (pos > Integer.MAX_VALUE) {
                        return 0;
                    }

                    while (file.content.size() < pos) {
                        file.content.add((byte) 0);
                    }

                    int len = src.remaining();
                    int totalWritten = 0;

                    if (len == 0) {
                        return totalWritten;
                    }
                    byte[] buffer = new byte[8192];
                    while (totalWritten < len) {
                        int bytesToWrite = Math.min(len - totalWritten, buffer.length);
                        src.get(buffer, 0, bytesToWrite);
                        for (int i = 0; i < bytesToWrite && pos <= Integer.MAX_VALUE; i++, pos++) {
                            if (pos < file.content.size()) {
                                file.content.set((int) pos, buffer[i]);
                            } else {
                                assert pos == file.content.size();
                                file.content.add(buffer[i]);
                            }
                        }
                        totalWritten += bytesToWrite;
                    }
                    file.updateLastModifiedAndAccessTimes();
                    return totalWritten;
                }
            }

            @Override
            public synchronized long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
                int totalWritten = 0;
                for (int i = 0; i < length; i++) {
                    int written = write(srcs[offset + i]);
                    totalWritten += written;
                }
                return totalWritten;
            }

            @Override
            public synchronized long position() throws IOException {
                checkOpen();
                return position;
            }

            @Override
            public synchronized FileChannel position(long newPosition) throws IOException {
                if (newPosition < 0) {
                    throw Messages.byteChannel().negativePosition(newPosition);
                }
                checkOpen();
                position = (int) Math.min(newPosition, Integer.MAX_VALUE);
                synchronized (file) {
                    file.updateLastAccessTime();
                }
                return this;
            }

            @Override
            public synchronized long size() throws IOException {
                checkOpen();
                synchronized (file) {
                    return file.content.size();
                }
            }

            @Override
            public synchronized FileChannel truncate(long size) throws IOException {
                checkOpen();
                checkWritable();
                if (size < 0) {
                    throw Messages.byteChannel().negativeSize(size);
                }
                synchronized (file) {
                    int oldSize = file.content.size();
                    if (size < oldSize) {
                        file.content.subList((int) size, oldSize).clear();
                    }
                    file.updateLastModifiedAndAccessTimes();
                }
                return this;
            }

            @Override
            public void force(boolean metaData) {
                // no need to do anything
            }

            @Override
            public synchronized long transferTo(long pos, long count, WritableByteChannel target) throws IOException {
                if (pos < 0) {
                    throw Messages.fileChannel().negativePosition(pos);
                }
                if (count < 0) {
                    throw Messages.fileChannel().negativeCount(count);
                }

                checkOpen();
                checkReadable();

                if (count == 0) {
                    return 0;
                }

                synchronized (file) {
                    if (pos > file.content.size()) {
                        // nothing to transfer
                        return 0;
                    }
                    if (target instanceof ContentFileChannel) {
                        return transferTo((int) pos, count, (ContentFileChannel) target);
                    }
                    return transferToGeneric((int) pos, count, target);
                }
            }

            private long transferTo(int pos, long count, ContentFileChannel target) throws IOException {
                // note: potential deadlock if transferring both ways
                synchronized (target.file) {
                    target.checkOpen();
                    target.checkWritable();

                    int toTransfer = (int) Math.min(count, file.content.size() - pos);
                    toTransfer = Math.min(toTransfer, Integer.MAX_VALUE - target.file.content.size());
                    // toTransfer is the min of what we want to transfer (count), what the source has and what the destination allows
                    if (toTransfer == 0) {
                        // nothing to transfer, or the destination is full
                        return 0;
                    }

                    target.file.content.addAll(file.content.subList(pos, pos + toTransfer));
                    target.position += toTransfer;
                    file.updateLastAccessTime();
                    target.file.updateLastModifiedAndAccessTimes();

                    return toTransfer;
                }
            }

            private long transferToGeneric(int pos, long count, WritableByteChannel target) throws IOException {
                ByteBuffer buffer = ByteBuffer.allocate(8192);

                int toTransfer = (int) Math.min(count, file.content.size() - pos);
                int remaining = toTransfer;
                int transferred = 0;
                while (remaining > 0) {
                    int bytesToRead = Math.min(buffer.capacity(), remaining);
                    for (int i = 0; i < bytesToRead; i++, pos++) {
                        buffer.put(file.content.get(pos));
                    }
                    buffer.flip();
                    target.write(buffer);
                    buffer.clear();
                    transferred += bytesToRead;
                    remaining -= bytesToRead;
                }
                file.updateLastAccessTime();
                return transferred;
            }

            @Override
            public synchronized long transferFrom(ReadableByteChannel src, long pos, long count) throws IOException {
                if (pos < 0) {
                    throw Messages.fileChannel().negativePosition(pos);
                }
                if (count < 0) {
                    throw Messages.fileChannel().negativeCount(count);
                }

                checkOpen();
                checkWritable();

                if (count == 0) {
                    return 0;
                }

                synchronized (file) {
                    if (pos > file.content.size()) {
                        // nothing to transfer
                        return 0;
                    }
                    if (src instanceof ContentFileChannel) {
                        return transferFrom((ContentFileChannel) src, (int) pos, count);
                    }
                    return transferFromGeneric(src, (int) pos, count);
                }
            }

            private long transferFrom(ContentFileChannel src, int pos, long count) throws IOException {
                // note: potential deadlock if transferring both ways
                synchronized (src.file) {
                    src.checkOpen();
                    src.checkReadable();

                    int toTransfer = (int) Math.min(count, src.file.content.size() - src.position);
                    toTransfer = Math.min(toTransfer, Integer.MAX_VALUE - pos);
                    // toTransfer is the min of what we want to transfer (count), what the source has and what the destination allows
                    if (toTransfer == 0) {
                        // nothing to transfer, or the destination is full
                        return 0;
                    }

                    int toOverwrite = Math.min(toTransfer, file.content.size() - pos);
                    int toAdd = toTransfer - toOverwrite;

                    for (int i = 0; i < toOverwrite; i++) {
                        file.content.set(pos + i, src.file.content.get(src.position++));
                    }
                    if (toAdd > 0) {
                        file.content.addAll(src.file.content.subList(src.position, src.position + toAdd));
                        src.position += toAdd;
                    }
                    file.updateLastModifiedAndAccessTimes();
                    src.file.updateLastAccessTime();

                    return toTransfer;
                }
            }

            private long transferFromGeneric(ReadableByteChannel src, int pos, long count) throws IOException {
                ByteBuffer buffer = ByteBuffer.allocate(8192);

                int toTransfer = (int) Math.min(count, Integer.MAX_VALUE - pos);
                int remaining = toTransfer;
                int transferred = 0;
                while (remaining > 0) {
                    int bytesToRead = Math.min(buffer.capacity(), remaining);
                    buffer.limit(bytesToRead);
                    int bytesRead = src.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }
                    buffer.flip();
                    for (int i = 0; i < bytesRead; i++, pos++) {
                        if (pos < file.content.size()) {
                            file.content.set(pos, buffer.get(i));
                        } else {
                            assert pos == file.content.size();
                            file.content.add(buffer.get(i));
                        }
                    }
                    buffer.clear();
                    transferred += bytesRead;
                    remaining -= bytesRead;
                }
                file.updateLastModifiedAndAccessTimes();
                return transferred;
            }

            @Override
            public MappedByteBuffer map(MapMode mode, long position, long size) {
                throw Messages.unsupportedOperation(FileChannel.class, "map"); //$NON-NLS-1$
            }

            @Override
            public synchronized FileLock lock(long position, long size, boolean shared) throws IOException {
                checkOpen();
                if (shared && !readable) {
                    throw new NonReadableChannelException();
                }
                if (!shared && !writeable) {
                    throw new NonWritableChannelException();
                }

                LockTable lockTable = file.lockTable();
                Lock lock = new Lock(this, position, size, lockTable);
                lockTable.add(lock);
                return lock;
            }

            @Override
            public FileLock tryLock(long position, long size, boolean shared) throws IOException {
                // in-memory files cannot be accessed externally, and therefore locking will never block, so lock and tryLock actually do the same
                return lock(position, size, shared);
            }
        }

        private static final class Lock extends FileLock {

            private final LockTable lockTable;
            private boolean isValid = true;

            private Lock(ContentFileChannel channel, long position, long size, LockTable lockTable) {
                super(channel, position, size, false);
                this.lockTable = lockTable;
            }

            @Override
            public synchronized boolean isValid() {
                return isValid;
            }

            private synchronized void invalidate() {
                isValid = false;
            }

            @Override
            @SuppressWarnings("resource")
            public synchronized void release() throws IOException {
                Channel channel = acquiredBy();
                if (!channel.isOpen()) {
                    throw new ClosedChannelException();
                }
                if (isValid) {
                    lockTable.remove(this);
                    isValid = false;
                }
            }
        }

        private static final class LockTable {

            private final List<Lock> locks = new ArrayList<>();

            private void checkLocks(long position, long size) {
                for (Lock lock : locks) {
                    if (lock.overlaps(position, size)) {
                        throw new OverlappingFileLockException();
                    }
                }
            }

            private void add(Lock lock) {
                synchronized (locks) {
                    checkLocks(lock.position(), lock.size());
                    locks.add(lock);
                }
            }

            private void remove(Lock lock) {
                synchronized (locks) {
                    locks.remove(lock);
                }
            }

            @SuppressWarnings("resource")
            private void invalidateAndRemoveAll(FileChannel channel) {
                synchronized (locks) {
                    for (Iterator<Lock> i = locks.iterator(); i.hasNext(); ) {
                        Lock lock = i.next();
                        if (lock.channel() == channel) {
                            lock.invalidate();
                            i.remove();
                        }
                    }
                }
            }
        }
    }

    static final class Link extends Node {
        private static final int MAX_DEPTH = 100;

        final String target;

        Link(String target) {
            this.target = Objects.requireNonNull(target);
        }

        @Override
        boolean isRegularFile() {
            return false;
        }

        @Override
        boolean isDirectory() {
            return false;
        }

        @Override
        boolean isSymbolicLink() {
            return true;
        }

        @Override
        long getSize() {
            return 0;
        }

        @Override
        Node copy(boolean copyAttributes) {
            Link copy = new Link(target);
            if (copyAttributes) {
                copyAttributes(copy);
            }
            return copy;
        }
    }

    interface OnCloseAction {

        void run() throws IOException;
    }
}
