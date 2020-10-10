module com.github.robtimus.filesystems.memory {
    requires com.github.robtimus.filesystems;

    exports com.github.robtimus.filesystems.memory;

    provides java.nio.file.spi.FileSystemProvider with com.github.robtimus.filesystems.memory.MemoryFileSystemProvider;
}
