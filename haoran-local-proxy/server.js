const http = require('node:http');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const { randomBytes, timingSafeEqual } = require('node:crypto');
const host = '127.0.0.1';
const port = readInteger(process.env.LOCAL_PROXY_PORT, 7777, 1, 65535);
const maxActiveRequests = readInteger(process.env.LOCAL_PROXY_MAX_ACTIVE_REQUESTS, 16, 1, 64);
const maxConnections = readInteger(process.env.LOCAL_PROXY_MAX_CONNECTIONS, 32, 1, 128);
const maxScanFiles = readInteger(process.env.LOCAL_PROXY_MAX_SCAN_FILES, 2000, 1, 5000);
const maxScanDepth = readInteger(process.env.LOCAL_PROXY_MAX_SCAN_DEPTH, 20, 0, 64);
const scanTimeoutMs = readInteger(process.env.LOCAL_PROXY_SCAN_TIMEOUT_MS, 10000, 1000, 30000);
const maxCheckPaths = readInteger(process.env.LOCAL_PROXY_MAX_CHECK_PATHS, 200, 1, 1000);
const metadataTimeoutMs = readInteger(process.env.LOCAL_PROXY_METADATA_TIMEOUT_MS, 4000, 500, 10000);
const maxMetadataProcesses = readInteger(process.env.LOCAL_PROXY_MAX_METADATA_PROCESSES, 2, 1, 4);
const ffprobePath = process.env.LOCAL_PROXY_FFPROBE_PATH || 'ffprobe';
const maxBodyBytes = 256 * 1024;
const accessToken = randomBytes(32).toString('hex');
const mediaTicketTtlMs = readInteger(process.env.LOCAL_PROXY_MEDIA_TICKET_TTL_MS, 30 * 60 * 1000, 60000, 2 * 60 * 60 * 1000);
const maxMediaTickets = readInteger(process.env.LOCAL_PROXY_MAX_MEDIA_TICKETS, 256, 16, 1000);
const folderPickerTimeoutMs = 5 * 60 * 1000;
const configuredRoots = normalizeConfiguredRoots(process.env.LOCAL_PROXY_ALLOWED_ROOTS || '');
const allowedOrigins = new Set((process.env.LOCAL_PROXY_ALLOWED_ORIGINS || [
    'http://localhost:3000',
    'http://127.0.0.1:3000',
    'http://192.168.153.131:3223'
].join(';')).split(';').map(value => value.trim()).filter(Boolean));
const mediaExtensions = new Set([
    '.mp3', '.flac', '.wav', '.m4a', '.aac', '.ogg', '.wma', '.ape',
    '.mp4', '.mov', '.avi', '.mkv', '.flv', '.wmv'
]);
const videoExtensions = new Set(['.mp4', '.mov', '.avi', '.mkv', '.flv', '.wmv']);
const mimeTypes = {
    '.aac': 'audio/aac', '.ape': 'audio/ape', '.flac': 'audio/flac', '.m4a': 'audio/mp4',
    '.mp3': 'audio/mpeg', '.ogg': 'audio/ogg', '.wav': 'audio/wav', '.wma': 'audio/x-ms-wma',
    '.avi': 'video/x-msvideo', '.flv': 'video/x-flv', '.mkv': 'video/x-matroska',
    '.mov': 'video/quicktime', '.mp4': 'video/mp4', '.wmv': 'video/x-ms-wmv'
};
let allowedRoots = [];
let activeRequests = 0;
let activeMetadataProcesses = 0;
const mediaTickets = new Map();
let folderPickerOpen = false;
const metadataWaiters = [];
const execFileAsync = promisify(execFile);
const folderPickerScript = [
    'Add-Type -AssemblyName System.Windows.Forms',
    '$dialog = New-Object System.Windows.Forms.FolderBrowserDialog',
    "$dialog.Description = '选择需要扫描的本地音乐文件夹'",
    '$dialog.ShowNewFolderButton = $false',
    'if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { [Console]::Out.Write($dialog.SelectedPath) }'
].join('; ');
function readInteger(value, fallback, minimum, maximum) {
    if (value === undefined || value === '')
        return fallback;
    const parsed = Number(value);
    if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
        throw new Error(`invalid proxy configuration value: ${value}`);
    }
    return parsed;
}
function normalizeConfiguredRoots(rawRoots) {
    const roots = [];
    for (const rawRoot of rawRoots.split(';').map(value => value.trim()).filter(Boolean)) {
        const resolved = path.resolve(rawRoot);
        if (path.parse(resolved).root.toLowerCase() === resolved.toLowerCase()) {
            throw new Error('LOCAL_PROXY_ALLOWED_ROOTS must contain folders, not a disk root');
        }
        if (!roots.some(root => root.toLowerCase() === resolved.toLowerCase()))
            roots.push(resolved);
    }
    return roots;
}
function isWithin(rootPath, candidatePath) {
    const relative = path.relative(rootPath, candidatePath);
    return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}
