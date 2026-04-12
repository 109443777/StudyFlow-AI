package com.studyflow.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.vo.MaterialVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JacksonLongSerializationTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSerializeLongIdsAsStringsToAvoidJavascriptPrecisionLoss() throws Exception {
        MaterialVO materialVO = MaterialVO.builder()
                .id(2043240841914798081L)
                .userId(2042603746976243714L)
                .fileName("knowledge-graph.pdf")
                .build();

        String json = objectMapper.writeValueAsString(Result.success(materialVO));

        assertTrue(json.contains("\"id\":\"2043240841914798081\""));
        assertTrue(json.contains("\"userId\":\"2042603746976243714\""));
    }
}
