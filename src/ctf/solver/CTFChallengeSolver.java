package ctf.solver;

import java.util.List;
import java.util.Optional;

import ctf.CTFChallenge;

public interface CTFChallengeSolver {
    
    public List<String> attempt(CTFChallenge challenge);

    public default Optional<String> solve(CTFChallenge challenge, int triesToLeave, boolean solveSolved){
        if(!solveSolved && challenge.isSolved()){
            // no need to solve, already solved!
            return Optional.empty();
        }
        if(challenge.getAttempts() != -1 && challenge.getAttempts() < triesToLeave){
            // not enough attempts left
            return Optional.empty();
        }
        for(String possibleFlag : this.attempt(challenge)){
            if(challenge.submitFlag(possibleFlag)){
                return Optional.of(possibleFlag);
            }
            if(challenge.getAttempts() != -1 && challenge.getAttempts() < triesToLeave){
                // not enough attempts left
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
