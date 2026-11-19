# PlanerWycieczek_Transport

Mikroserwis **Transport** (zespół 3) w projekcie **Planer Wycieczek**. Zwraca czas i koszt przejazdu dla par lokalizacji w wybranym trybie przemieszczania się (HTTP + JSON).

> **Dla innych zespołów:** skrócony kontrakt API (co przyjmujemy i co zwracamy) jest w [KONTRAKT_API.md](KONTRAKT_API.md).
>
> Ten plik opisuje cały kontekst projektu. Można go w całości wkleić do prompta agenta AI, żeby wiedział, co budujemy, jakie są zasady i czego nie wolno zmieniać bez uzgodnienia z innymi zespołami.

---

## 1. Kontekst projektu

Projekt zaliczeniowy (PSSO). Pięć zespołów buduje wspólną aplikację **Planer Wycieczek**. Każdy zespół odpowiada za **jeden mikroserwis**, który działa na **osobnym komputerze laboratoryjnym w sieci lokalnej**.

- Najpierw jest 45 minut planowania i karta projektu, potem **Speedkathon**: 3 godziny na implementację i pokaz.
- Każdy zespół ma własne repozytorium i własny stos technologiczny. Nasz to **Java 21 + Spring Boot**.
- Serwisy komunikują się wyłącznie przez **HTTP + JSON**.
- **Każdy właściciel danych sam wybiera sposób ich przechowywania. Nikt nie czyta cudzej bazy.** Dane innych zespołów są dostępne tylko przez ich API.
- Architektura jest proponowana. Można ją doprecyzować, ale **zmiany kontraktu trzeba uzgodnić z zespołami, których dotyczą**.

### Co robi aplikacja

Jednodniowa wycieczka po jednym mieście w godzinach **10:00–16:00**.

**Wejście** (formularz Planera): data, budżet, zainteresowania, wymagania żywieniowe, sposób przemieszczania się.

**Wynik**: godziny wizyt, łączny koszt (atrakcje + posiłek + transport), pozostały budżet albo komunikat o braku pasującego planu.

**Kształt dnia:**

```
10:00 start → Atrakcja 1 → Posiłek → Atrakcja 2 (inna niż pierwsza) → powrót do startu przed 16:00
```

### Co musi pokazać demo

1. Po zmianie dnia pogodnego na deszczowy plan zawiera wyłącznie atrakcje pod dachem.
2. Gdy żaden plan nie mieści się w budżecie lub czasie, aplikacja pokazuje komunikat o braku pasującego planu.
3. Co najmniej jeden plan zgadza się z **ręcznie policzonym** czasem i kosztem, **łącznie z powrotem**.

---

## 2. Zespoły, serwisy i adresy w sieci laboratoryjnej

| # | Zespół | Adres (IP:port) | Posiada | Korzysta z |
|---|---|---|---|---|
| 1 | Atrakcje | `172.20.147.24` | katalog atrakcji: kategorie, lokalizacje, ceny, czasy zwiedzania, czy pod dachem | Warunki dnia, **Transport** |
| 2 | Gastronomia | `172.20.147.27` | lokale, posiłki, ceny, diety, czas wizyty | **Transport** |
| **3** | **Transport (my)** | **`172.20.147.17:8083`** | **tabela odcinków między lokalizacjami: czas i koszt dla par w danym trybie** | **nikogo** |
| 4 | Warunki dnia | `172.20.147.21` | przygotowane prognozy dla dat demonstracyjnych | nikogo |
| 5 | Planer i integracja | `172.20.147.15:8090` | formularz i widok planu, kolejność wizyt, kontrola budżetu i godzin; bez własnej bazy | Atrakcje, Gastronomia, **Transport** |

**Nasz serwis:** `http://172.20.147.17:8083`

Porty Atrakcji, Gastronomii i Warunków dnia: _do uzupełnienia_.

### Zależności (strzałka od klienta do dostawcy API)

