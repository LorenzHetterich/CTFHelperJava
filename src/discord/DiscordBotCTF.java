package discord;

import ctf.CTFApi;
import ctf.CTFChallenge;
import ctf.ctfd.CTFdApi;
import ctf.rctf.RCTFApi;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static discord.DiscordBot.jda;


import com.google.gson.Gson;

public class DiscordBotCTF {

    public static transient final Map<String, String> category_symbols = new HashMap<>();

    // non-static class wouldn't get rid of additional arguments either as gson cannot handle them correctly
    public static class Challenge {

        public String discordChannel;
        public String discordMessage;
        public CTFChallenge challenge;
        // may be null
        public String flag;

        public Challenge(CTFChallenge challenge, DiscordBotCTF ctf) {
            this.challenge = challenge;
            String name = toChannelName(challenge.getName());
            if(category_symbols.containsKey(challenge.getCategory().toLowerCase())){
                name = category_symbols.get(challenge.getCategory().toLowerCase()) + "-" + name;
            }
            TextChannel textChannel = jda.getCategoryById(challenge.isSolved()? ctf.solvedCategory : ctf.unsolvedCategory).createTextChannel(name).complete();
            this.discordChannel = textChannel.getId();
            attachFiles(textChannel.sendMessage(getMessageContent(true)).complete());
            this.discordMessage = jda.getTextChannelById(challenge.isSolved() ? ctf.solvedChallengesChannel : ctf.unsolvedChallengesChannel).sendMessage(
                this.getMessageContent(false)
            ).complete().getId(); 
        }

        public String getMessageContent(boolean full){
            if(full){
                return String.format("# %s\n* Category: %s\n* Url: %s\n\n%s",
                this.challenge.getName(),
                this.challenge.getCategory(),
                this.challenge.getUrl(),
                this.challenge.getDescription());
            } else {
            return
                String.format("# %s\n%s* Category: %s\n* Channel: <#%s>\n* Url: %s",
                        this.challenge.getName(),
                        this.flag != null ? String.format("* `%s`\n", this.flag) : "",
                        this.challenge.getCategory(), 
                        this.discordChannel,
                        this.challenge.getUrl());
            }
        }

          public boolean update(DiscordBotCTF ctf, CTFChallenge challenge){

                // if nothing changed, no update is required!
                if(new Gson().toJson(this.challenge).equals(new Gson().toJson(challenge))){
                    return false;
                }

                if(challenge != null){
                    this.challenge = challenge;
                }
                
                TextChannel channel = jda.getTextChannelById(this.discordChannel);
                TextChannel unsolved = jda.getTextChannelById(ctf.unsolvedChallengesChannel);
                TextChannel solved = jda.getTextChannelById(ctf.solvedChallengesChannel);

                if(this.challenge.isSolved() && channel.getParentCategoryId().equals(ctf.unsolvedCategory)){
                    // challenge is now solved, move to solved category
                    channel.getManager().setParent(jda.getCategoryById(ctf.solvedCategory)).queue();
                    // and move message to solved channel
                    try{
                        unsolved.deleteMessageById(this.discordMessage).queue();
                    } catch(Exception e){}
                    this.discordMessage = solved.sendMessage(this.getMessageContent(false)).complete().getId();
                } else if(challenge != null){
                    (challenge.isSolved() ? solved : unsolved).editMessageById(this.discordMessage, this.getMessageContent(false)).queue();
                }

                return true;
            }

        public void attachFiles(Message message){
            List<String> files = this.challenge.getFiles();
            if(files == null)
                return;
            String links = String.join("\n", files);
            if(!links.isBlank()){
                    message.reply(
                    links
                ).queue();
            }
        }
    }

    static {
        // web
        category_symbols.put("web", "🌐");

        // crypto
        category_symbols.put("crypto", "🔐");
        category_symbols.put("cryptography", "🔐");

        // rev
        category_symbols.put("rev", "⭯");
        category_symbols.put("reversing", "⭯");
        category_symbols.put("reverse engineering", "⭯");

        // pwn
        category_symbols.put("pwn", "🎆");
        category_symbols.put("exploitation", "🎆");
        category_symbols.put("binary exploitation", "🎆");


        // forensics
        category_symbols.put("forensics", "🔎");

        // misc
        category_symbols.put("misc", "❓");
        category_symbols.put("other", "❓");
    }

