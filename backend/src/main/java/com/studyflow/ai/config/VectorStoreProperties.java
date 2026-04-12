package com.studyflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studyflow.vector-store")
public class VectorStoreProperties {

    private String provider = "database";

    private Milvus milvus = new Milvus();

    @Data
    public static class Milvus {

        private String uri = "http://localhost:19530";

        private String token = "";

        private String collectionName = "studyflow_material_chunks";

        private Integer dimension = 1024;

        private String metricType = "COSINE";
    }
}
