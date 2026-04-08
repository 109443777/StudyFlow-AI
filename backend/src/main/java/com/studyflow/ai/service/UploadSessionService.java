package com.studyflow.ai.service;

import com.studyflow.ai.dto.CompleteUploadDTO;
import com.studyflow.ai.dto.InitUploadDTO;
import com.studyflow.ai.dto.UploadChunkDTO;
import com.studyflow.ai.vo.ChunkUploadVO;
import com.studyflow.ai.vo.InitUploadVO;
import com.studyflow.ai.vo.MaterialVO;
import com.studyflow.ai.vo.UploadedChunksVO;

public interface UploadSessionService {

    InitUploadVO initUpload(InitUploadDTO initUploadDTO);

    ChunkUploadVO uploadChunk(UploadChunkDTO uploadChunkDTO);

    UploadedChunksVO checkUploadedChunks(String uploadId);

    MaterialVO completeUpload(CompleteUploadDTO completeUploadDTO);
}
