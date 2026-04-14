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
import com.studyflow.ai.service.rag.QaAnswerStreamObserver;
import com.studyflow.ai.service.rag.QaSessionContextCache;
import com.studyflow.ai.service.rag.RagPromptBuilder;
import com.studyflow.ai.service.vector.ChunkSearchResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    public void deleteSession(Long userId, Long sessionId) {
        getOwnedSession(userId, sessionId);
        qaMessageMapper.delete(new LambdaQueryWrapper<QaMessage>()
                .eq(QaMessage::getSessionId, sessionId)
                .eq(QaMessage::getUserId, userId));
        qaSessionMaterialMapper.delete(new LambdaQueryWrapper<QaSessionMaterial>()
                .eq(QaSessionMaterial::getSessionId, sessionId)
                .eq(QaSessionMaterial::getUserId, userId));
        qaSessionMapper.delete(new LambdaQueryWrapper<QaSession>()
                .eq(QaSession::getId, sessionId)
                .eq(QaSession::getUserId, userId));
        qaSessionContextCache.evict(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaAnswerResult ask(Long userId, Long sessionId, AskQuestionDTO askQuestionDTO) {
        AskExecutionContext context = buildAskExecutionContext(userId, sessionId, askQuestionDTO);
        long startTime = System.currentTimeMillis();
        String answer = aiGateway.answer(context.prompt(), context.contexts());
        log.info("RAG answer generated, sessionId={}, materialIds={}, topK={}, costMs={}",
                context.qaSession().getId(), context.materialIds(), context.topK(), System.currentTimeMillis() - startTime);

        QaMessage questionMessage = persistQuestionMessage(userId, context.primaryMaterial(), context.qaSession(), askQuestionDTO);
        QaMessage answerMessage = persistAnswerMessage(userId, context.primaryMaterial(), context.qaSession(), answer, context.references());
        qaSessionContextCache.append(context.qaSession().getId(), QaMessageRoleEnum.USER.name(),
                questionMessage.getContent(), ragProperties.getHistorySize());
        qaSessionContextCache.append(context.qaSession().getId(), QaMessageRoleEnum.ASSISTANT.name(),
                answerMessage.getContent(), ragProperties.getHistorySize());

        return QaAnswerResult.builder()
                .sessionId(context.qaSession().getId())
                .questionMessage(questionMessage)
                .answerMessage(answerMessage)
                .references(context.references())
                .build();
    }

    @Override
    public void streamAnswer(Long userId, Long sessionId, AskQuestionDTO askQuestionDTO, QaAnswerStreamObserver observer) {
        AskExecutionContext context = buildAskExecutionContext(userId, sessionId, askQuestionDTO);
        QaMessage questionMessage = persistQuestionMessage(userId, context.primaryMaterial(), context.qaSession(), askQuestionDTO);
        qaSessionContextCache.append(context.qaSession().getId(), QaMessageRoleEnum.USER.name(),
                questionMessage.getContent(), ragProperties.getHistorySize());
        observer.onContext(context.references());
        StringBuilder answerBuilder = new StringBuilder();
        long startTime = System.currentTimeMillis();
        AtomicInteger chunkEventCount = new AtomicInteger(0);
        AtomicLong firstTokenCostMs = new AtomicLong(-1L);
        aiGateway.streamAnswer(context.prompt(), context.contexts(), new com.studyflow.ai.gateway.AiAnswerStreamHandler() {
            @Override
            public void onNext(String token) {
                if (!StringUtils.hasText(token)) {
                    return;
                }
                chunkEventCount.incrementAndGet();
                if (firstTokenCostMs.compareAndSet(-1L, System.currentTimeMillis() - startTime)) {
                    log.info("RAG streaming first token arrived, sessionId={}, materialIds={}, firstTokenCostMs={}",
                            context.qaSession().getId(), context.materialIds(), firstTokenCostMs.get());
                }
                answerBuilder.append(token);
                observer.onToken(token);
            }

            @Override
            public void onComplete() {
                try {
                    String answer = answerBuilder.toString().trim();
                    if (!StringUtils.hasText(answer)) {
                        throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "AI answer is empty");
                    }
                    QaMessage answerMessage = persistAnswerMessage(
                            userId, context.primaryMaterial(), context.qaSession(), answer, context.references());
                    qaSessionContextCache.append(context.qaSession().getId(), QaMessageRoleEnum.ASSISTANT.name(),
                            answerMessage.getContent(), ragProperties.getHistorySize());
                    log.info("RAG streaming answer completed, sessionId={}, materialIds={}, topK={}, chunkEvents={}, firstTokenCostMs={}, costMs={}",
                            context.qaSession().getId(), context.materialIds(), context.topK(),
                            chunkEventCount.get(), firstTokenCostMs.get(),
                            System.currentTimeMillis() - startTime);
                    observer.onComplete(QaAnswerResult.builder()
                            .sessionId(context.qaSession().getId())
                            .questionMessage(questionMessage)
                            .answerMessage(answerMessage)
                            .references(context.references())
                            .build());
                } catch (RuntimeException exception) {
                    observer.onError(exception);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                observer.onError(throwable);
            }
        });
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
            return primaryMaterial.getFileName() + " 问答";
        }
        return primaryMaterial.getFileName() + " 等 " + materials.size() + " 份资料问答";
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

    private String trimContext(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 600 ? normalized : normalized.substring(0, 600) + "...";
    }

    private AskExecutionContext buildAskExecutionContext(Long userId, Long sessionId, AskQuestionDTO askQuestionDTO) {
        QaSession qaSession = getOwnedSession(userId, sessionId);
        List<Long> materialIds = listSessionMaterialIds(userId, qaSession.getId());
        if (materialIds.isEmpty()) {
            materialIds = List.of(qaSession.getMaterialId());
        }
        List<Material> materials = getOwnedMaterials(userId, materialIds);
        Map<Long, Material> materialMap = buildReferenceMaterialMap(materials);
        Material primaryMaterial = materials.get(0);
        int topK = askQuestionDTO.getTopK() == null ? ragProperties.getTopK() : askQuestionDTO.getTopK();
        List<Long> effectiveMaterialIds = materials.stream()
                .map(this::effectiveMaterialId)
                .distinct()
                .toList();
        List<ChunkSearchResult> searchResults = vectorStoreService.searchByMaterialIds(effectiveMaterialIds, askQuestionDTO.getQuestion(), topK);
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
                .limit(3)
                .map(item -> "资料《" + item.getFileName() + "》第 " + item.getChunkIndex() + " 段："
                        + trimContext(item.getChunkText()))
                .toList();
        String prompt = ragPromptBuilder.build(
                primaryMaterial,
                askQuestionDTO.getQuestion(),
                qaSessionContextCache.recentHistory(qaSession.getId(), ragProperties.getHistorySize()));
        return new AskExecutionContext(qaSession, materialIds, primaryMaterial, topK, references, contexts, prompt);
    }

    private Map<Long, Material> buildReferenceMaterialMap(List<Material> materials) {
        Map<Long, Material> materialMap = materials.stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        for (Material material : materials) {
            if (material.getReuseSourceMaterialId() != null) {
                materialMap.putIfAbsent(material.getReuseSourceMaterialId(), material);
            }
        }
        return materialMap;
    }

    private Long effectiveMaterialId(Material material) {
        return material.getReuseSourceMaterialId() == null ? material.getId() : material.getReuseSourceMaterialId();
    }

    private QaMessage persistQuestionMessage(Long userId, Material primaryMaterial, QaSession qaSession, AskQuestionDTO askQuestionDTO) {
        QaMessage questionMessage = new QaMessage();
        questionMessage.setSessionId(qaSession.getId());
        questionMessage.setUserId(userId);
        questionMessage.setMaterialId(primaryMaterial.getId());
        questionMessage.setRole(QaMessageRoleEnum.USER.name());
        questionMessage.setContent(askQuestionDTO.getQuestion().trim());
        qaMessageMapper.insert(questionMessage);
        return questionMessage;
    }

    private QaMessage persistAnswerMessage(
            Long userId,
            Material primaryMaterial,
            QaSession qaSession,
            String answer,
            List<ChunkReference> references) {
        QaMessage answerMessage = new QaMessage();
        answerMessage.setSessionId(qaSession.getId());
        answerMessage.setUserId(userId);
        answerMessage.setMaterialId(primaryMaterial.getId());
        answerMessage.setRole(QaMessageRoleEnum.ASSISTANT.name());
        answerMessage.setContent(answer);
        answerMessage.setReferenceChunks(writeAsJson(references));
        qaMessageMapper.insert(answerMessage);
        return answerMessage;
    }

    private record AskExecutionContext(
            QaSession qaSession,
            List<Long> materialIds,
            Material primaryMaterial,
            int topK,
            List<ChunkReference> references,
            List<String> contexts,
            String prompt) {
    }
}
