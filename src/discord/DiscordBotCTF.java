package discord;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.html.Option;

import ctf.CTF;
import ctf.CTFChallenge;
import ctf.ctfd.CTFdCTFAdapter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class DiscordBotCTF {
    
    private String toChannelName(String s){
        StringBuilder result = new StringBuilder();
        for(char c : s.toLowerCase().replace(" ", "-").toCharArray()){
            if("abcdefghjiklmnopqrstuvwxyz-0123456789".contains(c + "")){
                result.append(c);
            }
        }
        if(result.isEmpty()){
            return "unnamed";
        }
        return result.toString();
    }

    private class Challenge {
        public CTFChallenge challenge;
        public TextChannel channel;
        public Message message;
        public Optional<String> flag;
        public boolean solved;

        public void updateMessageContent(){
            this.message.editMessage(
                String.format("<b>%s</b>\nCategory: %s\nDifficulty: %s\n", challenge.getName(), challenge.getCategory(), "*".repeat(challenge.getDifficulty()))
            );
        }
        
        public void update(){
            this.challenge.refresh();
            if(challenge.isSolved() && !solved){
                // now it is solved, yey!
                channel.getManager().setParent(solvedChallenges).queue();
                message.delete().queue();
                message = solvedChallengesChannel.sendMessage("blubb").complete();
                updateMessageContent();
            }
        }

        public Challenge(CTFChallenge ctf){
            this.challenge = ctf;
            this.channel = unsolvedChallenges.createTextChannel(toChannelName(ctf.getName())).complete();
            this.flag = Optional.empty();
            this.solved = false;
            this.message = unsolvedChallengesChannel.sendMessage("\nTODO: make this look nice and stuff").complete();
            this.updateMessageContent();
        }
    }

    private CTF ctf;
    private String name;

    private Map<String, Challenge> challengesByName;
    private Category solvedChallenges;
    private Category unsolvedChallenges;
    private Category general;

    private TextChannel solvedChallengesChannel;
    private TextChannel unsolvedChallengesChannel;
    private TextChannel generalChannel;

    private VoiceChannel generalVoice;

    public DiscordBotCTF(Guild server, String CTFname, CTF ctf){
        this.ctf = ctf;
        this.name = CTFname;
        this.challengesByName = new HashMap<>();
        this.general = server.createCategory(CTFname).complete();
        this.unsolvedChallenges = server.createCategory(String.format("%s-%s", CTFname, "challenges")).complete();
        this.solvedChallenges = server.createCategory(String.format("%s-%s", CTFname, "solved")).complete();
        this.generalChannel = this.general.createTextChannel("general").complete();
        // TODO: don't let others write to channel
        this.solvedChallengesChannel = this.general.createTextChannel("solved").complete();
        this.unsolvedChallengesChannel = this.general.createTextChannel("unsolved").complete();
        this.generalVoice = this.general.createVoiceChannel("general").complete();
    }

    public void onMessage(DiscordBot bot, MessageReceivedEvent event){
        if(event.getAuthor().isBot()){
            // TODO: allow other bots I guess
            return;
        }
        String channelId = event.getChannel().getId();
        if(channelId.equals(generalChannel.getId())){
            // TODO: maybe some commands?
            if(event.getMessage().getContentDisplay().contains("&refresh challenges")){
                this.refreshChallenges();
            }
            if(event.getMessage().getContentDisplay().contains("&save ctf")){
                this.save();
            }
            if(event.getMessage().getContentDisplay().contains("&destroy ctf")){
                this.destroy();
                bot.ctfs.remove(this.name);
            }
        } else {
            if(this.ctf.getFlagRegex().isEmpty()){
                return;
            }
            for(Challenge c : this.challengesByName.values()){
                if(c.channel.getId().equals(channelId)){
                    if(c.solved){
                        return;
                    }
                    Pattern p = Pattern.compile(this.ctf.getFlagRegex().get());

                    Matcher m = p.matcher(event.getMessage().getContentDisplay());
                    while(m.find()){
                        if(c.challenge.submitFlag(m.group(0))){
                            event.getMessage().reply("Flag " + m.group(0) + " was correct!").queue();
                            c.update();
                            return;
                        } else {
                            event.getMessage().reply("Flag " + m.group(0) + " was incorrect!").queue();
                        }
                    }

                    // no flags in message, ignore it
                    return;
                }
            }
        }

        
    }

    public void save(){

        String dir = general.getName() + "/";

        new File(dir).mkdir();

        for(Challenge c : this.challengesByName.values()){
            String path;
            if(DiscordBot.tryCreateFile(dir + c.challenge.getName() + ".log")){
                path = dir + c.challenge.getName() + ".log";
            } else if(DiscordBot.tryCreateFile(dir + c.channel.getName() + ".log")) {
                path = dir + c.channel.getName() + ".log";
            } else if(DiscordBot.tryCreateFile(dir + c.channel.getId() + ".log")){
                path = dir + c.channel.getId() + ".log";
            } else {
                System.err.printf("Could not save channel for %s\n", c.challenge.getName());
                continue;
            }
            DiscordBot.saveMessageHistory(path, c.channel.getIterableHistory(), (a,b) -> System.out.printf("Saved channel for %s\n", c.challenge.getName()));
        }

        DiscordBot.saveMessageHistory(dir + "general.log", generalChannel.getIterableHistory(), (a,b) -> {});
    }

    public void destroy(){
        for(Channel c : general.getChannels()){
            c.delete().queue();
        }
        for(Channel c : unsolvedChallenges.getChannels()){
            c.delete().queue();
        }
        for(Channel c : solvedChallenges.getChannels()){
            c.delete().queue();
        }
        general.delete().queue();
        unsolvedChallenges.delete().queue();
        solvedChallenges.delete().queue();
    }

    public void refreshChallenges(){
        for(CTFChallenge c : ctf.getChallenges()){
            challengesByName.computeIfAbsent(c.getName(), x -> new Challenge(c)).update();
        }
    }

}
