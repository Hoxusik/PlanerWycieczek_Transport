package pl.psso.transport;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:transport-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class TransportApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void returnsTimeAndCostInBothDirectionsAndNoConnection() throws Exception {
        String body = """
                {"mode":"WALK","pairs":[
                  {"fromLocationId":"start","toLocationId":"ATR_001"},
                  {"fromLocationId":"ATR_001","toLocationId":"start"},
                  {"fromLocationId":"start","toLocationId":"rest-04"},
                  {"fromLocationId":"start","toLocationId":"start"},
                  {"fromLocationId":"start","toLocationId":"rest-01"},
                  {"fromLocationId":"ATR_001","toLocationId":"NIE_MA"}
                ]}""";
        mvc.perform(post("/api/v1/routes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("WALK"))
                .andExpect(jsonPath("$.results[0].status").value("OK"))
                .andExpect(jsonPath("$.results[0].durationMin").value(15))
                .andExpect(jsonPath("$.results[0].costGrosze").value(0))
                .andExpect(jsonPath("$.results[1].fromLocationId").value("ATR_001"))
                .andExpect(jsonPath("$.results[1].durationMin").value(15))
                .andExpect(jsonPath("$.results[2].status").value("NO_CONNECTION"))
                .andExpect(jsonPath("$.results[2].durationMin").doesNotExist())
                .andExpect(jsonPath("$.results[3].durationMin").value(0))
                .andExpect(jsonPath("$.results[4].status").value("OK"))
                .andExpect(jsonPath("$.results[4].durationMin").value(9))
                .andExpect(jsonPath("$.results[5].status").value("NO_CONNECTION"));
    }

    @Test
    void unknownModeIsBadRequest() throws Exception {
        String body = """
                {"mode":"TELEPORT","pairs":[{"fromLocationId":"start","toLocationId":"ATR_001"}]}""";
        mvc.perform(post("/api/v1/routes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void emptyPairsIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/routes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"CAR\",\"pairs\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsLocationsFromDatabase() throws Exception {
        mvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(containsInAnyOrder(
                        "ATR_001", "ATR_002", "ATR_004", "ATR_005",
                        "rest-01", "rest-02", "rest-03", "rest-04", "start")));
    }

    @Test
    void transportOptionsListAllModesForAttraction() throws Exception {
        mvc.perform(get("/api/v1/locations/ATR_001/transport-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromLocationId").value("start"))
                .andExpect(jsonPath("$.toLocationId").value("ATR_001"))
                .andExpect(jsonPath("$.options.length()").value(3))
                .andExpect(jsonPath("$.options[0].mode").value("WALK"))
                .andExpect(jsonPath("$.options[0].durationMin").value(15))
                .andExpect(jsonPath("$.options[0].costGrosze").value(0))
                .andExpect(jsonPath("$.options[1].mode").value("PUBLIC_TRANSPORT"))
                .andExpect(jsonPath("$.options[1].costGrosze").value(480))
                .andExpect(jsonPath("$.options[2].mode").value("CAR"))
                .andExpect(jsonPath("$.options[2].costGrosze").value(1320));
    }

    @Test
    void transportOptionsSkipUnavailableModes() throws Exception {
        // rest-04: brak odcinka pieszo
        mvc.perform(get("/api/v1/locations/rest-04/transport-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[0].mode").value("PUBLIC_TRANSPORT"));
    }

    @Test
    void transportOptionsForUnknownLocationAreEmpty() throws Exception {
        mvc.perform(get("/api/v1/locations/NIE_MA/transport-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toLocationId").value("NIE_MA"))
                .andExpect(jsonPath("$.options.length()").value(0));
    }

    @Test
    void transportOptionsBetweenLocationsUseTheSameSegmentsInBothDirections() throws Exception {
        String path = "/api/v1/locations/{from}/to/{to}/transport-options";
        mvc.perform(get(path, "ATR_001", "rest-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromLocationId").value("ATR_001"))
                .andExpect(jsonPath("$.toLocationId").value("rest-01"))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[0].mode").value("WALK"))
                .andExpect(jsonPath("$.options[0].durationMin").value(12))
                .andExpect(jsonPath("$.options[0].costGrosze").value(0))
                .andExpect(jsonPath("$.options[1].mode").value("PUBLIC_TRANSPORT"))
                .andExpect(jsonPath("$.options[1].durationMin").value(10))
                .andExpect(jsonPath("$.options[1].costGrosze").value(480));

        mvc.perform(get(path, "rest-01", "ATR_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromLocationId").value("rest-01"))
                .andExpect(jsonPath("$.toLocationId").value("ATR_001"))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[0].durationMin").value(12))
                .andExpect(jsonPath("$.options[1].costGrosze").value(480));

        mvc.perform(post("/api/v1/routes").contentType(MediaType.APPLICATION_JSON).content("""
                {"mode":"WALK","pairs":[{"fromLocationId":"ATR_001","toLocationId":"rest-01"}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("OK"))
                .andExpect(jsonPath("$.results[0].durationMin").value(12));
    }

    @Test
    void transportOptionsBetweenLocationsWithoutASegmentAreEmpty() throws Exception {
        mvc.perform(get("/api/v1/locations/ATR_001/to/NIE_MA/transport-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromLocationId").value("ATR_001"))
                .andExpect(jsonPath("$.toLocationId").value("NIE_MA"))
                .andExpect(jsonPath("$.options.length()").value(0));
    }

    @ParameterizedTest
    @CsvSource({"start,rest-01", "ATR_001,start", "ATR_001,ATR_001", "' ',rest-01", "ATR_001,' '"})
    void transportOptionsBetweenLocationsRejectInvalidIds(String from, String to) throws Exception {
        mvc.perform(get("/api/v1/locations/{from}/to/{to}/transport-options", from, to))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
