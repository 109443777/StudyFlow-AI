const STORAGE_KEYS = {
    token: "studyflow.accessToken",
    latestMaterialId: "studyflow.latestMaterialId",
    latestUploadId: "studyflow.latestUploadId",
    latestSessionId: "studyflow.latestSessionId"
};

const RESULT_SUCCESS_CODE = 0;
const LOG_LIMIT = 80;
const LATEST_VALUE_STORAGE_MAP = {
    materialId: STORAGE_KEYS.latestMaterialId,
    uploadId: STORAGE_KEYS.latestUploadId,
    sessionId: STORAGE_KEYS.latestSessionId
};

function getToken() {
    return localStorage.getItem(STORAGE_KEYS.token) || "";
}

function setToken(token) {
    if (token) {
        localStorage.setItem(STORAGE_KEYS.token, token);
        return;
    }
    localStorage.removeItem(STORAGE_KEYS.token);
}

function clearToken() {
    localStorage.removeItem(STORAGE_KEYS.token);
}

function getLatestStorageKey(latestKey) {
    return LATEST_VALUE_STORAGE_MAP[latestKey] || "";
}

function saveLatestValue(latestKey, value) {
    const storageKey = getLatestStorageKey(latestKey);
    if (!storageKey) {
        return;
    }

    const normalizedValue = value === undefined || value === null ? "" : String(value).trim();
    if (!normalizedValue) {
        return;
    }

    localStorage.setItem(storageKey, normalizedValue);
    fillLatestValues();
}

function getLatestValue(latestKey) {
    const storageKey = getLatestStorageKey(latestKey);
    return storageKey ? (localStorage.getItem(storageKey) || "") : "";
}

function fillLatestValues(root = document) {
    root.querySelectorAll("[data-latest-key]").forEach((input) => {
        const latestKey = input.dataset.latestKey;
        const latestValue = getLatestValue(latestKey);
        if (!latestValue) {
            return;
        }

        if (document.activeElement === input && input.value) {
            return;
        }

        if (!input.value || input.dataset.autofilled === "true") {
            input.value = latestValue;
            input.dataset.autofilled = "true";
        }
    });
}

function bindLatestValueInputs(root = document) {
    root.querySelectorAll("[data-latest-key]").forEach((input) => {
        const saveCurrentValue = () => {
            const latestKey = input.dataset.latestKey;
            if (!latestKey) {
                return;
            }

            const value = typeof input.value === "string" ? input.value.trim() : "";
            if (!value) {
                return;
            }

            input.dataset.autofilled = "false";
            saveLatestValue(latestKey, value);
        };

        input.addEventListener("change", saveCurrentValue);
        input.addEventListener("blur", saveCurrentValue);
        input.addEventListener("input", () => {
            input.dataset.autofilled = "false";
        });
    });
}

function safeJsonParse(value) {
    try {
        return JSON.parse(value);
    } catch (error) {
        return value;
    }
}

function formatJson(value) {
    const normalizedValue = value === undefined ? null : value;
    return JSON.stringify(normalizedValue, null, 2);
}

function renderJson(target, value) {
    if (!target) {
        return;
    }

    target.textContent = formatJson(value);
}

function setButtonBusy(button, busy) {
    if (!button) {
        return;
    }

    if (!button.dataset.defaultLabel) {
        button.dataset.defaultLabel = button.textContent;
    }

    button.disabled = busy;
    button.textContent = busy ? (button.dataset.loadingLabel || "Working...") : button.dataset.defaultLabel;
}

function buildLogLine({ method, path, ok, message }) {
    const timestamp = new Date().toLocaleString("zh-CN", { hour12: false });
    const statusText = ok ? "SUCCESS" : "FAIL";
    const detail = message || (ok ? "OK" : "Request failed");
    return `[${timestamp}] ${method} ${path} ${statusText} ${detail}`;
}

function clearLogPlaceholder(logContainer) {
    const placeholder = logContainer.querySelector("[data-log-placeholder]");
    if (placeholder) {
        placeholder.remove();
    }
}

function writeLog(entry) {
    const logContainer = document.querySelector("[data-log-container]");
    if (!logContainer) {
        return;
    }

    clearLogPlaceholder(logContainer);

    const line = document.createElement("div");
    line.className = `log-entry ${entry.ok ? "log-entry-success" : "log-entry-fail"}`;
    line.textContent = buildLogLine(entry);
    logContainer.prepend(line);

    while (logContainer.children.length > LOG_LIMIT) {
        logContainer.removeChild(logContainer.lastElementChild);
    }
}

