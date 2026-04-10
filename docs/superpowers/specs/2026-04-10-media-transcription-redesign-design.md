# StudyFlow AI 音视频转写链路重构设计

## 背景
当前 StudyFlow AI 已具备音频/视频上传、解析任务中心、转写网关抽象和媒体转写结果落库能力，但真实转写链路仍停留在 mock/占位阶段。现有 `ExternalMediaTranscriptionGateway` 尚未接入任何实际供应商，`VIDEO_TRANSCRIBE` 任务也没有执行视频抽音轨预处理，导致当前实现与真实生产环境仍有明显差距。

参考 DOVideo-AI 的实现模式后，本次重构目标是将视频转写链路改造成“媒体预处理 + ASR 转写 + 文本统一回写”的标准工程方案，并与现有任务中心、摘要链路和治理能力保持兼容。

## 目标
- 为 `VIDEO_TRANSCRIBE` 增加 FFmpeg 抽音轨能力
- 为 `AUDIO_TRANSCRIBE` / `VIDEO_TRANSCRIBE` 接入阿里云百炼文件转写 API
- 保持 `MediaTranscriptionGateway` 抽象，不在业务层写死厂商
- 将转写文本继续统一写入 `material_content`
- 保持与现有 `parse_task`、失败重试、失败记录、后续 `AI_SUMMARY` 派发机制兼容

## 非目标
- 不实现本地 Whisper 部署
- 不实现视频切片并行转写
- 不实现音频降噪、重采样、静音裁剪
- 不改造向量检索到 Milvus
- 不新增前端页面

## 总体方案
采用如下链路：

- 音频：`MinIO -> 下载音频 -> 阿里云百炼文件转写 -> media_transcript -> material_content`
- 视频：`MinIO -> 下载视频 -> FFmpeg 抽音轨(wav) -> 阿里云百炼文件转写 -> media_transcript -> material_content`

处理完成后，现有任务中心继续推进：
`AUDIO_TRANSCRIBE / VIDEO_TRANSCRIBE -> AI_SUMMARY -> EMBEDDING`

## 模块职责设计

### 1. MediaAudioExtractService
新增媒体预处理服务，专门负责视频抽音轨。

职责：
- 接收本地临时视频文件
- 调用本机 `ffmpeg` 命令抽取 `wav` 音频
- 返回抽取后的音频文件结果
- 对 ffmpeg 缺失、命令失败、超时等情况抛出明确业务异常

该服务不负责任何转写逻辑，只负责“视频 -> 音频”。

### 2. MediaTranscriptionGateway
保留现有网关抽象，继续作为音频转文字的唯一外部接口。

职责：
- 接收音频文件或音频字节流
- 调用阿里云百炼文件转写 API
- 解析响应并转成统一的 `MediaTranscriptionResult`

业务层不感知阿里云请求细节，未来若切换供应商，只替换 gateway 实现即可。

### 3. MediaTranscriptService
保留现有编排服务，改造成区分音频和视频处理路径。

职责：
- 从 MinIO 下载原始媒体文件到临时目录
- 如果是音频，直接调用转写网关
- 如果是视频，先调用 `MediaAudioExtractService` 抽出音频，再调用转写网关
- 保存 `media_transcript`
- 将转写文本回写 `material_content`

### 4. 任务中心
不修改总体状态机设计，只扩展执行细节。

职责保持：
- `MaterialTaskExecutionServiceImpl` 继续根据 `ParseTaskTypeEnum` 分发
- `AUDIO_TRANSCRIBE` / `VIDEO_TRANSCRIBE` 仍统一调用 `MediaTranscriptService`
- 失败继续走现有 `retry_count` / `task_failure_record` / dead-letter 机制

## 类设计

### 新增类
- `backend/src/main/java/com/studyflow/ai/service/MediaAudioExtractService.java`
- `backend/src/main/java/com/studyflow/ai/service/impl/MediaAudioExtractServiceImpl.java`
- `backend/src/main/java/com/studyflow/ai/service/media/AudioExtractionResult.java`

### 重点修改类
- `backend/src/main/java/com/studyflow/ai/config/TranscriptionProperties.java`
- `backend/src/main/java/com/studyflow/ai/gateway/ExternalMediaTranscriptionGateway.java`
- `backend/src/main/java/com/studyflow/ai/service/impl/MediaTranscriptServiceImpl.java`
- `backend/src/main/resources/application.yml`
- `backend/config/application-local.yml`（仅本地配置，不入库）

