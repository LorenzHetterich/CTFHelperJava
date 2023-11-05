package ctfdapi;

import com.google.gson.annotations.SerializedName;

public class CTFdFile {

    @SerializedName("id")
    public int id;

    @SerializedName("type")
    public String type;

    @SerializedName("location")
    public String location;
}
