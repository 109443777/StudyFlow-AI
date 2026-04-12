package com.studyflow.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.RagProperties;
import com.studyflow.ai.dto.AskQuestionDTO;
import com.studyflow.ai.dto.CreateQaSessionDTO;
import com.studyflow.ai.dto.UpdateQaSessionMaterialsDTO;
import com.studyflow.ai.entity.Material;
import com.studyflow.ai.entity.QaMessage;
import com.studyflow.ai.entity.QaSession;
import com.studyflow.ai.entity.QaSessionMaterial;
import com.studyflow.ai.enums.QaMessageRoleEnum;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.gateway.AiGateway;
import com.studyflow.ai.mapper.MaterialMapper;
import com.studyflow.ai.mapper.QaMessageMapper;
import com.studyflow.ai.mapper.QaSessionMapper;
import com.studyflow.ai.mapper.QaSessionMaterialMapper;
import com.studyflow.ai.service.RAGQueryService;
import com.studyflow.ai.service.VectorStoreService;
import com.studyflow.ai.service.rag.ChunkReference;
import com.studyflow.ai.service.rag.QaAnswerResult;
import com.studyflow.ai.service.rag.QaSessionContextCache;
import com.studyflow.ai.service.rag.RagPromptBuilder;
import com.studyflow.ai.service.vector.ChunkSearchResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGQueryServiceImpl implements RAGQueryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MaterialMapper materialMapper;

    private final QaSessionMapper qaSessionMapper;

    private final QaSessionMaterialMapper qaSessionMaterialMapper;

    private final QaMessageMapper qaMessageMapper;

    private final VectorStoreService vectorStoreService;

    private final AiGateway aiGateway;

    private final RagPromptBuilder ragPromptBuilder;

    private final QaSessionContextCache qaSessionContextCache;

    private final RagProperties ragProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaSession createSession(Long userId, CreateQaSessionDTO createQaSessionDTO) {
        List<Long> materialIds = resolveMaterialIds(createQaSessionDTO.getMaterialId(), createQaSessionDTO.getMaterialIds());
        List<Material> materials = getOwnedMaterials(userId, materialIds);
        Material primaryMaterial = materials.get(0);
        QaSession qaSession = new QaSession();
        qaSession.setUserId(userId);
        qaSession.setMaterialId(primaryMaterial.getId());
        qaSession.setSessionName(resolveSessionName(primaryMaterial, materials, createQaSessionDTO.getSessionName()));
        qaSessionMapper.insert(qaSession);
        saveSessionMaterials(userId, qaSession.getId(), materials);
        return qaSession;
    }

    @Override
    public List<QaSession> listSessions(Long userId) {
        return qaSessionMapper.selectList(new LambdaQueryWrapper<QaSession>()
                .eq(QaSession::getUserId, userId)
                .orderByDesc(QaSession::getUpdateTime)
                .orderByDesc(QaSession::getCreateTime));
    }

    @Override
    public List<Long> listSessionMaterialIds(Long userId, Long sessionId) {
        getOwnedSession(userId, sessionId);
        return qaSessionMaterialMapper.selectList(new LambdaQueryWrapper<QaSessionMaterial>()
                        .eq(QaSessionMaterial::getSessionId, sessionId)
                        .eq(QaSessionMaterial::getUserId, userId)
                        .orderByAsc(QaSessionMaterial::getCreateTime))
                .stream()
                .map(QaSessionMaterial::getMaterialId)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaSession updateSessionMaterials(Long userId, Long sessionId, UpdateQaSessionMaterialsDTO updateQaSessionMaterialsDTO) {
        QaSession qaSession = getOwnedSession(userId, sessionId);
        List<Long> materialIds = resolveMaterialIds(null, updateQaSessionMaterialsDTO.getMaterialIds());
        List<Material> materials = getOwnedMaterials(userId, materialIds);
        qaSessionMaterialMapper.delete(new LambdaQueryWrapper<QaSessionMaterial>()
                .eq(QaSessionMaterial::getSessionId, sessionId)
                .eq(QaSessionMaterial::getUserId, userId));
        saveSessionMaterials(userId, sessionId, materials);
        QaSession updateSession = new QaSession();
        updateSession.setId(qaSession.getId());
        updateSession.setMaterialId(materials.get(0).getId());
        qaSessionMapper.updateById(updateSession);
        return qaSessionMapper.selectById(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaAnswerResult ask(Long userId, Long sessionId, AskQuestionDTO askQuestionDTO) {
        QaSession qaSession = getOwnedSession(userId, sessionId);
        List<Long> materialIds = listSessionMaterialIds(userId, qaSession.getId());
        if (materialIds.isEmpty()) {
            materialIds = List.of(qaSession.getMaterialId());
        }
        List<Material> materials = getOwnedMaterials(userId, materialIds);
        Map<Long, Material> materialMap = materials.stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        Material primaryMaterial = materials.get(0);
        int topK = askQuestionDTO.getTopK() == null ? ragProperties.getTopK() : askQuestionDTO.getTopK();
        List<ChunkSearchResult> searchResults = vectorStoreService.searchByMaterialIds(materialIds, askQuestionDTO.getQuestion(), topK);
        if (searchResults.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.QA_CONTEXT_NOT_FOUND);
        }
        List<ChunkReference> references = searchResults.stream()
                .map(item -> ChunkReference.builder()
                        .chunkId(item.getChunk().getId())
                        .materialId(item.getChunk().getMaterialId())
                        .fileName(resolveReferenceFileName(materialMap, item.getChunk().getMaterialId()))
                        .chunkIndex(item.getChunk().getChunkIndex())
                        .score(item.getScore())
                        .chunkText(item.getChunk().getChunkText())
                        .build())
                .toList();
        List<String> contexts = references.stream()
                .map(item -> "File " + item.getFileName() + ", Chunk " + item.getChunkIndex() + ": " + item.getChunkText())
                .toList();
        String prompt = ragPromptBuilder.build(
                primaryMaterial,
                askQuestionDTO.getQuestion(),
                qaSessionContextCache.recentHistory(qaSession.getId(), ragProperties.getHistorySize()));
        long startTime = System.currentTimeMillis();
        String answer = aiGateway.answer(prompt, contexts);
        log.info("RAG answer generated, sessionId={}, materialIds={}, topK={}, costMs={}",
                qaSession.getId(), materialIds, topK, System.currentTimeMillis() - startTime);

        QaMessage questionMessage = new QaMessage();
        questionMessage.setSessionId(qaSession.getId());
        questionMessage.setUserId(userId);
        questionMessage.setMaterialId(primaryMaterial.getId());
        questionMessage.setRole(QaMessageRoleEnum.USER.name());
        questionMessage.setContent(askQuestionDTO.getQuestion().trim());
        qaMessageMapper.insert(questionMessage);

        QaMessage answerMessage = new QaMessage();
        answerMessage.setSessionId(qaSession.getId());
        answerMessage.setUserId(userId);
        answerMessage.setMaterialId(primaryMaterial.getId());
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

    private String resolveSessionName(Material primaryMaterial, List<Material> materials, String sessionName) {
        if (StringUtils.hasText(sessionName)) {
            return sessionName.trim();
        }
        if (materials.size() == 1) {
            return "Q&A - " + primaryMaterial.getFileName();
        }
        return "Q&A - " + primaryMaterial.getFileName() + " and " + (materials.size() - 1) + " more materials";
    }

    private List<Material> getOwnedMaterials(Long userId, List<Long> materialIds) {
        List<Material> materials = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .eq(Material::getUserId, userId)
                .in(Material::getId, materialIds));
        if (materials.size() != materialIds.size()) {
            throw new BusinessException(ResultCodeEnum.MATERIAL_NOT_FOUND);
        }
        Map<Long, Material> materialMap = materials.stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        return materialIds.stream()
                .map(materialMap::get)
                .toList();
    }

    private List<Long> resolveMaterialIds(Long materialId, List<Long> materialIds) {
        LinkedHashSet<Long> resolvedIds = new LinkedHashSet<>();
        if (materialIds != null) {
            materialIds.stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(resolvedIds::add);
        }
        if (resolvedIds.isEmpty() && materialId != null && materialId > 0) {
            resolvedIds.add(materialId);
        }
        if (resolvedIds.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.BAD_REQUEST, "materialIds cannot be empty");
        }
        if (resolvedIds.size() > 20) {
            throw new BusinessException(ResultCodeEnum.BAD_REQUEST, "materialIds size must be at most 20");
        }
        return List.copyOf(resolvedIds);
    }

    private void saveSessionMaterials(Long userId, Long sessionId, List<Material> materials) {
        for (Material material : materials) {
            QaSessionMaterial qaSessionMaterial = new QaSessionMaterial();
            qaSessionMaterial.setSessionId(sessionId);
            qaSessionMaterial.setUserId(userId);
            qaSessionMaterial.setMaterialId(material.getId());
            qaSessionMaterialMapper.insert(qaSessionMaterial);
        }
    }

    private String resolveReferenceFileName(Map<Long, Material> materialMap, Long materialId) {
        Material material = materialMap.get(materialId);
        return material == null ? "unknown material" : material.getFileName();
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