```
                ┌──────────────┐  pogoda   ┌────────────────┐
         ┌────► │ 1. Atrakcje  │ ────────► │ 4. Warunki dnia│
propozycje      └──────────────┘           └────────────────┘
         │             │ od startu
┌────────┴───────┐     ▼
│ 5. Planer      │ ─────────────────────► ┌──────────────┐
│ i integracja   │   pozostałe odcinki    │ 3. TRANSPORT │
└────────┬───────┘                        └──────────────┘
         │ posiłki     ┌──────────────┐        ▲
         └───────────► │2. Gastronomia│ ───────┘ od startu
                       └──────────────┘
```

Transport i Warunki dnia nie zależą od nikogo, więc **jako pierwsze udostępniają przykładowe odpowiedzi**.

### Model odległości w Transporcie: wszystko liczone od jednego punktu `start`

W mieście jest **jeden punkt startowy** (`start`). Transport zna odległości **tylko**:
- **start ↔ atrakcja** (dojazd do atrakcji i powrót),
- **start ↔ lokal gastronomiczny** (dojazd do lokalu i powrót).

**Nie** liczymy odległości atrakcja ↔ atrakcja ani atrakcja ↔ lokal. Taka para dostaje odpowiedź `NO_CONNECTION`.

Kto o co pyta:
- **Atrakcje** pytają o `start → atrakcja` dla swoich atrakcji.
- **Gastronomia** pyta o `start → lokal` dla swoich lokali.
- **Planer** pyta o dowolne pary ze `start`, np. powrót `atrakcja → start`.

> Slajdy proponowały też odcinki atrakcja → lokal → atrakcja. Nasz zespół celowo zawęził zakres do odległości od startu. Ten model trzeba uzgodnić z Gastronomią i Planerem.

---

## 3. Nasza odpowiedzialność (zespół Transport)

- Przechowujemy **tabelę odcinków**: dla pary lokalizacji i trybu znamy czas w minutach i koszt w groszach.
- Udostępniamy **jedną operację HTTP**, wspólną dla wszystkich trzech klientów. Dostaje listę par `fromLocationId`–`toLocationId` i tryb, a zwraca dla każdej pary czas i koszt albo informację o braku połączenia.
- **Nie** nadajemy `locationId` atrakcjom ani lokalom. Robią to Atrakcje i Gastronomia, a my używamy ich identyfikatorów. Sami nadajemy tylko `locationId` punktu startowego (`start`).
- **Nie** wołamy żadnego innego serwisu. Pogoda nie wpływa na przejazdy.
- **Nie** układamy planu ani nie liczymy budżetu. To robi Planer.

### Zakres tabeli odcinków

| Odcinek | Kto o niego pyta | W tabeli? |
|---|---|---|
| start ↔ atrakcja | Atrakcje, Planer | tak |
| start ↔ lokal | Gastronomia, Planer | tak |
| atrakcja ↔ atrakcja | — | nie (`NO_CONNECTION`) |
| atrakcja ↔ lokal | — | nie (`NO_CONNECTION`) |
| lokal ↔ lokal | — | nie (`NO_CONNECTION`) |

---

## 4. Wspólne identyfikatory, jednostki i formaty

| Co | Format | Przykład |
|---|---|---|
| lokalizacja | `locationId`, string | `start`, `ATR_001`, `rest-01` |
| czas | minuty, liczba całkowita | `durationMin: 15` |
| koszt | **grosze**, liczba całkowita, **na 1 osobę** | `costGrosze: 480` = 4,80 zł |
| data | `YYYY-MM-DD` | `2026-11-20` (Transport nie używa daty) |
| tryb | enum, wielkie litery | `WALK`, `PUBLIC_TRANSPORT`, `CAR` |
| kodowanie | UTF-8, `Content-Type: application/json` | |

Założenia:
- Odcinki są **symetryczne**: A → B ma ten sam czas i koszt co B → A.
- Przejazd w obrębie tego samego punktu (A → A) zwraca `0` min i `0` gr.
- Tryby `WALK`, `PUBLIC_TRANSPORT` i `CAR` są tymczasowe, do potwierdzenia z Planerem.

---

## 5. Kontrakt API

Bazowy adres: `http://172.20.147.17:8083`

### 5.1 `POST /api/v1/routes`: czas i koszt dla listy par