    private static String toChannelName(String s) {
        StringBuilder result = new StringBuilder();
        for (char c : s.toLowerCase().replace(" ", "-").toCharArray()) {
            if ("abcdefghjiklmnopqrstuvwxyz-0123456789".contains(c + "")) {
                result.append(c);
            }
        }
        if (result.isEmpty()) {
            return "unnamed";
        }
        return result.toString();
    }

    public static class ChallengeList extends ArrayList<Challenge> {

        public Optional<Challenge> find(CTFChallenge challenge){
            return this.stream().filter(x -> x.challenge.getId().equals(challenge.getId())).findAny();
        }

        public Optional<Challenge> find(MessageChannelUnion channel){
            return this.stream().filter(x -> x.discordChannel.equals(channel.getId())).findAny();
        }

        public Optional<Challenge> find(String channelId){
            return this.stream().filter(x -> x.discordChannel.equals(channelId)).findAny();
        }

    }

    public ChallengeList challenges = new ChallengeList();
    public String generalCategory;
    public String unsolvedCategory;
    public String solvedCategory;
    public String solvedChallengesChannel;
    public String unsolvedChallengesChannel;
    public String generalChannel;

    private String flagPattern = "\\A\\b\\Z";
    private String jsonPath;
    private String name;
    private CTFApi api;

    public transient Gson gson = new Gson();

    public DiscordBotCTF(String jsonPath, String name, CTFApi api, Guild guild){
        this.jsonPath = jsonPath;
        this.name = toChannelName(name);
        this.api = api;

        Category general = guild.createCategory(this.name).complete();

        this.generalCategory = general.getId();
        this.unsolvedCategory = guild.createCategory(this.name + "-⏳").complete().getId();
        this.solvedCategory = guild.createCategory(this.name + "-🚩").complete().getId();
        this.solvedChallengesChannel = general.createTextChannel("🚩-solved").complete().getId();
        this.unsolvedChallengesChannel = general.createTextChannel("⏳-unsolved").complete().getId();
        this.generalChannel = general.createTextChannel("general").complete().getId();
    }

