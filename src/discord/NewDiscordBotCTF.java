package discord;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import ctfdapi.CTFdApi;
import ctfdapi.CTFdChallenge;
import ctfdapi.CTFdApi.CTFdAuth;
import discord.NewDiscordBotCTF.State.Challenge;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class NewDiscordBotCTF {

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

    public static class State {

        public static class Challenge {
            public String discordChannelId;
            public String discordMessageId;
            public CTFdChallenge ctfdChallenge;
            public String flag; // may be null
            public String url;

            public Challenge(CTFdChallenge ctfdChallenge, String url, State state, JDA jda, CTFdApi api){
                this.url = url;
                this.ctfdChallenge = ctfdChallenge;
                TextChannel textChannel = jda.getCategoryById(ctfdChallenge.solvedByMe ? state.solvedCategory : state.unsolvedCategory).createTextChannel(toChannelName(ctfdChallenge.name)).complete();
                this.discordChannelId = textChannel.getId();
                attachFiles(textChannel.sendMessage(getMessageContent(true)).complete(), api);
                this.discordMessageId = jda.getTextChannelById(ctfdChallenge.solvedByMe ? state.solvedChallengesChannelId : state.unsolvedChallengesChannelId).sendMessage(
                    this.getMessageContent(false)
                ).complete().getId();   
            }

            public String getMessageContent(boolean full){
                if(full){
                    return String.format("# %s\n* Category: %s\n* Url: %s\n\n%s",
                    this.ctfdChallenge.name,
                    this.ctfdChallenge.category,
                    this.url,
                    this.ctfdChallenge.description);
                } else {
                return
                    String.format("# %s\n%s* Category: %s\n* Channel: <#%s>\n* Url: %s",
                            this.ctfdChallenge.name,
                            this.flag != null ? String.format("* `%s`\n", this.flag) : "",
                            this.ctfdChallenge.category, this.discordChannelId,
                            this.url);
                }
            }

            public void attachFiles(Message message, CTFdApi api){
                /*
                List<AttachedFile> files = new ArrayList<>();
                if(this.ctfdChallenge.files == null)
                    return;
                    
                for(String file : this.ctfdChallenge.files){
                    String fileUrl = String.format("%s/%s", api.url, file);
                    
                    byte[] data = api.simpleRawReq(HttpRequest.newBuilder().uri(URI.create(fileUrl)).GET()).get();
                    if(!files.add(AttachedFile.fromData(data, file))) {
                        // TODO: upload to own server
                        System.err.printf("[Warning][Challenge][%s]: Could not attach file %s (size: %d)\n", this.ctfdChallenge.name, file, data.length);
                        message.getChannel().sendMessage(String.format("[AttachFailed] %s", fileUrl)).complete();
                    }
                }
                message.editMessageAttachments(files).queue();
                    */
                if(this.ctfdChallenge.files == null)
                    return;
                String links = String.join("\n", Arrays.stream(this.ctfdChallenge.files).map(x -> String.format("%s/%s", api.url, x)).toList());
                if(!links.isBlank()){
                    message.reply(
                        links
                    ).queue();
                }
            }

            public boolean update(JDA jda, State state, CTFdChallenge ctfdChallenge){

                // if nothing changed, no update is required!
                if(new Gson().toJson(this.ctfdChallenge).equals(new Gson().toJson(ctfdChallenge))){
                    return false;
                }

                if(ctfdChallenge != null){
                    this.ctfdChallenge = ctfdChallenge;
                }
                
                TextChannel channel = jda.getTextChannelById(this.discordChannelId);
                TextChannel unsolved = jda.getTextChannelById(state.unsolvedChallengesChannelId);
                TextChannel solved = jda.getTextChannelById(state.solvedChallengesChannelId);

                if(this.ctfdChallenge.solvedByMe && channel.getParentCategoryId().equals(state.unsolvedCategory)){
                    // challenge is now solved, move to solved category
                    channel.getManager().setParent(jda.getCategoryById(state.solvedCategory)).queue();
                    // and move message to solved channel
                    try{
                        unsolved.deleteMessageById(this.discordMessageId).queue();
                    } catch(Exception e){}
                    this.discordMessageId = solved.sendMessage(this.getMessageContent(false)).complete().getId();
                } else if(ctfdChallenge != null){
                    (ctfdChallenge.solvedByMe ? solved : unsolved).editMessageById(this.discordMessageId, this.getMessageContent(false)).queue();
                }

                return true;
            }

            public boolean tryFlag(String flag, CTFdApi api){
                if(this.ctfdChallenge.solvedByMe){
                    System.err.printf("[Warning][Challenge][%s]: Attempting to submit flag '%s' to already solved challenge!\n", ctfdChallenge.name, flag);
                    return true;
                }

                return api.postChallengeAttempt(this.ctfdChallenge.id, flag).get().status.equals("correct");
            }
        }

        public static class ChallengeList extends ArrayList<Challenge> {

            public Optional<Challenge> find(CTFdChallenge challenge){
                return this.stream().filter(x -> x.ctfdChallenge.id == challenge.id).findAny();
            }

            public Optional<Challenge> find(MessageChannelUnion channel){
                return this.stream().filter(x -> x.discordChannelId.equals(channel.getId())).findAny();
            }

        }

        public String ctfName;
        public String ctfUrl;
        public String flagRegex;


        public CTFdAuth auth;
        public ChallengeList challenges;

        public String generalCategory;
        public String unsolvedCategory;
        public String solvedCategory;

        public String solvedChallengesChannelId;
        public String unsolvedChallengesChannelId;
        public String generalChannelId;

        public State(String ctfName, String ctfUrl, String flagRegex, CTFdAuth auth, Guild guild){
            this.ctfName = toChannelName(ctfName);
            this.ctfUrl = ctfUrl;
            this.flagRegex = flagRegex;
            this.auth = auth;
            this.challenges = new ChallengeList();

            Category general = guild.createCategory(this.ctfName).complete();

            this.generalCategory = general.getId();
            this.unsolvedCategory = guild.createCategory(this.ctfName + "-unsolved").complete().getId();
            this.solvedCategory = guild.createCategory(this.ctfName + "-solved").complete().getId();

            this.solvedChallengesChannelId = general.createTextChannel("solved").complete().getId();
            this.unsolvedChallengesChannelId = general.createTextChannel("unsolved").complete().getId();
            this.generalChannelId = general.createTextChannel("general").complete().getId();
        }

        public boolean save(String path){
            try{
                Files.writeString(new File(path).toPath(), new Gson().toJson(this), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return true;
            } catch(IOException e){
                return false;
            }
        }
    }


    public State state;
    public CTFdApi api;
    public JDA jda;
    public String jsonPath;

    public AtomicBoolean alive;

    public void destroy(){
        alive.set(false);
        for(State.Challenge c : state.challenges){
            jda.getTextChannelById(c.discordChannelId).delete().queue();
        }
        jda.getTextChannelById(state.generalChannelId).delete().queue();
        jda.getTextChannelById(state.unsolvedChallengesChannelId).delete().queue();
        jda.getTextChannelById(state.solvedChallengesChannelId).delete().queue();
        jda.getCategoryById(state.generalCategory).delete().queue();
        jda.getCategoryById(state.unsolvedCategory).delete().queue();
        jda.getCategoryById(state.solvedCategory).delete().queue();
    }


    // automatically submit flags
    public void onMessage(MessageReceivedEvent event) {
        state.challenges.find(event.getChannel()).ifPresent(x -> {
            String msg = event.getMessage().getContentDisplay();
            Matcher m = Pattern.compile(state.flagRegex).matcher(msg);

            boolean f = false;

            while(m.find()){
                f = true;
                if(x.ctfdChallenge.solvedByMe){
                    event.getMessage().addReaction(Emoji.fromFormatted("❎")).queue();
                    break;
                }

                String flag = m.group(0);
                if(x.tryFlag(flag, api)){
                    event.getMessage().addReaction(Emoji.fromFormatted("🚩")).queue();
                    x.flag = flag;
                    jda.getTextChannelById(state.generalChannelId).sendMessage(
                        String.format("Challenge %s was just solved by %s with flag %s\n", x.ctfdChallenge.name, event.getAuthor().getAsMention(), flag)
                    ).queue();
                    NewDiscordBotCTF.this.update();
                    return;
                }
            }
            if(f) {
                    event.getMessage().addReaction(Emoji.fromFormatted("❌")).queue();
            }
        });
    }

    // "create" challenge that wasn't there before. Either because it was just released or because we never loaded the challenges until now.
    private Challenge newChallenge(CTFdChallenge challenge){
        return new Challenge(challenge, String.format("%s/challenges#%s-%d", api.url, URLEncoder.encode(challenge.name, StandardCharsets.UTF_8).replace("+", "%20"), challenge.id), state, jda, api);
    }
    
    // update all existing challenges and add new ones. Save state to json file if it changed.
    public boolean update(){
        try {
            this.state.auth.authenticate(this.api);
            this.api.updateCSRFToken();
            boolean needsSave = Arrays.stream(api.getChallengeList().get()).map(c -> {
                c = api.getChallenge(c.id).get();
                Optional<Challenge> challenge = state.challenges.find(c);
                if(challenge.isPresent()){
                    return challenge.get().update(jda, state, c);
                } else {
                    Challenge ch = this.newChallenge(c);
                    state.challenges.add(
                        ch
                    );
                    jda.getTextChannelById(state.generalChannelId).sendMessage(
                        String.format("New Challenge %s released: <#%s>", c.name, ch.discordChannelId)
                    ).queue();
                    return true;
                }
            }).reduce(false, (a,b) -> a || b);

            if(needsSave){
                if(!this.state.save(this.jsonPath)){
                    System.err.printf("[ERROR][DiscordCTF] Failed to save state to %s\n", jsonPath);
                }
            }
            return true;
        } catch(RuntimeException e){
            jda.getTextChannelById(state.generalChannelId).sendMessage("Unable to update challenges, make sure authentication is valid!").queue();
            return false;
        }
    }

    public NewDiscordBotCTF(JDA jda, String jsonPath, State state) throws JsonSyntaxException, JsonIOException, IOException{
        this.jda = jda;
        this.jsonPath = jsonPath;
        if(state == null){
            state = new Gson().fromJson(Files.newBufferedReader(new File(jsonPath).toPath()), State.class);
        }
        this.state = state;
        this.api = new CTFdApi(state.ctfUrl);
        this.api.updateCSRFToken();
        this.alive = new AtomicBoolean(true);

        new Thread(() -> {
            try{
                while(true){
                    // every 20 Minutes!
                    Thread.sleep(20 * 60 * 1000);
                    if(!this.alive.get())
                        break;
                    if(!this.update()){
                        try{
                            jda.getTextChannelById(this.state.generalChannelId).sendMessage("Automatic update failed!").queue();
                        } catch(Exception e){
                            // ignore
                        }
                    }
                }
            } catch(InterruptedException e){

            }
        }).start();
    }
}
