package pl.psso.transport.domain;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.psso.transport.api.RouteDtos.LocationPair;
import pl.psso.transport.api.RouteDtos.RouteResult;
import pl.psso.transport.data.SegmentRepository;

@Service
@Transactional(readOnly = true)
public class RouteService {

    private final SegmentRepository repository;

    public RouteService(SegmentRepository repository) {
        this.repository = repository;
    }

    /** Zwraca wynik dla każdej pary, w tej samej kolejności co w żądaniu. */
    public List<RouteResult> resolve(TravelMode mode, List<LocationPair> pairs) {
        return pairs.stream().map(pair -> resolve(mode, pair)).toList();
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
