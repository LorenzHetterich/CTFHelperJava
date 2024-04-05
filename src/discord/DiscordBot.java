package discord;

import java.util.HashMap;
import java.util.Optional;

import com.google.gson.Gson;

import java.util.Map;

import ctf.ctfd.CTFdApi;
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
import java.io.File;
import java.nio.file.Files;

public class DiscordBot extends ListenerAdapter{

    // there are some race conditions in this class, but they should only lead to Exceptions which the DiscordAPI thingy handles nicely

    public static JDA jda;

    private Gson gson = new Gson();
    
    // mapping Discord servers to CTFs
    private Map<String, DiscordBotCTF> ctfs = new HashMap<>();

    public DiscordBot(String token){
        // setup discord api thingy
        JDABuilder builder = JDABuilder.createDefault(token);
        builder.setActivity(Activity.playing("CTF"));
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING);
        builder.setLargeThreshold(30);
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
        jda = builder.build();
        jda.updateCommands().addCommands(

        // TODO: rCTF 

        /* ctfd specific */
        Commands.slash("ctfd-add", "creates a new CTFd CTF")
                .addOption(OptionType.STRING, "name", "CTF name", true)
                .addOption(OptionType.STRING, "endpoint", "endpoint", true),
        
        /* generic */
        Commands.slash("ctf-pattern", "sets the flag pattern")
            .addOption(OptionType.STRING, "pattern", "regex to set", true),
        Commands.slash("ctf-cookie", "sets cookie authentication for the associated CTF")
        .addOption(OptionType.STRING, "name", "name of the cookie to set", true)
        .addOption(OptionType.STRING, "value", "session cookie", true),
        Commands.slash("ctf-flag", "manually submit flag for current challenge")
                .addOption(OptionType.STRING, "flag", "flag", true),
        Commands.slash("ctf-update", "refresh all challenges"),
        Commands.slash("ctf-archive", "archive all messages within ctf channels of this ctf"),
        Commands.slash("ctf-end", "Don't use before ctf is over! Otherwise I will personally visit you and wet all your socks.")).queue();

        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            // whatever
            throw new RuntimeException("DO NOT INTERRUPT MEEEE!");
        }

         // load currently ongoing ctfs        
         for(File f : new File("ctfs").listFiles()) {
            try{
                DiscordBotCTF ctf = gson.fromJson(Files.readString(f.toPath()), DiscordBotCTF.class);
                ctf.gson = gson;
                ctf.update();
                ctfs.put(
                    f.getName().split(".json")[0], 
                    ctf
                );
                System.out.printf("Loaded CTF from file %s\n", f.getName());
            } catch(Exception e){
                e.printStackTrace();
                System.err.printf("Failed to load CTF from file %s\n", f.getName());
            }
        }

        // add event listener in the end to not race anything above
        jda.addEventListener(this);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()){
            return;
        }
        Guild guild = event.getGuild();
        if(this.ctfs.containsKey(guild.getId())) {
            this.ctfs.get(guild.getId()).onMessage(event);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if(!event.isFromGuild()){
            return;
        }
        Guild guild = event.getGuild();
        InteractionHook hook = event.reply("...").complete();
        Optional<DiscordBotCTF> ctf = Optional.ofNullable(this.ctfs.getOrDefault(guild.getId(), null));

        switch(event.getName()) {
            case "ctfd-add": {
                if(ctf.isPresent()){
                    hook.editOriginal("a CTF is already active on this server. You must end it first!").queue();
                    return;
                }

                String endpoint = event.getOption("endpoint").getAsString();
                String name = event.getOption("name").getAsString();
                CTFdApi api = new CTFdApi(endpoint);
                
                try{
                    DiscordBotCTF newCtf = new DiscordBotCTF(String.format("ctfs/%s.json", guild.getId()), name, api, guild);
                    newCtf.update();
                    this.ctfs.put(guild.getId(), newCtf);
                    hook.editOriginal("done!").queue();
                } catch(Exception e){
                    hook.editOriginal("something went wrong!").queue();
                }
                break;
            }

            case "ctf-pattern": {
                if(!ctf.isPresent()){
                    hook.editOriginal("ctf-pattern requires an ongoing CTF on the server!").queue();
                    return;
                }

                String pattern = event.getOption("pattern").getAsString();
                ctf.get().setFlagPattern(pattern);
                hook.editOriginal("done").queue();
                break;
            }

            case "ctf-cookie": {
                if(!ctf.isPresent()){
                    hook.editOriginal("ctf-cookie requires an ongoing CTF on the server!").queue();
                    return;
                }

                String name = event.getOption("name").getAsString();
                String value = event.getOption("value").getAsString();

                try {
                    ctf.get().setCookie(name, value);
                    hook.editOriginal("done!").queue();
                } catch(Exception e){
                    e.printStackTrace();
                    hook.editOriginal("something went wrong!").queue();
                }
                break;
            }

            case "ctf-flag": {
                if(!ctf.isPresent()){
                    hook.editOriginal("ctf-flag requires an ongoing CTF on the server!").queue();
                    return;
                }

                String flag = event.getOption("flag").getAsString();

                try {
                    ctf.get().submitFlag(event.getChannelId(), flag, hook);
                } catch(Exception e){
                    hook.editOriginal("something went wrong!").queue();
                }
                break;
            }

            case "ctf-update": {
                if(!ctf.isPresent()){
                    hook.editOriginal("ctf-update requires an ongoing CTF on the server!").queue();
                    return;
                }

                try {
                    ctf.get().update(hook);
                } catch(Exception e){
                    hook.editOriginal("something went wrong!").queue();
                }
                break;
            }

            case "ctf-archive": {
                if(!ctf.isPresent()){
                    hook.editOriginal("ctf-archive requires an ongoing CTF on the server!").queue();
                    return;
                }

                try {
                    ctf.get().archive(hook);
                } catch(Exception e){
                    hook.editOriginal("something went wrong!").queue();
                }
                break;
            }

            case "ctf-end": {
                if(!ctf.isPresent()){
                    hook.editOriginal("ctf-end requires an ongoing CTF on the server!").queue();
                    return;
                }

                // TODO: require role!

                try {
                    this.ctfs.remove(guild.getId()).end(hook);
                } catch(Exception e){
                    hook.editOriginal("something went wrong!").queue();
                }
                break;
            }

            default: {
                hook.editOriginal(String.format("Unknown command '%s'", event.getName())).queue();
                break;
            }
        }
    }
}