async function readResponseBody(response) {
    const rawText = await response.text();
    if (!rawText) {
        return null;
    }

    return safeJsonParse(rawText);
}

function createRequestError(message, details = {}) {
    const error = new Error(message, details.cause ? { cause: details.cause } : undefined);
    if (details.result !== undefined) {
        error.result = details.result;
    }
    if (details.response !== undefined) {
        error.response = details.response;
    }
    if (details.method) {
        error.method = details.method;
    }
    if (details.path) {
        error.path = details.path;
    }
    return error;
}

function isBinaryBody(value) {
    return typeof Blob !== "undefined" && value instanceof Blob
        || typeof ArrayBuffer !== "undefined" && value instanceof ArrayBuffer
        || typeof ArrayBuffer !== "undefined" && ArrayBuffer.isView && ArrayBuffer.isView(value);
}

function buildRequestBody(method, headers, body) {
    if (method === "GET" || method === "HEAD" || body === undefined || body === null) {
        return undefined;
    }

    if (typeof FormData !== "undefined" && body instanceof FormData) {
        return body;
    }

    if (typeof URLSearchParams !== "undefined" && body instanceof URLSearchParams) {
        return body;
    }

    if (typeof body === "string" || isBinaryBody(body)) {
        return body;
    }

    if (!headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }

    return JSON.stringify(body);
}

async function apiRequest(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    const token = getToken();

    if (token && !headers.has("Authorization")) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    const requestBody = buildRequestBody(method, headers, options.body);

    let response;
    let payload;

    try {
        response = await fetch(path, {
            method,
            headers,
            body: requestBody
        });
        payload = await readResponseBody(response);
    } catch (error) {
        const networkMessage = error instanceof Error ? error.message : "Unknown network error";
        writeLog({ method, path, ok: false, message: networkMessage });
        throw createRequestError(networkMessage, { cause: error, method, path });
    }

    if (!payload || typeof payload !== "object" || !("code" in payload)) {
        const invalidMessage = "Invalid Result payload";
        writeLog({ method, path, ok: false, message: invalidMessage });
        throw createRequestError(invalidMessage, { result: payload, response, method, path });
    }

    const resultMessage = payload.message || (response.ok ? "OK" : `HTTP ${response.status}`);
    const success = response.ok && payload.code === RESULT_SUCCESS_CODE;
    writeLog({ method, path, ok: success, message: resultMessage });

    if (!success) {
        throw createRequestError(resultMessage, { result: payload, response, method, path });
    }

    return payload;
}

function getAuthCard() {
    return document.getElementById("authCard");
}

function updateTokenStatus() {
    const token = getToken();
    const tokenStatus = document.querySelector("[data-role='hero-token-status']");
    const authCard = getAuthCard();
    const authStatus = authCard ? authCard.querySelector("[data-role='auth-status']") : null;
    const tokenEcho = authCard ? authCard.querySelector("[data-role='token-echo']") : null;
    const tokenSummary = token ? `${token.slice(0, 16)}${token.length > 16 ? "..." : ""}` : "Stored in localStorage only";

    if (tokenStatus) {
        tokenStatus.textContent = token ? "Token loaded" : "No token";
        tokenStatus.classList.toggle("status-pill-muted", !token);
    }

    if (authStatus) {
        authStatus.textContent = token ? "Authorized locally" : "Anonymous";
    }

    if (tokenEcho) {
        tokenEcho.textContent = tokenSummary;
        tokenEcho.title = token || "";
    }
}

function renderError(target, error) {
    if (!target) {
        return;
    }

    if (error && typeof error === "object" && error.result) {
        renderJson(target, error.result);
        return;
    }

    renderJson(target, { message: error instanceof Error ? error.message : String(error) });
}

function formToObject(form) {
    return Object.fromEntries(new FormData(form).entries());
}

function buildQueryString(params) {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null) {
            return;
        }

        const normalizedValue = String(value).trim();
        if (!normalizedValue) {
            return;
        }

        searchParams.set(key, normalizedValue);
    });
    const queryString = searchParams.toString();
    return queryString ? `?${queryString}` : "";
}

function requireTrimmedValue(input, fieldName) {
    const value = input && typeof input.value === "string" ? input.value.trim() : "";
    if (!value) {
        throw new Error(`${fieldName} is required`);
    }
    return value;
}