**Dostawca:** Transport. **Klienci:** Atrakcje, Gastronomia, Planer.

Żądanie:

```http
POST /api/v1/routes
Content-Type: application/json
```

```json
{
  "mode": "WALK",
  "pairs": [
    { "fromLocationId": "start",   "toLocationId": "ATR_001" },
    { "fromLocationId": "ATR_001", "toLocationId": "start" },
    { "fromLocationId": "start",   "toLocationId": "rest-01" },
    { "fromLocationId": "start",   "toLocationId": "rest-04" },
    { "fromLocationId": "ATR_001", "toLocationId": "rest-01" }
  ]
}
```

Odpowiedź `200 OK`:

```json
{
  "mode": "WALK",
  "results": [
    { "fromLocationId": "start",   "toLocationId": "ATR_001", "status": "OK", "durationMin": 15, "costGrosze": 0 },
    { "fromLocationId": "ATR_001", "toLocationId": "start",   "status": "OK", "durationMin": 15, "costGrosze": 0 },
    { "fromLocationId": "start",   "toLocationId": "rest-01", "status": "OK", "durationMin": 9,  "costGrosze": 0 },
    { "fromLocationId": "start",   "toLocationId": "rest-04", "status": "NO_CONNECTION" },
    { "fromLocationId": "ATR_001", "toLocationId": "rest-01", "status": "NO_CONNECTION" }
  ]
}
```

W tym przykładzie `start → rest-04` daje `NO_CONNECTION`, bo pieszo jest za daleko. `ATR_001 → rest-01` też daje `NO_CONNECTION`, bo to para bez `start`, a takich nie liczymy.

Reguły:
- `mode` jest wymagany. `pairs` jest wymagane, niepuste i ma maksymalnie 500 elementów.
- `results` mają **tę samą kolejność i liczbę elementów** co `pairs`.
- `status: "OK"` oznacza, że `durationMin` i `costGrosze` są wypełnione.
- `status: "NO_CONNECTION"` oznacza, że pól `durationMin` i `costGrosze` **nie ma**. Dotyczy też nieznanego `locationId`.
- Wszystkie tryby mają jeden endpoint. Klient, który chce porównać tryby, wysyła kilka żądań.

### 5.2 Brak połączenia a awaria (ważne)

| Sytuacja | Odpowiedź Transportu | Co powinien zrobić klient |
|---|---|---|
| brak połączenia między punktami | `200`, `status: NO_CONNECTION` przy parze | odrzucić tę opcję i szukać innej; to może prowadzić do „brak pasującego planu” |
| błędne żądanie (brak pól, nieznany tryb, pusta lista) | `400` + `{"error":"BAD_REQUEST","message":"..."}` | poprawić żądanie (błąd po stronie klienta) |
| wewnętrzny błąd Transportu | `500` + `{"error":"INTERNAL_ERROR","message":"..."}` | potraktować jak niedostępność |
| serwis nie odpowiada | timeout / connection refused | po **2 s** przerwać i pokazać „usługa Transport niedostępna”, **nie** „brak pasującego planu” |

**Limit oczekiwania:** 2 s po stronie klientów. Typowa odpowiedź trwa kilka milisekund.

### 5.3 Endpointy pomocnicze

| Metoda i ścieżka | Odpowiedź |
|---|---|
| `GET /health` | `{"status":"UP", ...}` |
| `GET /api/v1/modes` | `["WALK","PUBLIC_TRANSPORT","CAR"]` |
| `GET /api/v1/locations` | lista `locationId` znanych w tabeli odcinków |

Gotowe pliki z prawdziwymi odpowiedziami są w [examples/](examples/). Klienci mogą na nich pracować, zanim połączą się z działającym serwisem.

Szybki test z innego komputera (PowerShell):

```powershell
Invoke-RestMethod http://172.20.147.17:8083/api/v1/routes -Method Post -ContentType 'application/json' -Body '{"mode":"WALK","pairs":[{"fromLocationId":"start","toLocationId":"ATR_001"}]}'
```

---

## 6. Dane

### Baza danych: H2 (wbudowana, w pliku)

