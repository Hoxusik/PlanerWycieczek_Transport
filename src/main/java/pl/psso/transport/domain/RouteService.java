package pl.psso.transport.domain;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.psso.transport.api.RouteDtos.LocationPair;
import pl.psso.transport.api.RouteDtos.RouteResult;
import pl.psso.transport.api.RouteDtos.TransportOption;
import pl.psso.transport.data.Segment;
import pl.psso.transport.data.SegmentRepository;

@Service
@Transactional(readOnly = true)
public class RouteService {

    /** locationId jedynego punktu startowego wycieczki. */
    public static final String START = "start";

    private final SegmentRepository repository;

    public RouteService(SegmentRepository repository) {
        this.repository = repository;
    }

    /** Zwraca wynik dla każdej pary, w tej samej kolejności co w żądaniu. */
    public List<RouteResult> resolve(TravelMode mode, List<LocationPair> pairs) {
        return pairs.stream().map(pair -> resolve(mode, pair)).toList();
    }

    /**
     * Wszystkie dostępne tryby dotarcia ze startu do danej lokalizacji (np. atrakcji),
     * posortowane wg kolejności trybów: WALK, PUBLIC_TRANSPORT, CAR.
     * Tryby bez odcinka są pomijane; nieznany locationId daje pustą listę.
     */
    public List<TransportOption> optionsFromStart(String locationId) {
        if (START.equals(locationId)) {
            return List.of();
        }
        return repository.findAllModesBetween(START, locationId).stream()
                .sorted(Comparator.comparing(Segment::getMode))
                .map(s -> new TransportOption(s.getMode(), s.getDurationMin(), s.getCostGrosze()))
                .toList();
    }

    private RouteResult resolve(TravelMode mode, LocationPair pair) {
        String from = pair.fromLocationId();
        String to = pair.toLocationId();
        if (from.equals(to)) {
            return RouteResult.ok(from, to, 0, 0);
        }
        return repository.findSegment(from, to, mode)
                .map(s -> RouteResult.ok(from, to, s.getDurationMin(), s.getCostGrosze()))
                .orElseGet(() -> RouteResult.noConnection(from, to));
    }
}
