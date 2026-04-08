package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.MaterialContentQueryDTO;
import com.studyflow.ai.entity.MaterialContent;
import com.studyflow.ai.service.MaterialContentService;
import com.studyflow.ai.vo.MaterialContentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Material Content")
@RestController
@RequestMapping("/api/material-contents")
@RequiredArgsConstructor
public class MaterialContentController {

    private final MaterialContentService materialContentService;

    @LoginRequired
    @Operation(summary = "Get material parsed content")
    @GetMapping
    public Result<MaterialContentVO> getMaterialContent(@Valid @ModelAttribute MaterialContentQueryDTO materialContentQueryDTO) {
        Long userId = UserContext.getRequiredUserId();
        MaterialContent materialContent = materialContentService.getByMaterialId(userId, materialContentQueryDTO);
        return Result.success(MaterialContentVO.builder()
                .id(materialContent.getId())
                .materialId(materialContent.getMaterialId())
                .contentType(materialContent.getContentType())
                .rawText(materialContent.getRawText())
                .cleanedText(materialContent.getCleanedText())
                .chapterInfo(parseChapterInfo(materialContent.getChapterInfo()))
                .createTime(materialContent.getCreateTime())
                .updateTime(materialContent.getUpdateTime())
                .build());
    }

    private List<String> parseChapterInfo(String chapterInfo) {
        return com.studyflow.ai.common.util.TextCleanupSupport.readChapterInfo(chapterInfo);
    }
}
