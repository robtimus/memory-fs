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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import com.github.robtimus.filesystems.AbstractDirectoryStream;
import com.github.robtimus.filesystems.Messages;

/**
 * The in-memory file system store.
 *
 * @author Rob Spoor
 */
// not final for test purposes
class MemoryFileStore extends FileStore {

    static final MemoryFileStore INSTANCE = new MemoryFileStore();

    final Directory rootNode;

    // package private for test purposes
    MemoryFileStore() {
        rootNode = new Directory();
    }

    @Override
    public String name() {
        return "/"; //$NON-NLS-1$
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
        return "basic".equals(name) || "memory".equals(name); //$NON-NLS-1$ //$NON-NLS-2$
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

    private Directory findParentNode(MemoryPath path) {
        path = path.toAbsolutePath().normalize();

        int nameCount = path.getNameCount();
        if (nameCount == 0) {
            // root path has no parent node
            return null;
        }
        Directory parent = rootNode;
        for (int i = 0; i < nameCount - 1; i++) {
            Node node = parent.get(path.nameAt(i));
            if (!(node instanceof Directory)) {
                return null;
            }
            parent = (Directory) node;
        }
        return parent;
    }

    private Directory getExistingParentNode(MemoryPath path) throws NoSuchFileException {
        Directory parent = findParentNode(path);
        if (parent == null) {
            throw new NoSuchFileException(path.parentPath());
        }
        return parent;
    }

    private Node findNode(MemoryPath path) {
        path = path.toAbsolutePath().normalize();

        if (path.getNameCount() == 0) {
            return rootNode;
        }
        Directory parent = findParentNode(path);
        return parent == null ? null : parent.get(path.fileName());
    }

    private Node getExistingNode(MemoryPath path) throws NoSuchFileException {
        Node node = findNode(path);
        if (node == null) {
            throw new NoSuchFileException(path.path());
        }
        return node;
    }

    MemoryPath toRealPath(MemoryPath path, @SuppressWarnings("unused") LinkOption... options) throws IOException {
        // no links are supported
        MemoryPath normalized = path.toAbsolutePath().normalize();
        getExistingNode(normalized);
        return normalized;
    }

    synchronized InputStream newInputStream(MemoryPath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewInputStream(options);
        assert openOptions.read;

        Node node = getExistingNode(path);
        if (node instanceof Directory) {
            throw Messages.fileSystemProvider().isDirectory(path.path());
        }
        File file = (File) node;
        OnCloseAction onClose = openOptions.deleteOnClose ? new DeletePathAction(path) : null;

        return file.newInputStream(onClose);
    }

    synchronized OutputStream newOutputStream(MemoryPath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewOutputStream(options);
        assert openOptions.write;

        Node node = findNode(path);
        if (node instanceof Directory) {
            throw Messages.fileSystemProvider().isDirectory(path.path());
        }
        File file = (File) node;
        OnCloseAction onClose = openOptions.deleteOnClose ? new DeletePathAction(path) : null;

        if (file == null) {
            if (!openOptions.create && !openOptions.createNew) {
                throw new NoSuchFileException(path.path());
            }

            Directory parent = getExistingParentNode(path);
            validateTarget(parent, path, openOptions.create && !openOptions.createNew);

            file = new File();
            parent.add(path.fileName(), file);

        } else if (openOptions.createNew) {
            throw new FileAlreadyExistsException(path.path());
        }
        if (file.isReadOnly()) {
            throw new AccessDeniedException(path.path());
        }
        return file.newOutputStream(openOptions.append, onClose);
    }

    synchronized SeekableByteChannel newByteChannel(MemoryPath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {

        OpenOptions openOptions = OpenOptions.forNewByteChannel(options);

        if (openOptions.read) {
            Node node = getExistingNode(path);
            if (node instanceof Directory) {
                throw Messages.fileSystemProvider().isDirectory(path.path());
            }
            File file = (File) node;
            OnCloseAction onClose = openOptions.deleteOnClose ? new DeletePathAction(path) : null;
            return file.newByteChannel(false, false, onClose);
        }

        Node node = findNode(path);
        if (node instanceof Directory) {
            throw Messages.fileSystemProvider().isDirectory(path.path());
        }
        File file = (File) node;
        OnCloseAction onClose = openOptions.deleteOnClose ? new DeletePathAction(path) : null;

        if (file == null) {
            if (!openOptions.create && !openOptions.createNew) {
                throw new NoSuchFileException(path.path());
            }

            Directory parent = getExistingParentNode(path);
            validateTarget(parent, path, openOptions.create && !openOptions.createNew);

            file = new File();

            // creating the byte channel will update some of the attributes; therefore, set the attributes afterwards

            @SuppressWarnings("resource")
            SeekableByteChannel channel = file.newByteChannel(true, openOptions.append, onClose);

            for (FileAttribute<?> attribute : attrs) {
                try {
                    setAttribute(file, attribute.name(), attribute.value());
                } catch (IllegalArgumentException e) {
                    channel.close();
                    throw new UnsupportedOperationException(e.getMessage());
                }
            }

            parent.add(path.fileName(), file);

            if (file.isReadOnly()) {
                throw new AccessDeniedException(path.path());
            }

            return channel;
        }
        if (openOptions.createNew) {
            throw new FileAlreadyExistsException(path.path());
        }
        if (file.isReadOnly()) {
            throw new AccessDeniedException(path.path());
        }
        return file.newByteChannel(true, openOptions.append, onClose);
    }

    synchronized DirectoryStream<Path> newDirectoryStream(MemoryPath path, Filter<? super Path> filter) throws IOException {
        // only retrieve the node to check if it's an existing directory
        Node node = getExistingNode(path);
        if (!(node instanceof Directory)) {
            throw new NotDirectoryException(path.path());
        }
        Objects.requireNonNull(filter);
        return new MemoryPathDirectoryStream(path, filter);
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
            Node node = findNode(path);
            if (node instanceof Directory) {
                Directory directory = (Directory) node;
                names = new ArrayList<>(directory.children.keySet()).iterator();
            } else {
                names = Collections.emptyIterator();
            }
        }

        @Override
        protected Path getNext() throws IOException {
            if (names.hasNext()) {
                String name = names.next();
                return path.resolve(name);
            }
            return null;
        }
    }

    synchronized void createDirectory(MemoryPath dir, FileAttribute<?>... attrs) throws IOException {
        if (dir.getNameCount() == 0) {
            throw new FileAlreadyExistsException(dir.path());
        }

        Directory parent = getExistingParentNode(dir);
        validateTarget(parent, dir, false);

        Directory directory = new Directory();

        for (FileAttribute<?> attribute : attrs) {
            try {
                setAttribute(directory, attribute.name(), attribute.value());
            } catch (IllegalArgumentException e) {
                throw new UnsupportedOperationException(e.getMessage());
            }
        }

        parent.add(dir.fileName(), directory);
    }

    synchronized void delete(MemoryPath path) throws IOException {
        Node node = getExistingNode(path);

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

    synchronized void copy(MemoryPath source, MemoryPath target, CopyOption... options) throws IOException {
        CopyOptions copyOptions = CopyOptions.forCopy(options);

        Node sourceNode = getExistingNode(source);

        if (source.toAbsolutePath().path().equals(target.toAbsolutePath().path())) {
            // non-op, don't do a thing
            return;
        }

        Directory targetDirectory = findParentNode(target);

        validateTarget(targetDirectory, target, copyOptions.replaceExisting);

        Node targetNode = sourceNode.copy(copyOptions.copyAttributes);

        targetDirectory.add(target.fileName(), targetNode);
    }

    synchronized void move(MemoryPath source, MemoryPath target, CopyOption... options) throws IOException {
        CopyOptions copyOptions = CopyOptions.forMove(options);

        Node sourceNode = getExistingNode(source);

        if (sourceNode == findNode(target)) {
            // non-op, don't do a thing
            return;
        }

        if (sourceNode == rootNode) {
            // cannot move or rename the root
            throw new DirectoryNotEmptyException(source.path());
        }

        Directory sourceDirectory = sourceNode.parent;
        Directory targetDirectory = findParentNode(target);

        if (sourceDirectory.isReadOnly()) {
            throw new AccessDeniedException(source.parentPath());
        }
        validateTarget(targetDirectory, target, copyOptions.replaceExisting);

        sourceDirectory.remove(source.fileName());
        targetDirectory.add(target.fileName(), sourceNode);
    }

    private void validateTarget(Directory targetDirectory, MemoryPath target, boolean replaceExisting) throws IOException {
        if (targetDirectory == null) {
            throw new NoSuchFileException(target.parentPath());
        }
        if (targetDirectory.isReadOnly()) {
            throw new AccessDeniedException(target.parentPath());
        }

        Node targetNode = targetDirectory.get(target.fileName());
        if (targetNode != null && !replaceExisting) {
            throw new FileAlreadyExistsException(target.path());
        }
        if (isNonEmptyDirectory(targetNode)) {
            throw new DirectoryNotEmptyException(target.path());
        }
    }

    private boolean isNonEmptyDirectory(Node node) {
        return node instanceof Directory && !((Directory) node).isEmpty();
    }

    synchronized boolean isSameFile(MemoryPath path, MemoryPath path2) throws IOException {
        if (path.equals(path2)) {
            return true;
        }
        return getExistingNode(path) == getExistingNode(path2);
    }

    synchronized boolean isHidden(MemoryPath path) throws IOException {
        return getExistingNode(path).isHidden();
    }

    synchronized FileStore getFileStore(MemoryPath path) throws IOException {
        getExistingNode(path);
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

        Node node = getExistingNode(path);
        boolean isReadOnly = node.isReadOnly();
        if (w && isReadOnly) {
            throw new AccessDeniedException(path.path());
        }
        if (x && !node.isDirectory()) {
            throw new AccessDeniedException(path.path());
        }
    }

    synchronized void setTimes(MemoryPath path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        Node node = getExistingNode(path);

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

    synchronized void setReadOnly(MemoryPath path, boolean value) throws IOException {
        getExistingNode(path).setReadOnly(value);
    }

    synchronized void setHidden(MemoryPath path, boolean value) throws IOException {
        getExistingNode(path).setHidden(value);
    }

    synchronized MemoryFileAttributes readAttributes(MemoryPath path) throws IOException {
        return getExistingNode(path).getAttributes();
    }

    @SuppressWarnings("nls")
    private static final Set<String> BASIC_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "basic:lastModifiedTime", "basic:lastAccessTime", "basic:creationTime", "basic:size",
            "basic:isRegularFile", "basic:isDirectory", "basic:isSymbolicLink", "basic:isOther", "basic:fileKey")));

