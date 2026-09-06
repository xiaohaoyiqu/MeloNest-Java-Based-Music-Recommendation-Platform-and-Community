export type RequestFailureKind = 'unauthorized' | 'forbidden' | 'conflict' | 'rate-limited' | 'network' | 'server' | 'unknown';
export interface RequestFailureState {
    kind: RequestFailureKind;
    title: string;
    description: string;
    retryable: boolean;
}
function responseStatus(error: unknown): number | undefined {
    if (!error || typeof error !== 'object')
        return undefined;
    const candidate = error as {
        code?: unknown;
        response?: {
            status?: unknown;
            data?: {
                code?: unknown;
            };
        };
    };
    const raw = candidate.response?.status ?? candidate.response?.data?.code;
    const status = Number(raw);
    return Number.isFinite(status) ? status : undefined;
}
export function classifyRequestFailure(error: unknown, fallbackTitle = '内容加载失败'): RequestFailureState {
    const status = responseStatus(error);
    const code = error && typeof error === 'object' ? String((error as {
        code?: unknown;
    }).code || '') : '';
    if (status === 401 || code === '401' || code === '1004' || code === '1005' || (error instanceof Error && error.message === 'AUTH_ERROR')) {
        return { kind: 'unauthorized', title: '登录状态已失效', description: '重新登录后即可继续访问这部分内容。', retryable: false };
    }
    if (status === 403) {
        return { kind: 'forbidden', title: '暂时无法访问', description: '当前账号没有访问这部分内容的权限。', retryable: false };
    }
    if (status === 409) {
        return { kind: 'conflict', title: '内容状态已变化', description: '刷新后可获取最新状态。', retryable: true };
    }
    if (status === 429) {
        return { kind: 'rate-limited', title: '这一站暂时有点忙', description: '稍等片刻再试，已经显示的内容不会受影响。', retryable: true };
    }
    if (code === 'ERR_NETWORK' || status === undefined && /network error/i.test(String((error as {
        message?: unknown;
    })?.message || ''))) {
        return { kind: 'network', title: '网络连接不可用', description: '检查网络连接后重试。', retryable: true };
    }
    if (status !== undefined && status >= 500) {
        return { kind: 'server', title: '服务暂时不可用', description: '服务器没有正常响应，请稍后重试。', retryable: true };
    }
    return { kind: 'unknown', title: fallbackTitle, description: '暂时没能取得内容，请稍后重试。', retryable: true };
}
