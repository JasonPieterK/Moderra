package com.jason.plugins.moderra.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

/**
 * Upgrade help for servers that ran the project under its former name (AntiBadWords): on the first start, when the new
 * data folder does not exist yet and the old one does, the old folder is moved to the new place. A note file stays in the
 * old place. Legacy code: the old names are written here and nowhere else (except the legacy permission nodes in
 * {@code PermissionNodes}).
 *
 * <p>Old folder names: {@code plugins/AntiBadWords} (Bukkit family) for {@code plugins/Moderra}, and
 * {@code config/antibadwords} (Fabric, Forge) for {@code config/moderra}. The old name is derived from the new folder name.
 */
public final class FolderMigration {
    /** Name of the note file that stays in the old folder. */
    public static final String NOTE_FILE = "MOVED-TO-MODERRA.txt";

    private FolderMigration() {
    }

    /** The folder an older version used for the data folder {@code current}, or {@code null} when there is none to look for. */
    public static Path oldFolderOf(Path current) {
        Path name = current.getFileName();
        if (name == null) {
            return null;
        }
        String old = switch (name.toString()) {
            case "Moderra" -> "AntiBadWords";
            case "moderra" -> "antibadwords";
            default -> null;
        };
        return old == null ? null : current.resolveSibling(old);
    }

    /**
     * Moves the old data folder to {@code current} when {@code current} does not exist (or is empty) and the old folder does.
     *
     * @return {@code true} when a folder was moved
     */
    public static boolean migrate(Path current, Consumer<String> info, Consumer<String> warn) {
        Path old = oldFolderOf(current);
        if (old == null || !Files.isDirectory(old) || !isMissingOrEmpty(current)) {
            return false;
        }
        if (Files.exists(old.resolve(NOTE_FILE)) && isOnlyNote(old)) {
            return false; // already moved before; only the note is left
        }
        try {
            if (Files.isDirectory(current)) {
                Files.delete(current); // an empty folder that the server created
            }
            Path parent = current.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.move(old, current, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                copyTree(old, current);
                deleteTree(old);
            }
            Files.createDirectories(old);
            Files.writeString(old.resolve(NOTE_FILE),
                "The settings and data of this plugin were moved to the folder \"" + current.getFileName() + "\" (the plugin was renamed to Moderra).\n"
                    + "You can delete this folder.\n", StandardCharsets.UTF_8);
            info.accept("Moved the old data folder " + old.getFileName() + " to " + current.getFileName() + " (the plugin is now called Moderra).");
            return true;
        } catch (IOException | RuntimeException ex) {
            warn.accept("Could not move the old data folder " + old + " to " + current + ": " + ex
                + ". Move it by hand (stop the server first).");
            return false;
        }
    }

    private static boolean isMissingOrEmpty(Path folder) {
        if (Files.notExists(folder)) {
            return true;
        }
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            return !entries.iterator().hasNext();
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isOnlyNote(Path folder) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                if (!entry.getFileName().toString().equals(NOTE_FILE)) {
                    return false;
                }
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path folder) throws IOException {
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
