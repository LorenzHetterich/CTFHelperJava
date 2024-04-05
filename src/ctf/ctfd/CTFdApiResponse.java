package ctf.ctfd;

import java.util.Optional;
import java.util.function.Function;

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

	public T get() {
		if (success) {
			return data.orElseGet(() -> null);
		}
		if (exception.isPresent()) {
			throw new RuntimeException("Api Request was not successfull", exception.get());
		}
		throw new RuntimeException("Api Request was not successfull");
	}

	public <D> CTFdApiResponse<D> map(Function<T, CTFdApiResponse<D>> func) {
		try {
			return func.apply(get());
		} catch (Throwable t) {
			return new CTFdApiResponse<D>(t);
		}
	}
}
