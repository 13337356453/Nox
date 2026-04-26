/**
 * Nox 前端主逻辑脚本。
 * 实现左侧连接列表管理、右侧 Information Collection / Code Execution 面板的交互，
 * 以及每个连接的独立状态隔离（info 内容、终端历史、execTarget、命令历史）。
 */

// 当前选中的连接信息
let selectedId = null;
let selectedUrl = null;
let selectedElement = null;

// 全局状态：连接列表与当前活跃 Tab
const state = {
    connections: [],
    activeTab: 'info',
};

// 以连接 ID 为 key，存储每个连接独立的右侧状态
const connData = {};

// DOM 元素缓存对象
const els = {};

/**
 * 缓存常用的 DOM 元素，避免频繁查询。
 */
function cacheElements() {
    els.connList = document.getElementById('connList');
    els.infoBox = document.getElementById('infoBox');
    els.output = document.getElementById('output');
    els.prompt = document.getElementById('prompt');
    els.cmd = document.getElementById('cmd');
    els.tabs = Array.from(document.querySelectorAll('.tab'));
    els.modifyBtn = document.getElementById('modifyBtn');
    els.removeBtn = document.getElementById('removeBtn');
    els.currentPath = document.getElementById('currentPath');
    els.fileTable = document.getElementById('fileTable');
    els.fileTableBody = document.getElementById('fileTableBody');
    els.fileEmpty = document.getElementById('fileEmpty');
    els.editorModal = document.getElementById('editorModal');
    els.editorFileName = document.getElementById('editorFileName');
    els.editorTextarea = document.getElementById('editorTextarea');
    els.editorStatus = document.getElementById('editorStatus');
}

/**
 * HTML 实体转义，防止 XSS 攻击。
 * @param {string} str 原始字符串
 * @returns {string} 转义后的字符串
 */
function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/**
 * 设置修改和删除按钮的启用/禁用状态。
 * @param {boolean} enabled 是否启用
 */
function setButtonsEnabled(enabled) {
    [els.modifyBtn, els.removeBtn].forEach(btn => {
        if (btn) btn.disabled = !enabled;
    });
}

/**
 * 从信息收集返回的文本中解析出用户信息、工作目录和操作系统。
 * @param {string} text 信息收集的原始文本
 * @returns {object} 包含 user, workingDir, osName, complete, prompt 的对象
 */
function parseExecTarget(text) {
    const getValue = (label) => {
        const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const match = text.match(new RegExp(`^${escaped}\\s*:\\s*(.+)$`, 'im'));
        return match ? match[1].trim() : '';
    };

    const user = getValue('User Name');
    const workingDir = getValue('Working dir');
    const osName = getValue('OS Name');
    const complete = Boolean(user && workingDir && osName);

    return {
        user,
        workingDir,
        osName,
        complete,
        prompt: complete ? `${user}@${workingDir} >` : 'nox >',
    };
}

/**
 * 从后端加载所有连接记录。
 */
async function loadConnections() {
    const res = await fetch('/api/data');
    state.connections = await res.json();
    renderConnections();
}

/**
 * 渲染左侧连接列表。
 * 每个连接项支持单击选中和双击打开（加载信息）。
 */
function renderConnections() {
    els.connList.innerHTML = '';
    state.connections.forEach(item => {
        const div = document.createElement('div');
        div.className = 'conn-item' + (item.id === selectedId ? ' selected' : '');
        div.dataset.id = item.id;
        div.dataset.url = item.url;
        div.innerHTML = `
            <div class="conn-top">
                <span class="conn-id">#${item.id}</span>
                <span class="conn-pill">URL</span>
            </div>
            <div class="conn-url">${escapeHtml(item.url)}</div>
        `;
        div.addEventListener('click', () => selectConn(item.id, item.url, div));
        div.addEventListener('dblclick', () => openConn(item.id, item.url, div));
        els.connList.appendChild(div);
    });
    setButtonsEnabled(selectedId !== null);
}

