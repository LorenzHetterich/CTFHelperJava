package ctfdapi;

import com.google.gson.annotations.SerializedName;

public class CTFdAttemptResponseData {

    @SerializedName("message")
    public String message;

    @SerializedName("status")
    public String status;
}