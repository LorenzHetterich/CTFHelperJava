package ctf;

import java.util.List;

public interface CTFChallenge {

    // if no url is applicable, return empty string
    public String getUrl();

    // if no name is applicable, return a unique identifier
    public String getName();

    // if no description available, just leave empty
    public String getDescription();

    // if no category available, just leave empty
    public String getCategory();

    // if not available, just return 1
    public int getDifficulty();

    // maximum amount of attempts. -1 for infinite
    public int getAttempts();

    // current amount of points
    public int getPoints();

    // current amount of solves. -1 if not available
    public int getSolves();

    // whether the challenge is solved by current team
    public boolean isSolved();

    // get associated files. return empty list if it doesn't apply
    public List<? extends CTFFile> getFiles();

    // refresh challenge data
    public boolean refresh(); 

    // submit a flag. returns true if solved
    public boolean submitFlag(String flag);

}
