package ctf.rctf;

import java.util.Optional;
import java.util.function.Function;

public class RCTFApiResponse<T> {

	public final boolean success;
	public final Optional<Throwable> exception;
	public final Optional<T> data;

	public RCTFApiResponse(Throwable exception) {
		this.exception = Optional.ofNullable(exception);
		this.data = Optional.empty();
		this.success = false;
	}

	public RCTFApiResponse(T data) {
		this.success = true;
		this.data = Optional.ofNullable(data);
		this.exception = Optional.empty();
	}

	public T get() {
		if (success) {
			return data.orElseGet(() -> null);
		}
		if (exception.isPresent()) {
			throw new RuntimeException("Api Request was not successfull", exception.get());
		}
		throw new RuntimeException("Api Request was not successfull");
	}

	public <D> RCTFApiResponse<D> map(Function<T, RCTFApiResponse<D>> func) {
		try {
			return func.apply(get());
		} catch (Throwable t) {
			return new RCTFApiResponse<D>(t);
		}
	}
}