Tabela odcinków jest w bazie **H2** uruchamianej razem z aplikacją. Nie trzeba niczego instalować. Baza leży w pliku `./data/transport.mv.db`, a folder `data/` jest w `.gitignore`. Dostęp odbywa się przez Spring Data JPA.

Tabela `SEGMENT`:

| Kolumna | Typ | Opis |
|---|---|---|
| `id` | bigint, PK | auto |
| `from_location_id` | varchar(64) | `locationId` początku |
| `to_location_id` | varchar(64) | `locationId` końca |
| `mode` | varchar(32) | `WALK` / `PUBLIC_TRANSPORT` / `CAR` |
| `duration_min` | int | czas w minutach |
| `cost_grosze` | int | koszt w groszach |

Na kolumnach (`from_location_id`, `to_location_id`, `mode`) jest ograniczenie unikalności. Jeden wiersz obsługuje oba kierunki, a brak wiersza oznacza `NO_CONNECTION`.

**Dane startowe są w [src/main/resources/data.sql](src/main/resources/data.sql)**. Spring Boot wykonuje ten skrypt przy **każdym** starcie: najpierw `DELETE FROM segment`, potem `INSERT`. Dzięki temu baza zawsze odpowiada temu, co jest w repo.

```sql
INSERT INTO segment (from_location_id, to_location_id, mode, duration_min, cost_grosze) VALUES
('start', 'ATR_001', 'WALK', 15, 0),
...
```

Zmiany zrobione w konsoli H2 działają tylko **do restartu**. Zmiany na stałe wprowadzaj w `data.sql`.

**Konsola H2** do podglądu i edycji na żywo działa tylko z komputera, na którym uruchomiono serwis:
`http://localhost:8083/h2-console`. JDBC URL: `jdbc:h2:file:./data/transport;AUTO_SERVER=TRUE`, użytkownik `sa`, hasło puste.
Przykład: `SELECT * FROM SEGMENT WHERE MODE = 'WALK';`

Testy używają osobnej bazy w pamięci (`jdbc:h2:mem:...`), więc nie ruszają pliku.

### Dane syntetyczne (tymczasowe)

Każda lokalizacja ma umowną odległość od `start`. Czas i koszt policzono ze wzorów:

| Tryb | Czas | Koszt | Uwagi |
|---|---|---|---|
| `WALK` | 12 min/km | 0 gr | brak odcinków > 3,5 km |
| `PUBLIC_TRANSPORT` | 5 min + 3 min/km | 480 gr (bilet) | |
| `CAR` (taxi) | 3 min + 2 min/km | 900 gr + 350 gr/km | |

Lokalizacje: `start`, atrakcje `ATR_001`, `ATR_002`, `ATR_004` oraz lokale `rest-01`, `rest-02`, `rest 03`, `rest-04`.
Dla `rest-04` tryb `WALK` zwraca `NO_CONNECTION`.

> **Do zrobienia:** dopasować czasy i koszty do rzeczywistych tras.

### Pełna tabela odcinków (czas min / koszt gr, w obie strony)

| Od `start` do | WALK | PUBLIC_TRANSPORT | CAR |
|---|---|---|---|
| `ATR_001` | 15 / 0 | 9 / 480 | 6 / 1320 |
| `ATR_002` | 21 / 0 | 11 / 480 | 7 / 1495 |
| `ATR_004` | 29 / 0 | 13 / 480 | 8 / 1740 |
| `rest-01` | 9 / 0 | 8 / 480 | 5 / 1145 |
| `rest-02` | 22 / 0 | 11 / 480 | 7 / 1530 |
| `rest 03` | 34 / 0 | 14 / 480 | 9 / 1880 |
| `rest-04` | brak | 26 / 480 | 17 / 3245 |

Przykład ręcznego liczenia, `PUBLIC_TRANSPORT`, start → `ATR_002` i z powrotem: 11 + 11 = **22 min**, 480 + 480 = **960 gr**.

---

## 7. Technologia i struktura kodu

