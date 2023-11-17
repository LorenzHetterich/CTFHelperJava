package discord;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import ctf.ctfd.CTFdCTFAdapter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
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
        this.jda.updateCommands().addCommands(
                Commands.slash("ctfd-start", "creates a new CTFd CTF")
                        .addOption(OptionType.STRING, "name", "CTF category name", true)
                        .addOption(OptionType.STRING, "endpoint", "endpoint", true)
                        .addOption(OptionType.STRING, "cookies", "Cookies", false)
                        .addOption(OptionType.STRING, "username", "username", false)
                        .addOption(OptionType.STRING, "password", "password", false)
                        .addOption(OptionType.STRING, "pattern", "flag pattern", false),
                Commands.slash("ctfd-login", "re-login into ctfd")
                        .addOption(OptionType.STRING, "username", "username", true)
                        .addOption(OptionType.STRING, "password", "password", true),
                Commands.slash("ctfd-cookie", "add cookie to ctfd").addOption(OptionType.STRING, "cookies", "cookies",
                        true),
                Commands.slash("ctfd-flag", "submit flag for current challenge")
                        .addOption(OptionType.STRING, "flag", "flag", true),
                Commands.slash("ctfd-challenge", "refresh current challenge"),
                Commands.slash("ctfd-challenges", "refresh challenges"),
                Commands.slash("ctfd-archive", "archive all messages within ctf channels of this ctf"),
                Commands.slash("ctfd-end", "nuke all channels associated with the CTF")).queue();
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
            }).thenAccept(_ignored -> {
                Collections.reverse(messages);
                for (Message m : messages) {
                    out.printf("[%s][%s]: %s\n", m.getTimeCreated(), m.getAuthor().getEffectiveName(),
                            m.getContentDisplay());
                }
            }).whenComplete(onDone.andThen((a, b) -> out.close()));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static boolean tryCreateDir(String path) {
        File f = new File(path);
        if (f.exists() && f.isDirectory()) {
            return true;
        }
        return f.mkdir();
    }

    public static boolean tryCreateFile(String path) {
        File f = new File(path);
        if (f.exists() && f.isFile()) {
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
        if (!event.isFromGuild())
            return;
        Category category = findCategory(event.getGuild(), event.getChannel());
        if (category == null)
            return;
        int idx = category.getName().lastIndexOf("-");
        String name = category.getName();
        if (idx != -1) {
            name = category.getName().substring(0, idx);
        }

        DiscordBotCTF ctf = this.ctfs.getOrDefault(name, this.ctfs.getOrDefault(category.getName(), null));
        if (ctf == null)
            return;
        ctf.onMessage(this, event);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        MessageChannelUnion channel = event.getChannel();

        InteractionHook action = event.reply("working . . .").complete();

        if (!hasRole(member, guild, "CTF")) {
            action.editOriginal("You don't have the CTF role, get r3al!").queue();
            return;
        }

        try {
            switch (event.getName()) {

            case "ctfd-cookie":
            case "ctfd-challenge":
            case "ctfd-challenges":
            case "ctfd-archive":
            case "ctfd-end":
            case "ctfd-flag":
            case "ctfd-login": {
                if (!event.isFromGuild()) {
                    action.editOriginal("only works on servers in a ctf channel").queue();
                    return;
                }

                Category category = findCategory(event.getGuild(), event.getChannel());
                if (category == null) {
                    action.editOriginal("no category found").queue();
                    return;
                }
                int idx = category.getName().lastIndexOf("-");
                String name = category.getName();
                if (idx != -1) {
                    name = category.getName().substring(0, idx);
                }

                DiscordBotCTF ctf = this.ctfs.getOrDefault(name, this.ctfs.getOrDefault(category.getName(), null));
                if (ctf == null) {
                    action.editOriginal("Only works in CTF channel").queue();
                    return;
                }

                switch (event.getName()) {
                case "ctfd-cookie": {
                    String cookies = event.getOption("cookies").getAsString();
                    for (String cookie : cookies.split(";")) {
                        if (!cookie.contains("=")) {
                            continue;
                        }
                        String cookieName = cookie.split("=")[0].trim();
                        String cookieValue = cookie.split("=")[1].trim();
                        ((CTFdCTFAdapter)ctf.ctf).ctfd.addCookie(URLDecoder.decode(cookieName, StandardCharsets.UTF_8),
                                URLDecoder.decode(cookieValue, StandardCharsets.UTF_8));
                        System.out.printf("Added cookie: %s=%s\n", cookieName, cookieValue);
                    }
                    action.editOriginal("probably ok").queue();
                    break;
                }

                case "ctfd-challenge": {
                    if(ctf.refreshChallenge(channel.getId())){
                        action.editOriginal("probably ok").queue();
                    } else {
                        action.editOriginal("probably challenge not found").queue();
                    }
                    break;
                }
                case "ctfd-challenges": {
                    ctf.refreshChallenges();
                    action.editOriginal("probably ok").queue();
                    break;
                }
                case "ctfd-archive": {
                    ctf.save();
                    action.editOriginal("probably ok").queue();
                    break;
                }
                case "ctfd-end": {
                    ctf.save();
                    ctf.destroy();
                    break;
                }
                case "ctfd-flag": {
                    if(ctf.submitFlag(channel.getId(), event.getOption("flag").getAsString())){
                        action.editOriginal("probably ok").queue();
                    } else {
                        action.editOriginal("probably wrong flag or challenge not found").queue();
                    }
                    break;
                }
                case "ctfd-login": {
                    if (ctf.ctf.login(event.getOption("username").getAsString(), event.getOption("password").getAsString())) {
                        action.editOriginal("Probably ok").queue();
                    } else {
                        action.editOriginal("probably invalid username or password").queue();
                    }
                    break;
                }

                default: {
                    action.editOriginal("WTF happened here?!").queue();
                    break;
                }
                }
                break;
            }

            case "ctfd-start": {
                String name = event.getOption("name", null, OptionMapping::getAsString);

                if (this.ctfs.containsKey(name)) {
                    action.editOriginal("ctf with that name already started!").queue();
                    return;
                }

                String flagPattern = event.getOption("pattern", null, OptionMapping::getAsString);
                String endpoint = event.getOption("endpoint", null, OptionMapping::getAsString);
                String username = event.getOption("username", null, OptionMapping::getAsString);
                String password = event.getOption("password", null, OptionMapping::getAsString);
                String cookies = event.getOption("cookies", null, OptionMapping::getAsString);

                CTFdCTFAdapter ctf = new CTFdCTFAdapter(endpoint, flagPattern);
                ctf.ctfd.setUserAgent(
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36");
                if (cookies != null) {
                    for (String cookie : cookies.split(";")) {
                        if (!cookie.contains("=")) {
                            continue;
                        }
                        String cookieName = cookie.split("=")[0].trim();
                        String cookieValue = cookie.split("=")[1].trim();
                        ctf.ctfd.addCookie(URLDecoder.decode(cookieName, StandardCharsets.UTF_8),
                                URLDecoder.decode(cookieValue, StandardCharsets.UTF_8));
                        System.out.printf("Added cookie: %s=%s\n", cookieName, cookieValue);
                    }
                }

                if (username != null && password != null) {
                    // login with provided credentials
                    if (!ctf.login(username, password)) {
                        action.editOriginal("login failed!").queue();
                        return;
                    }
                }

                if (cookies == null && (username == null || password == null)) {
                    action.editOriginal("either session cookie(s) or username + password must be provided").queue();
                    return;
                }

                action.editOriginal("probably ok").queue();

                DiscordBotCTF dctf = new DiscordBotCTF(guild, name, ctf);
                this.ctfs.put(name, dctf);
                break;
            }
            }
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            String tStr = sw.toString();
            if (tStr.length() > 1500) {
                tStr = tStr.substring(0, 1500);
            }
            action.editOriginal("Exception: \n" + tStr);
        }
    }

}