/**
 * 单击选中连接。
 * 更新选中状态，并立即渲染该连接对应的右侧内容。
 * @param {number} id 连接 ID
 * @param {string} url 连接 URL
 * @param {HTMLElement} element 被点击的 DOM 元素
 */
function selectConn(id, url, element) {
    selectedId = id;
    selectedUrl = url;
    selectedElement = element;
    document.querySelectorAll('.conn-item').forEach(el => el.classList.remove('selected'));
    element.classList.add('selected');
    setButtonsEnabled(true);
    renderRightPanel();
}

/**
 * 根据当前选中的连接 ID，渲染右侧面板内容。
 * 包括 infoBox 的文本、终端输出和 prompt。
 */
function renderRightPanel() {
    const data = connData[selectedId];
    if (!data) {
        els.infoBox.innerHTML = `<pre class="info-pre">Select a connection and double click it to load information.</pre>`;
        els.output.innerHTML = '';
        els.prompt.textContent = 'nox >';
        return;
    }

    els.infoBox.innerHTML = `<pre class="info-pre">${escapeHtml(data.infoText || '')}</pre>`;
    els.output.innerHTML = data.terminalHtml || '';
    els.prompt.textContent = data.execTarget?.prompt || 'nox >';
}

/**
 * 双击打开连接。
 * 自动跳转到 Information Collection 页面，并异步请求目标信息。
 * @param {number} id 连接 ID
 * @param {string} url 连接 URL
 * @param {HTMLElement} element 被双击的 DOM 元素
 */
async function openConn(id, url, element) {
    selectConn(id, url, element);
    await showTab('info');

    const preservedTerminal = connData[id]?.terminalHtml || '';
    connData[id] = {
        infoText: 'Loading...',
        terminalHtml: preservedTerminal,
        execTarget: null,
        hasInfo: false,
        fileManager: connData[id]?.fileManager,
    };
    renderRightPanel();

    try {
        const res = await fetch(`/getinfo?url=${encodeURIComponent(url)}`);
        const text = await res.text();

        // 连接失败检测：校验响应中是否包含关键字段 "Working dir"
        if (!text.includes('Working dir')) {
            alert('连接失败');
            connData[id] = {
                infoText: '连接失败：响应中缺少必要字段 (Working dir)。',
                terminalHtml: preservedTerminal,
                execTarget: null,
                hasInfo: false,
                fileManager: connData[id]?.fileManager,
            };
            renderRightPanel();
            return;
        }

        const execTarget = parseExecTarget(text);
        connData[id] = {
            infoText: text,
            terminalHtml: preservedTerminal,
            execTarget,
            hasInfo: true,
            fileManager: connData[id]?.fileManager,
        };
    } catch (e) {
        connData[id] = {
            infoText: 'Request failed.',
            terminalHtml: preservedTerminal,
            execTarget: null,
            hasInfo: false,
            fileManager: connData[id]?.fileManager,
        };
    }
    renderRightPanel();
}

/**
 * 添加新连接。
 * 弹出输入框获取 URL，向后端发送 POST 请求创建记录。
 */
async function addConn() {
    const url = prompt('Please enter connection URL');
    if (!url) return;
    await fetch('/api/data', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({url})
    });
    await loadConnections();
}

/**
 * 修改当前选中连接的 URL。
 */
async function modifyConn() {
    if (selectedId == null) return;
    const url = prompt('Please enter new connection URL', selectedUrl || '');
    if (!url) return;
    await fetch(`/api/data/${selectedId}`, {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({url})
    });
    selectedUrl = url;
    await loadConnections();
}

/**
 * 删除当前选中的连接。
 */
async function removeConn() {
    if (selectedId == null) return;
    const ok = confirm(`Delete #${selectedId}?`);
    if (!ok) return;
    await fetch(`/api/data/${selectedId}`, {method: 'DELETE'});
    delete connData[selectedId];
    selectedId = null;
    selectedUrl = null;
    selectedElement = null;
    await loadConnections();
    renderRightPanel();
}

/**
 * 将终端滚动到底部。
 */