- Java 21, Spring Boot 4.1.2 (`spring-boot-starter-webmvc`, `-validation`, `-actuator`, `-data-jpa`, `spring-boot-h2console`), baza H2, Maven Wrapper (instalacja Mavena nie jest potrzebna).
- Port `8083`, `server.address: 0.0.0.0`, czyli serwis jest dostępny z sieci lokalnej. CORS dla `/api/**` jest otwarty, więc frontend Planera może wołać API z przeglądarki.

```
src/main/java/pl/psso/transport/
├─ TransportApplication.java       # start aplikacji
├─ api/
│  ├─ TransportController.java     # POST /api/v1/routes, GET /modes, GET /locations
│  ├─ RouteDtos.java               # RouteRequest, LocationPair, RouteResponse, RouteResult, ErrorResponse
│  ├─ ApiExceptionHandler.java     # mapowanie błędów na 400 / 500
│  └─ CorsConfig.java
├─ domain/
│  ├─ TravelMode.java              # WALK, PUBLIC_TRANSPORT, CAR
│  └─ RouteService.java            # logika: A->A = 0, szukanie w obu kierunkach, NO_CONNECTION
└─ data/
   ├─ Segment.java                 # encja JPA, tabela SEGMENT
   └─ SegmentRepository.java       # zapytania do H2 (oba kierunki, lista locationId)
src/main/resources/
├─ application.yml                 # port, adres, baza H2, konsola, limit par
└─ data.sql                        # dane startowe tabeli SEGMENT (wykonywane przy każdym starcie)
data/                              # plik bazy H2 (tworzony automatycznie, poza gitem)
src/test/java/.../TransportApplicationTests.java   # testy kontraktu (MockMvc)
examples/                          # przykładowe żądania i odpowiedzi dla innych zespołów
```

---

## 8. Uruchomienie na komputerze laboratoryjnym

Wymagana Java 21 (`java -version`).

```powershell
.\mvnw.cmd spring-boot:run
```

Testy:

```powershell
.\mvnw.cmd test
```

Otwarcie portu w zaporze Windows (PowerShell jako administrator):

```powershell
New-NetFirewallRule -DisplayName "Transport 8083" -Direction Inbound -Protocol TCP -LocalPort 8083 -Action Allow
```

Sprawdzenie z innego komputera: `http://172.20.147.17:8083/health`

Podgląd bazy (tylko lokalnie): `http://localhost:8083/h2-console`

Aby zmienić dane na stałe, edytuj `src/main/resources/data.sql` i zrestartuj serwis.

---

## 9. Zasady dla osób i agentów pracujących nad tym repo

- **Nie zmieniaj kontraktu** z sekcji 5 (ścieżki, nazwy pól, jednostki, statusy, kody HTTP) bez uzgodnienia z Atrakcjami, Gastronomią i Planerem. Pola można dodawać, ale nie wolno ich usuwać ani zmieniać ich nazw.
- Czas zawsze w **minutach**, koszt zawsze w **groszach**, oba jako liczby całkowite.
- `NO_CONNECTION` to **nie** błąd: zawsze zwracamy go jako `200`.
- Transport nie wywołuje innych serwisów i nie czyta niczyjej bazy. Nasza baza H2 jest wyłącznie nasza, a inni widzą dane tylko przez API.
- Dane zmieniaj w `data.sql`, a nie wyłącznie w konsoli H2, bo zmiany z konsoli znikają po restarcie.
- Liczymy tylko odcinki ze `start`. Nie dodawaj odcinków atrakcja ↔ atrakcja ani atrakcja ↔ lokal.
- Po każdej zmianie uruchom `.\mvnw.cmd test`, a gdy zmienia się format odpowiedzi, zaktualizuj [examples/](examples/).
- Pracuj na osobnych gałęziach i scalaj do `main`.

---

## 10. Otwarte sprawy do uzgodnienia

- [ ] nazwa i położenie punktu `start`; miasto
- [ ] lista trybów w formularzu Planera (zespół 5)
- [ ] potwierdzenie limitu oczekiwania 2 s
- [ ] lokalizacje użyte w scenariuszu pokazu i ręcznie policzony plan
- [ ] porty serwisów pozostałych zespołów
- [ ] osoby kontaktowe w każdym zespole
