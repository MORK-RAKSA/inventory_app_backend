package com.retailflow.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.retailflow.entity.AppUser;
import com.retailflow.service.PayWaySandboxService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PayWaySandboxControllerTest {
  @Test
  void disallowsNonAdministrators() throws Exception {
    var service = mock(PayWaySandboxService.class);
    var mvc = MockMvcBuilders.standaloneSetup(new PayWaySandboxController(service)).build();
    mvc.perform(get("/api/payments/sandbox/config").requestAttr("currentUser", new AppUser()))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void rejectsInvalidTestAmountsBeforeCallingProvider() throws Exception {
    var service = mock(PayWaySandboxService.class);
    var mvc = MockMvcBuilders.standaloneSetup(new PayWaySandboxController(service)).build();
    var admin = new AppUser();
    admin.role = "ADMIN";
    for (String amount : new String[] {"0", "-1", "10.01", "0.001", "null"}) {
      mvc.perform(
              post("/api/payments/sandbox")
                  .requestAttr("currentUser", admin)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"requestId\":\"9d039f86-2b5e-4fad-bd04-a50d8e9aa8ab\",\"amount\":"
                          + amount
                          + "}"))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
  }
}