function requireNumericValue(input, fieldName) {
    const value = requireTrimmedValue(input, fieldName);
    const parsedValue = Number(value);
    if (!Number.isFinite(parsedValue)) {
        throw new Error(`${fieldName} must be a valid number`);
    }
    return parsedValue;
}

function requireFile(input, fieldName) {
    const file = input && input.files ? input.files[0] : null;
    if (!file) {
        throw new Error(`${fieldName} is required`);
    }
    return file;
}

function rememberMaterialId(materialId) {
    if (materialId !== undefined && materialId !== null && materialId !== "") {
        saveLatestValue("materialId", materialId);
    }
}

function rememberUploadId(uploadId) {
    if (uploadId) {
        saveLatestValue("uploadId", uploadId);
    }
}

function rememberSessionId(sessionId) {
    if (sessionId !== undefined && sessionId !== null && sessionId !== "") {
        saveLatestValue("sessionId", sessionId);
    }
}

function rememberMaterialFromPayload(data) {
    if (!data) {
        return;
    }

    if (Array.isArray(data)) {
        if (data.length > 0 && data[0] && data[0].id !== undefined) {
            rememberMaterialId(data[0].id);
        }
        return;
    }

    if (data.materialId !== undefined) {
        rememberMaterialId(data.materialId);
    }

    if (data.id !== undefined && data.fileName !== undefined) {
        rememberMaterialId(data.id);
    }
}

function rememberParseTaskPayload(data) {
    if (!data) {
        return;
    }

    if (Array.isArray(data)) {
        if (data.length > 0 && data[0] && data[0].materialId !== undefined) {
            rememberMaterialId(data[0].materialId);
        }
        return;
    }

    if (data.materialId !== undefined) {
        rememberMaterialId(data.materialId);
    }
}

function rememberQaPayload(data) {
    if (!data) {
        return;
    }

    if (Array.isArray(data)) {
        if (data.length > 0) {
            rememberQaPayload(data[0]);
        }
        return;
    }

    if (data.sessionId !== undefined) {
        rememberSessionId(data.sessionId);
    }

    if (data.id !== undefined && data.sessionName !== undefined) {
        rememberSessionId(data.id);
    }

    if (data.materialId !== undefined) {
        rememberMaterialId(data.materialId);
    }
}

function rememberStudyPlanPayload(data) {
    if (!data) {
        return;
    }

    if (Array.isArray(data)) {
        if (data.length > 0 && data[0] && data[0].materialId !== undefined) {
            rememberMaterialId(data[0].materialId);
        }
        return;
    }

    if (data.materialId !== undefined) {
        rememberMaterialId(data.materialId);
    }
}

function rememberFailurePayload(data) {
    if (!data) {
        return;
    }

    if (Array.isArray(data)) {
        if (data.length > 0 && data[0] && data[0].materialId !== undefined) {
            rememberMaterialId(data[0].materialId);
        }
        return;
    }

    if (data.materialId !== undefined) {
        rememberMaterialId(data.materialId);
    }
}