### 可选辅助类
如后续需要隔离临时文件管理，可新增 `MediaTempFileSupport` 工具类；本轮不强制拆出，优先保持改动聚焦。

## 配置设计
在 `studyflow.transcription` 下扩展如下配置：

```yaml
studyflow:
  transcription:
    provider: external
    mock-prefix: "[Mock Transcript]"
    ffmpeg-path: ffmpeg
    temp-dir: ${java.io.tmpdir}/studyflow-media
    external:
      base-url: https://dashscope.aliyuncs.com
      api-key: ${STUDYFLOW_DASHSCOPE_API_KEY:}
      model: paraformer-v2
```

说明：
- `ffmpeg-path`：支持 PATH 中的 `ffmpeg`，也支持绝对路径
- `temp-dir`：统一存放下载后的媒体文件和抽出的音频文件
- `external.base-url/api-key/model`：阿里云百炼文件转写 API 配置
- `provider`：支持 `mock` 和 `external`

## 数据流设计

### 音频链路
1. 从 MinIO 下载音频到本地临时文件
2. 调用 `ExternalMediaTranscriptionGateway`
3. 得到 transcript text / segments / duration
4. 写 `media_transcript`
5. 清洗文本后写 `material_content`

### 视频链路
1. 从 MinIO 下载视频到本地临时文件
2. 调用 `MediaAudioExtractService` 执行 FFmpeg 抽音轨
3. 得到本地 `wav` 临时文件
4. 调用 `ExternalMediaTranscriptionGateway`
5. 得到 transcript text / segments / duration
6. 写 `media_transcript`
7. 清洗文本后写 `material_content`

## 错误处理设计

### 下载失败
- 场景：MinIO 下载失败、临时文件写入失败
- 处理：抛业务异常，记录 `parse_task.fail_reason`
- 后果：由现有重试机制接管

### FFmpeg 执行失败
- 场景：ffmpeg 未安装、命令执行异常、视频损坏、进程超时
- 处理：抛明确业务异常，如“ffmpeg audio extraction failed”
- 后果：任务置为失败，进入现有重试/补偿链路

### 外部 ASR 调用失败
- 场景：鉴权失败、接口超时、限流、响应格式不符合预期
- 处理：统一转为业务异常并保留核心错误信息
- 后果：由现有任务治理机制处理重试和失败记录

### 清理临时文件失败
- 场景：文件句柄占用、删除失败
- 处理：记录 warning 日志，不影响主流程成功结果

## 测试策略

### 1. MediaAudioExtractService 单元/集成测试
- 输入小型视频样例
- 验证抽音频输出文件存在且非空
- 当 ffmpeg 不可用时验证异常路径

说明：若 CI 环境无 ffmpeg，可将该测试设计为条件化集成测试；核心逻辑测试仍需保留。

### 2. ExternalMediaTranscriptionGateway 测试
- 使用 mock HTTP 响应，不直接调用真实阿里云
- 验证请求头、请求体、返回 JSON 解析行为
- 验证转成 `MediaTranscriptionResult` 的逻辑

### 3. MediaTranscriptService 集成测试
- 音频任务：直接转写并落库
- 视频任务：先抽音轨再转写并落库
- 验证 `media_transcript` 与 `material_content` 均被正确写入

### 4. 任务链路回归测试
- 验证 `VIDEO_TRANSCRIBE` 成功后仍会继续派发 `AI_SUMMARY`
- 验证失败仍会写入现有失败记录与重试计数

## 兼容性与扩展性
- 保持 `MediaTranscriptionGateway` 抽象，业务层不绑定阿里云
- `MediaAudioExtractService` 独立存在，后续可扩展降噪、采样率转换、切片预处理
- 任务中心、RAG、AI 摘要模块无须大改，只消费 `material_content`

## 实施边界
本轮实施只覆盖：
- FFmpeg 抽音轨
- 阿里云百炼文件转写接入
- 媒体转写服务编排改造
- 测试与配置补充

本轮不覆盖：
- Milvus 向量库接入
- Whisper 本地部署
- 多段音频并行转写
- OCR / 图片转文字
