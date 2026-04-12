package com.studyflow.ai;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StaticConsoleResourceTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeIndexHtmlForStaticConsole() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"zh-CN\">")));
    }

    @Test
    void shouldServeAppJsForStaticConsole() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("applyChineseConsoleLocale")));
    }

    @Test
    void shouldServeGuidedE2eConsole() throws Exception {
        mockMvc.perform(get("/e2e.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("StudyFlow AI E2E Console")));
    }

    @Test
    void shouldServeGuidedE2eConsoleScript() throws Exception {
        mockMvc.perform(get("/e2e.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("StudyFlowE2EConsole")));
    }

    @Test
    void shouldServeGuidedE2eConsoleStyles() throws Exception {
        mockMvc.perform(get("/e2e.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("e2e-shell")));
    }
}
