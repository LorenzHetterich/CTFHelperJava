package discord;

import java.util.List;
import java.util.Optional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;

import ctfdapi.CTFdApi;
import ctfdapi.CTFdApi.CTFdAuth;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class NewDiscordBot  extends ListenerAdapter{
    
    public static class State {

        public static class CTF {
            public String name;
            public String jsonPath;
            public String discordGuildId;

            public transient NewDiscordBotCTF instance;

            public CTF(String name, String jsonPath, String discordGuildId, NewDiscordBotCTF instance){
                this.name = name;
                this.jsonPath = jsonPath;
                this.discordGuildId = discordGuildId;
                this.instance = instance;
            }
        }

        public static class CTFList extends ArrayList<CTF>{}

        public CTFList ctfs; 

        public State() {
            ctfs = new CTFList();
        }

        public boolean save(){
            try{
                Files.writeString(new File("ctfs/all.json").toPath(), new Gson().toJson(this), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return true;
            } catch(IOException e){
                return false;
            }
        }

    }

    public State state;
    public JDA jda;

    public NewDiscordBot(String token, State state){
        this.state = state;
        
        JDABuilder builder = JDABuilder.createDefault(token);
        builder.setActivity(Activity.playing("CTF"));
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING);
        builder.setLargeThreshold(30);
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
        this.jda = builder.build();
        this.jda.updateCommands().addCommands(
                Commands.slash("ctfd-add", "creates a new CTFd CTF")
                        .addOption(OptionType.STRING, "name", "CTF category name", true)
                        .addOption(OptionType.STRING, "endpoint", "endpoint", true)
                        .addOption(OptionType.STRING, "pattern", "flag pattern", true),
                Commands.slash("ctfd-cookie", "sets cookie authentication for the associated CTF")
                        .addOption(OptionType.STRING, "name", "name of the cookie to set", true)
                        .addOption(OptionType.STRING, "value", "session cookie", true),
                Commands.slash("ctfd-flag", "manually submit flag for current challenge")
                        .addOption(OptionType.STRING, "flag", "flag", true),
                Commands.slash("ctfd-update", "refresh all challenges"),
                Commands.slash("ctfd-url", "change CTFd endpoint")
                        .addOption(OptionType.STRING, "endpoint", "CTFd endpoint", true),
                Commands.slash("ctfd-archive", "archive all messages within ctf channels of this ctf"),
                Commands.slash("ctfd-end", "nuke all channels associated with the CTF")).queue();

        List<State.CTF> toRemove = new ArrayList<>();

        for(State.CTF ctf : state.ctfs){
            try {
                ctf.instance = new NewDiscordBotCTF(this.jda, ctf.jsonPath, null);
            } catch (JsonIOException | IOException e) {
                toRemove.add(ctf);
                System.err.printf("[DiscordBot][Error] Unable to load CTF from json file %s\n", ctf.jsonPath);
            }
        }
        toRemove.forEach(state.ctfs::remove);

        this.jda.addEventListener(this);
    }

    public void addCTF(NewDiscordBotCTF ctf){
        state.ctfs.add(
            new State.CTF(ctf.state.ctfName, ctf.jsonPath, jda.getCategoryById(ctf.state.generalCategory).getGuild().getId(), ctf)
        );
        ctf.state.save(ctf.jsonPath);
        if(!this.state.save()){
            System.err.printf("[ERROR][DiscordBot] Failed to save state\n");
        }
    }

    public boolean createCTF(String name, String flagPattern, String endpoint, Guild guild){
        // TODO: make this a safe file path
        String jsonPath = String.format("ctfs/%s.json", name);
        NewDiscordBotCTF.State state = new NewDiscordBotCTF.State(name, endpoint, flagPattern, CTFdAuth.None(), guild);
        try{
            NewDiscordBotCTF ctf = new NewDiscordBotCTF(jda, jsonPath, state);
            ctf.update();
            addCTF(ctf);
            return true;
        } catch(IOException e){
            return false;
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()){
            return;
        }
        Guild guild = event.getGuild();
        Optional<State.CTF> ctf = state.ctfs.stream().filter(x -> jda.getCategoryById(x.instance.state.generalCategory).getGuild().getId().equals(guild.getId())).findAny();

        if(ctf.isPresent()){
            ctf.get().instance.onMessage(event);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if(!event.isFromGuild()){
            return;
        }
        Guild guild = event.getGuild();
        Optional<State.CTF> ctf = state.ctfs.stream().filter(x -> jda.getCategoryById(x.instance.state.generalCategory).getGuild().getId().equals(guild.getId())).findAny();

        InteractionHook hook = event.reply("...").complete();

        switch(event.getName()){
            case "ctfd-add": {
                String name = event.getOption("name").getAsString();
                String endpoint = event.getOption("endpoint").getAsString();
                String pattern = event.getOption("pattern").getAsString();

                if(ctf.isPresent()){
                    hook.editOriginal("A CTF is already active on this server!").queue();
                    return;
                }

                if(createCTF(name, pattern, endpoint, guild)) {
                    hook.editOriginal("Sucecss!").queue();
                } else {
                    hook.editOriginal("something went wrong, sorry :(").queue();
                }

                break;
            }

            case "ctfd-cookie": {
                String name = event.getOption("name").getAsString();
                String value = event.getOption("value").getAsString();

                if(!ctf.isPresent()){
                    hook.editOriginal("No ctf active on this server!").queue();
                    return;
                }

                ctf.get().instance.state.auth = CTFdAuth.COOKIE(name, value);
                ctf.get().instance.update();

                hook.editOriginal("Done!").queue();

                break;
            }

            case "ctfd-flag": {
                if(!ctf.isPresent()){
                    hook.editOriginal("No ctf active on this server!").queue();
                    return;
                }

                Optional<NewDiscordBotCTF.State.Challenge> challenge = ctf.get().instance.state.challenges.find(event.getChannel());

                if(!challenge.isPresent()){
                    hook.editOriginal("No challenge associated with this channel!").queue();
                    return;
                }

                if(challenge.get().tryFlag(event.getOption("flag").getAsString(), ctf.get().instance.api)) {
                    challenge.get().flag = event.getOption("flag").getAsString();
                    hook.editOriginal("Correct flag").queue();
                    jda.getTextChannelById(ctf.get().instance.state.generalChannelId).sendMessage(
                        String.format("Challenge %s was just solved by %s with flag %s\n", challenge.get().ctfdChallenge.name, event.getUser().getAsMention(), event.getOption("flag").getAsString())
                    ).queue();
                    ctf.get().instance.update();
                } else {
                    hook.editOriginal("Incorrect flag").queue();
                }

                break;
            }

            case "ctfd-update": {
                if(!ctf.isPresent()){
                    hook.editOriginal("No ctf active on this server!").queue();
                    return;
                }
                ctf.get().instance.update();
                hook.editOriginal("done").queue();
                break;
            }

            case "ctfd-archive": {
                hook.editOriginal("TODO: implement ctfd-archive").queue();
                break;
            }

            case "ctfd-end": {
                if(!ctf.isPresent()){
                    hook.editOriginal("no CTF associated with this server").queue();
                    return;
                }
                ctf.get().instance.destroy();
                state.ctfs.remove(ctf.get());
                this.state.save();
                new File(ctf.get().jsonPath).delete();
                break;
            }

            case "ctfd-url": {
                if(!ctf.isPresent()){
                    hook.editOriginal("no CTF associated with this server").queue();
                    return;
                }
                String endpoint = event.getOption("endpoint").getAsString();
                ctf.get().instance.state.ctfUrl = endpoint;
                ctf.get().instance.api = new CTFdApi(endpoint);
                ctf.get().instance.update();
                hook.editOriginal("done").queue();
                break;
            }

            default: {
                hook.editOriginal("unknown command").queue();
                break;
            }
        }
    }
}
