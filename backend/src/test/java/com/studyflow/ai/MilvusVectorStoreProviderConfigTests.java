package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.studyflow.ai.service.VectorStoreService;
import com.studyflow.ai.service.impl.MilvusVectorStoreServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "studyflow.vector-store.provider=milvus")
class MilvusVectorStoreProviderConfigTests {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    void shouldUseMilvusVectorStoreWhenConfigured() {
        assertThat(vectorStoreService).isInstanceOf(MilvusVectorStoreServiceImpl.class);
    }
}