function scrollTerminalToBottom() {
    if (!els.output) return;
    els.output.scrollTop = els.output.scrollHeight;
}

/**
 * 规范化路径。
 * 支持 Windows（X:\）和 Unix（/）绝对路径，自动处理 . 和 .. 。
 * @param {string} path 原始路径
 * @param {string} sep 路径分隔符（'/' 或 '\\'）
 * @returns {string} 规范化后的路径
 */
function normalizePath(path, sep) {
    sep = sep || '/';
    // 统一将反斜杠替换为正斜杠进行逻辑处理
    const unified = path.replace(/\\/g, '/');
    const isWindowsAbsolute = /^[A-Za-z]:\//.test(unified);
    const isAbsolute = isWindowsAbsolute || unified.startsWith('/');
    const parts = unified.split('/');
    const stack = [];
    // Windows 绝对路径跳过盘符部分（索引 0）
    const startIndex = isWindowsAbsolute ? 1 : 0;

    for (let i = startIndex; i < parts.length; i++) {
        const part = parts[i];
        if (!part || part === '.') continue;
        if (part === '..') {
            if (stack.length) stack.pop();
            continue;
        }
        stack.push(part);
    }

    const result = stack.join(sep);
    if (isWindowsAbsolute) {
        return parts[0] + sep + result;
    }
    if (isAbsolute) {
        return sep + result;
    }
    return result || '.';
}

/**
 * 根据当前目录和目标目录解析出新的工作目录。
 * 支持绝对路径、相对路径、~（家目录）和 -（上次目录）等特殊符号。
 * @param {string} currentDir 当前工作目录
 * @param {string} targetDir  目标目录（用户输入的 cd 参数）
 * @returns {string} 解析后的绝对路径
 */
function resolveWorkingDir(currentDir, targetDir) {
    if (!targetDir || targetDir === '~') {
        if (currentDir && /^[A-Za-z]:/.test(currentDir)) {
            return currentDir.charAt(0) + ':\\';
        }
        return '/';
    }
    if (targetDir === '-') return currentDir || '/';
    if (targetDir.startsWith('/') || /^[A-Za-z]:/.test(targetDir)) {
        const useBackslash = currentDir && currentDir.includes('\\');
        return normalizePath(targetDir, useBackslash ? '\\' : '/');
    }
    const useBackslash = currentDir && currentDir.includes('\\');
    const sep = useBackslash ? '\\' : '/';
    const base = currentDir || sep;
    const trailing = (base.endsWith('\\') || base.endsWith('/')) ? '' : sep;
    return normalizePath(base + trailing + targetDir, sep);
}

/**
 * 更新指定连接的 prompt 显示。
 * @param {number} id 连接 ID
 */
function updatePromptFromWorkingDir(id) {
    const data = connData[id];
    if (!data || !data.execTarget) return;
    data.execTarget.prompt = `${data.execTarget.user}@${data.execTarget.workingDir} >`;
    if (selectedId === id) {
        els.prompt.textContent = data.execTarget.prompt;
    }
}

/**
 * 保存当前终端的 HTML 内容到对应连接的缓存中。
 */
function saveTerminalState() {
    if (selectedId != null && els.output) {
        connData[selectedId] = connData[selectedId] || {};
        connData[selectedId].terminalHtml = els.output.innerHTML;
    }
}

/**
 * 在终端中追加一行命令及其输出。
 * @param {string} command 输入的命令
 * @param {string} output  命令的输出结果
 */
function appendTerminalLine(command, output) {
    const data = connData[selectedId];
    const prompt = data?.execTarget?.prompt || 'nox >';
    const commandLine = `<div class="terminal-line terminal-line-command"><span class="terminal-prompt">${escapeHtml(prompt)}</span>${escapeHtml(command)}</div>`;
    const outputLine = `<div class="terminal-line terminal-line-output">${escapeHtml(output || '')}</div>`;
    const spacer = '<div class="terminal-line terminal-line-spacer">&nbsp;</div>';
    els.output.insertAdjacentHTML('beforeend', commandLine + outputLine + spacer);
    scrollTerminalToBottom();
    saveTerminalState();
}

