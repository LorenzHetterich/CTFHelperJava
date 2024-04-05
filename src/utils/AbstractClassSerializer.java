package utils;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;

public class AbstractClassSerializer<T> implements JsonDeserializer<T>{

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if(!json.isJsonObject()){
            throw new JsonParseException("AbstractClassSerializer requires JsonObject");
        }
        JsonObject obj = json.getAsJsonObject();
        try{
            Class<?> t = Class.forName(obj.get("class").getAsString());
            return context.deserialize(obj, t);
        } catch(Exception e){
            throw new JsonParseException("Failed to deserialize", e);
        }
    }

}
