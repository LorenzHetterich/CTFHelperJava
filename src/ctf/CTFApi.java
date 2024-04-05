package ctf;

import com.google.gson.annotations.JsonAdapter;

import utils.AbstractClassSerializer;
import utils.AbstractSerializable;
import java.util.List;

@JsonAdapter(AbstractClassSerializer.class)
public abstract class CTFApi extends AbstractSerializable {

    public abstract List<String> getChallengeIds();
    public abstract CTFChallenge getChallenge(String id);

    // overwrite if there is a more efficient way to do this with less requests!
    // TODO: maybe put a ? extends here
    public List<CTFChallenge> getChallenges() {
        return getChallengeIds().stream().map(this::getChallenge).toList();
    }
    
}
