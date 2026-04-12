package com.studyflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.service.impl.MilvusVectorStoreServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

class MilvusVectorStoreServiceUnitTests {

    @Test
    void shouldConvertDoubleVectorToFloatVector() {
        List<Float> vector = MilvusVectorStoreServiceImpl.toFloatVector(List.of(0.25D, -0.5D, 1.0D), 3);

        assertThat(vector).containsExactly(0.25F, -0.5F, 1.0F);
    }

    @Test
    void shouldRejectEmbeddingDimensionMismatch() {
        assertThatThrownBy(() -> MilvusVectorStoreServiceImpl.toFloatVector(List.of(0.25D, 0.5D), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("embedding dimension mismatch");
    }

    @Test
    void shouldBuildMaterialFilterExpression() {
        assertThat(MilvusVectorStoreServiceImpl.buildMaterialFilter(123L))
                .isEqualTo("material_id == 123");
    }
}
