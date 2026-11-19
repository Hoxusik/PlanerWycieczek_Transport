-- Dane startowe tabeli odcinków serwisu Transport (dane syntetyczne).
-- Spring Boot wykonuje ten plik przy KAŻDYM starcie: tabela jest czyszczona i wypełniana od nowa,
-- więc baza zawsze odpowiada temu plikowi z repo. Zmiany na stałe wprowadzaj TUTAJ.
--
-- Model: jeden punkt startowy 'start'. Trzymamy tylko odcinki start <-> atrakcja i start <-> lokal.
--  - czas w minutach, koszt w groszach (na 1 osobę)
--  - odcinki są symetryczne: start -> X = X -> start (jeden wiersz na parę i tryb)
--  - brak wiersza = NO_CONNECTION (dotyczy też par bez 'start', np. atrakcja -> lokal)
-- Tryby: WALK (0 gr, max 3,5 km), PUBLIC_TRANSPORT (bilet 480 gr), CAR (taxi: 900 gr + 350 gr/km)
-- locationId są TYMCZASOWE: podmienić na identyfikatory od Atrakcji i Gastronomii.

DELETE FROM segment;

INSERT INTO segment (from_location_id, to_location_id, mode, duration_min, cost_grosze) VALUES
-- start <-> atrakcje
('start', 'attr-01', 'WALK', 15, 0),
('start', 'attr-01', 'PUBLIC_TRANSPORT', 9, 480),
('start', 'attr-01', 'CAR', 6, 1320),
('start', 'attr-02', 'WALK', 21, 0),
('start', 'attr-02', 'PUBLIC_TRANSPORT', 11, 480),
('start', 'attr-02', 'CAR', 7, 1495),
('start', 'attr-03', 'WALK', 28, 0),
('start', 'attr-03', 'PUBLIC_TRANSPORT', 12, 480),
('start', 'attr-03', 'CAR', 8, 1705),
('start', 'attr-04', 'WALK', 29, 0),
('start', 'attr-04', 'PUBLIC_TRANSPORT', 13, 480),
('start', 'attr-04', 'CAR', 8, 1740),
('start', 'attr-05', 'PUBLIC_TRANSPORT', 17, 480),
('start', 'attr-05', 'CAR', 11, 2230),
('start', 'attr-06', 'PUBLIC_TRANSPORT', 28, 480),
('start', 'attr-06', 'CAR', 19, 3560),
-- start <-> lokale
('start', 'rest-01', 'WALK', 9, 0),
('start', 'rest-01', 'PUBLIC_TRANSPORT', 8, 480),
('start', 'rest-01', 'CAR', 5, 1145),
('start', 'rest-02', 'WALK', 22, 0),
('start', 'rest-02', 'PUBLIC_TRANSPORT', 11, 480),
('start', 'rest-02', 'CAR', 7, 1530),
('start', 'rest-03', 'WALK', 34, 0),
('start', 'rest-03', 'PUBLIC_TRANSPORT', 14, 480),
('start', 'rest-03', 'CAR', 9, 1880),
('start', 'rest-04', 'PUBLIC_TRANSPORT', 26, 480),
('start', 'rest-04', 'CAR', 17, 3245);
