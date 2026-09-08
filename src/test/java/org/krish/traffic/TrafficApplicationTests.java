package org.krish.traffic;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.krish.traffic.config.TrafficRulesProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TrafficApplicationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private TrafficRulesProperties rulesProperties;

  @Test
  void contextLoads() {}

  @Test
  void testRulesPropertiesLoaded() {
    assert rulesProperties.getSpeedThreshold() == 80.0;
    assert rulesProperties.getFineTiers().size() == 3;
  }

  @Test
  void testSubmitValidViolationFormHtml() throws Exception {
    mockMvc
        .perform(
            post("/process")
                .param("vehicleId", "MH12AB1234")
                .param("speed", "95.5")
                .param("zone", "Zone-A")
                .param("emergency", "false"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Violation Saved Successfully")));
  }

  @Test
  void testSubmitInvalidViolationFormHtml() throws Exception {
    mockMvc
        .perform(
            post("/process")
                .param("vehicleId", "")
                .param("speed", "-5.0")
                .param("zone", "Zone-!")
                .param("emergency", "false"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Validation failed")));
  }

  @Test
  void testSubmitValidViolationRest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/violations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"vehicleId\": \"KA03MM1234\", \"speed\": 110.0, \"zone\": \"Zone-B\", \"emergency\": false}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.vehicleId").value("KA03MM1234"))
        .andExpect(jsonPath("$.speed").value(110.0))
        .andExpect(jsonPath("$.fine").value(2000));
  }

  @Test
  void testSubmitInvalidViolationRest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/violations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"vehicleId\": \"\", \"speed\": 350.0, \"zone\": \"Zone-?\", \"emergency\": false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(1))));
  }

  @Test
  void testGetViolationsRest() throws Exception {
    mockMvc
        .perform(get("/api/v1/violations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
