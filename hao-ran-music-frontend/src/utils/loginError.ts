interface LoginErrorRecord {
    canceled?: boolean;
    code?: number | string;
    isAuthError?: boolean;
    isUserNotExist?: boolean;
    message?: string;
    data?: LoginErrorData;
    response?: {
        status?: number;
        data?: LoginErrorPayload;
    };
}
interface LoginErrorData {
    needCaptcha?: boolean;
    retryAfterSeconds?: number;
}
interface LoginErrorPayload extends LoginErrorData {
    code?: number;
    message?: string;
    data?: LoginErrorData;
}
export interface LoginErrorFeedback {
    title: string;
    message: string;
    tone: 'danger' | 'warning';
    retryAfterSeconds?: number;
}
function toLoginErrorRecord(error: unknown): LoginErrorRecord {
    return error && typeof error === 'object' ? (error as LoginErrorRecord) : {};
}
function getStructuredData(details: LoginErrorRecord): LoginErrorData {
    return details.data ?? details.response?.data?.data ?? details.response?.data ?? {};
}
function normalizeRetryAfter(value: unknown): number | undefined {
    const seconds = Number(value);
    if (!Number.isFinite(seconds) || seconds <= 0)
        return undefined;
    return Math.min(Math.ceil(seconds), 24 * 60 * 60);
}
const ACCOUNT_ERROR_TRANSLATIONS: Record<string, string> = {
    'Passwords do not match': '两次输入的密码不一致',
    'Username already exists': '该用户名已被注册',
    'Phone number already registered': '该手机号已被注册',
    'Email already registered': '该邮箱已被注册',
    'This phone number is not registered': '该手机号尚未注册'
};
export function getAccountActionErrorMessage(error: unknown, fallback: string): string | null {
    const details = toLoginErrorRecord(error);
    if (details.canceled || details.isAuthError)
        return null;
    const responseData = details.response?.data;
    const code = details.code ?? responseData?.code;
    const status = details.response?.status;
    const message = (responseData?.message ?? details.message ?? '').trim();
    if (code === 'ECONNABORTED' || message.toLowerCase().includes('timeout')) {
        return '请求超时，请稍后重试';
    }
    if (code === 'ERR_NETWORK' || (!status && message === 'Network Error')) {
        return '无法连接服务，请检查网络或稍后重试';
    }
    if (status === 429 || code === 429 || message.includes('频繁')) {
        return '操作过于频繁，请稍后再试';
    }
    if ((status && status >= 500) ||
        (typeof code === 'number' && code >= 500 && code < 600)) {
        return '服务暂时不可用，请稍后重试';
    }
    if (code === 1003 && !message) {
        return '用户名、手机号或邮箱已被注册';
    }
    if (message && ACCOUNT_ERROR_TRANSLATIONS[message]) {
        return ACCOUNT_ERROR_TRANSLATIONS[message];
    }
    const genericMessages = new Set(['Request failed', 'Network Error', 'Error']);
    if (message && message.length <= 80 && !genericMessages.has(message)) {
        return message;
    }
    return fallback;
}
export function getLoginErrorFeedback(error: unknown): LoginErrorFeedback | null {
    const details = toLoginErrorRecord(error);
    if (details.canceled || details.isAuthError)
        return null;
    const responseData = details.response?.data;
    const code = details.code ?? responseData?.code;
    const status = details.response?.status;
    const message = (responseData?.message ?? details.message ?? '').trim();
    const structuredData = getStructuredData(details);
    const retryAfterSeconds = normalizeRetryAfter(structuredData.retryAfterSeconds);
    if (code === 1001 ||
        code === 1002 ||
        details.isUserNotExist ||
        message === 'USER_NOT_EXIST' ||
        message.includes('用户名或密码错误')) {
        return {
            title: '登录未成功',
            message: '账号或密码错误，请检查后重新输入',
            tone: 'danger'
        };
    }
    if (code === 'ECONNABORTED' || message.toLowerCase().includes('timeout')) {
        return { title: '请求超时', message: '登录服务响应较慢，请稍后重试', tone: 'warning' };
    }
    if (code === 'ERR_NETWORK' || (!status && message === 'Network Error')) {
        return {
            title: '暂时无法连接',
            message: '请检查网络连接，确认后再试一次',
            tone: 'warning'
        };
    }
    if (status === 429 || code === 429 || message.includes('登录尝试过于频繁')) {
        return {
            title: '请稍候再登录',
            message: retryAfterSeconds
                ? `尝试次数较多，为保护账号安全，请在 ${retryAfterSeconds} 秒后重试`
                : '尝试次数较多，为保护账号安全，请稍后再试',
            tone: 'warning',
            retryAfterSeconds
        };
    }
    if (message.includes('验证码')) {
        return { title: '需要安全验证', message, tone: 'warning' };
    }
    if (message === 'Account access is restricted' || message.includes('账号已被限制')) {
        return {
            title: '账号暂时受限',
            message: '当前账号已被限制登录，如有疑问请联系管理员',
            tone: 'danger'
        };
    }
    if (status === 403 || code === 403) {
        return {
            title: '暂时无法登录',
            message: '当前账号暂时无法登录，如有疑问请联系管理员',
            tone: 'danger'
        };
    }
    if ((status && status >= 500) ||
        (typeof code === 'number' && code >= 500 && code < 600)) {
        return {
            title: '服务暂时不可用',
            message: '登录服务正在恢复，请稍后重试',
            tone: 'warning'
        };
    }
    if ((status === 400 || code === 400) && message && message !== 'Request failed') {
        return { title: '请检查登录信息', message, tone: 'danger' };
    }
    return { title: '登录未成功', message: '请稍后重试', tone: 'danger' };
}
export function getLoginErrorMessage(error: unknown): string | null {
    return getLoginErrorFeedback(error)?.message ?? null;
}
export function isLoginCaptchaRequired(error: unknown): boolean {
    const details = toLoginErrorRecord(error);
    const responseData = details.response?.data;
    const structuredData = getStructuredData(details);
    const code = details.code ?? responseData?.code;
    const message = (responseData?.message ?? details.message ?? '').trim();
    return Boolean(structuredData.needCaptcha ||
        ((code === 400 || code === 403) && message.includes('验证码')));
}