function isDiskRoot(filePath) {
    return path.parse(filePath).root.toLowerCase() === filePath.toLowerCase();
}
function isMediaPath(filePath) {
    return mediaExtensions.has(path.extname(filePath).toLowerCase());
}
function sendJson(response, statusCode, body) {
    if (response.writableEnded)
        return;
    response.writeHead(statusCode, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'DENY',
        'Referrer-Policy': 'no-referrer'
    });
    response.end(JSON.stringify(body));
}
function originIsAllowed(request) {
    const origin = request.headers.origin;
    return !origin || allowedOrigins.has(origin);
}
function applyCors(request, response) {
    const origin = request.headers.origin;
    if (origin) {
        response.setHeader('Access-Control-Allow-Origin', origin);
        response.setHeader('Vary', 'Origin');
    }
    response.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    response.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-HaoRan-Local-Token');
    response.setHeader('Access-Control-Max-Age', '600');
}
function hasValidAccessToken(request) {
    const supplied = request.headers['x-haoran-local-token'];
    if (typeof supplied !== 'string' || supplied.length !== accessToken.length)
        return false;
    return timingSafeEqual(Buffer.from(supplied), Buffer.from(accessToken));
}
function pruneMediaTickets(now = Date.now()) {
    for (const [ticket, value] of mediaTickets) {
        if (value.expiresAt <= now)
            mediaTickets.delete(ticket);
    }
    while (mediaTickets.size >= maxMediaTickets)
        mediaTickets.delete(mediaTickets.keys().next().value);
}
function createMediaTicket(filePath) {
    pruneMediaTickets();
    const ticket = randomBytes(24).toString('hex');
    mediaTickets.set(ticket, { filePath, expiresAt: Date.now() + mediaTicketTtlMs });
    return ticket;
}
function hasValidMediaTicket(ticket, filePath) {
    if (typeof ticket !== 'string' || ticket.length !== 48)
        return false;
    pruneMediaTickets();
    const value = mediaTickets.get(ticket);
    return Boolean(value && value.filePath === filePath && value.expiresAt > Date.now());
}
async function initializeAllowedRoots() {
    const existingRoots = [];
    for (const configuredRoot of configuredRoots) {
        try {
            const stats = await fsp.stat(configuredRoot);
            if (!stats.isDirectory())
                continue;
            const realRoot = await fsp.realpath(configuredRoot);
            if (!existingRoots.some(root => root.toLowerCase() === realRoot.toLowerCase()))
                existingRoots.push(realRoot);
        }
        catch {
        }
    }
    allowedRoots = existingRoots;
}
async function resolveExistingAllowedPath(input) {
    if (typeof input !== 'string' || !input.trim())
        return null;
    const lexicalPath = path.resolve(input);
    if (isDiskRoot(lexicalPath))
        return null;
    const lexicalRoots = configuredRoots.length > 0 ? configuredRoots : allowedRoots;
    if (!lexicalRoots.some(root => isWithin(root, lexicalPath)))
        return null;
    try {
        const realPath = await fsp.realpath(lexicalPath);
        if (isDiskRoot(realPath))
            return null;
        return allowedRoots.some(root => isWithin(root, realPath)) ? realPath : null;
    }
    catch {
        return null;
    }
}
async function resolveExistingNonRootDirectory(input) {
    if (typeof input !== 'string' || !input.trim())
        return null;
    const lexicalPath = path.resolve(input);
    if (isDiskRoot(lexicalPath))
        return null;
    try {
        const realPath = await fsp.realpath(lexicalPath);
        if (isDiskRoot(realPath) || !(await fsp.stat(realPath)).isDirectory())
            return null;
        return realPath;
    }
    catch {
        return null;
    }
}
async function selectFolderFromSystem() {
    if (process.platform !== 'win32')
        throw new Error('folder picker is only available on Windows');
    if (folderPickerOpen)
        throw new Error('folder picker is already open');
    folderPickerOpen = true;
    try {
        const { stdout } = await execFileAsync('powershell.exe', [
            '-NoProfile',
            '-Sta',
            '-Command',
            folderPickerScript
        ], {
            timeout: folderPickerTimeoutMs,
            maxBuffer: 64 * 1024,
            windowsHide: false
        });
        const selectedPath = await resolveExistingNonRootDirectory(stdout.trim());
        if (!selectedPath)
            return null;
        if (configuredRoots.length > 0) {
            return allowedRoots.some(root => isWithin(root, selectedPath)) ? selectedPath : null;
        }
        if (!allowedRoots.some(root => root.toLowerCase() === selectedPath.toLowerCase())) {
            allowedRoots.push(selectedPath);
        }
        return selectedPath;
    }
    finally {
        folderPickerOpen = false;
    }
}
function toLocalFile(filePath, stats) {
    const extension = path.extname(filePath).toLowerCase();
    const type = videoExtensions.has(extension) ? 'video' : 'audio';
    return { path: filePath, type, format: extension.slice(1), size: stats.size, modified: stats.mtime.toISOString() };
}
function toFiniteNumber(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
}
function toPositiveInteger(value) {
    const parsed = toFiniteNumber(value);
    return parsed && parsed > 0 ? Math.round(parsed) : undefined;
}
function getTag(tags, names) {
    if (!tags || typeof tags !== 'object')
        return undefined;
    const normalized = Object.fromEntries(Object.entries(tags).map(([key, value]) => [key.toLowerCase(), value]));
    for (const name of names) {
        const value = normalized[name];
        if (typeof value === 'string' && value.trim())
            return value.trim();
    }
    return undefined;
}
function determineQuality(extension, bitrate) {
    if (['.flac', '.wav', '.ape'].includes(extension))
        return 'lossless';
    if (bitrate && bitrate >= 256)
        return 'high';
    return 'standard';
}
function runWithMetadataSlot(task) {
    return new Promise((resolve, reject) => {
        const start = () => {
            activeMetadataProcesses += 1;
            Promise.resolve(task()).then(resolve, reject).finally(() => {
                activeMetadataProcesses -= 1;
                const next = metadataWaiters.shift();
                if (next)
                    next();
            });
        };
        if (activeMetadataProcesses < maxMetadataProcesses)
            start();
        else
            metadataWaiters.push(start);
    });
}
async function readMediaMetadata(filePath) {
    const extension = path.extname(filePath).toLowerCase();
    try {
        const { stdout } = await runWithMetadataSlot(() => execFileAsync(ffprobePath, [
            '-v', 'error',
            '-show_entries', 'format=duration,bit_rate,format_name:format_tags=title,artist,album,date,year,genre,track:stream=codec_type,codec_name,bit_rate,sample_rate,bits_per_raw_sample,bits_per_sample,channels:stream_tags=title,artist,album',
            '-of', 'json',
            filePath
        ], {
            timeout: metadataTimeoutMs,
            maxBuffer: 256 * 1024,
            windowsHide: true,
            encoding: 'utf8'
        }));
        const inspected = JSON.parse(stdout);
        const format = inspected.format || {};
        const stream = (inspected.streams || []).find(item => item?.codec_type === 'audio');
        if (!stream)
            return undefined;
        const bitrate = toPositiveInteger(stream.bit_rate) || toPositiveInteger(format.bit_rate);
        const yearValue = getTag(format.tags, ['date', 'year']) || getTag(stream.tags, ['date', 'year']);
        const yearMatch = yearValue?.match(/^(\d{4})/);
        const genreValue = getTag(format.tags, ['genre']) || getTag(stream.tags, ['genre']);
        const trackValue = getTag(format.tags, ['track', 'tracknumber']) || getTag(stream.tags, ['track', 'tracknumber']);
        const [trackNo, trackOf] = (trackValue || '').split('/').map(toPositiveInteger);
        return {
            duration: toFiniteNumber(format.duration),
            bitrate,
            codec: typeof stream.codec_name === 'string' ? stream.codec_name : extension.slice(1),
            quality: determineQuality(extension, bitrate),
            formatName: typeof format.format_name === 'string' ? format.format_name : extension.slice(1),
            title: getTag(format.tags, ['title']) || getTag(stream.tags, ['title']),
            artist: getTag(format.tags, ['artist', 'album_artist']) || getTag(stream.tags, ['artist', 'album_artist']),
            album: getTag(format.tags, ['album']) || getTag(stream.tags, ['album']),
            ...(yearMatch ? { year: Number(yearMatch[1]) } : {}),
            ...(genreValue ? { genre: genreValue.split(/[;/]/).map(value => value.trim()).filter(Boolean) } : {}),
            ...(trackNo ? { track: { no: trackNo, ...(trackOf ? { of: trackOf } : {}) } } : {}),
            sampleRate: toPositiveInteger(stream.sample_rate),
            bitsPerSample: toPositiveInteger(stream.bits_per_raw_sample) || toPositiveInteger(stream.bits_per_sample),
            numberOfChannels: toPositiveInteger(stream.channels),
            formatQuality: determineQuality(extension, bitrate)
        };
    }
    catch {
        return undefined;
    }
}
async function scanDirectory(rootPath, recursive) {
    const files = [];
    let audio = 0;
    let video = 0;
    let stopReason = '';
    const deadline = Date.now() + scanTimeoutMs;
    async function visit(currentPath, depth) {
        if (stopReason)
            return;
        if (Date.now() > deadline) {
            stopReason = 'time_limit';
            return;
        }
        if (files.length >= maxScanFiles) {
            stopReason = 'file_limit';
            return;
        }
        let entries;
        try {
            entries = await fsp.readdir(currentPath, { withFileTypes: true });
        }
        catch {
            return;
        }
        for (const entry of entries) {
            if (stopReason)
                return;
            if (Date.now() > deadline) {
                stopReason = 'time_limit';
                return;
            }
            if (files.length >= maxScanFiles) {
                stopReason = 'file_limit';
                return;
            }
            const entryPath = path.join(currentPath, entry.name);
            if (entry.isSymbolicLink())
                continue;
            if (entry.isDirectory()) {
                if (recursive && depth < maxScanDepth)
                    await visit(entryPath, depth + 1);
                continue;
            }
            if (!entry.isFile() || !isMediaPath(entryPath))
                continue;
            try {
                const stats = await fsp.stat(entryPath);
                const file = toLocalFile(entryPath, stats);
                files.push(file);
                if (file.type === 'audio')
                    audio += 1;
                else
                    video += 1;
            }
            catch {
            }
        }
    }
    await visit(rootPath, 0);
    return {
        path: rootPath,
        total: files.length,
        audio,
        video,
        files,
        truncated: Boolean(stopReason),
        ...(stopReason ? { stopReason } : {})
    };
}
function readJsonBody(request) {
    return new Promise((resolve, reject) => {
        let size = 0;
        let tooLarge = false;
        const chunks = [];
        request.on('data', chunk => {
            size += chunk.length;
            if (size > maxBodyBytes) {
                tooLarge = true;
                return;
            }
            chunks.push(chunk);
        });
        request.on('end', () => {
            if (tooLarge)
                return reject(new Error('request body too large'));
            try {
                resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
            }
            catch {
                reject(new Error('invalid json'));
            }
        });
        request.on('error', () => reject(new Error('invalid request body')));
    });
}
function pipeFile(response, filePath, options) {
    const stream = fs.createReadStream(filePath, options);
    const closeStream = () => stream.destroy();
    response.once('close', closeStream);
    stream.once('error', () => {
        response.off('close', closeStream);
        if (!response.writableEnded)
            response.destroy();
    });
    stream.once('end', () => response.off('close', closeStream));
    stream.pipe(response);
}
async function handleFile(request, response, filePath) {
    let stats;
    try {
        stats = await fsp.stat(filePath);
    }
    catch {
        return sendJson(response, 404, { error: 'file not found' });
    }
    if (!stats.isFile())
        return sendJson(response, 400, { error: 'not a file' });
    if (!isMediaPath(filePath))
        return sendJson(response, 415, { error: 'unsupported media type' });
    const extension = path.extname(filePath).toLowerCase();
    const range = request.headers.range;
    response.setHeader('Accept-Ranges', 'bytes');
    response.setHeader('Content-Type', mimeTypes[extension] || 'application/octet-stream');
    response.setHeader('Cache-Control', 'private, no-store');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    response.setHeader('X-Frame-Options', 'DENY');
    response.setHeader('Referrer-Policy', 'no-referrer');
    if (stats.size === 0) {
        if (range) {
            response.writeHead(416, { 'Content-Range': 'bytes */0' });
            return response.end();
        }
        response.writeHead(200, { 'Content-Length': 0 });
        return response.end();
    }
    if (!range) {
        response.writeHead(200, { 'Content-Length': stats.size });
        return pipeFile(response, filePath);
    }
    const match = /^bytes=(\d*)-(\d*)$/.exec(range);
    if (!match || (!match[1] && !match[2])) {
        response.writeHead(416, { 'Content-Range': `bytes */${stats.size}` });
        return response.end();
    }
    const rawStart = match[1] ? Number(match[1]) : undefined;
    const rawEnd = match[2] ? Number(match[2]) : undefined;
    if ((rawStart !== undefined && !Number.isSafeInteger(rawStart)) || (rawEnd !== undefined && !Number.isSafeInteger(rawEnd))) {
        response.writeHead(416, { 'Content-Range': `bytes */${stats.size}` });
        return response.end();
    }
    const start = rawStart === undefined ? Math.max(stats.size - rawEnd, 0) : rawStart;
    const end = rawStart === undefined ? stats.size - 1 : Math.min(rawEnd === undefined ? stats.size - 1 : rawEnd, stats.size - 1);
    if (start < 0 || start >= stats.size || start > end || (rawStart === undefined && rawEnd <= 0)) {
        response.writeHead(416, { 'Content-Range': `bytes */${stats.size}` });
        return response.end();
    }
    response.writeHead(206, {
        'Content-Range': `bytes ${start}-${end}/${stats.size}`,
        'Content-Length': end - start + 1
    });
    return pipeFile(response, filePath, { start, end });
}
async function checkPaths(paths) {
    const results = new Array(paths.length);
    let nextIndex = 0;
    const workerCount = Math.min(16, paths.length);
    async function worker() {
        while (nextIndex < paths.length) {
            const index = nextIndex++;
            const inputPath = paths[index];
            const allowedPath = await resolveExistingAllowedPath(inputPath);
            if (!allowedPath) {
                results[index] = { path: inputPath, exists: false };
                continue;
            }
            if (!isMediaPath(allowedPath)) {
                results[index] = { path: inputPath, exists: false, error: 'unsupported media type' };
                continue;
            }
            try {
                const stats = await fsp.stat(allowedPath);
                results[index] = {
                    path: inputPath,
                    exists: stats.isFile(),
                    ...(stats.isFile() ? { size: stats.size } : {}),
                    modified: stats.mtime.toISOString()
                };
            }
            catch {
                results[index] = { path: inputPath, exists: false };
            }
        }
    }
    await Promise.all(Array.from({ length: workerCount }, worker));
    return results;
}
async function handleRequest(request, response) {
    if (!originIsAllowed(request))
        return sendJson(response, 403, { error: 'origin not allowed' });
    applyCors(request, response);
    if (request.method === 'OPTIONS') {
        response.writeHead(204);
        return response.end();
    }
    const requestUrl = new URL(request.url, `http://${host}:${port}`);
    if (request.method === 'GET' && requestUrl.pathname === '/health') {
        return sendJson(response, 200, {
            status: 'ok',
            port,
            availableRoots: allowedRoots.length,
            pathMode: configuredRoots.length > 0 ? 'configured-folders' : 'selected-folders'
        });
    }
    if (request.method === 'POST' && requestUrl.pathname === '/session') {
        const origin = request.headers.origin;
        if (!origin || !allowedOrigins.has(origin)) {
            return sendJson(response, 403, { error: 'an allowed browser origin is required' });
        }
        return sendJson(response, 200, { accessToken });
    }
    if (requestUrl.pathname.startsWith('/api/')
        && requestUrl.pathname !== '/api/file'
        && !hasValidAccessToken(request)) {
        return sendJson(response, 401, { error: 'local proxy authorization required' });
    }
    if (request.method === 'GET' && requestUrl.pathname === '/api/default-paths') {
        return sendJson(response, 200, { paths: allowedRoots });
    }
    if (request.method === 'POST' && requestUrl.pathname === '/api/select-folder') {
        if (folderPickerOpen)
            return sendJson(response, 409, { error: 'folder picker is already open' });
        try {
            return sendJson(response, 200, { path: await selectFolderFromSystem() });
        }
        catch {
            return sendJson(response, 500, { error: 'folder picker failed' });
        }
    }
    const requestedPath = requestUrl.searchParams.get('path');
    if (request.method === 'GET' && requestUrl.pathname === '/api/scan') {
        const allowedPath = await resolveExistingAllowedPath(requestedPath);
        if (!allowedPath)
            return sendJson(response, 403, { error: 'path is unavailable, a disk root, or outside authorized media folders' });
        try {
            if (!(await fsp.stat(allowedPath)).isDirectory())
                return sendJson(response, 400, { error: 'path is not a directory' });
            return sendJson(response, 200, await scanDirectory(allowedPath, requestUrl.searchParams.get('recursive') === 'true'));
        }
        catch {
            return sendJson(response, 404, { error: 'directory not found' });
        }
    }
    if (request.method === 'GET' && requestUrl.pathname === '/api/file-info') {
        const allowedPath = await resolveExistingAllowedPath(requestedPath);
        if (!allowedPath)
            return sendJson(response, 403, { error: 'path is unavailable, a disk root, or outside authorized media folders' });
        if (!isMediaPath(allowedPath))
            return sendJson(response, 415, { error: 'unsupported media type' });
        try {
            const stats = await fsp.stat(allowedPath);
            if (!stats.isFile())
                return sendJson(response, 400, { error: 'not a file' });
            const audio = await readMediaMetadata(allowedPath);
            return sendJson(response, 200, {
                path: allowedPath,
                name: path.basename(allowedPath),
                size: stats.size,
                modified: stats.mtime.toISOString(),
                created: stats.birthtime.toISOString(),
                mime: mimeTypes[path.extname(allowedPath).toLowerCase()] || 'application/octet-stream',
                format: path.extname(allowedPath).slice(1).toLowerCase(),
                ...(audio ? { audio } : {})
            });
        }
        catch {
            return sendJson(response, 404, { error: 'file not found' });
        }
    }
    if (request.method === 'POST' && requestUrl.pathname === '/api/file-ticket') {
        try {
            const body = await readJsonBody(request);
            const allowedPath = await resolveExistingAllowedPath(body && body.path);
            if (!allowedPath)
                return sendJson(response, 403, { error: 'path is unavailable or outside authorized media folders' });
            if (!isMediaPath(allowedPath))
                return sendJson(response, 415, { error: 'unsupported media type' });
            const stats = await fsp.stat(allowedPath);
            if (!stats.isFile())
                return sendJson(response, 400, { error: 'not a file' });
            return sendJson(response, 200, {
                ticket: createMediaTicket(allowedPath),
                expiresInSeconds: Math.floor(mediaTicketTtlMs / 1000)
            });
        }
        catch {
            return sendJson(response, 400, { error: 'invalid request body' });
        }
    }
    if (request.method === 'GET' && requestUrl.pathname === '/api/file') {
        const allowedPath = await resolveExistingAllowedPath(requestedPath);
        if (!allowedPath)
            return sendJson(response, 403, { error: 'path is unavailable, a disk root, or outside authorized media folders' });
        if (!hasValidMediaTicket(requestUrl.searchParams.get('ticket'), allowedPath)) {
            return sendJson(response, 401, { error: 'a valid media ticket is required' });
        }
        return handleFile(request, response, allowedPath);
    }
    if (request.method === 'POST' && requestUrl.pathname === '/api/check') {
        try {
            const paths = await readJsonBody(request);
            if (!Array.isArray(paths) || paths.length > maxCheckPaths || paths.some(item => typeof item !== 'string')) {
                return sendJson(response, 400, { error: 'invalid path list' });
            }
            return sendJson(response, 200, { results: await checkPaths(paths) });
        }
        catch {
            return sendJson(response, 400, { error: 'invalid request body' });
        }
    }
    return sendJson(response, 404, { error: 'not found' });
}
const server = http.createServer((request, response) => {
    if (!originIsAllowed(request))
        return sendJson(response, 403, { error: 'origin not allowed' });
    applyCors(request, response);
    if (activeRequests >= maxActiveRequests)
        return sendJson(response, 503, { error: 'proxy is busy, retry shortly' });
    activeRequests += 1;
    let released = false;
    const release = () => {
        if (released)
            return;
        released = true;
        activeRequests -= 1;
    };
    response.once('finish', release);
    response.once('close', release);
    handleRequest(request, response).catch(() => {
        sendJson(response, 500, { error: 'proxy request failed' });
    });
});
server.requestTimeout = 30000;
server.headersTimeout = 10000;
server.keepAliveTimeout = 5000;
server.maxRequestsPerSocket = 100;
server.maxConnections = maxConnections;
server.on('clientError', (_error, socket) => socket.end('HTTP/1.1 400 Bad Request\r\n\r\n'));
async function start() {
    await initializeAllowedRoots();
    server.listen(port, host, () => {
        const pathMode = configuredRoots.length > 0
            ? `${allowedRoots.length} configured media folders`
            : 'no media folders selected; choose a folder from the app to grant access for this session';
        console.log(`HaoRan local music proxy listening on http://${host}:${port}; media access: ${pathMode}`);
    });
}
start().catch(error => {
    console.error(`Unable to start HaoRan local music proxy: ${error.message}`);
    process.exitCode = 1;
});