    public synchronized boolean save() {
        try {
            Files.writeString(new File(this.jsonPath).toPath(), gson.toJson(this), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void onMessage(MessageReceivedEvent event){
        challenges.find(event.getChannel()).ifPresent(x -> {
            String msg = event.getMessage().getContentDisplay();
            Matcher m = Pattern.compile(flagPattern).matcher(msg);

            boolean f = false;

            while(m.find()){
                f = true;
                if(x.challenge.isSolved()){
                    event.getMessage().addReaction(Emoji.fromFormatted("❎")).queue();
                    break;
                }

                String flag = m.group(0);
                if(x.challenge.submitFlag(flag)){
                    event.getMessage().addReaction(Emoji.fromFormatted("🚩")).queue();
                    x.flag = flag;
                    jda.getTextChannelById(generalChannel).sendMessage(
                        String.format("Challenge %s was just solved by %s with flag %s\n", x.challenge.getName(), event.getAuthor().getAsMention(), flag)
                    ).queue();
                    this.update();
                    return;
                }
            }
            if(f) {
                    event.getMessage().addReaction(Emoji.fromFormatted("❌")).queue();
            }
        });
    }

    public boolean update(){
        try {
            this.challenges.forEach(x -> x.challenge.setApi(api));
            boolean needsSave = api.getChallenges().stream().map(c -> {
                if(api instanceof CTFdApi){
                    // getChallenges() is good enough for rCTF to get all details (kinda)
                    c = api.getChallenge(c.getId());
                }
                c.setApi(api);
                Optional<Challenge> challenge = challenges.find(c);
                if(challenge.isPresent()){
                    return challenge.get().update(DiscordBotCTF.this, c);
                } else {
                    Challenge ch = new Challenge(c, DiscordBotCTF.this);
                    challenges.add(ch);
                    jda.getTextChannelById(generalChannel).sendMessage(
                        String.format("New Challenge %s released: <#%s>", c.getName(), ch.discordChannel)
                    ).queue();
                    return true;
                }
            }).reduce(false, (a,b) -> a || b);

            if(needsSave){
                if(!this.save()){
                    System.err.printf("[ERROR][DiscordCTF] Failed to save state to %s\n", jsonPath);
                }
            }

            this.sortChallenges();
            return true;
        } catch(Exception e){
            e.printStackTrace();
            jda.getTextChannelById(generalChannel).sendMessage("Unable to update challenges, make sure authentication is valid!").queue();
            return false;
        }
    }

    private void sortChallenges(){
        
        // solved challenges
        List<TextChannel> sortedChannels = this.challenges
            .stream()
            .filter(x -> x.challenge.isSolved())
            .map(x -> jda.getTextChannelById(x.discordChannel))
            .sorted((a,b) -> a.getName().compareTo(b.getName()))
            .toList();
        for(int i = 0; i < sortedChannels.size(); i++){
            sortedChannels.get(i).getManager().setPosition(i).complete();
        }

        // unsolved challenges
        sortedChannels = this.challenges
            .stream()
            .filter(x -> !x.challenge.isSolved())
            .map(x -> jda.getTextChannelById(x.discordChannel))
            .sorted((a,b) -> a.getName().compareTo(b.getName()))
            .toList();
        for(int i = 0; i < sortedChannels.size(); i++){
            sortedChannels.get(i).getManager().setPosition(i).complete();
        }
    }

    public void setCookie(String name, String value){
        if(this.api instanceof CTFdApi ctfd){
            ctfd.setCookie(name, value);
            this.update();
        } else {
            throw new IllegalStateException(String.format("%s implementation does not support cookies", this.api.getClass().getName()));
        }
        this.save();
    }

    public void setHeader(String name, String value){
        if(this.api instanceof RCTFApi rctf){
            rctf.setHeader(name, value);
            this.update();
        } else {
            // TODO CTFd
            throw new IllegalStateException(String.format("%s implementation does not support cookies", this.api.getClass().getName()));
        }
        this.save();
    }

    public void submitFlag(String channelid, String flag, InteractionHook hook) {
        Optional<Challenge> challenge = this.challenges.find(channelid);
        if(challenge.isPresent()){
            CTFChallenge c = challenge.get().challenge;
            if(c.isSolved()){
                hook.editOriginal("Already solved!").queue();
                return;
            }
            if(c.submitFlag(flag)){
                hook.editOriginal("Correct flag!").queue();
                this.save();
            } else {
                hook.editOriginal("Incorrect flag").queue();
            }
        }
    }

    public void setFlagPattern(String pattern) {
        this.flagPattern = pattern;
        this.save();
    }

    public void update(InteractionHook hook) {
        if(this.update()) {
            hook.editOriginal("ok").queue();
        } else {
            hook.editOriginal("something went wrong!").queue();
        }
    }

    private void trySaveChannel(Path dir, TextChannel channel, String name){
        try {
            PrintStream out = new PrintStream(dir.resolve(toChannelName(name) + ".log").toFile());
            List<Message> messages = new ArrayList<>();
            channel.getIterableHistory().forEachAsync(x -> {
                messages.add(x);
                return true;
            }).thenAccept(_ignored -> {
                Collections.reverse(messages);
                for (Message m : messages) {
                    out.printf("[%s][%s]: %s\n", m.getTimeCreated(), m.getAuthor().getEffectiveName(),
                            m.getContentDisplay());
                }
            }).whenComplete((a,b) -> {
                System.out.printf("Saved channel for %s\n", name);
                out.close();
                channel.sendMessage("Saved sucessfully!").queue();
            });
        } catch (FileNotFoundException e) {
            System.err.println("Failed to save state for " + name);
            channel.sendMessage("Failed to save!").queue();
        }
    }

    public void archive(InteractionHook hook) {
        Path dir = Paths.get("archives", toChannelName(this.name));

        dir.toFile().mkdirs();

        trySaveChannel(dir, jda.getTextChannelById(this.generalChannel), "general");

        for(Challenge c : this.challenges){
            TextChannel channel = jda.getTextChannelById(c.discordChannel);
            trySaveChannel(dir, channel, c.challenge.getName());
        }
    }

    public void end(InteractionHook hook) {
        for(Challenge c : challenges){
            jda.getTextChannelById(c.discordChannel).delete().queue();
        }
        jda.getTextChannelById(generalChannel).delete().queue();
        jda.getTextChannelById(unsolvedChallengesChannel).delete().queue();
        jda.getTextChannelById(solvedChallengesChannel).delete().queue();
        jda.getCategoryById(generalCategory).delete().queue();
        jda.getCategoryById(unsolvedCategory).delete().queue();
        jda.getCategoryById(solvedCategory).delete().queue();

        new File(this.jsonPath).delete();
    }

}
