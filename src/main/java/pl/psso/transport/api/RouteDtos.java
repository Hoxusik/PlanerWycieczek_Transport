package pl.psso.transport.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import pl.psso.transport.domain.TravelMode;

/** Obiekty żądania i odpowiedzi endpointu POST /api/v1/routes. */
public final class RouteDtos {

    private RouteDtos() {
    }

    public record RouteRequest(
            @NotNull TravelMode mode,
            @NotEmpty List<@Valid @NotNull LocationPair> pairs) {
    }

    public record LocationPair(
            @NotBlank String fromLocationId,
            @NotBlank String toLocationId) {
    }

    public record RouteResponse(TravelMode mode, List<RouteResult> results) {
    }

    public enum RouteStatus {
        /** Połączenie istnieje, durationMin i costGrosze są wypełnione. */
        OK,
        /** Brak połączenia między punktami w danym trybie (to NIE jest awaria serwisu). */
        NO_CONNECTION
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RouteResult(
            String fromLocationId,
            String toLocationId,
            RouteStatus status,
            Integer durationMin,
            Integer costGrosze) {

        public static RouteResult ok(String from, String to, int durationMin, int costGrosze) {
            return new RouteResult(from, to, RouteStatus.OK, durationMin, costGrosze);
        }

        public static RouteResult noConnection(String from, String to) {
            return new RouteResult(from, to, RouteStatus.NO_CONNECTION, null, null);
        }
    }

    /** Dostępne opcje dojazdu dla wskazanej pary lokalizacji. */
    public record TransportOptionsResponse(
            String fromLocationId,
            String toLocationId,
            List<TransportOption> options) {
    }

    /** Jeden dostępny sposób dotarcia: tryb, czas w minutach, koszt w groszach (w jedną stronę). */
    public record TransportOption(TravelMode mode, int durationMin, int costGrosze) {
    }

    public record ErrorResponse(String error, String message) {
    }
}