    @SuppressWarnings("nls")
    private static final Set<String> MEMORY_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "memory:lastModifiedTime", "memory:lastAccessTime", "memory:creationTime", "memory:size",
            "memory:isRegularFile", "memory:isDirectory", "memory:isSymbolicLink", "memory:isOther", "memory:fileKey",
            "memory:readOnly", "memory:hidden")));

    synchronized Map<String, Object> readAttributes(MemoryPath path, String attributes, @SuppressWarnings("unused") LinkOption... options)
            throws IOException {
        // no links are supported

        String view;
        int pos = attributes.indexOf(':');
        if (pos == -1) {
            view = "basic"; //$NON-NLS-1$
            attributes = "basic:" + attributes; //$NON-NLS-1$
        } else {
            view = attributes.substring(0, pos);
        }
        if (!"basic".equals(view) && !"memory".equals(view)) { //$NON-NLS-1$ //$NON-NLS-2$
            throw Messages.fileSystemProvider().unsupportedFileAttributeView(view);
        }

        Set<String> allowedAttributes;
        if (attributes.startsWith("basic:")) { //$NON-NLS-1$
            allowedAttributes = BASIC_ATTRIBUTES;
        } else if (attributes.startsWith("memory:")) { //$NON-NLS-1$
            allowedAttributes = MEMORY_ATTRIBUTES;
        } else {
            // should not occur
            throw Messages.fileSystemProvider().unsupportedFileAttributeView(attributes.substring(0, attributes.indexOf(':')));
        }

        Map<String, Object> result = getAttributeMap(attributes, allowedAttributes);

        Node node = getExistingNode(path);

        for (Map.Entry<String, Object> entry : result.entrySet()) {
            switch (entry.getKey()) {
            case "basic:lastModifiedTime": //$NON-NLS-1$
            case "memory:lastModifiedTime": //$NON-NLS-1$
                entry.setValue(node.getLastModifiedTime());
                break;
            case "basic:lastAccessTime": //$NON-NLS-1$
            case "memory:lastAccessTime": //$NON-NLS-1$
                entry.setValue(node.getLastAccessTime());
                break;
            case "basic:creationTime": //$NON-NLS-1$
            case "memory:creationTime": //$NON-NLS-1$
                entry.setValue(node.getCreationTime());
                break;
            case "basic:size": //$NON-NLS-1$
            case "memory:size": //$NON-NLS-1$
                entry.setValue(node.getSize());
                break;
            case "basic:isRegularFile": //$NON-NLS-1$
            case "memory:isRegularFile": //$NON-NLS-1$
                entry.setValue(node.isRegularFile());
                break;
            case "basic:isDirectory": //$NON-NLS-1$
            case "memory:isDirectory": //$NON-NLS-1$
                entry.setValue(node.isDirectory());
                break;
            case "basic:isSymbolicLink": //$NON-NLS-1$
            case "memory:isSymbolicLink": //$NON-NLS-1$
                entry.setValue(false);
                break;
            case "basic:isOther": //$NON-NLS-1$
            case "memory:isOther": //$NON-NLS-1$
                entry.setValue(false);
                break;
            case "basic:fileKey": //$NON-NLS-1$
            case "memory:fileKey": //$NON-NLS-1$
                entry.setValue(null);
                break;
            case "memory:readOnly": //$NON-NLS-1$
                entry.setValue(node.isReadOnly());
                break;
            case "memory:hidden": //$NON-NLS-1$
                entry.setValue(node.isHidden());
                break;
            default:
                // should not occur
                throw new IllegalStateException("unexpected attribute name: " + entry.getKey()); //$NON-NLS-1$
            }
        }
        return result;
    }

    private Map<String, Object> getAttributeMap(String attributes, Set<String> allowedAttributes) {
        int indexOfColon = attributes.indexOf(':');
        String prefix = attributes.substring(0, indexOfColon + 1);
        attributes = attributes.substring(indexOfColon + 1);

        String[] attributeList = attributes.split(","); //$NON-NLS-1$
        Map<String, Object> result = new HashMap<>(allowedAttributes.size());

        for (String attribute : attributeList) {
            String prefixedAttribute = prefix + attribute;
            if (allowedAttributes.contains(prefixedAttribute)) {
                result.put(prefixedAttribute, null);
            } else if ("*".equals(attribute)) { //$NON-NLS-1$
                for (String s : allowedAttributes) {
                    result.put(s, null);
                }
            } else {
                throw Messages.fileSystemProvider().unsupportedFileAttribute(attribute);
            }
        }
        return result;
    }

    synchronized void setAttribute(MemoryPath path, String attribute, Object value, @SuppressWarnings("unused") LinkOption... options)
            throws IOException {
        // no links are supported

        String view;
        int pos = attribute.indexOf(':');
        if (pos == -1) {
            view = "basic"; //$NON-NLS-1$
            attribute = "basic:" + attribute; //$NON-NLS-1$
        } else {
            view = attribute.substring(0, pos);
        }
        if (!"basic".equals(view) && !"memory".equals(view)) { //$NON-NLS-1$ //$NON-NLS-2$
            throw Messages.fileSystemProvider().unsupportedFileAttributeView(view);
        }

        Node node = getExistingNode(path);
        setAttribute(node, attribute, value);
    }

    private void setAttribute(Node node, String attribute, Object value) {
        switch (attribute) {
        case "basic:lastModifiedTime": //$NON-NLS-1$
        case "memory:lastModifiedTime": //$NON-NLS-1$
            node.setLastModifiedTime((FileTime) value);
            break;
        case "basic:lastAccessTime": //$NON-NLS-1$
        case "memory:lastAccessTime": //$NON-NLS-1$
            node.setLastAccessTime((FileTime) value);
            break;
        case "basic:creationTime": //$NON-NLS-1$
        case "memory:creationTime": //$NON-NLS-1$
            node.setCreationTime((FileTime) value);
            break;
        case "memory:readOnly": //$NON-NLS-1$
            node.setReadOnly((Boolean) value);
            break;
        case "memory:hidden": //$NON-NLS-1$
            node.setHidden((Boolean) value);
            break;
        default:
            throw Messages.fileSystemProvider().unsupportedFileAttribute(attribute);
        }
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
                return false;
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
    }

    static final class File extends Node {
        private final List<Byte> content = new ArrayList<>();

        @Override
        boolean isRegularFile() {
            return true;
        }

        @Override
        boolean isDirectory() {
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
            return new ContentInputStream(onClose);
        }

        synchronized OutputStream newOutputStream(boolean append, OnCloseAction onClose) {
            if (!append) {
                content.clear();
            }
            updateLastModifiedAndAccessTimes();
            return new ContentOutputStream(onClose);
        }

        synchronized SeekableByteChannel newByteChannel(boolean writable, boolean append, OnCloseAction onClose) {
            // assert that append => writable
            assert !append || writable : "append is only allowed if writable is true"; //$NON-NLS-1$
            ContentByteChannel channel = new ContentByteChannel(writable, onClose);
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

        private final class ContentInputStream extends InputStream {

            private final OnCloseAction onClose;

            private int pos = 0;
            private int mark = 0;

            private boolean open = true;

            private ContentInputStream(OnCloseAction onClose) {
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
                synchronized (File.this) {
                    int result = pos < content.size() ? content.get(pos++) & 0xFF : -1;
                    updateLastAccessTime();
                    return result;
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                Objects.requireNonNull(b);
                if (off < 0 || len < 0 || len > b.length - off) {
                    throw new IndexOutOfBoundsException();
                }

                synchronized (File.this) {
                    int size = content.size();
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
                        b[off + i] = content.get(pos);
                    }
                    updateLastAccessTime();
                    return len;
                }
            }

            @Override
            public long skip(long n) throws IOException {
                synchronized (File.this) {
                    long k = content.size() - pos;
                    if (n < k) {
                        k = n < 0 ? 0 : n;
                    }
                    pos += k;
                    updateLastAccessTime();
                    return k;
                }
            }

            @Override
            public int available() throws IOException {
                synchronized (File.this) {
                    int result = Math.max(0, content.size() - pos);
                    updateLastAccessTime();
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

        private final class ContentOutputStream extends OutputStream {

            private final OnCloseAction onClose;

            private boolean open = true;

            private ContentOutputStream(OnCloseAction onClose) {
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
                synchronized (File.this) {
                    content.add((byte) b);
                    updateLastModifiedAndAccessTimes();
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (off < 0 || off > b.length || len < 0 || off + len - b.length > 0) {
                    throw new IndexOutOfBoundsException();
                }
                synchronized (File.this) {
                    for (int i = 0; i < len; i++) {
                        content.add(b[off + i]);
                    }
                    updateLastModifiedAndAccessTimes();
                }
            }
        }

        private final class ContentByteChannel implements SeekableByteChannel {

            private boolean writeable;
            private final OnCloseAction onClose;

            private boolean open = true;
            private int position = 0;

            private ContentByteChannel(boolean writable, OnCloseAction onClose) {
                this.writeable = writable;
                this.onClose = onClose;
            }

            private void checkReadable() {
                if (writeable) {
                    throw new NonReadableChannelException();
                }
            }

            private void checkWritable() {
                if (!writeable) {
                    throw new NonWritableChannelException();
                }
            }

            private void checkOpen() throws ClosedChannelException {
                if (!open) {
                    throw new ClosedChannelException();
                }
            }

            @Override
            public synchronized boolean isOpen() {
                return open;
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
            public synchronized int read(ByteBuffer dst) throws IOException {
                checkOpen();
                checkReadable();

                synchronized (File.this) {
                    int size = content.size();
                    if (position >= size) {
                        return -1;
                    }
                    int available = size - position;
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
                        for (int i = 0; i < bytesToRead; i++, position++) {
                            buffer[i] = content.get(position);
                        }
                        dst.put(buffer, 0, bytesToRead);
                        totalRead += bytesToRead;
                    }
                    updateLastAccessTime();
                    return totalRead;
                }
            }

            @Override
            public synchronized int write(ByteBuffer src) throws IOException {
                checkOpen();
                checkWritable();

                synchronized (File.this) {
                    while (content.size() < position) {
                        content.add((byte) 0);
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
                        for (int i = 0; i < bytesToWrite; i++, position++) {
                            if (position < content.size()) {
                                content.set(position, buffer[i]);
                            } else {
                                content.add(position, buffer[i]);
                            }
                        }
                        totalWritten += bytesToWrite;
                    }
                    updateLastModifiedAndAccessTimes();
                    return totalWritten;
                }
            }

            @Override
            public synchronized long position() throws IOException {
                checkOpen();
                return position;
            }

            @Override
            public synchronized SeekableByteChannel position(long newPosition) throws IOException {
                if (newPosition < 0) {
                    throw Messages.byteChannel().negativePosition(newPosition);
                }
                checkOpen();
                position = (int) Math.min(newPosition, Integer.MAX_VALUE);
                synchronized (File.this) {
                    updateLastAccessTime();
                }
                return this;
            }

            @Override
            public synchronized long size() throws IOException {
                checkOpen();
                synchronized (File.this) {
                    return content.size();
                }
            }

            @Override
            public synchronized SeekableByteChannel truncate(long size) throws IOException {
                checkOpen();
                checkWritable();
                if (size < 0) {
                    throw Messages.byteChannel().negativeSize(size);
                }
                synchronized (File.this) {
                    int oldSize = content.size();
                    if (size < oldSize) {
                        content.subList((int) size, oldSize).clear();
                    }
                    updateLastModifiedAndAccessTimes();
                }
                return this;
            }
        }
    }

    interface OnCloseAction {

        void run() throws IOException;
    }

    private final class DeletePathAction implements OnCloseAction {

        private final MemoryPath path;

        private DeletePathAction(MemoryPath path) {
            this.path = path;
        }

        @Override
        public void run() throws IOException {
            delete(path);
        }
    }
}