/**
 * 切换右侧 Tab 面板。
 * @param {string} id Tab 标识：'info' | 'exec' | 'mem'
 */
async function showTab(id) {
    state.activeTab = id;
    document.querySelectorAll('.panel').forEach(el => el.classList.remove('active'));
    document.getElementById(id).classList.add('active');

    els.tabs.forEach(tab => {
        const tabId = tab.dataset.tab;
        tab.classList.toggle('active', tabId === id);
        tab.classList.remove('locked');
        tab.classList.add('unlocked');
    });

    if (id === 'exec') {
        scrollTerminalToBottom();
        els.cmd.focus();
    }
}

/**
 * 打开 Code Execution Tab 并聚焦输入框。
 */
async function openExecTab() {
    await showTab('exec');
    els.cmd.focus();
}

/**
 * 提交命令。
 * 处理 clear、cd 和普通命令，向后端 /exec 发送请求并显示结果。
 */
async function submitCmd() {
    const value = els.cmd.value.trim();
    if (!value || !selectedUrl) return;

    const data = connData[selectedId];
    const execTarget = data?.execTarget;

    // clear 命令：清空当前连接的终端输出
    if (value === 'clear') {
        els.output.innerHTML = '';
        saveTerminalState();
        els.cmd.value = '';
        els.cmd.focus();
        return;
    }

    // cd 命令：前端本地维护工作目录，不发送到后端
    if (value.startsWith('cd')) {
        let nextDir = value === 'cd' ? '/' : value.substring(2).trim();
        // 截断 && 和 ; 等命令分隔符，防止 cd /root && ls 被当成整体路径
        const stopIndex = Math.min(
            nextDir.indexOf('&&') >= 0 ? nextDir.indexOf('&&') : Infinity,
            nextDir.indexOf(';') >= 0 ? nextDir.indexOf(';') : Infinity
        );
        if (stopIndex !== Infinity) {
            nextDir = nextDir.substring(0, stopIndex).trim();
        }
        if (nextDir && execTarget) {
            execTarget.workingDir = resolveWorkingDir(execTarget.workingDir, nextDir);
            updatePromptFromWorkingDir(selectedId);
        }
        if (!data.history) data.history = [];
        data.history.push(value);
        data.historyIndex = data.history.length;
        els.cmd.value = '';
        appendTerminalLine(value, '');
        scrollTerminalToBottom();
        els.cmd.focus();
        return;
    }

    // 普通命令：记录历史，发送到后端执行
    if (!data.history) data.history = [];
    data.history.push(value);
    data.historyIndex = data.history.length;

    els.cmd.value = '';
    appendTerminalLine(value, '');

    const pwd = execTarget?.workingDir || '/';
    const osName = execTarget?.osName || '';

    try {
        const execUrl = `/exec?url=${encodeURIComponent(selectedUrl)}&cmd=${encodeURIComponent(value)}&pwd=${encodeURIComponent(pwd)}&osName=${encodeURIComponent(osName)}`;
        const res = await fetch(execUrl);
        const text = await res.text();
        const output = text.replace(/\r?\n/g, '\n').trimEnd();
        els.output.insertAdjacentHTML('beforeend', `<div class="terminal-line terminal-line-output">${escapeHtml(output || ' ')}</div><div class="terminal-line terminal-line-spacer">&nbsp;</div>`);
    } catch (e) {
        els.output.insertAdjacentHTML('beforeend', `<div class="terminal-line terminal-line-output terminal-line-error">Request failed.</div><div class="terminal-line terminal-line-spacer">&nbsp;</div>`);
    }

    scrollTerminalToBottom();
    saveTerminalState();
    els.cmd.focus();
}

/**
 * 键盘事件处理。
 * Enter 提交命令，ArrowUp/ArrowDown 浏览命令历史。
 * @param {KeyboardEvent} event
 */
