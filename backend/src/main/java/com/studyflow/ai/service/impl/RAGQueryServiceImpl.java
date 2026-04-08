package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.RagProperties;
import com.studyflow.ai.dto.AskQuestionDTO;
import com.studyflow.ai.dto.CreateQaSessionDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.QaMessage;
import com.studyflow.ai.entity.QaSession;
import com.studyflow.ai.enums.QaMessageRoleEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.gateway.AiGateway;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.QaMessageMapper;
import com.studyflow.ai.mapper.QaSessionMapper;
import com.studyflow.ai.service.RAGQueryService;
import com.studyflow.ai.service.VectorStoreService;
import com.studyflow.ai.service.rag.ChunkReference;
import com.studyflow.ai.service.rag.QaAnswerResult;
import com.studyflow.ai.service.rag.QaSessionContextCache;
import com.studyflow.ai.service.rag.RagPromptBuilder;
import com.studyflow.ai.service.vector.ChunkSearchResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RAGQueryServiceImpl implements RAGQueryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MaterialMapper materialMapper;

    private final QaSessionMapper qaSessionMapper;

    private final QaMessageMapper qaMessageMapper;

    private final VectorStoreService vectorStoreService;

    private final AiGateway aiGateway;

    private final RagPromptBuilder ragPromptBuilder;

    private final QaSessionContextCache qaSessionContextCache;

    private final RagProperties ragProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaSession createSession(Long userId, CreateQaSessionDTO createQaSessionDTO) {
        Material material = getOwnedMaterial(userId, createQaSessionDTO.getMaterialId());
        QaSession qaSession = new QaSession();
        qaSession.setUserId(userId);
        qaSession.setMaterialId(material.getId());
        qaSession.setSessionName(resolveSessionName(material, createQaSessionDTO.getSessionName()));
        qaSessionMapper.insert(qaSession);
        return qaSession;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaAnswerResult ask(Long userId, Long sessionId, AskQuestionDTO askQuestionDTO) {
        QaSession qaSession = getOwnedSession(userId, sessionId);
        Material material = getOwnedMaterial(userId, qaSession.getMaterialId());
        int topK = askQuestionDTO.getTopK() == null ? ragProperties.getTopK() : askQuestionDTO.getTopK();
        List<ChunkSearchResult> searchResults = vectorStoreService.searchByMaterialId(material.getId(), askQuestionDTO.getQuestion(), topK);
        if (searchResults.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.QA_CONTEXT_NOT_FOUND);
        }
        List<ChunkReference> references = searchResults.stream()
                .map(item -> ChunkReference.builder()
                        .chunkId(item.getChunk().getId())
                        .chunkIndex(item.getChunk().getChunkIndex())
                        .score(item.getScore())
                        .chunkText(item.getChunk().getChunkText())
                        .build())
                .toList();
        List<String> contexts = references.stream()
                .map(item -> "Chunk " + item.getChunkIndex() + ": " + item.getChunkText())
                .toList();
        String prompt = ragPromptBuilder.build(
                material,
                askQuestionDTO.getQuestion(),
                qaSessionContextCache.recentHistory(qaSession.getId(), ragProperties.getHistorySize()));
        String answer = aiGateway.answer(prompt, contexts);

        QaMessage questionMessage = new QaMessage();
        questionMessage.setSessionId(qaSession.getId());
        questionMessage.setUserId(userId);
        questionMessage.setMaterialId(material.getId());
        questionMessage.setRole(QaMessageRoleEnum.USER.name());
        questionMessage.setContent(askQuestionDTO.getQuestion().trim());
        qaMessageMapper.insert(questionMessage);

        QaMessage answerMessage = new QaMessage();
        answerMessage.setSessionId(qaSession.getId());
        answerMessage.setUserId(userId);
        answerMessage.setMaterialId(material.getId());
        answerMessage.setRole(QaMessageRoleEnum.ASSISTANT.name());
        answerMessage.setContent(answer);
        answerMessage.setReferenceChunks(writeAsJson(references));
        qaMessageMapper.insert(answerMessage);

        qaSessionContextCache.append(qaSession.getId(), QaMessageRoleEnum.USER.name(), questionMessage.getContent(), ragProperties.getHistorySize());
        qaSessionContextCache.append(qaSession.getId(), QaMessageRoleEnum.ASSISTANT.name(), answerMessage.getContent(), ragProperties.getHistorySize());

        return QaAnswerResult.builder()
                .sessionId(qaSession.getId())
                .questionMessage(questionMessage)
                .answerMessage(answerMessage)
                .references(references)
                .build();
    }

    @Override
    public List<QaMessage> listHistory(Long userId, Long sessionId) {
        getOwnedSession(userId, sessionId);
        return qaMessageMapper.selectList(new LambdaQueryWrapper<QaMessage>()
                .eq(QaMessage::getSessionId, sessionId)
                .eq(QaMessage::getUserId, userId)
                .orderByAsc(QaMessage::getCreateTime));
    }

    @Override
    public List<ChunkReference> readReferences(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<ChunkReference>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to read qa references");
        }
    }

    private String resolveSessionName(Material material, String sessionName) {
        if (StringUtils.hasText(sessionName)) {
            return sessionName.trim();
        }
        return "Q&A - " + material.getFileName();
    }

    private Material getOwnedMaterial(Long userId, Long materialId) {
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, materialId)
                .eq(Material::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        return material;
    }

    private QaSession getOwnedSession(Long userId, Long sessionId) {
        QaSession qaSession = qaSessionMapper.selectOne(new LambdaQueryWrapper<QaSession>()
                .eq(QaSession::getId, sessionId)
                .eq(QaSession::getUserId, userId)
                .last("limit 1"));
        if (qaSession == null) {
            throw new BusinessException(ResultCodeEnum.QA_SESSION_NOT_FOUND);
        }
        return qaSession;
    }

    private String writeAsJson(List<ChunkReference> references) {
        try {
            return OBJECT_MAPPER.writeValueAsString(references);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCodeEnum.INTERNAL_ERROR, "failed to write qa references");
        }
    }
}
