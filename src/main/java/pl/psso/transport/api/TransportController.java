package pl.psso.transport.api;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pl.psso.transport.api.RouteDtos.RouteRequest;
import pl.psso.transport.api.RouteDtos.RouteResponse;
import pl.psso.transport.api.RouteDtos.TransportOptionsResponse;
import pl.psso.transport.data.SegmentRepository;
import pl.psso.transport.domain.RouteService;
import pl.psso.transport.domain.TravelMode;

@RestController
@RequestMapping("/api/v1")
public class TransportController {

    private final RouteService routeService;
    private final SegmentRepository repository;
    private final int maxPairs;

    public TransportController(RouteService routeService, SegmentRepository repository,
                               @Value("${transport.max-pairs}") int maxPairs) {
        this.routeService = routeService;
        this.repository = repository;
        this.maxPairs = maxPairs;
    }

    /** Główna operacja: czas i koszt dla listy par lokalizacji w danym trybie. */
    @PostMapping("/routes")
    public RouteResponse routes(@Valid @RequestBody RouteRequest request) {
        if (request.pairs().size() > maxPairs) {
            throw new IllegalArgumentException("Za dużo par w jednym żądaniu (max " + maxPairs + ")");
        }
        return new RouteResponse(request.mode(), routeService.resolve(request.mode(), request.pairs()));
    }

    /**
     * Wszystkie dostępne sposoby dotarcia ze startu do lokalizacji (np. atrakcji)
     * z czasem i kosztem. Brak połączenia / nieznane ID = 200 z pustą listą options.
     */
    @GetMapping("/locations/{locationId}/transport-options")
    public TransportOptionsResponse transportOptions(@PathVariable String locationId) {
        return new TransportOptionsResponse(RouteService.START, locationId,
                routeService.optionsFromStart(locationId));
    }

    @GetMapping("/locations/{fromLocationId}/to/{toLocationId}/transport-options")
    public TransportOptionsResponse transportOptionsBetween(
            @PathVariable String fromLocationId, @PathVariable String toLocationId) {
        if (fromLocationId.isBlank() || toLocationId.isBlank()) {
            throw new IllegalArgumentException("Identyfikatory lokalizacji muszą być niepuste");
        }
        if (RouteService.START.equals(fromLocationId) || RouteService.START.equals(toLocationId)) {
            throw new IllegalArgumentException("Ta operacja wymaga dwóch lokalizacji poza startem");
        }
        if (fromLocationId.equals(toLocationId)) {
            throw new IllegalArgumentException("Lokalizacja początkowa i docelowa muszą być różne");
        }
        return new TransportOptionsResponse(fromLocationId, toLocationId,
                routeService.optionsBetween(fromLocationId, toLocationId));
    }

    /** Pomocniczo: dostępne tryby przemieszczania się. */
    @GetMapping("/modes")
    public List<TravelMode> modes() {
        return Arrays.asList(TravelMode.values());
    }

    /** Pomocniczo: locationId występujące w tabeli odcinków. */
    @GetMapping("/locations")
    public List<String> locations() {
        return repository.findAllLocationIds();
    }
}
