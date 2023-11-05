package ctfdapi;

import java.lang.reflect.Type;
import java.util.HashMap;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(CTFdChallengeTypesResponseData.Adapter.class)
public class CTFdChallengeTypesResponseData extends HashMap<String, CTFdChallenge.Type>{

    public static class Adapter implements JsonDeserializer<CTFdChallengeTypesResponseData>{

        @Override
        public CTFdChallengeTypesResponseData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            CTFdChallengeTypesResponseData result = new CTFdChallengeTypesResponseData();
            
            for (Entry<String,JsonElement> entry : json.getAsJsonObject().entrySet()) {
                result.put(entry.getKey(), context.deserialize(entry.getValue(), CTFdChallenge.Type.class));
            }

            return result;
        }

    }
    
}