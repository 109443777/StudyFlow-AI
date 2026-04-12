package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.ratelimit.RateLimit;
import com.studyflow.ai.common.ratelimit.RateLimitTarget;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.AskQuestionDTO;
import com.studyflow.ai.dto.CreateQaSessionDTO;
import com.studyflow.ai.dto.UpdateQaSessionMaterialsDTO;
import com.studyflow.ai.entity.QaMessage;
import com.studyflow.ai.entity.QaSession;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.service.RAGQueryService;
import com.studyflow.ai.service.rag.ChunkReference;
import com.studyflow.ai.service.rag.QaAnswerResult;
import com.studyflow.ai.vo.ChunkReferenceVO;
import com.studyflow.ai.vo.QaAnswerVO;
import com.studyflow.ai.vo.QaMessageVO;
import com.studyflow.ai.vo.QaSessionMaterialVO;
import com.studyflow.ai.vo.QaSessionVO;
import com.studyflow.ai.vo.QaStreamEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

@Validated
@Tag(name = "RAG QA")
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final RAGQueryService ragQueryService;

    private final MaterialMapper materialMapper;

    @LoginRequired
    @Operation(summary = "Create QA session")
    @PostMapping("/sessions")
    public Result<QaSessionVO> createSession(@Valid @RequestBody CreateQaSessionDTO createQaSessionDTO) {
        Long userId = UserContext.getRequiredUserId();
        QaSession qaSession = ragQueryService.createSession(userId, createQaSessionDTO);
        return Result.success(toQaSessionVO(userId, qaSession));
    }

    @LoginRequired
    @Operation(summary = "List QA sessions")
    @GetMapping("/sessions")
    public Result<List<QaSessionVO>> listSessions() {
        Long userId = UserContext.getRequiredUserId();
        return Result.success(ragQueryService.listSessions(userId).stream()
                .map(session -> toQaSessionVO(userId, session))
                .toList());
    }

    @LoginRequired
    @Operation(summary = "Update QA session materials")
    @PutMapping("/sessions/{sessionId}/materials")
    public Result<QaSessionVO> updateSessionMaterials(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateQaSessionMaterialsDTO updateQaSessionMaterialsDTO) {
        Long userId = UserContext.getRequiredUserId();
        QaSession qaSession = ragQueryService.updateSessionMaterials(userId, sessionId, updateQaSessionMaterialsDTO);
        return Result.success(toQaSessionVO(userId, qaSession));
    }

    @LoginRequired
    @Operation(summary = "Delete QA session")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = UserContext.getRequiredUserId();
        ragQueryService.deleteSession(userId, sessionId);
        return Result.success(null);
    }

    @LoginRequired
    @RateLimit(scene = "rag_ask", limit = 20, windowSeconds = 60, target = RateLimitTarget.USER,
            message = "AI question answering is too frequent, please retry later")
    @Operation(summary = "Ask question for a material session")
    @PostMapping("/sessions/{sessionId}/ask")
    public Result<QaAnswerVO> askQuestion(@PathVariable Long sessionId, @Valid @RequestBody AskQuestionDTO askQuestionDTO) {
        Long userId = UserContext.getRequiredUserId();
        QaAnswerResult result = ragQueryService.ask(userId, sessionId, askQuestionDTO);
        return Result.success(QaAnswerVO.builder()
                .sessionId(result.getSessionId())
                .questionMessageId(result.getQuestionMessage().getId())
                .answerMessageId(result.getAnswerMessage().getId())
                .answer(result.getAnswerMessage().getContent())
                .references(toReferenceVOs(result.getReferences()))
                .build());
    }

    @LoginRequired
    @RateLimit(scene = "rag_ask", limit = 20, windowSeconds = 60, target = RateLimitTarget.USER,
            message = "AI question answering is too frequent, please retry later")
    @Operation(summary = "Ask question for a material session with streaming response")
    @PostMapping(value = "/sessions/{sessionId}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askQuestionStream(@PathVariable Long sessionId, @Valid @RequestBody AskQuestionDTO askQuestionDTO) {
        Long userId = UserContext.getRequiredUserId();
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(exception -> emitter.complete());
        try {
            ragQueryService.streamAnswer(userId, sessionId, askQuestionDTO, new com.studyflow.ai.service.rag.QaAnswerStreamObserver() {
                @Override
                public void onContext(List<ChunkReference> references) {
                    sendEvent(emitter, "context", QaStreamEventVO.builder()
                            .type("context")
                            .references(toReferenceVOs(references))
                            .build());
                }

                @Override
                public void onToken(String token) {
                    sendEvent(emitter, "chunk", QaStreamEventVO.builder()
                            .type("chunk")
                            .content(token)
                            .build());
                }

                @Override
                public void onComplete(QaAnswerResult result) {
                    sendEvent(emitter, "done", QaStreamEventVO.builder()
                            .type("done")
                            .sessionId(String.valueOf(result.getSessionId()))
                            .questionMessageId(String.valueOf(result.getQuestionMessage().getId()))
                            .answerMessageId(String.valueOf(result.getAnswerMessage().getId()))
                            .answer(result.getAnswerMessage().getContent())
                            .references(toReferenceVOs(result.getReferences()))
                            .build());
                    emitter.complete();
                }

                @Override
                public void onError(Throwable throwable) {
                    sendEvent(emitter, "error", QaStreamEventVO.builder()
                            .type("error")
                            .message(throwable.getMessage())
                            .build());
                    emitter.completeWithError(throwable);
                }
            });
        } catch (RuntimeException exception) {
            sendEvent(emitter, "error", QaStreamEventVO.builder()
                    .type("error")
                    .message(exception.getMessage())
                    .build());
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @LoginRequired
    @Operation(summary = "Get QA message history")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<QaMessageVO>> getHistory(@PathVariable Long sessionId) {
        Long userId = UserContext.getRequiredUserId();
        List<QaMessage> messages = ragQueryService.listHistory(userId, sessionId);
        return Result.success(messages.stream().map(this::toQaMessageVO).toList());
    }

    private QaSessionVO toQaSessionVO(Long userId, QaSession qaSession) {
        List<Long> materialIds = ragQueryService.listSessionMaterialIds(userId, qaSession.getId());
        if (materialIds.isEmpty()) {
            materialIds = List.of(qaSession.getMaterialId());
        }
        return QaSessionVO.builder()
                .id(qaSession.getId())
                .materialId(qaSession.getMaterialId())
                .materialIds(materialIds)
                .materials(toSessionMaterialVOs(userId, materialIds))
                .sessionName(qaSession.getSessionName())
                .createTime(qaSession.getCreateTime())
                .updateTime(qaSession.getUpdateTime())
                .build();
    }

    private QaMessageVO toQaMessageVO(QaMessage qaMessage) {
        List<ChunkReference> references = List.of();
        if (qaMessage.getReferenceChunks() != null && !qaMessage.getReferenceChunks().isBlank()) {
            references = ragQueryService.readReferences(qaMessage.getReferenceChunks());
        }
        return QaMessageVO.builder()
                .id(qaMessage.getId())
                .role(qaMessage.getRole())
                .content(qaMessage.getContent())
                .referenceChunks(toReferenceVOs(references))
                .createTime(qaMessage.getCreateTime())
                .build();
    }

    private List<ChunkReferenceVO> toReferenceVOs(List<ChunkReference> references) {
        return references.stream()
                .map(item -> ChunkReferenceVO.builder()
                        .chunkId(item.getChunkId())
                        .materialId(item.getMaterialId())
                        .fileName(item.getFileName())
                        .chunkIndex(item.getChunkIndex())
                        .score(item.getScore())
                        .chunkText(item.getChunkText())
                        .build())
                .toList();
    }

    private List<QaSessionMaterialVO> toSessionMaterialVOs(Long userId, List<Long> materialIds) {
        if (materialIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Material> materialMap = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                        .eq(Material::getUserId, userId)
                        .in(Material::getId, materialIds))
                .stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        return materialIds.stream()
                .map(materialMap::get)
                .filter(material -> material != null)
                .map(material -> QaSessionMaterialVO.builder()
                        .id(material.getId())
                        .fileName(material.getFileName())
                        .materialType(material.getMaterialType())
                        .parseStatus(material.getParseStatus())
                        .build())
                .toList();
    }

    private void sendEvent(SseEmitter emitter, String eventName, QaStreamEventVO payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write stream event", exception);
        }
    }
}
