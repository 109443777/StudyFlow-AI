const StudyFlowE2EConsole = (() => {
    const state = {
        token: localStorage.getItem("studyflow.e2e.token") || "",
        materialId: localStorage.getItem("studyflow.e2e.materialId") || "",
        sessionId: localStorage.getItem("studyflow.e2e.sessionId") || ""
    };

    const $ = (id) => document.getElementById(id);

    function init() {
        $("materialId").value = state.materialId;
        $("sessionId").value = state.sessionId;
        $("examDate").value = nextWeekDate();
        updateTokenStatus();
        document.querySelectorAll("[data-action]").forEach((button) => {
            button.addEventListener("click", () => run(button.dataset.action, button));
        });
    }

    async function run(action, button) {
        try {
            button.disabled = true;
            const result = await actions[action]();
            showResponse(result);
        } catch (error) {
            showResponse({ error: error.message });
            addLog("请求失败", error.message, true);
        } finally {
            button.disabled = false;
        }
    }

    const actions = {
        health: () => request("/api/health", { auth: false }),
        clearLog: () => {
            $("logList").innerHTML = "";
            return { message: "日志已清空" };
        },
        resetState: () => {
            state.token = "";
            state.materialId = "";
            state.sessionId = "";
            localStorage.removeItem("studyflow.e2e.token");
            localStorage.removeItem("studyflow.e2e.materialId");
            localStorage.removeItem("studyflow.e2e.sessionId");
            $("materialId").value = "";
            $("sessionId").value = "";
            updateTokenStatus();
            return { message: "本地状态已清空" };
        },
        register: () => request("/api/auth/register", {
            method: "POST",
            auth: false,
            body: {
                username: value("username"),
                password: value("password"),
                nickname: value("nickname"),
                avatar: ""
            }
        }),
        login: async () => {
            const result = await request("/api/auth/login", {
                method: "POST",
                auth: false,
                body: {
                    username: value("username"),
                    password: value("password")
                }
            });
            const token = pickToken(result.data);
            if (!token) {
                throw new Error("登录成功但响应中没有 accessToken/token 字段");
            }
            state.token = token;
            localStorage.setItem("studyflow.e2e.token", token);
            updateTokenStatus();
            return result;
        },
        me: () => request("/api/users/me"),
        upload: async () => {
            const fileInput = $("materialFile");
            if (!fileInput.files.length) {
                throw new Error("请先选择要上传的学习资料文件");
            }
            const formData = new FormData();
            formData.append("file", fileInput.files[0]);
            const result = await request("/api/materials/upload", {
                method: "POST",
                body: formData,
                form: true
            });
            const materialId = pickId(result.data, ["materialId", "id"]);
            if (materialId) {
                setMaterialId(materialId);
            }
            return result;
        },
        materialDetail: () => request(`/api/materials/${requiredMaterialId()}`),
        myMaterials: () => request("/api/materials/my"),
        dispatchParse: () => request(`/api/parse-tasks/materials/${requiredMaterialId()}/dispatch`, { method: "POST" }),
        parseTasks: () => request(`/api/parse-tasks?materialId=${encodeURIComponent(requiredMaterialId())}`),
        materialContent: () => request(`/api/material-contents?materialId=${encodeURIComponent(requiredMaterialId())}`),
        mediaTranscript: () => request(`/api/media-transcripts?materialId=${encodeURIComponent(requiredMaterialId())}`),
        generateSummary: () => request(`/api/material-summaries/${requiredMaterialId()}/generate`, { method: "POST" }),
        summary: () => request(`/api/material-summaries?materialId=${encodeURIComponent(requiredMaterialId())}`),
        reviewOutline: () => request("/api/study-plans/review-outline", {
            method: "POST",
            body: {
                materialId: requiredMaterialId(),
                planName: "端到端测试复习提纲"
            }
        }),
        studyPlan: () => request("/api/study-plans/exam-plan", {
            method: "POST",
            body: {
                materialId: requiredMaterialId(),
                examDate: value("examDate"),
                planName: "端到端测试 7 天复习计划"
            }
        }),
        createSession: async () => {
            const result = await request("/api/qa/sessions", {
                method: "POST",
                body: {
                    materialId: requiredMaterialId(),
                    sessionName: value("sessionName")
                }
            });
            const sessionId = pickId(result.data, ["sessionId", "id"]);
            if (sessionId) {
                setSessionId(sessionId);
            }
            return result;
        },
        ask: () => request(`/api/qa/sessions/${requiredSessionId()}/ask`, {
            method: "POST",
            body: {
                question: value("question"),
                topK: Number(value("topK") || 5)
            }
        }),
        qaHistory: () => request(`/api/qa/sessions/${requiredSessionId()}/messages`)
    };

    async function request(path, options = {}) {
        const method = options.method || "GET";
        const headers = {};
        const useAuth = options.auth !== false;
        let body = options.body;

        if (useAuth) {
            if (!state.token) {
                throw new Error("当前未登录，请先登录并保存 Token");
            }
            headers.Authorization = `Bearer ${state.token}`;
        }

        if (!options.form && body !== undefined) {
            headers["Content-Type"] = "application/json";
            body = JSON.stringify(body);
        }

        const url = `${apiBase()}${path}`;
        addLog("发起请求", `${method} ${path}`);
        const response = await fetch(url, { method, headers, body });
        const payload = await readPayload(response);
        addLog(response.ok ? "请求完成" : "请求异常", `${response.status} ${method} ${path}`, !response.ok);
        if (!response.ok) {
            throw new Error(formatError(payload, response.status));
        }
        return payload;
    }

    async function readPayload(response) {
        const text = await response.text();
        if (!text) {
            return {};
        }
        try {
            return JSON.parse(text);
        } catch (error) {
            return { raw: text };
        }
    }

    function apiBase() {
        return value("apiBase").replace(/\/$/, "");
    }

    function value(id) {
        return $(id).value.trim();
    }

    function requiredMaterialId() {
        const id = value("materialId") || state.materialId;
        if (!id) {
            throw new Error("缺少 materialId，请先上传资料或手动填写 materialId");
        }
        return id;
    }

    function requiredSessionId() {
        const id = value("sessionId") || state.sessionId;
        if (!id) {
            throw new Error("缺少 sessionId，请先创建问答会话或手动填写 sessionId");
        }
        return id;
    }

    function pickToken(data) {
        return data?.accessToken || data?.token || data?.jwt || "";
    }

    function pickId(data, names) {
        if (!data) {
            return "";
        }
        for (const name of names) {
            if (data[name]) {
                return String(data[name]);
            }
        }
        return "";
    }

    function setMaterialId(id) {
        state.materialId = String(id);
        $("materialId").value = state.materialId;
        localStorage.setItem("studyflow.e2e.materialId", state.materialId);
    }

    function setSessionId(id) {
        state.sessionId = String(id);
        $("sessionId").value = state.sessionId;
        localStorage.setItem("studyflow.e2e.sessionId", state.sessionId);
    }

    function updateTokenStatus() {
        $("tokenStatus").textContent = state.token ? "已登录" : "未登录";
        $("lastAction").textContent = state.token ? "JWT 已保存到浏览器 localStorage" : "请先注册或登录";
    }

    function showResponse(result) {
        $("responseBox").textContent = JSON.stringify(result, null, 2);
        if (result?.code && result.code !== 0 && result.code !== 200) {
            addLog("业务响应", `code=${result.code}, message=${result.message || "无消息"}`, true);
        }
    }

    function addLog(title, detail, isError = false) {
        const item = document.createElement("li");
        item.className = isError ? "error" : "";
        item.innerHTML = `<strong>${escapeHtml(title)}</strong><br>${escapeHtml(detail)}<br><small>${new Date().toLocaleTimeString()}</small>`;
        $("logList").prepend(item);
        $("lastAction").textContent = `${title}: ${detail}`;
    }

    function formatError(payload, status) {
        if (payload?.message) {
            return `${status}: ${payload.message}`;
        }
        if (payload?.error) {
            return `${status}: ${payload.error}`;
        }
        return `${status}: ${JSON.stringify(payload)}`;
    }

    function escapeHtml(text) {
        return String(text)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function nextWeekDate() {
        const date = new Date();
        date.setDate(date.getDate() + 7);
        return date.toISOString().slice(0, 10);
    }

    return { init };
})();

window.addEventListener("DOMContentLoaded", StudyFlowE2EConsole.init);
