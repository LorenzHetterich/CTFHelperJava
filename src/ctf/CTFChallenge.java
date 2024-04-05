package ctf;

import com.google.gson.annotations.JsonAdapter;

import utils.AbstractClassSerializer;
import utils.AbstractSerializable;
import java.util.List;

@JsonAdapter(AbstractClassSerializer.class)
public abstract class CTFChallenge extends AbstractSerializable implements CTFApiPart {

    public abstract int getId();
    public abstract String getName();
    public abstract String getCategory();
    public abstract String getDescription();
    public abstract List<String> getFiles();
    public abstract boolean submitFlag(String flag);
    public abstract boolean isSolved();
    public abstract void update();
    public abstract String getUrl();

}