function handleCmd(event) {
    if (event.key === 'Enter') {
        event.preventDefault();
        submitCmd();
        return;
    }

    const data = connData[selectedId];
    const history = data?.history || [];

    if (event.key === 'ArrowUp') {
        event.preventDefault();
        if (!history.length) return;
        if (data.historyIndex == null) data.historyIndex = history.length;
        data.historyIndex = Math.max(0, data.historyIndex - 1);
        els.cmd.value = history[data.historyIndex] || '';
        setTimeout(() => els.cmd.setSelectionRange(els.cmd.value.length, els.cmd.value.length), 0);
        return;
    }

    if (event.key === 'ArrowDown') {
        event.preventDefault();
        if (!history.length) return;
        if (data.historyIndex == null) data.historyIndex = history.length;
        data.historyIndex = Math.min(history.length, data.historyIndex + 1);
        els.cmd.value = data.historyIndex >= history.length ? '' : (history[data.historyIndex] || '');
        setTimeout(() => els.cmd.setSelectionRange(els.cmd.value.length, els.cmd.value.length), 0);
    }
}

/* ========== 通用工具函数 ========== */

function getSeparator(path) {
    if (!path) return '/';
    const isWindows = /^[A-Za-z]:[\\/]/.test(path) || (path.includes('\\') && !path.includes('/'));
    return isWindows ? '\\' : '/';
}

function sanitizePath(path) {
    if (!path) return '/';
    path = path.replace(/[\x00-\x1f\x7f]/g, '');
    const isWindows = /^[A-Za-z]:[\\/]/.test(path) || (path.includes('\\') && !path.includes('/'));
    const sep = isWindows ? '\\' : '/';
    const wrongSep = isWindows ? '/' : '\\';
    path = path.split(wrongSep).join(sep);
    const doubleSep = sep + sep;
    while (path.indexOf(doubleSep) >= 0) {
        path = path.split(doubleSep).join(sep);
    }
    if (isWindows) {
        const m = path.match(/^([A-Za-z]:)(.*)$/);
        if (m) {
            let rest = m[2];
            while (rest.startsWith(sep)) rest = rest.substring(1);
            path = m[1] + sep + rest;
        }
        if (path.length > 3 && path.endsWith(sep)) {
            path = path.substring(0, path.length - 1);
        }
    } else {
        if (path.length > 1 && path.endsWith(sep)) {
            path = path.substring(0, path.length - 1);
        }
    }
    return path || sep;
}

function utf8ToBase64(str) {
    const bytes = new TextEncoder().encode(str);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
}

