package main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import com.google.gson.Gson;

import ctf.CTF;
import ctf.CTFChallenge;
import ctf.CTFFile;
import discord.DiscordBot;


public class Main {
    
    private static String validFileChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789";

    private static String toValidFileName(String name){
        name = name.replace(" ", "_");
        StringBuilder s = new StringBuilder();
        for(char c : name.toCharArray()){
            if(validFileChars.contains(c + "")){
                s.append(c);
            }
        }
        return s.toString();
    }

    public static void downloadChallenges(String path, CTF ctf) {
        for(CTFChallenge c : ctf.getChallenges()){
            System.out.println("Downloading Challenge " + c.getName());
            String categoryPath = toValidFileName(c.getCategory());
            String challengePath = toValidFileName(c.getName());

            try {
                Files.createDirectories(Paths.get(path, categoryPath, challengePath));
                String markup = String.format("# %s\n\n## Overview\n\n- Category: %s\n- Points: %d\n- Difficulty: %d\n- Files: %s\n\n## Description\n\n%s\n", c.getName(), c.getCategory(), c.getPoints(), c.getDifficulty(), String.join(", ", c.getFiles().stream().map(CTFFile::getName).toArray(String[]::new)), c.getDescription());
                if(c.getDescription().contains("</")){
                    // probably html
                    Files.writeString(Paths.get(path, categoryPath, challengePath, "CHALLENGE.html"), c.getDescription());
                }
                Files.writeString(Paths.get(path, categoryPath, challengePath, "CHALLENGE.md"), markup);
                for(CTFFile f : c.getFiles()){
                    f.download(Paths.get(path, categoryPath, challengePath).toFile().getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("could not get challenge: " + c.getName() + ":");
                e.printStackTrace();
            }
        }
    }
    

    public static void main(String[] args) throws IOException{
        new File("ctfs").mkdir();
        String token = Files.readString(Paths.get("token.txt")).trim();
        DiscordBot.State state;
        if(new File("ctfs/all.json").exists()){
            state = new Gson().fromJson(Files.readString(new File("ctfs/all.json").toPath()), DiscordBot.State.class);
        } else {
            state = new DiscordBot.State();
        }
        new DiscordBot(token, state);
    }

}
