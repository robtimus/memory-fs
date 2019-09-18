# memory-fs

The `memory-fs` library provides an in-memory NIO.2 file system. It can be used where a NIO.2 file system implementation is required, without needing to write anything to disk.

## Creating paths

If the in-memory file system library is available on the class path, it will register a [FileSystemProvider](https://docs.oracle.com/javase/8/docs/api/java/nio/file/spi/FileSystemProvider.html) for scheme `memory`. This means that, to create a [Path](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Path.html) for the in-memory file system, you can simply use [Paths.get](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Paths.html#get-java.net.URI-):

    Path path = Paths.get(URI.create("memory:/foo/bar"));

## Attributes

### File attributes

The in-memory file system fully supports the attributes defined in [BasicFileAttributeView](https://docs.oracle.com/javase/8/docs/api/java/nio/file/attribute/BasicFileAttributeView.html) and [BasicFileAttributes](https://docs.oracle.com/javase/8/docs/api/java/nio/file/attribute/BasicFileAttributes.html). These are available both with and without prefix `basic:`.

Besides these attributes, the in-memory file system provides two extra attributes:

* `readOnly`: if a file or directory is read only, its contents cannot be modified. For a directory this means that the list of child files and directories cannot be modified. The child files and directories themselves can still be modified if they are not read only.
* `hidden`: files and directories can be hidden, allowing the hidden attribute to be used for filtering.

These attributes are accessible through interfaces [MemoryFileAttributeView](https://robtimus.github.io/memory-fs/apidocs/com/github/robtimus/filesystems/memory/MemoryFileAttributeView.html) and [MemoryFileAttributes](https://robtimus.github.io/memory-fs/apidocs/com/github/robtimus/filesystems/memory/MemoryFileAttributes.html) (which extend [BasicFileAttributeView](https://docs.oracle.com/javase/8/docs/api/java/nio/file/attribute/BasicFileAttributeView.html) and [BasicFileAttributes](https://docs.oracle.com/javase/8/docs/api/java/nio/file/attribute/BasicFileAttributes.html) respectively), or with prefix `memory:` (e.g. `memory:hidden`). The basic attributes are also available using prefix `memory:`.

### File store attributes

When calling [getAttribute](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileStore.html#getAttribute-java.lang.String-) on a file store, the following attributes are supported:

* `totalSpace`: returns the same value as the [getTotalSpace](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileStore.html#getTotalSpace--) method, based on the contents of the in-memory file system.
* `usableSpace`: returns the same value as the [getUsableSpace](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileStore.html#getUsableSpace--) method, which is based on the maximum amount of memory available to the JVM.
* `unallocatedSpace`: returns the same value as the [getUnallocatedSpace](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileStore.html#getUnallocatedSpace--) method, which is based on the amount of free memory in the JVM.

There is no support for [FileStoreAttributeView](https://docs.oracle.com/javase/8/docs/api/java/nio/file/attribute/FileStoreAttributeView.html). Calling [getFileStoreAttributeView](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileStore.html#getFileStoreAttributeView-java.lang.Class-) on a file store will simply return `null`.

## Thread safety

Most operations are atomic and thread safe. Concurrent access to files using streams or channels may give unexpected results though. If concurrent access to files is necessary, it is suggested to use proper synchronisation.

## Limitations

The in-memory file system knows the following limitations:

* All paths use `/` as separator. `/` is not allowed inside file or directory names.
* Symbolic links can only be nested up to 100 times.
* Files are never executable. Calling [checkAccess](https://docs.oracle.com/javase/8/docs/api/java/nio/file/spi/FileSystemProvider.html#checkAccess-java.nio.file.Path-java.nio.file.AccessMode...-) on a file with [AccessMode.EXECUTE](https://docs.oracle.com/javase/8/docs/api/java/nio/file/AccessMode.html#EXECUTE) will cause an [AccessDeniedException](https://docs.oracle.com/javase/8/docs/api/java/nio/file/AccessDeniedException.html) to be thrown.
* There is only one file system. Any attempt to create a new one will fail.
* There is no support for [UserPrincipalLookupService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/attribute/UserPrincipalLookupService.html).
* There is no support for [WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html).
