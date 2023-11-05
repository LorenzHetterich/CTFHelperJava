package ctf.solver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ctf.CTFChallenge;
import ctf.CTFFile;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FlagRegexSolver implements CTFChallengeSolver {

    protected final Pattern pattern;

    public FlagRegexSolver(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    private List<String> findMatches(String s){
        List<String> result = new ArrayList<>();
        Matcher m = pattern.matcher(s);
        while(m.find()){
            result.add(m.group(0));
        }
        return result;
    }

    @Override
    public List<String> attempt(CTFChallenge challenge) {
        List<String> possibleFlags = new ArrayList<>();

        possibleFlags.addAll(findMatches(challenge.getCategory()));
        possibleFlags.addAll(findMatches(challenge.getDescription()));
        possibleFlags.addAll(findMatches(challenge.getName()));

        List<? extends CTFFile> files = challenge.getFiles();
        
        if(!files.isEmpty()) {
            Path tempdir;
            try {
                tempdir = Files.createTempDirectory("regex_solver");
                for(CTFFile file : files){
                    System.out.println("Downloading file " + file.getName());
                    file.download(tempdir.toFile().getAbsolutePath());
                    File f = Paths.get(tempdir.toFile().getAbsolutePath(), file.getName()).toFile();
                    BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f),"utf-8"));
                    String line;

                    while((line = r.readLine()) != null){
                        possibleFlags.addAll(findMatches(line));
                    }

                    r.close();
                    f.delete();
                }
            } catch (IOException e) {
                System.err.println("Could not create temp dir - skipping file regex matching");
            }
        }

        return possibleFlags;
    }
    
}
