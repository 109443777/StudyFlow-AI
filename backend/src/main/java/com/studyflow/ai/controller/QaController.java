package com.studyflow.ai.controller;

import com.studyflow.ai.common.auth.LoginRequired;
import com.studyflow.ai.common.auth.UserContext;
import com.studyflow.ai.common.ratelimit.RateLimit;
import com.studyflow.ai.common.ratelimit.RateLimitTarget;
import com.studyflow.ai.common.response.Result;
import com.studyflow.ai.dto.AskQuestionDTO;
import com.studyflow.ai.dto.CreateQaSessionDTO;
import com.studyflow.ai.entity.QaMessage;
import com.studyflow.ai.entity.QaSession;
import com.studyflow.ai.service.RAGQueryService;
import com.studyflow.ai.service.rag.ChunkReference;
import com.studyflow.ai.service.rag.QaAnswerResult;
import com.studyflow.ai.vo.ChunkReferenceVO;
import com.studyflow.ai.vo.QaAnswerVO;
import com.studyflow.ai.vo.QaMessageVO;
import com.studyflow.ai.vo.QaSessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "RAG QA")
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final RAGQueryService ragQueryService;

    @LoginRequired
    @Operation(summary = "Create QA session")
    @PostMapping("/sessions")
    public Result<QaSessionVO> createSession(@Valid @RequestBody CreateQaSessionDTO createQaSessionDTO) {
        Long userId = UserContext.getRequiredUserId();
        QaSession qaSession = ragQueryService.createSession(userId, createQaSessionDTO);
        return Result.success(toQaSessionVO(qaSession));
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
    @Operation(summary = "Get QA message history")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<QaMessageVO>> getHistory(@PathVariable Long sessionId) {
        Long userId = UserContext.getRequiredUserId();
        List<QaMessage> messages = ragQueryService.listHistory(userId, sessionId);
        return Result.success(messages.stream().map(this::toQaMessageVO).toList());
    }

    private QaSessionVO toQaSessionVO(QaSession qaSession) {
        return QaSessionVO.builder()
                .id(qaSession.getId())
                .materialId(qaSession.getMaterialId())
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
                        .chunkIndex(item.getChunkIndex())
                        .score(item.getScore())
                        .chunkText(item.getChunkText())
                        .build())
                .toList();
    }
}