async function fileRequest(action, path, extra) {
    const body = { url: selectedUrl, action, path: path || '' };
    if (extra !== undefined) body.extra = extra;
    console.log('[fileRequest]', JSON.stringify(body));
    const res = await fetch('/file', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    const text = await res.text();
    console.log('[fileRequest] response:', text.substring(0, 200));
    return text;
}

/* ========== 文件管理器模块 ========== */

/**
 * 获取当前选中连接的文件管理器状态。
 * 若不存在则自动初始化，默认路径为工作目录或根目录。
 */
function getFileManagerState() {
    let data = connData[selectedId];
    if (!data) {
        data = { infoText: '', terminalHtml: '', execTarget: null, hasInfo: false };
        connData[selectedId] = data;
    }
    if (!data.fileManager) {
        data.fileManager = { currentPath: data.execTarget?.workingDir || '/', files: [], editorOpen: false, editorPath: '', editorFileName: '', editorContent: '', editorDirty: false };
    }
    return data.fileManager;
}

/**
 * 根据路径获取上级目录。
 * 兼容 Windows（C:\）与 Unix（/）路径格式。
 */
function resolveParent(path) {
    if (!path) return '/';
    path = sanitizePath(path);
    const isWindows = /^[A-Za-z]:\\/.test(path);
    const sep = isWindows ? '\\' : '/';
    if (isWindows && /^[A-Za-z]:\\?$/.test(path)) return path.charAt(0) + ':' + sep;
    if (!isWindows && path === sep) return sep;
    const idx = path.lastIndexOf(sep);
    if (idx < 0) return isWindows ? path.charAt(0) + ':' + sep : sep;
    if (isWindows && idx === 2) return path.substring(0, 3);
    if (!isWindows && idx === 0) return sep;
    return path.substring(0, idx);
}

/**
 * 将字节数格式化为人类可读字符串。
 */
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

/**
 * 打开 File Manager Tab 并加载当前目录列表。
 */
async function openFileTab() {
    await showTab('file');
    if (!selectedId || !connData[selectedId]?.execTarget) {
        els.fileTable.style.display = 'none';
        els.fileEmpty.style.display = 'block';
        els.fileEmpty.textContent = 'Select a connection and open it to browse files.';
        return;
    }
    els.fileTable.style.display = 'table';
    els.fileEmpty.style.display = 'none';
    const state = getFileManagerState();
    els.currentPath.value = state.currentPath;
    await loadFileList(state.currentPath);
}

/**
 * 向服务端请求指定路径的目录列表。
 */
async function loadFileList(path) {
    if (!selectedUrl) return false;
    console.log('[loadFileList] path=', path);
    els.fileTableBody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--muted)">Loading...</td></tr>';
    try {
        const text = await fileRequest('LIST', path);
        if (text.startsWith('ERROR|')) {
            els.fileTableBody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--danger)">${escapeHtml(text)}</td></tr>`;
            return false;
        }
        const state = getFileManagerState();
        state.currentPath = path;
        els.currentPath.value = path;
        console.log('[loadFileList] before render, state.currentPath=', state.currentPath);
        renderFileList(text, path);
        return true;
    } catch (e) {
        els.fileTableBody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--danger)">Request failed.</td></tr>`;
        return false;
    }
}

/**
 * 渲染文件列表到表格。
 * 解析服务端返回的 [DIR]/[FILE] 格式行。
 */
function renderFileList(text, currentPath) {
    console.log('[renderFileList] currentPath=', currentPath);
    els.fileTableBody.innerHTML = '';
    const lines = text.trim().split('\n').filter(l => l);
    const sep = getSeparator(currentPath);
    lines.forEach(line => {
        const m = line.match(/^\[(DIR|FILE)\](.+)\|(\d+)\|(\d+)\|(.+)$/);
        if (!m) return;
        const [, type, name, size, time, date] = m;
        const path = sanitizePath(currentPath + (currentPath.endsWith(sep) ? '' : sep) + name);
        const tr = document.createElement('tr');
        tr.dataset.path = path;
        tr.dataset.name = name;
        tr.dataset.type = type;
        tr.addEventListener('dblclick', () => {
            if (type === 'DIR') navigateTo(name);
            else openFileEditor(path, name);
        });
        const tdName = document.createElement('td');
        tdName.textContent = name;
        tr.appendChild(tdName);
        const tdType = document.createElement('td');
        tdType.textContent = type;
        tr.appendChild(tdType);
        const tdSize = document.createElement('td');
        tdSize.textContent = type === 'DIR' ? '-' : formatBytes(parseInt(size));
        tr.appendChild(tdSize);
        const tdDate = document.createElement('td');
        tdDate.textContent = date;
        tr.appendChild(tdDate);
        const tdActions = document.createElement('td');
        tdActions.className = 'file-actions-cell';
        if (type === 'FILE') {
            const btnView = document.createElement('button');
            btnView.className = 'file-btn-sm';
            btnView.textContent = 'View';
            btnView.addEventListener('click', (e) => { e.stopPropagation(); openFileEditor(path, name); });
            tdActions.appendChild(btnView);
            const btnDownload = document.createElement('button');
            btnDownload.className = 'file-btn-sm';
            btnDownload.textContent = 'Download';
            btnDownload.addEventListener('click', (e) => { e.stopPropagation(); downloadFile(path, name); });
            tdActions.appendChild(btnDownload);
        }
        const btnDelete = document.createElement('button');
        btnDelete.className = 'file-btn-sm file-btn-danger';
        btnDelete.textContent = 'Delete';
        btnDelete.addEventListener('click', (e) => { e.stopPropagation(); deleteFile(path, name); });
        tdActions.appendChild(btnDelete);
        tr.appendChild(tdActions);
        els.fileTableBody.appendChild(tr);
    });
}

