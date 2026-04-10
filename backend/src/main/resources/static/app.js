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

function applyChineseConsoleLocale() {
    document.documentElement.lang = "zh-CN";
    document.title = "\u0053\u0074\u0075\u0064\u0079\u0046\u006c\u006f\u0077\u0020\u0041\u0049\u0020\u540e\u7aef\u6d4b\u8bd5\u53f0";

    const replacements = [
        ["StudyFlow AI test console", "\u0053\u0074\u0075\u0064\u0079\u0046\u006c\u006f\u0077\u0020\u0041\u0049\u0020\u540e\u7aef\u6d4b\u8bd5\u53f0"],
        ["A static shell for auth, upload, parse, QA, study flow, and failure review. This console now supports health checks, auth flows, current-user queries, and request logs.", "\u4e00\u4e2a\u9762\u5411\u540e\u7aef\u8054\u8c03\u7684\u9759\u6001\u6d4b\u8bd5\u53f0\uff0c\u8986\u76d6\u8ba4\u8bc1\u3001\u4e0a\u4f20\u3001\u89e3\u6790\u3001\u95ee\u7b54\u3001\u5b66\u4e60\u8f85\u52a9\u548c\u5931\u8d25\u8865\u507f\u7b49\u6838\u5fc3\u6d41\u7a0b\u3002\u5f53\u524d\u9875\u9762\u5df2\u7ecf\u652f\u6301\u5065\u5eb7\u68c0\u67e5\u3001\u767b\u5f55\u6001\u64cd\u4f5c\u3001\u5f53\u524d\u7528\u6237\u67e5\u8be2\u548c\u7edf\u4e00\u8bf7\u6c42\u65e5\u5fd7\u5c55\u793a\u3002"],
        ["token status", "\u4ee4\u724c\u72b6\u6001"],
        ["Token status", "\u4ee4\u724c\u72b6\u6001"],
        ["The auth console stores the current access token in <code>localStorage</code> and reuses it for authenticated requests.", "\u8ba4\u8bc1\u533a\u4f1a\u628a\u5f53\u524d\u8bbf\u95ee\u4ee4\u724c\u4fdd\u5b58\u5728 <code>localStorage</code> \u4e2d\uff0c\u5e76\u5728\u540e\u7eed\u9700\u8981\u9274\u6743\u7684\u8bf7\u6c42\u91cc\u81ea\u52a8\u590d\u7528\u3002"],
        ["Not checked", "\u672a\u68c0\u67e5"],
        ["System", "\u7cfb\u7edf\u72b6\u6001"],
        ["Environment", "\u73af\u5883\u4fe1\u606f"],
        ["Shows service, runtime, and basic health information.", "\u7528\u4e8e\u67e5\u770b\u670d\u52a1\u72b6\u6001\u3001\u8fd0\u884c\u73af\u5883\u548c\u57fa\u7840\u5065\u5eb7\u4fe1\u606f\u3002"],
        ["Run health check", "\u6267\u884c\u5065\u5eb7\u68c0\u67e5"],
        ["Health result", "\u5065\u5eb7\u68c0\u67e5\u7ed3\u679c"],
        ["Calls <code>GET /api/health</code> and renders the returned <code>HealthCheckVO</code>.", "\u8c03\u7528 <code>GET /api/health</code>\uff0c\u5e76\u5c55\u793a\u540e\u7aef\u8fd4\u56de\u7684 <code>HealthCheckVO</code>\u3002"],
        ["Click the button to fetch /api/health", "\u70b9\u51fb\u6309\u94ae\u540e\u83b7\u53d6 /api/health \u7684\u7ed3\u679c"],
        ["Auth", "\u8ba4\u8bc1"],
        ["Current state", "\u5f53\u524d\u72b6\u6001"],
        ["Stored token", "\u5df2\u4fdd\u5b58\u4ee4\u724c"],
        ["Username", "\u7528\u6237\u540d"],
        ["Nickname", "\u6635\u79f0"],
        ["Password", "\u5bc6\u7801"],
        ["Avatar", "\u5934\u50cf"],
        ["At least 6 characters", "\u81f3\u5c11 6 \u4f4d\u5b57\u7b26"],
        ["data-loading-label=\"Registering...\"", "data-loading-label=\"\u6ce8\u518c\u4e2d...\""],
        ["Register", "\u6ce8\u518c"],
        ["data-loading-label=\"Logging in...\"", "data-loading-label=\"\u767b\u5f55\u4e2d...\""],
        ["Login", "\u767b\u5f55"],
        ["data-loading-label=\"Fetching user...\"", "data-loading-label=\"\u83b7\u53d6\u4e2d...\""],
        ["Current user", "\u5f53\u524d\u7528\u6237"],
        ["Clear token", "\u6e05\u9664\u4ee4\u724c"],
        ["Auth result", "\u8ba4\u8bc1\u7ed3\u679c"],
        ["Displays register, login, and current-user results as formatted JSON.", "\u5c55\u793a\u6ce8\u518c\u3001\u767b\u5f55\u548c\u5f53\u524d\u7528\u6237\u63a5\u53e3\u8fd4\u56de\u7684\u683c\u5f0f\u5316 JSON \u7ed3\u679c\u3002"],
        ["Run register, login, or current user actions", "\u6267\u884c\u6ce8\u518c\u3001\u767b\u5f55\u6216\u5f53\u524d\u7528\u6237\u64cd\u4f5c\u540e\uff0c\u8fd9\u91cc\u4f1a\u663e\u793a\u7ed3\u679c"],
        ["Upload", "\u8d44\u6599\u4e0a\u4f20"],
        ["Covers direct upload, chunk session lifecycle, and material lookup APIs.", "\u8986\u76d6\u666e\u901a\u4e0a\u4f20\u3001\u5206\u7247\u4e0a\u4f20\u4f1a\u8bdd\u751f\u547d\u5468\u671f\u548c\u8d44\u6599\u67e5\u8be2\u76f8\u5173\u63a5\u53e3\u3002"],
        ["File", "\u6587\u4ef6"],
        ["data-loading-label=\"Uploading...\"", "data-loading-label=\"\u4e0a\u4f20\u4e2d...\""],
        ["Upload material", "\u666e\u901a\u4e0a\u4f20\u8d44\u6599"],
        ["Upload file", "\u4e0a\u4f20\u6587\u4ef6"],
        ["Select a file to call /api/materials/upload", "\u9009\u62e9\u4e00\u4e2a\u6587\u4ef6\u540e\u8c03\u7528 /api/materials/upload"],
        ["Init chunk upload", "\u521d\u59cb\u5316\u5206\u7247\u4e0a\u4f20"],
        ["Reference file", "\u53c2\u8003\u6587\u4ef6"],
        ["File name", "\u6587\u4ef6\u540d"],
        ["File size", "\u6587\u4ef6\u5927\u5c0f"],
        ["Total chunks", "\u603b\u5206\u7247\u6570"],
        ["data-loading-label=\"Initializing...\"", "data-loading-label=\"\u521d\u59cb\u5316\u4e2d...\""],
        ["Init upload", "\u521d\u59cb\u5316\u4e0a\u4f20"],
        ["Fill fileName, fileSize, totalChunks, and fileMd5", "\u586b\u5199 fileName\u3001fileSize\u3001totalChunks \u548c fileMd5 \u540e\u521d\u59cb\u5316\u4e0a\u4f20"],
        ["Upload one chunk", "\u4e0a\u4f20\u5355\u4e2a\u5206\u7247"],
        ["Upload ID", "\u4e0a\u4f20\u4f1a\u8bdd ID"],
        ["Chunk index", "\u5206\u7247\u7d22\u5f15"],
        ["Chunk file", "\u5206\u7247\u6587\u4ef6"],
        ["data-loading-label=\"Uploading chunk...\"", "data-loading-label=\"\u5206\u7247\u4e0a\u4f20\u4e2d...\""],
        ["Upload chunk", "\u4e0a\u4f20\u5206\u7247"],
        ["Upload one chunk with uploadId, chunkIndex, and chunk", "\u4f7f\u7528 uploadId\u3001chunkIndex \u548c chunk \u6587\u4ef6\u4e0a\u4f20\u5355\u4e2a\u5206\u7247"],
        ["Uploaded chunks", "\u5df2\u4e0a\u4f20\u5206\u7247"],
        ["Check chunks", "\u67e5\u8be2\u5206\u7247"],
        ["Query the uploaded chunk indexes for the latest uploadId", "\u67e5\u8be2\u5f53\u524d uploadId \u5df2\u6210\u529f\u4e0a\u4f20\u7684\u5206\u7247\u7d22\u5f15"],
        ["data-loading-label=\"Completing...\"", "data-loading-label=\"\u5408\u5e76\u4e2d...\""],
        ["Complete upload", "\u5b8c\u6210\u4e0a\u4f20"],
        ["Material detail", "\u8d44\u6599\u8be6\u60c5"],
        ["Get detail", "\u67e5\u8be2\u8be6\u60c5"],
        ["Fetch one material detail by materialId", "\u6839\u636e materialId \u67e5\u8be2\u5355\u4e2a\u8d44\u6599\u8be6\u60c5"],
        ["My materials", "\u6211\u7684\u8d44\u6599\u5217\u8868"],
        ["Material type", "\u8d44\u6599\u7c7b\u578b"],
        ["Parse status", "\u89e3\u6790\u72b6\u6001"],
        ["Optional materialType", "\u53ef\u9009 materialType"],
        ["Optional parseStatus", "\u53ef\u9009 parseStatus"],
        ["data-loading-label=\"Listing materials...\"", "data-loading-label=\"\u67e5\u8be2\u4e2d...\""],
        ["List materials", "\u67e5\u8be2\u5217\u8868"],
        ["Parse", "\u89e3\u6790\u7ed3\u679c"],
        ["Parse pipeline", "\u89e3\u6790\u94fe\u8def"],
        ["data-loading-label=\"Loading tasks...\"", "data-loading-label=\"\u52a0\u8f7d\u4e2d...\""],
        ["data-loading-label=\"Dispatching...\"", "data-loading-label=\"\u6d3e\u53d1\u4e2d...\""],
        ["data-loading-label=\"Loading content...\"", "data-loading-label=\"\u52a0\u8f7d\u4e2d...\""],
        ["data-loading-label=\"Loading summary...\"", "data-loading-label=\"\u52a0\u8f7d\u4e2d...\""],
        ["data-loading-label=\"Generating summary...\"", "data-loading-label=\"\u751f\u6210\u4e2d...\""],
        ["data-loading-label=\"Loading transcript...\"", "data-loading-label=\"\u52a0\u8f7d\u4e2d...\""],
        ["Q&A", "\u8d44\u6599\u95ee\u7b54"],
        ["RAG QA", "\u0052\u0041\u0047 \u95ee\u7b54"],
        ["data-loading-label=\"Creating session...\"", "data-loading-label=\"\u521b\u5efa\u4e2d...\""],
        ["Study", "\u5b66\u4e60\u8f85\u52a9"],
        ["Study flow", "\u5b66\u4e60\u8f85\u52a9"],
        ["Session name", "\u4f1a\u8bdd\u540d\u79f0"],
        ["Optional session name", "\u53ef\u9009\u4f1a\u8bdd\u540d\u79f0"],
        ["Session ID", "\u4f1a\u8bdd ID"],
        ["Top K", "\u53ec\u56de Top K"],
        ["Question", "\u95ee\u9898"],
        ["What are the key concepts in this material?", "\u8fd9\u4efd\u8d44\u6599\u7684\u6838\u5fc3\u77e5\u8bc6\u70b9\u662f\u4ec0\u4e48\uff1f"],
        ["data-loading-label=\"Asking...\"", "data-loading-label=\"\u63d0\u95ee\u4e2d...\""],
        ["Submit question", "\u63d0\u4ea4\u95ee\u9898"],
        ["History", "\u4f1a\u8bdd\u5386\u53f2"],
        ["Latest sessionId auto-fills here", "\u8fd9\u91cc\u4f1a\u81ea\u52a8\u56de\u586b\u6700\u8fd1\u4e00\u6b21\u7684 sessionId"],
        ["data-loading-label=\"Loading history...\"", "data-loading-label=\"\u52a0\u8f7d\u4e2d...\""],
        ["Load history", "\u52a0\u8f7d\u5386\u53f2"],
        ["Plan name", "\u8ba1\u5212\u540d\u79f0"],
        ["data-loading-label=\"Generating outline...\"", "data-loading-label=\"\u751f\u6210\u4e2d...\""],
        ["Exam date", "\u8003\u8bd5\u65e5\u671f"],
        ["data-loading-label=\"Generating plan...\"", "data-loading-label=\"\u751f\u6210\u4e2d...\""],
        ["Plan detail", "\u8ba1\u5212\u8be6\u60c5"],
        ["Plan ID", "\u8ba1\u5212 ID"],
        ["Plan history", "\u8ba1\u5212\u5386\u53f2"],
        ["Plan type", "\u8ba1\u5212\u7c7b\u578b"],
        ["Failure", "\u5931\u8d25\u6cbb\u7406"],
        ["Failure monitor", "\u5931\u8d25\u76d1\u63a7"],
        ["Record status", "\u8bb0\u5f55\u72b6\u6001"],
        ["data-loading-label=\"Loading failures...\"", "data-loading-label=\"\u52a0\u8f7d\u4e2d...\""],
        ["Compensate failure", "\u8865\u507f\u5931\u8d25\u4efb\u52a1"],
        ["Record ID", "\u8bb0\u5f55 ID"],
        ["data-loading-label=\"Compensating...\"", "data-loading-label=\"\u8865\u507f\u4e2d...\""],
        ["Global log", "\u5168\u5c40\u65e5\u5fd7"],
        ["Request log", "\u8bf7\u6c42\u65e5\u5fd7"],
        ["Waiting for request activity.", "\u7b49\u5f85\u8bf7\u6c42\u4ea7\u751f..."]
    ];

    const currentHtml = document.body.innerHTML;
    document.body.innerHTML = replacements.reduce((html, [source, target]) => html.split(source).join(target), currentHtml);
}

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
    button.textContent = busy ? (button.dataset.loadingLabel || "\u5904\u7406\u4e2d...") : button.dataset.defaultLabel;
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
        const networkMessage = error instanceof Error ? error.message : "\u672a\u77e5\u7f51\u7edc\u9519\u8bef";
        writeLog({ method, path, ok: false, message: networkMessage });
        throw createRequestError(networkMessage, { cause: error, method, path });
    }

    if (!payload || typeof payload !== "object" || !("code" in payload)) {
        const invalidMessage = "\u8fd4\u56de\u7ed3\u679c\u683c\u5f0f\u4e0d\u6b63\u786e";
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
    const tokenSummary = token ? `${token.slice(0, 16)}${token.length > 16 ? "..." : ""}` : "\u4ec5\u4fdd\u5b58\u5728 localStorage";

    if (tokenStatus) {
        tokenStatus.textContent = token ? "\u5df2\u52a0\u8f7d\u4ee4\u724c" : "\u672a\u68c0\u6d4b\u5230\u4ee4\u724c";
        tokenStatus.classList.toggle("status-pill-muted", !token);
    }

    if (authStatus) {
        authStatus.textContent = token ? "\u672c\u5730\u5df2\u6388\u6743" : "\u672a\u767b\u5f55";
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
        renderJson(output, { message: "\u4ee4\u724c\u5df2\u6e05\u9664" });
        writeLog({
            method: "LOCAL",
            path: "localStorage",
            ok: true,
            message: "\u5df2\u6e05\u9664\u8bbf\u95ee\u4ee4\u724c"
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
    applyChineseConsoleLocale();
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
        message: "\u9759\u6001\u6d4b\u8bd5\u53f0\u5df2\u521d\u59cb\u5316"
    });
}

document.addEventListener("DOMContentLoaded", bootstrap);
