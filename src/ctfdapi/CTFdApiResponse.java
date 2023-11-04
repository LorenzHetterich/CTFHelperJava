package ctfdapi;

import java.util.Optional;

public class CTFdApiResponse<T> {

	public final boolean success;
	public final Optional<Throwable> exception;
	public final Optional<T> data;
	
	public CTFdApiResponse(Throwable exception) {
		this.exception = Optional.ofNullable(exception);
		this.data = Optional.empty();
		this.success = false;
	}
	
	public CTFdApiResponse(T data) {
		this.success = true;
		this.data = Optional.ofNullable(data);
		this.exception = Optional.empty();
	}
	
}