/**
 * 进入子目录。
 */
function navigateTo(name) {
    const state = getFileManagerState();
    const sep = getSeparator(state.currentPath);
    const rawPath = state.currentPath + (state.currentPath.endsWith(sep) ? '' : sep) + name;
    const path = sanitizePath(rawPath);
    loadFileList(path);
}

/**
 * 返回上级目录。
 */
function navigateUp() {
    const state = getFileManagerState();
    const parent = resolveParent(state.currentPath);
    loadFileList(parent);
}

/**
 * 刷新当前目录列表。
 */
async function refreshFileList() {
    const state = getFileManagerState();
    await loadFileList(state.currentPath);
}

/**
 * 上传文件到当前目录。
 * 使用 FileReader 读取为 ArrayBuffer，Base64 编码后通过 WRITE 动作写入远程。
 */
async function handleUpload(input) {
    const file = input.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async () => {
        const bytes = new Uint8Array(reader.result);
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        const base64 = btoa(binary);
        const state = getFileManagerState();
        const sep = getSeparator(state.currentPath);
        const path = sanitizePath(state.currentPath + (state.currentPath.endsWith(sep) ? '' : sep) + file.name);
        try {
            const text = await fileRequest('WRITE', path, base64);
            if (text.startsWith('OK|')) {
                alert('上传成功');
                refreshFileList();
            } else {
                alert('上传失败: ' + text);
            }
        } catch (e) {
            alert('上传失败');
        }
    };
    reader.readAsArrayBuffer(file);
    input.value = '';
}

/**
 * 下载文件。
 * 通过 DOWNLOAD 动作获取 Base64 内容，解码后生成 Blob 并触发浏览器下载。
 */
async function downloadFile(path, name) {
    try {
        const text = await fileRequest('DOWNLOAD', path);
        if (!text.startsWith('OK|')) {
            alert('下载失败: ' + text);
            return;
        }
        const base64 = text.substring(3);
        const bytes = Uint8Array.from(atob(base64), c => c.charCodeAt(0));
        const blob = new Blob([bytes]);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    } catch (e) {
        alert('下载失败');
    }
}

/**
 * 删除文件或目录。
 * 弹出确认框，确认后发送 DELETE 请求。
 */
async function deleteFile(path, name) {
    if (!confirm('确认删除 ' + name + ' ?')) return;
    try {
        const text = await fileRequest('DELETE', path);
        if (text.startsWith('OK|')) {
            alert('删除成功');
            refreshFileList();
        } else {
            alert('删除失败: ' + text);
        }
    } catch (e) {
        alert('删除失败');
    }
}

/* ========== 文件编辑器模块 ========== */

/**
 * 打开文件编辑器。
 * 根据扩展名判断是否为文本文件；二进制文件提示无法预览。
 * 通过 READ 动作获取文件内容，支持大文件限制（服务端已限制 1MB）。
 */
async function openFileEditor(path, name) {
    const textExts = ['.txt', '.log', '.java', '.py', '.js', '.html', '.css', '.xml', '.json', '.md', '.sh', '.bat', '.cmd', '.ini', '.conf', '.properties', '.yml', '.yaml', '.jsp', '.php', '.go', '.rs', '.c', '.cpp', '.h', '.hpp', '.sql'];
    const isText = textExts.some(ext => name.toLowerCase().endsWith(ext));
    if (!isText) {
        alert('无法预览，请使用下载功能');
        return;
    }
    try {
        const text = await fileRequest('READ', path);
        if (!text.startsWith('OK|')) {
            alert('读取失败: ' + text);
            return;
        }
        const content = text.substring(3);
        const state = getFileManagerState();
        state.editorOpen = true;
        state.editorPath = path;
        state.editorFileName = name;
        state.editorContent = content;
        state.editorDirty = false;
        renderEditor();
    } catch (e) {
        alert('读取失败');
    }
}

