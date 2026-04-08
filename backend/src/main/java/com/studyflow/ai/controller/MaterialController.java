package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.MaterialQueryDTO;
import com.studyflow.ai.dto.MaterialUploadDTO;
import com.studyflow.ai.service.MaterialService;
import com.studyflow.ai.vo.MaterialVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Material")
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @LoginRequired
    @Operation(summary = "Upload material")
    @PostMapping("/upload")
    public Result<MaterialVO> uploadMaterial(@Valid @ModelAttribute MaterialUploadDTO materialUploadDTO) {
        return Result.success(materialService.uploadMaterial(materialUploadDTO));
    }

    @LoginRequired
    @Operation(summary = "Get material detail")
    @GetMapping("/{materialId}")
    public Result<MaterialVO> getMaterialDetail(@PathVariable Long materialId) {
        return Result.success(materialService.getMaterialDetail(materialId));
    }

    @LoginRequired
    @Operation(summary = "List my materials")
    @GetMapping("/my")
    public Result<List<MaterialVO>> listMyMaterials(@ModelAttribute MaterialQueryDTO materialQueryDTO) {
        return Result.success(materialService.listMyMaterials(materialQueryDTO));
    }
}
