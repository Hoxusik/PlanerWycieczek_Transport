package pl.psso.transport.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pl.psso.transport.domain.TravelMode;

public interface SegmentRepository extends JpaRepository<Segment, Long> {

    /** Szuka odcinka w obu kierunkach (odcinki są symetryczne). */
    @Query("""
            select s from Segment s
            where s.mode = :mode
              and ((s.fromLocationId = :a and s.toLocationId = :b)
                or (s.fromLocationId = :b and s.toLocationId = :a))
            """)
    List<Segment> findBothDirections(@Param("a") String a, @Param("b") String b, @Param("mode") TravelMode mode);

    default Optional<Segment> findSegment(String from, String to, TravelMode mode) {
        return findBothDirections(from, to, mode).stream().findFirst();
    }

    /** Wszystkie odcinki (we wszystkich trybach) między dwoma punktami, w obu kierunkach. */
    @Query("""
            select s from Segment s
            where (s.fromLocationId = :a and s.toLocationId = :b)
               or (s.fromLocationId = :b and s.toLocationId = :a)
            """)
    List<Segment> findAllModesBetween(@Param("a") String a, @Param("b") String b);

    @Query("""
            select distinct id from (
              select s.fromLocationId as id from Segment s
              union
              select s.toLocationId as id from Segment s
            ) order by id
            """)
    List<String> findAllLocationIds();
}
