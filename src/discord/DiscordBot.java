package discord;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import ctf.CTF;
import ctf.ctfd.CTFdCTFAdapter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction;

public class DiscordBot extends ListenerAdapter {

    protected final JDA jda;
    protected Map<String, DiscordBotCTF> ctfs;

    public DiscordBot(String token) { 
        this.ctfs = new HashMap<>();
        JDABuilder builder = JDABuilder.createDefault(token);
        builder.setActivity(Activity.playing("CTF"));
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING);
        builder.setLargeThreshold(30);
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
        this.jda = builder.build();
        this.jda.updateCommands()
                .addCommands(
                        Commands.slash("nuke", "Nuke the current channel or category")
                                .addOption(OptionType.BOOLEAN, "category", "nuke whole category").setGuildOnly(true),
                        Commands.slash("archive", "Archive the current channel or category")
                                .addOption(OptionType.BOOLEAN, "category", "archive whole category"),
                        Commands.slash("ctfd", "creates a new CTFd CTF")
                            .addOption(OptionType.STRING, "name", "CTF category name", true)
                            .addOption(OptionType.STRING, "endpoint", "endpoint", true)
                            .addOption(OptionType.STRING, "username", "username", true)
                            .addOption(OptionType.STRING, "password", "password", true)
                            .addOption(OptionType.STRING, "pattern", "flag pattern", true)
                ).queue();
        this.jda.addEventListener(this);
    }

    private boolean hasRole(Member member, Guild guild, String roleName) {
        for (Role r : guild.getRolesByName(roleName, true)) {
            if (member.canInteract(r)) {
                return true;
            }
        }
        return false;
    }

    private Category findCategory(Guild guild, MessageChannelUnion channel) {
        for (Category c : guild.getCategories()) {
            if (c.getChannels().stream().anyMatch(x -> x.getId().equals(channel.getId()))) {
                return c;
            }
        }
        return null;
    }

    public static void saveMessageHistory(String filepath, MessagePaginationAction action,
            BiConsumer<? super Object, ? super Throwable> onDone) {
        final List<Message> messages = new ArrayList<>();
        
        try {
            PrintStream out = new PrintStream(filepath); 
            action.forEachAsync(x -> {
                messages.add(x);
                return true;
            })
            .thenAccept(_ignored -> {
                Collections.reverse(messages);
                for(Message m : messages){
                    out.printf("[%s][%s]: %s\n", m.getTimeCreated(), m.getAuthor().getEffectiveName(), m.getContentDisplay());
                }
            }).whenComplete(onDone.andThen((a,b) -> out.close()));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static boolean tryCreateDir(String path) {
        File f = new File(path);
        if(f.exists() && f.isDirectory()){
            return true;
        }
        return f.mkdir();
    }

    public static boolean tryCreateFile(String path) {
        File f = new File(path);
        if(f.exists() && f.isFile()){
            return true;
        }
        try {
            return f.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if(!event.isFromGuild())
            return;
        Category category = findCategory(event.getGuild(), event.getChannel());
        if(category == null)
            return;
        int idx = category.getName().lastIndexOf("-");
        String name = category.getName();
        if(idx != -1) {
            name = category.getName().substring(0, idx);
        }

        DiscordBotCTF ctf = this.ctfs.getOrDefault(name, this.ctfs.getOrDefault(category.getName(), null));
        if(ctf == null)
            return;
        ctf.onMessage(this, event);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        MessageChannelUnion channel = event.getChannel();

        if (!hasRole(member, guild, "CTF")) {
            event.reply("You don't have the CTF role, get r3al!").queue();
            return;
        }

        switch (event.getName()) {

        case "ctfd": {
            String name = event.getOption("name", null, OptionMapping::getAsString);

            if(this.ctfs.containsKey(name)){
                event.reply("ctf with that name already started!").queue();
                return;
            }

            String flagPattern = event.getOption("pattern", null, OptionMapping::getAsString);
            String endpoint = event.getOption("endpoint", null, OptionMapping::getAsString);
            String username = event.getOption("username", null, OptionMapping::getAsString);
            String password = event.getOption("password", null, OptionMapping::getAsString);


            InteractionHook action = event.reply("working . . .").complete();

            CTF ctf = new CTFdCTFAdapter(endpoint, flagPattern);

            if(!ctf.login(username, password)){
                action.editOriginal("login failed!").queue();
                return;
            }

            action.editOriginal("probably ok").queue();

            DiscordBotCTF dctf = new DiscordBotCTF(guild, name, ctf);
            this.ctfs.put(name, dctf);
            break;
        }

        case "nuke": {
            if (event.getOption("category", false, OptionMapping::getAsBoolean)) {
                // nuke whole category
                Category category = findCategory(guild, channel);
                if (category == null) {
                    event.reply("This channel has no category!").queue();
                    return;
                }
                event.reply("if you insist").queue();
                category.delete().queueAfter(5, TimeUnit.SECONDS);
                category.getChannels().forEach(x -> x.delete().queueAfter(3, TimeUnit.SECONDS));
            } else {
                // only nuke channel
                event.reply("if you insist").queue();
                event.getChannel().delete().queueAfter(5, TimeUnit.SECONDS);
            }
            break;
        }

        case "archive": {
            InteractionHook action = event.reply("working on it ...").complete();

            String path = ".";

            // nice vulnerability for a ctf here: name a discord server with a guild id to
            // conflict one with an invalid name ;)
            // same for channels, etc. below
            // also, this can probably be exploited with a bit of path traversal.
            if (tryCreateDir(path + "/" + guild.getName())) {
                path += "/" + guild.getName();
            } else if (tryCreateDir(path + "/" + guild.getId())) {
                path += "/" + guild.getId();
            } else {
                action.editOriginal("Could not create guild directory").queue();
                return;
            }

            Category category = findCategory(guild, channel);

            if (category != null) {
                if (tryCreateDir(path + "/" + category.getName())) {
                    path += "/" + category.getName();
                } else if (tryCreateDir(path + "/" + category.getId())) {
                    path += "/" + category.getId();
                } else {
                    action.editOriginal("Could not create category directory").queue();
                    return;
                }
            }

            String filePath;
            if(event.getOption("category", false, OptionMapping::getAsBoolean)){
                // who is this concurrency guy, I'd like to meet him one day
                // race condition, space condition
                int[] amountOkError = {0, 0};
                for(TextChannel c : category.getTextChannels()){
                    if (tryCreateFile(path + "/" + c.getName())) {
                        filePath = path + "/" + c.getName();
                    } else if (tryCreateFile(path + "/" + c.getId())) {
                        filePath = path + "/" + c.getId();
                    } else {
                        action.editOriginal("Could not create file").queue();
                        return;
                    }
                    saveMessageHistory(filePath, c.getIterableHistory(), (a, b) -> {
                        amountOkError[b == null ? 0 : 1] ++;
                        action.editOriginal(String.format("%d ok / %d error", amountOkError[0], amountOkError[1])).queue();
                        c.sendMessage(b == null ? "Saved this channel" : "Failed to save this channel").queue();
                    });
                }
                
                return;
            } else {
               if (tryCreateFile(path + "/" + channel.getName())) {
                    filePath = path + "/" + channel.getName();
                } else if (tryCreateFile(path + "/" + channel.getId())) {
                    filePath = path + "/" + channel.getId();
                } else {
                    action.editOriginal("Could not create file").queue();
                    return;
                }
                saveMessageHistory(filePath, channel.getIterableHistory(), (a, b) -> {
                    if(b == null){
                        action.editOriginal("done").queue();
                    } else {
                        action.editOriginal("error").queue();
                    }
                });
                return;
            }
        }

        }
    }

}
