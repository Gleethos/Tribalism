package app.models.bootstrap;

import java.io.File;
import java.nio.file.Files;

public class Util {

    /**
     * Reads a text file from the given path and returns its contents as a string.
     * This method does not care if the file is in the resource folder or in the working directory.
     *
     * @param path The path to the file.
     * @return The contents of the file as a string.
     */
    public static String readTextFile(String path) {
        // We need to check if it is a resource file or a file in the working directory.
        // Let's check if the file exists in the working directory:
        if (new File(path).exists())
            return readTextFileFromWorkingDirectory(path);
        // If the file does not exist in the working directory, we check if it exists in the resource folder.
        return readTextFileFromResourceFolder(path);
    }

    /**
     * Reads a text file from the given path and returns its contents as a string.
     * This method only reads files from the resource folder.
     *
     * @param path The path to the file.
     * @return The contents of the file as a string.
     */
    public static String readTextFileFromWorkingDirectory(String path) {
        try {
            return Files.readString(new File(path).toPath());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not read file at path: " + path);
        }
    }

    /**
     * Reads a text file from the given path and returns its contents as a string.
     * This method only reads files from the resource folder.
     *
     * @param path The path to the file.
     * @return The contents of the file as a string.
     */
    public static String readTextFileFromResourceFolder(String path) {
        try (var in = Util.class.getResourceAsStream(path)) {
            assert in != null;
            return new String(in.readAllBytes());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not load " + path);
        }
    }

}
