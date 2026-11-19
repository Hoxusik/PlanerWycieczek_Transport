package pl.psso.transport.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pl.psso.transport.api.RouteDtos.ErrorResponse;

/**
 * 400 = błędne żądanie klienta (brak pól, nieznany tryb, za dużo par).
 * 500 = awaria Transportu.
 * Brak połączenia między punktami NIE jest błędem: to 200 ze statusem NO_CONNECTION.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(MethodArgumentNotValidException e) {
        var first = e.getBindingResult().getFieldErrors().stream().findFirst();
        String msg = first.map(f -> f.getField() + ": " + f.getDefaultMessage()).orElse("Niepoprawne żądanie");
        return new ErrorResponse("BAD_REQUEST", msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadable(HttpMessageNotReadableException e) {
        return new ErrorResponse("BAD_REQUEST",
                "Niepoprawny JSON albo nieznany tryb (dozwolone: WALK, PUBLIC_TRANSPORT, CAR)");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse illegalArgument(IllegalArgumentException e) {
        return new ErrorResponse("BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse internal(Exception e) {
        log.error("Nieobsłużony błąd", e);
        return new ErrorResponse("INTERNAL_ERROR", "Wewnętrzny błąd serwisu Transport");
    }
}
