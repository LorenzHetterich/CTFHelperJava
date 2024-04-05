package utils;

import com.google.gson.annotations.SerializedName;

public class AbstractSerializable {
    
    @SerializedName("class")
    private String implementingClass;

    protected AbstractSerializable(){
        this.implementingClass = this.getClass().getName();
    }
}