async function handleHealthCheck(button, output) {
    setButtonBusy(button, true);
    try {
        const result = await apiRequest("/api/health");
        renderJson(output, result.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleRegister(form, button, output) {
    setButtonBusy(button, true);
    try {
        const payload = await apiRequest("/api/auth/register", {
            method: "POST",
            body: formToObject(form)
        });
        renderJson(output, payload.data);
        form.reset();
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleLogin(form, button, output) {
    setButtonBusy(button, true);
    try {
        const payload = await apiRequest("/api/auth/login", {
            method: "POST",
            body: formToObject(form)
        });
        const loginData = payload.data || {};
        setToken(loginData.accessToken || "");
        updateTokenStatus();
        renderJson(output, loginData);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleCurrentUser(button, output) {
    setButtonBusy(button, true);
    try {
        const payload = await apiRequest("/api/users/me");
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

function handleClearToken(output) {
    clearToken();
    updateTokenStatus();
    renderJson(output, { message: "Token cleared" });
    writeLog({
        method: "LOCAL",
        path: "localStorage",
        ok: true,
        message: "Cleared access token"
    });
}

async function handleMaterialUpload(form, button, output) {
    setButtonBusy(button, true);
    try {
        const fileInput = form.querySelector("input[name='file']");
        const file = requireFile(fileInput, "file");
        const body = new FormData();
        body.append("file", file);

        const payload = await apiRequest("/api/materials/upload", {
            method: "POST",
            body
        });
        rememberMaterialFromPayload(payload.data);
        renderJson(output, payload.data);
        form.reset();
        fillLatestValues(form.closest("#uploadCard") || document);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleUploadInit(form, button, output) {
    setButtonBusy(button, true);
    try {
        const fileNameInput = document.getElementById("chunkFileName");
        const fileSizeInput = document.getElementById("chunkFileSize");
        const totalChunksInput = document.getElementById("chunkTotalChunks");
        const fileMd5Input = document.getElementById("chunkFileMd5");

        const payload = await apiRequest("/api/material-uploads/init", {
            method: "POST",
            body: {
                fileName: requireTrimmedValue(fileNameInput, "fileName"),
                fileSize: requireNumericValue(fileSizeInput, "fileSize"),
                totalChunks: requireNumericValue(totalChunksInput, "totalChunks"),
                fileMd5: requireTrimmedValue(fileMd5Input, "fileMd5")
            }
        });
        if (payload.data) {
            rememberUploadId(payload.data.uploadId);
            rememberMaterialId(payload.data.materialId);
        }
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleUploadChunk(form, button, output) {
    setButtonBusy(button, true);
    try {
        const uploadIdInput = document.getElementById("chunkUploadIdInput");
        const chunkIndexInput = document.getElementById("chunkIndexInput");
        const chunkFileInput = document.getElementById("chunkFileInput");
        const body = new FormData();
        const uploadId = requireTrimmedValue(uploadIdInput, "uploadId");

        body.append("uploadId", uploadId);
        body.append("chunkIndex", String(requireNumericValue(chunkIndexInput, "chunkIndex")));
        body.append("chunk", requireFile(chunkFileInput, "chunk"));

        const payload = await apiRequest("/api/material-uploads/chunk", {
            method: "POST",
            body
        });
        rememberUploadId(uploadId);
        renderJson(output, payload.data);
        form.reset();
        fillLatestValues(form.closest("#uploadCard") || document);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleUploadedChunks(button, output) {
    setButtonBusy(button, true);
    try {
        const uploadIdInput = document.getElementById("uploadedChunksUploadIdInput");
        const uploadId = requireTrimmedValue(uploadIdInput, "uploadId");
        const payload = await apiRequest(`/api/material-uploads/${encodeURIComponent(uploadId)}/chunks`);
        rememberUploadId(uploadId);
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleCompleteUpload(form, button, output) {
    setButtonBusy(button, true);
    try {
        const uploadIdInput = document.getElementById("completeUploadIdInput");
        const uploadId = requireTrimmedValue(uploadIdInput, "uploadId");
        const payload = await apiRequest("/api/material-uploads/complete", {
            method: "POST",
            body: { uploadId }
        });
        rememberUploadId(uploadId);
        rememberMaterialFromPayload(payload.data);
        renderJson(output, payload.data);
        form.reset();
        fillLatestValues(form.closest("#uploadCard") || document);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleMaterialDetail(button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("materialDetailIdInput");
        const materialId = requireNumericValue(materialIdInput, "materialId");
        const payload = await apiRequest(`/api/materials/${materialId}`);
        rememberMaterialFromPayload(payload.data);
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleMaterialList(form, button, output) {
    setButtonBusy(button, true);
    try {
        const materialTypeInput = document.getElementById("materialListTypeInput");
        const parseStatusInput = document.getElementById("materialListParseStatusInput");
        const query = buildQueryString({
            materialType: materialTypeInput ? materialTypeInput.value : "",
            parseStatus: parseStatusInput ? parseStatusInput.value : ""
        });
        const payload = await apiRequest(`/api/materials/my${query}`);
        rememberMaterialFromPayload(payload.data);
        renderJson(output, payload.data);
        form.dataset.lastQuery = query;
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleParseTaskList(form, button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("parseTaskMaterialIdInput");
        const taskTypeInput = document.getElementById("parseTaskTypeInput");
        const statusInput = document.getElementById("parseTaskStatusInput");
        const query = buildQueryString({
            materialId: materialIdInput ? materialIdInput.value : "",
            taskType: taskTypeInput ? taskTypeInput.value : "",
            status: statusInput ? statusInput.value : ""
        });
        const payload = await apiRequest(`/api/parse-tasks${query}`);
        rememberParseTaskPayload(payload.data);
        renderJson(output, payload.data);
        form.dataset.lastQuery = query;
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleDispatchParse(button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("dispatchMaterialIdInput");
        const materialId = requireNumericValue(materialIdInput, "materialId");
        const payload = await apiRequest(`/api/parse-tasks/materials/${materialId}/dispatch`, {
            method: "POST"
        });
        rememberParseTaskPayload(payload.data);
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleMaterialContent(button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("materialContentIdInput");
        const materialId = requireNumericValue(materialIdInput, "materialId");
        const payload = await apiRequest(`/api/material-contents${buildQueryString({ materialId })}`);
        if (payload.data && payload.data.materialId !== undefined) {
            rememberMaterialId(payload.data.materialId);
        }
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleMaterialSummary(button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("materialSummaryIdInput");
        const materialId = requireNumericValue(materialIdInput, "materialId");
        const payload = await apiRequest(`/api/material-summaries${buildQueryString({ materialId })}`);
        if (payload.data && payload.data.materialId !== undefined) {
            rememberMaterialId(payload.data.materialId);
        }
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleGenerateSummary(button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("materialSummaryIdInput");
        const materialId = requireNumericValue(materialIdInput, "materialId");
        const payload = await apiRequest(`/api/material-summaries/${materialId}/generate`, {
            method: "POST"
        });
        if (payload.data && payload.data.materialId !== undefined) {
            rememberMaterialId(payload.data.materialId);
        }
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleMediaTranscript(button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("mediaTranscriptIdInput");
        const materialId = requireNumericValue(materialIdInput, "materialId");
        const payload = await apiRequest(`/api/media-transcripts${buildQueryString({ materialId })}`);
        if (payload.data && payload.data.materialId !== undefined) {
            rememberMaterialId(payload.data.materialId);
        }
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleQaSessionCreate(form, button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("qaSessionMaterialIdInput");
        const sessionNameInput = document.getElementById("qaSessionNameInput");
        const payload = await apiRequest("/api/qa/sessions", {
            method: "POST",
            body: {
                materialId: requireNumericValue(materialIdInput, "materialId"),
                sessionName: sessionNameInput ? sessionNameInput.value.trim() : ""
            }
        });
        rememberQaPayload(payload.data);
        renderJson(output, payload.data);
        form.reset();
        fillLatestValues(form.closest("#qaCard") || document);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleQaAsk(form, button, output) {
    setButtonBusy(button, true);
    try {
        const sessionIdInput = document.getElementById("qaAskSessionIdInput");
        const questionInput = document.getElementById("qaAskQuestionInput");
        const topKInput = document.getElementById("qaAskTopKInput");
        const sessionId = requireNumericValue(sessionIdInput, "sessionId");
        const payload = await apiRequest(`/api/qa/sessions/${sessionId}/ask`, {
            method: "POST",
            body: {
                question: requireTrimmedValue(questionInput, "question"),
                topK: requireNumericValue(topKInput, "topK")
            }
        });
        rememberQaPayload(payload.data);
        renderJson(output, payload.data);
        form.reset();
        fillLatestValues(form.closest("#qaCard") || document);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleQaHistory(button, output) {
    setButtonBusy(button, true);
    try {
        const sessionIdInput = document.getElementById("qaHistorySessionIdInput");
        const sessionId = requireNumericValue(sessionIdInput, "sessionId");
        const payload = await apiRequest(`/api/qa/sessions/${sessionId}/messages`);
        rememberQaPayload(payload.data);
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleReviewOutline(form, button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("reviewOutlineMaterialIdInput");
        const planNameInput = document.getElementById("reviewOutlinePlanNameInput");
        const payload = await apiRequest("/api/study-plans/review-outline", {
            method: "POST",
            body: {
                materialId: requireNumericValue(materialIdInput, "materialId"),
                planName: planNameInput ? planNameInput.value.trim() : ""
            }
        });
        rememberStudyPlanPayload(payload.data);
        renderJson(output, payload.data);
        form.reset();
        fillLatestValues(form.closest("#studyCard") || document);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleExamPlan(form, button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("examPlanMaterialIdInput");
        const examDateInput = document.getElementById("examPlanDateInput");
        const planNameInput = document.getElementById("examPlanNameInput");
        const payload = await apiRequest("/api/study-plans/exam-plan", {
            method: "POST",
            body: {
                materialId: requireNumericValue(materialIdInput, "materialId"),
                examDate: requireTrimmedValue(examDateInput, "examDate"),
                planName: planNameInput ? planNameInput.value.trim() : ""
            }
        });
        rememberStudyPlanPayload(payload.data);
        renderJson(output, payload.data);
        form.reset();
        fillLatestValues(form.closest("#studyCard") || document);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleStudyPlanDetail(button, output) {
    setButtonBusy(button, true);
    try {
        const planIdInput = document.getElementById("studyPlanDetailIdInput");
        const planId = requireNumericValue(planIdInput, "planId");
        const payload = await apiRequest(`/api/study-plans/${planId}`);
        rememberStudyPlanPayload(payload.data);
        renderJson(output, payload.data);
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleStudyPlanHistory(form, button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("studyPlanHistoryMaterialIdInput");
        const planTypeInput = document.getElementById("studyPlanHistoryTypeInput");
        const query = buildQueryString({
            materialId: materialIdInput ? materialIdInput.value : "",
            planType: planTypeInput ? planTypeInput.value : ""
        });
        const payload = await apiRequest(`/api/study-plans${query}`);
        rememberStudyPlanPayload(payload.data);
        renderJson(output, payload.data);
        form.dataset.lastQuery = query;
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleFailureList(form, button, output) {
    setButtonBusy(button, true);
    try {
        const materialIdInput = document.getElementById("failureMaterialIdInput");
        const taskTypeInput = document.getElementById("failureTaskTypeInput");
        const recordStatusInput = document.getElementById("failureRecordStatusInput");
        const query = buildQueryString({
            materialId: materialIdInput ? materialIdInput.value : "",
            taskType: taskTypeInput ? taskTypeInput.value : "",
            recordStatus: recordStatusInput ? recordStatusInput.value : ""
        });
        const payload = await apiRequest(`/api/task-failures${query}`);
        rememberFailurePayload(payload.data);
        renderJson(output, payload.data);
        form.dataset.lastQuery = query;
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

async function handleFailureCompensate(form, button, output) {
    setButtonBusy(button, true);
    try {
        const recordIdInput = document.getElementById("failureRecordIdInput");
        const recordId = requireNumericValue(recordIdInput, "recordId");
        const payload = await apiRequest(`/api/task-failures/${recordId}/compensate`, {
            method: "POST"
        });
        rememberFailurePayload(payload.data);
        renderJson(output, payload.data);
        form.reset();
    } catch (error) {
        renderError(output, error);
    } finally {
        setButtonBusy(button, false);
    }
}

function bindSystemActions() {
    const healthButton = document.getElementById("healthCheckButton");
    const healthOutput = document.getElementById("healthResult");

    if (!healthButton || !healthOutput) {
        return;
    }

    healthButton.addEventListener("click", () => {
        handleHealthCheck(healthButton, healthOutput);
    });
}

function bindAuthActions() {
    const authCard = getAuthCard();
    const registerForm = authCard ? authCard.querySelector("[data-form='register']") : null;
    const registerButton = authCard ? authCard.querySelector("[data-action='register']") : null;
    const loginForm = authCard ? authCard.querySelector("[data-form='login']") : null;
    const loginButton = authCard ? authCard.querySelector("[data-action='login']") : null;
    const currentUserButton = authCard ? authCard.querySelector("[data-action='current-user']") : null;
    const clearTokenButton = authCard ? authCard.querySelector("[data-action='clear-token']") : null;
    const authOutput = authCard ? authCard.querySelector("[data-result='auth']") : null;

    if (!authOutput) {
        return;
    }

    if (registerForm && registerButton) {
        registerForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleRegister(registerForm, registerButton, authOutput);
        });
    }

    if (loginForm && loginButton) {
        loginForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleLogin(loginForm, loginButton, authOutput);
        });
    }

    if (currentUserButton) {
        currentUserButton.addEventListener("click", () => {
            handleCurrentUser(currentUserButton, authOutput);
        });
    }

    if (clearTokenButton) {
        clearTokenButton.addEventListener("click", () => {
            handleClearToken(authOutput);
        });
    }
}

function bindUploadActions() {
    const materialUploadForm = document.getElementById("materialUploadForm");
    const materialUploadButton = document.getElementById("materialUploadButton");
    const materialUploadResult = document.getElementById("materialUploadResult");
    const uploadInitForm = document.getElementById("uploadInitForm");
    const uploadInitButton = document.getElementById("uploadInitButton");
    const uploadInitResult = document.getElementById("uploadInitResult");
    const uploadChunkForm = document.getElementById("uploadChunkForm");
    const uploadChunkButton = document.getElementById("uploadChunkButton");
    const uploadChunkResult = document.getElementById("uploadChunkResult");
    const uploadedChunksButton = document.getElementById("uploadedChunksButton");
    const uploadedChunksResult = document.getElementById("uploadedChunksResult");
    const completeUploadForm = document.getElementById("completeUploadForm");
    const completeUploadButton = document.getElementById("completeUploadButton");
    const completeUploadResult = document.getElementById("completeUploadResult");
    const materialDetailButton = document.getElementById("materialDetailButton");
    const materialDetailResult = document.getElementById("materialDetailResult");
    const materialListForm = document.getElementById("materialListForm");
    const materialListButton = document.getElementById("materialListButton");
    const materialListResult = document.getElementById("materialListResult");
    const chunkInitFile = document.getElementById("chunkInitFile");
    const chunkFileName = document.getElementById("chunkFileName");
    const chunkFileSize = document.getElementById("chunkFileSize");

    if (materialUploadForm && materialUploadButton && materialUploadResult) {
        materialUploadForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleMaterialUpload(materialUploadForm, materialUploadButton, materialUploadResult);
        });
    }

    if (uploadInitForm && uploadInitButton && uploadInitResult) {
        uploadInitForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleUploadInit(uploadInitForm, uploadInitButton, uploadInitResult);
        });
    }

    if (uploadChunkForm && uploadChunkButton && uploadChunkResult) {
        uploadChunkForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleUploadChunk(uploadChunkForm, uploadChunkButton, uploadChunkResult);
        });
    }

    if (uploadedChunksButton && uploadedChunksResult) {
        uploadedChunksButton.addEventListener("click", () => {
            handleUploadedChunks(uploadedChunksButton, uploadedChunksResult);
        });
    }

    if (completeUploadForm && completeUploadButton && completeUploadResult) {
        completeUploadForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleCompleteUpload(completeUploadForm, completeUploadButton, completeUploadResult);
        });
    }

    if (materialDetailButton && materialDetailResult) {
        materialDetailButton.addEventListener("click", () => {
            handleMaterialDetail(materialDetailButton, materialDetailResult);
        });
    }

    if (materialListForm && materialListButton && materialListResult) {
        materialListForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleMaterialList(materialListForm, materialListButton, materialListResult);
        });
    }

    if (chunkInitFile && chunkFileName && chunkFileSize) {
        chunkInitFile.addEventListener("change", () => {
            const file = chunkInitFile.files ? chunkInitFile.files[0] : null;
            if (!file) {
                return;
            }

            chunkFileName.value = file.name || "";
            chunkFileName.dataset.autofilled = "false";
            chunkFileSize.value = file.size ? String(file.size) : "";
            chunkFileSize.dataset.autofilled = "false";
        });
    }
}

function bindParseActions() {
    const parseTaskListForm = document.getElementById("parseTaskListForm");
    const parseTaskListButton = document.getElementById("parseTaskListButton");
    const parseTaskListResult = document.getElementById("parseTaskListResult");
    const dispatchParseButton = document.getElementById("dispatchParseButton");
    const dispatchParseResult = document.getElementById("dispatchParseResult");
    const materialContentButton = document.getElementById("materialContentButton");
    const materialContentResult = document.getElementById("materialContentResult");
    const materialSummaryButton = document.getElementById("materialSummaryButton");
    const generateSummaryButton = document.getElementById("generateSummaryButton");
    const materialSummaryQueryResult = document.getElementById("materialSummaryQueryResult");
    const materialSummaryGenerateResult = document.getElementById("materialSummaryGenerateResult");
    const mediaTranscriptButton = document.getElementById("mediaTranscriptButton");
    const mediaTranscriptResult = document.getElementById("mediaTranscriptResult");

    if (parseTaskListForm && parseTaskListButton && parseTaskListResult) {
        parseTaskListForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleParseTaskList(parseTaskListForm, parseTaskListButton, parseTaskListResult);
        });
    }

    if (dispatchParseButton && dispatchParseResult) {
        dispatchParseButton.addEventListener("click", () => {
            handleDispatchParse(dispatchParseButton, dispatchParseResult);
        });
    }

    if (materialContentButton && materialContentResult) {
        materialContentButton.addEventListener("click", () => {
            handleMaterialContent(materialContentButton, materialContentResult);
        });
    }

    if (materialSummaryButton && materialSummaryQueryResult) {
        materialSummaryButton.addEventListener("click", () => {
            handleMaterialSummary(materialSummaryButton, materialSummaryQueryResult);
        });
    }

    if (generateSummaryButton && materialSummaryGenerateResult) {
        generateSummaryButton.addEventListener("click", () => {
            handleGenerateSummary(generateSummaryButton, materialSummaryGenerateResult);
        });
    }

    if (mediaTranscriptButton && mediaTranscriptResult) {
        mediaTranscriptButton.addEventListener("click", () => {
            handleMediaTranscript(mediaTranscriptButton, mediaTranscriptResult);
        });
    }
}

function bindQaActions() {
    const qaSessionCreateForm = document.getElementById("qaSessionCreateForm");
    const qaSessionCreateButton = document.getElementById("qaSessionCreateButton");
    const qaSessionCreateResult = document.getElementById("qaSessionCreateResult");
    const qaAskForm = document.getElementById("qaAskForm");
    const qaAskButton = document.getElementById("qaAskButton");
    const qaAskResult = document.getElementById("qaAskResult");
    const qaHistoryButton = document.getElementById("qaHistoryButton");
    const qaHistoryResult = document.getElementById("qaHistoryResult");

    if (qaSessionCreateForm && qaSessionCreateButton && qaSessionCreateResult) {
        qaSessionCreateForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleQaSessionCreate(qaSessionCreateForm, qaSessionCreateButton, qaSessionCreateResult);
        });
    }

    if (qaAskForm && qaAskButton && qaAskResult) {
        qaAskForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleQaAsk(qaAskForm, qaAskButton, qaAskResult);
        });
    }

    if (qaHistoryButton && qaHistoryResult) {
        qaHistoryButton.addEventListener("click", () => {
            handleQaHistory(qaHistoryButton, qaHistoryResult);
        });
    }
}

function bindStudyActions() {
    const reviewOutlineForm = document.getElementById("reviewOutlineForm");
    const reviewOutlineButton = document.getElementById("reviewOutlineButton");
    const reviewOutlineResult = document.getElementById("reviewOutlineResult");
    const examPlanForm = document.getElementById("examPlanForm");
    const examPlanButton = document.getElementById("examPlanButton");
    const examPlanResult = document.getElementById("examPlanResult");
    const studyPlanDetailButton = document.getElementById("studyPlanDetailButton");
    const studyPlanDetailResult = document.getElementById("studyPlanDetailResult");
    const studyPlanHistoryForm = document.getElementById("studyPlanHistoryForm");
    const studyPlanHistoryButton = document.getElementById("studyPlanHistoryButton");
    const studyPlanHistoryResult = document.getElementById("studyPlanHistoryResult");

    if (reviewOutlineForm && reviewOutlineButton && reviewOutlineResult) {
        reviewOutlineForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleReviewOutline(reviewOutlineForm, reviewOutlineButton, reviewOutlineResult);
        });
    }

    if (examPlanForm && examPlanButton && examPlanResult) {
        examPlanForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleExamPlan(examPlanForm, examPlanButton, examPlanResult);
        });
    }

    if (studyPlanDetailButton && studyPlanDetailResult) {
        studyPlanDetailButton.addEventListener("click", () => {
            handleStudyPlanDetail(studyPlanDetailButton, studyPlanDetailResult);
        });
    }

    if (studyPlanHistoryForm && studyPlanHistoryButton && studyPlanHistoryResult) {
        studyPlanHistoryForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleStudyPlanHistory(studyPlanHistoryForm, studyPlanHistoryButton, studyPlanHistoryResult);
        });
    }
}

function bindFailureActions() {
    const failureListForm = document.getElementById("failureListForm");
    const failureListButton = document.getElementById("failureListButton");
    const failureListResult = document.getElementById("failureListResult");
    const failureCompensateForm = document.getElementById("failureCompensateForm");
    const failureCompensateButton = document.getElementById("failureCompensateButton");
    const failureCompensateResult = document.getElementById("failureCompensateResult");

    if (failureListForm && failureListButton && failureListResult) {
        failureListForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleFailureList(failureListForm, failureListButton, failureListResult);
        });
    }

    if (failureCompensateForm && failureCompensateButton && failureCompensateResult) {
        failureCompensateForm.addEventListener("submit", (event) => {
            event.preventDefault();
            handleFailureCompensate(failureCompensateForm, failureCompensateButton, failureCompensateResult);
        });
    }
}

function bootstrap() {
    updateTokenStatus();
    bindLatestValueInputs();
    fillLatestValues();
    bindSystemActions();
    bindAuthActions();
    bindUploadActions();
    bindParseActions();
    bindQaActions();
    bindStudyActions();
    bindFailureActions();
    writeLog({
        method: "BOOT",
        path: "/index.html",
        ok: true,
        message: "Static console initialized"
    });
}

document.addEventListener("DOMContentLoaded", bootstrap);
