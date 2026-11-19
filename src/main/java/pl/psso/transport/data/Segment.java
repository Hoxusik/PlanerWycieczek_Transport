package pl.psso.transport.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import pl.psso.transport.domain.TravelMode;

/**
 * Jeden odcinek tabeli (tabela SEGMENT w H2): czas w minutach, koszt w groszach.
 * W praktyce zawsze start <-> atrakcja albo start <-> lokal.
 * Odcinki są symetryczne: A -> B kosztuje i trwa tyle samo co B -> A,
 * więc wystarczy jeden wiersz na parę i tryb. Dane startowe: data.sql.
 */
@Entity
@Table(name = "segment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"from_location_id", "to_location_id", "mode"}))
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_location_id", nullable = false, length = 64)
    private String fromLocationId;

    @Column(name = "to_location_id", nullable = false, length = 64)
    private String toLocationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 32)
    private TravelMode mode;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "cost_grosze", nullable = false)
    private int costGrosze;

    protected Segment() {
    }

    public Segment(String fromLocationId, String toLocationId, TravelMode mode, int durationMin, int costGrosze) {
        this.fromLocationId = fromLocationId;
        this.toLocationId = toLocationId;
        this.mode = mode;
        this.durationMin = durationMin;
        this.costGrosze = costGrosze;
    }

    public Long getId() {
        return id;
    }

    public String getFromLocationId() {
        return fromLocationId;
    }

    public String getToLocationId() {
        return toLocationId;
    }

    public TravelMode getMode() {
        return mode;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public int getCostGrosze() {
        return costGrosze;
    }
}
