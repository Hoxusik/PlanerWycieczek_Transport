package pl.psso.transport.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pl.psso.transport.domain.TravelMode;

/**
 * Wypełnia bazę H2 danymi z pliku CSV przy starcie.
 * Format wiersza: fromLocationId,toLocationId,mode,durationMin,costGrosze
 * Linie puste i zaczynające się od '#' są pomijane.
 *
 * transport.reset-data-on-start=true  -> zawsze czyści tabelę i wczytuje CSV od nowa
 * transport.reset-data-on-start=false -> wczytuje CSV tylko, gdy tabela jest pusta
 *                                       (zmiany zrobione w konsoli H2 przetrwają restart)
 */
@Component
public class SegmentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SegmentSeeder.class);

    private final SegmentRepository repository;
    private final Resource seedFile;
    private final boolean resetOnStart;

    public SegmentSeeder(SegmentRepository repository,
                         @Value("${transport.seed-file}") Resource seedFile,
                         @Value("${transport.reset-data-on-start}") boolean resetOnStart) {
        this.repository = repository;
        this.seedFile = seedFile;
        this.resetOnStart = resetOnStart;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (resetOnStart) {
            repository.deleteAllInBatch();
        } else if (repository.count() > 0) {
            log.info("Baza zawiera już {} odcinków, pomijam wczytywanie {}", repository.count(), seedFile);
            return;
        }
        List<Segment> segments = parse(seedFile);
        repository.saveAll(segments);
        log.info("Wczytano {} odcinków z {}", segments.size(), seedFile);
    }

    static List<Segment> parse(Resource file) throws IOException {
        List<Segment> result = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("fromLocationId")) {
                    continue;
                }
                String[] p = line.split(",");
                if (p.length != 5) {
                    throw new IllegalStateException("Błędny wiersz " + lineNo + " w " + file + ": " + line);
                }
                result.add(new Segment(p[0].strip(), p[1].strip(), TravelMode.valueOf(p[2].strip()),
                        Integer.parseInt(p[3].strip()), Integer.parseInt(p[4].strip())));
            }
        }
        return result;
    }
}
