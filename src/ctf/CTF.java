package ctf;

import java.util.List;
import java.util.Optional;

public interface CTF {

    // try to login. Return true on success
    public boolean login(String username, String password);

    // get all challenges
    public List<? extends CTFChallenge> getChallenges();

    // flag regex (if available)
    public Optional<String> getFlagRegex();

}