/**
 * 渲染编辑器弹窗状态。
 */
function renderEditor() {
    const state = getFileManagerState();
    console.log('[renderEditor] editorOpen=', state.editorOpen);
    if (!state.editorOpen) {
        els.editorModal.style.display = 'none';
        return;
    }
    els.editorFileName.textContent = state.editorFileName || '';
    els.editorTextarea.value = state.editorContent || '';
    els.editorStatus.textContent = '';
    els.editorModal.style.display = 'flex';
    console.log('[renderEditor] modal display set to flex');
}

/**
 * 关闭编辑器。
 * 若内容有未保存的修改，弹出二次确认。
 */
function closeEditor() {
    const state = getFileManagerState();
    if (state.editorDirty) {
        if (!confirm('文件未保存，确定要关闭吗？')) return;
    }
    state.editorOpen = false;
    state.editorPath = '';
    state.editorFileName = '';
    state.editorContent = '';
    state.editorDirty = false;
    els.editorModal.style.display = 'none';
}

/**
 * 保存编辑器内容到远程文件。
 * 将内容转为 UTF-8 后 Base64 编码，通过 WRITE 动作写回服务端。
 */
async function saveFile() {
    const state = getFileManagerState();
    if (!state.editorOpen) return;
    const content = els.editorTextarea.value;
    const base64 = utf8ToBase64(content);
    try {
        const text = await fileRequest('WRITE', state.editorPath, base64);
        if (text.startsWith('OK|')) {
            els.editorStatus.textContent = '保存成功';
            els.editorStatus.style.color = '#28ffd3';
            state.editorContent = content;
            state.editorDirty = false;
            setTimeout(() => { els.editorStatus.textContent = ''; }, 2000);
        } else {
            els.editorStatus.textContent = '保存失败: ' + text;
            els.editorStatus.style.color = '#ff6b8b';
        }
    } catch (e) {
        els.editorStatus.textContent = '保存失败';
        els.editorStatus.style.color = '#ff6b8b';
    }
}

/**
 * 路径输入框键盘事件。
 * Enter 触发跳转。
 */
function handlePathKey(event) {
    if (event.key === 'Enter') {
        event.preventDefault();
        jumpToPath();
    }
}

/**
 * 根据输入框路径跳转。
 */
async function jumpToPath() {
    const input = els.currentPath;
    let path = sanitizePath(input.value);
    if (!path) path = '/';
    input.value = path;
    const ok = await loadFileList(path);
    if (!ok) {
        alert('目录不存在或无法访问');
    }
}

/**
 * 页面加载完成后初始化。
 */
window.addEventListener('DOMContentLoaded', async () => {
    cacheElements();
    setButtonsEnabled(false);
    await loadConnections();
    await showTab('info');

    // 编辑器输入监听：标记为已修改
    els.editorTextarea.addEventListener('input', () => {
        const state = getFileManagerState();
        state.editorDirty = true;
    });

    // 路径输入框回车监听
    if (els.currentPath) {
        els.currentPath.addEventListener('keydown', handlePathKey);
    }
});

// 将需要暴露给 HTML 内联事件调用的函数挂载到 window 对象
window.addConn = addConn;
window.modifyConn = modifyConn;
window.removeConn = removeConn;
window.showTab = showTab;
window.openExecTab = openExecTab;
window.handleCmd = handleCmd;
window.submitCmd = submitCmd;
window.openFileTab = openFileTab;
window.navigateUp = navigateUp;
window.refreshFileList = refreshFileList;
window.handleUpload = handleUpload;
window.navigateTo = navigateTo;
window.downloadFile = downloadFile;
window.deleteFile = deleteFile;
window.openFileEditor = openFileEditor;
window.saveFile = saveFile;
window.closeEditor = closeEditor;
window.handlePathKey = handlePathKey;
window.jumpToPath = jumpToPath;
