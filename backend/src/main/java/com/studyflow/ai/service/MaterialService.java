package com.studyflow.ai.service;

import com.studyflow.ai.dto.MaterialQueryDTO;
import com.studyflow.ai.dto.MaterialUploadDTO;
import com.studyflow.ai.vo.MaterialVO;
import java.util.List;

public interface MaterialService {

    MaterialVO uploadMaterial(MaterialUploadDTO materialUploadDTO);

    MaterialVO getMaterialDetail(Long materialId);

    List<MaterialVO> listMyMaterials(MaterialQueryDTO materialQueryDTO);
}
