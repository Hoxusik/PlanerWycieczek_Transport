# PlanerWycieczek_Transport

Mikroserwis **Transport** (zespół 3) w projekcie **Planer Wycieczek**. Zwraca czas i koszt przejazdu dla par lokalizacji w wybranym trybie przemieszczania się (HTTP + JSON).

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

| # | Zespół | Adres IP | Posiada | Korzysta z |
|---|---|---|---|---|
| 1 | Atrakcje | `172.20.147.24` | katalog atrakcji: kategorie, lokalizacje, ceny, czasy zwiedzania, czy pod dachem | Warunki dnia, **Transport** |
| 2 | Gastronomia | `172.20.147.27` | lokale, posiłki, ceny, diety, czas wizyty | **Transport** |
| **3** | **Transport (my)** | **`172.20.147.17`** | **tabela odcinków między lokalizacjami: czas i koszt dla par w danym trybie** | **nikogo** |
| 4 | Warunki dnia | `172.20.147.21` | przygotowane prognozy dla dat demonstracyjnych | nikogo |
| 5 | Planer i integracja | `172.20.147.14` | formularz i widok planu, kolejność wizyt, kontrola budżetu i godzin; bez własnej bazy | Atrakcje, Gastronomia, **Transport** |

**Nasz serwis:** `http://172.20.147.17:8083`

Porty pozostałych zespołów: _do uzupełnienia_.

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
         └───────────► │2. Gastronomia│ ───────┘ od atrakcji
                       └──────────────┘
```

Transport i Warunki dnia nie zależą od nikogo, więc **jako pierwsze udostępniają przykładowe odpowiedzi**.

### Jak Planer składa plan

1. Planer prosi **Atrakcje** o propozycje na dany dzień. Atrakcje pytają **Transport** o dojazd **start → atrakcja** i zwracają propozycje z kosztem i czasem dojazdu. Planer wybiera pierwszą atrakcję.
2. Planer prosi **Gastronomię** o posiłki. Gastronomia pyta **Transport** o dojazd **atrakcja 1 → lokal**.
3. Planer sam pyta **Transport** o pozostałe odcinki: **lokal → atrakcja 2** i **atrakcja 2 → start**.
4. Planer sprawdza budżet i okno 10:00–16:00.

Atrakcja daleko od startu może pasować na drugą wizytę, jeśli leży blisko restauracji. Dlatego potrzebujemy odcinków lokal ↔ atrakcja.

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
| start ↔ atrakcja | Atrakcje, Planer (powrót) | tak |
| atrakcja ↔ lokal | Gastronomia, Planer | tak |
| atrakcja ↔ atrakcja | nikt, bo w planie nie ma takiego przejazdu | nie (`NO_CONNECTION`) |
| start ↔ lokal, lokal ↔ lokal | nikt | nie (`NO_CONNECTION`) |

---

## 4. Wspólne identyfikatory, jednostki i formaty

| Co | Format | Przykład |
|---|---|---|
| lokalizacja | `locationId`, string | `start`, `attr-01`, `rest-01` |
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
    { "fromLocationId": "start",   "toLocationId": "attr-01" },
    { "fromLocationId": "attr-01", "toLocationId": "rest-01" },
    { "fromLocationId": "rest-01", "toLocationId": "attr-03" },
    { "fromLocationId": "attr-03", "toLocationId": "start" },
    { "fromLocationId": "start",   "toLocationId": "attr-06" }
  ]
}
```

Odpowiedź `200 OK`:

```json
{
  "mode": "WALK",
  "results": [
    { "fromLocationId": "start",   "toLocationId": "attr-01", "status": "OK", "durationMin": 15, "costGrosze": 0 },
    { "fromLocationId": "attr-01", "toLocationId": "rest-01", "status": "OK", "durationMin": 15, "costGrosze": 0 },
    { "fromLocationId": "rest-01", "toLocationId": "attr-03", "status": "OK", "durationMin": 21, "costGrosze": 0 },
    { "fromLocationId": "attr-03", "toLocationId": "start",   "status": "OK", "durationMin": 28, "costGrosze": 0 },
    { "fromLocationId": "start",   "toLocationId": "attr-06", "status": "NO_CONNECTION" }
  ]
}
```

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
Invoke-RestMethod http://172.20.147.17:8083/api/v1/routes -Method Post -ContentType 'application/json' -Body '{"mode":"WALK","pairs":[{"fromLocationId":"start","toLocationId":"attr-01"}]}'
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

**Źródłem prawdy jest plik [src/main/resources/segments.csv](src/main/resources/segments.csv)** trzymany w repo. Przy starcie [SegmentSeeder](src/main/java/pl/psso/transport/data/SegmentSeeder.java) wczytuje go do bazy:

```
fromLocationId,toLocationId,mode,durationMin,costGrosze
start,attr-01,WALK,15,0
```

Linie z `#` to komentarze. Zachowanie przy starcie określa `transport.reset-data-on-start` w `application.yml`:
- `true` (domyślnie): tabela jest czyszczona i wczytywana z CSV, więc dane zawsze odpowiadają repo. Zmiany zrobione w konsoli H2 działają **do restartu**.
- `false`: CSV jest wczytywany tylko do pustej bazy, więc zmiany z konsoli H2 przetrwają restart. Żeby wrócić do CSV, usuń folder `data/`.

**Konsola H2** do podglądu i edycji na żywo działa tylko z komputera, na którym uruchomiono serwis:
`http://localhost:8083/h2-console`. JDBC URL: `jdbc:h2:file:./data/transport;AUTO_SERVER=TRUE`, użytkownik `sa`, hasło puste.
Przykład: `SELECT * FROM SEGMENT WHERE MODE = 'WALK';`

Testy używają osobnej bazy w pamięci (`jdbc:h2:mem:...`), więc nie ruszają pliku.

### Dane syntetyczne (tymczasowe)

Lokalizacje mają umowne współrzędne na siatce miasta. Czas i koszt policzono ze wzorów:

| Tryb | Czas | Koszt | Uwagi |
|---|---|---|---|
| `WALK` | 12 min/km | 0 gr | brak odcinków > 3,5 km |
| `PUBLIC_TRANSPORT` | 5 min + 3 min/km | 480 gr (bilet) | |
| `CAR` (taxi) | 3 min + 2 min/km | 900 gr + 350 gr/km | |

Tymczasowe lokalizacje: `start`, atrakcje `attr-01` … `attr-06`, lokale `rest-01` … `rest-04`.
`attr-06` leży daleko od startu (pieszo `NO_CONNECTION`), ale blisko `rest-04`. To przypadek „daleka atrakcja jako druga wizyta”.

> **Do zrobienia:** podmienić `locationId` na identyfikatory od Atrakcji (`172.20.147.24`) i Gastronomii (`172.20.147.27`) oraz dobrać czasy tak, żeby pasowały do ich danych.

### Przykład ręcznego przeliczenia (WALK)

| Odcinek | Czas | Koszt |
|---|---|---|
| start → attr-01 | 15 min | 0 gr |
| attr-01 → rest-01 | 15 min | 0 gr |
| rest-01 → attr-03 | 21 min | 0 gr |
| attr-03 → start | 28 min | 0 gr |
| **Razem transport** | **79 min** | **0 gr** |

Planer dodaje do tego czasy zwiedzania i posiłku oraz ceny biletów i posiłku z Atrakcji i Gastronomii.

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
   ├─ SegmentRepository.java       # zapytania do H2 (oba kierunki, lista locationId)
   └─ SegmentSeeder.java           # wczytuje segments.csv do bazy przy starcie
src/main/resources/
├─ application.yml                 # port, adres, baza H2, konsola, plik startowy, limit par
└─ segments.csv                    # dane startowe tabeli odcinków (źródło prawdy)
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

Aby zmienić dane na stałe, edytuj `segments.csv` i zrestartuj serwis.

---

## 9. Zasady dla osób i agentów pracujących nad tym repo

- **Nie zmieniaj kontraktu** z sekcji 5 (ścieżki, nazwy pól, jednostki, statusy, kody HTTP) bez uzgodnienia z Atrakcjami, Gastronomią i Planerem. Pola można dodawać, ale nie wolno ich usuwać ani zmieniać ich nazw.
- Czas zawsze w **minutach**, koszt zawsze w **groszach**, oba jako liczby całkowite.
- `NO_CONNECTION` to **nie** błąd: zawsze zwracamy go jako `200`.
- Transport nie wywołuje innych serwisów i nie czyta niczyjej bazy. Nasza baza H2 jest wyłącznie nasza, a inni widzą dane tylko przez API.
- Dane zmieniaj w `segments.csv` (źródło prawdy w repo), a nie wyłącznie w konsoli H2.
- Po każdej zmianie uruchom `.\mvnw.cmd test`, a gdy zmienia się format odpowiedzi, zaktualizuj [examples/](examples/).
- Pracuj na osobnych gałęziach i scalaj do `main`.

---

## 10. Otwarte sprawy do uzgodnienia

- [ ] `locationId` atrakcji (od zespołu 1) i lokali (od zespołu 2); nazwa i położenie punktu `start`; miasto
- [ ] lista trybów w formularzu Planera (zespół 5)
- [ ] potwierdzenie limitu oczekiwania 2 s
- [ ] lokalizacje użyte w scenariuszu pokazu i ręcznie policzony plan
- [ ] porty serwisów pozostałych zespołów
- [ ] osoby kontaktowe w każdym zespole
