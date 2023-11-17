package ctf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public interface CTFFile {

    public String getName();

    public byte[] getContent();

    public String getUrl();

    public default boolean download(String directory, String name) {
        try {
            Files.write(Paths.get(directory, name), getContent());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public default boolean download(String directory){
        return download(directory, getName());
    }
}
