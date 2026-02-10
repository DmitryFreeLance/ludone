package ru.rollsroms.bot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MediaStore {
  private final Path tempDir;
  private final Map<String, String> fileIds = new ConcurrentHashMap<>();

  public MediaStore() {
    try {
      this.tempDir = Files.createTempDirectory("rollsroms-media");
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create temp dir for media", e);
    }
  }

  public Optional<String> getFileId(String resource) {
    return Optional.ofNullable(fileIds.get(resource));
  }

  public void cacheFileId(String resource, String fileId) {
    if (resource == null || fileId == null) {
      return;
    }
    fileIds.put(resource, fileId);
  }

  public File getFile(String resource) {
    try {
      Path target = tempDir.resolve(Path.of(resource).getFileName());
      if (!Files.exists(target)) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
          if (is == null) {
            throw new IllegalStateException("Missing resource: " + resource);
          }
          Files.copy(is, target);
        }
      }
      return target.toFile();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load resource: " + resource, e);
    }
  }
}
