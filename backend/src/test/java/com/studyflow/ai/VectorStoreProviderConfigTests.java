package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.studyflow.ai.config.VectorStoreProperties;
import com.studyflow.ai.service.VectorStoreService;
import com.studyflow.ai.service.impl.DatabaseVectorStoreServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "studyflow.vector-store.provider=database")
@EnableConfigurationProperties(VectorStoreProperties.class)
class VectorStoreProviderConfigTests {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private VectorStoreProperties vectorStoreProperties;

    @Test
    void shouldUseDatabaseVectorStoreByDefault() {
        assertThat(vectorStoreService).isInstanceOf(DatabaseVectorStoreServiceImpl.class);
        assertThat(vectorStoreProperties.getProvider()).isEqualTo("database");
        assertThat(vectorStoreProperties.getMilvus().getCollectionName())
                .isEqualTo("studyflow_material_chunks");
    }
}
