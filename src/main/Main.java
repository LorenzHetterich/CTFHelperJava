package main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.Gson;

import discord.DiscordBot;


public class Main {
    
    
    public static void main(String[] args) throws IOException{
        new File("ctfs").mkdir();
        String token = Files.readString(Paths.get("token.txt")).trim();
        new DiscordBot(token);
    }

}
