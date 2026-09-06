import axios, { AxiosInstance, AxiosRequestConfig, AxiosError, AxiosResponse } from 'axios';
import { ElMessage } from 'element-plus';
import { API_BASE_URL } from '@/config';
import router from '@/router';
import { logger as frontendLogger } from '@/utils/logger';
interface ApiResponse<T = any> {
    code: number;
    message: string;
    data: T;
    timestamp?: number;
}
const abortControllers = new Map<string, AbortController>();
let requestSequence = 0;
let isRedirectingToLogin = false;
export function clearAllRequests() {
    frontendLogger.capture('log', `[Request] 取消所有请求，共 ${abortControllers.size} 个`);
    abortControllers.forEach((controller, key) => {
        controller.abort();
    });
    abortControllers.clear();
}
export function resetLoginRedirectFlag() {
    isRedirectingToLogin = false;
}
function trackRequestStart(url: string) {
}
function trackRequestEnd(url: string, isCancelled: boolean, isFailed: boolean) {
}
function generateRequestId(config: AxiosRequestConfig): string {
    const method = config.method || 'GET';
    const url = config.url || '';
    return `${method}:${url}:${++requestSequence}`;
}
export function serializeParams(params: any): string {
    if (!params)
        return '';
    const parts: string[] = [];
    Object.keys(params).forEach(key => {
        const value = params[key];
        if (value === null || value === undefined)
            return;
        if (Array.isArray(value)) {
            value.forEach(item => {
                if (item !== null && item !== undefined) {
                    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(item)}`);
                }
            });
            return;
        }
        parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`);
    });
    return parts.join('&');
}
const service: AxiosInstance = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json;charset=UTF-8'
    },
    paramsSerializer: serializeParams
});
service.interceptors.request.use((config) => {
    const requestId = generateRequestId(config);
    const controller = config.signal ? undefined : new AbortController();
    if (controller) {
        config.signal = controller.signal;
        abortControllers.set(requestId, controller);
    }
    config.metadata = { requestId, abortController: controller };
    trackRequestStart(config.url || 'unknown');
    return config;
}, (error) => {
    return Promise.reject(error);
});
function isCurrentRoutePublic() {
    return router.currentRoute.value.matched.some(record => Boolean(record.meta?.public));
}
const AUTH_EXPIRED_EVENT = 'haoran:auth-expired';
type AuthAction = 'expired' | 'required' | null;
function clearStoredAuth() {
    localStorage.removeItem('token');
    window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
}
function handleAuthError(code: number): AuthAction {
    const tokenExpired = code === 1004 || code === 1005;
    const sessionRejected = tokenExpired || code === 401;
    if (sessionRejected) {
        clearStoredAuth();
    }
    if (isCurrentRoutePublic()) {
        isRedirectingToLogin = false;
        return null;
    }
    if (isRedirectingToLogin) {
        frontendLogger.capture('log', '[Request] 已在跳转登录页，跳过');
        return null;
    }
    if (router.currentRoute.value.path === '/login') {
        frontendLogger.capture('log', '[Request] 已在登录页，跳过');
        return null;
    }
    isRedirectingToLogin = true;
    router.push('/login').catch(() => {
    });
    return tokenExpired ? 'expired' : 'required';
}
function showAuthFeedback(action: AuthAction) {
    if (action === 'expired') {
        ElMessage.warning('登录状态已失效，请重新登录');
    }
    else if (action === 'required') {
        ElMessage.warning('请先登录');
    }
}
service.interceptors.response.use((response: AxiosResponse<ApiResponse>): any => {
    const requestId = response.config.metadata?.requestId;
    if (requestId) {
        abortControllers.delete(requestId);
    }
    trackRequestEnd(response.config.url || 'unknown', false, false);
    if (response.config.responseType === 'blob') {
        return response;
    }
    const res = response.data;
    if (res.code === 200) {
        return res;
    }
    if (res.code === 1004 || res.code === 1005 || res.code === 401) {
        showAuthFeedback(handleAuthError(res.code));
        const authError = new Error('AUTH_ERROR') as any;
        authError.isAuthError = true;
        authError.code = res.code;
        return Promise.reject(authError);
    }
    if (res.code === 1002) {
        const userNotExistError = new Error('USER_NOT_EXIST') as any;
        userNotExistError.isUserNotExist = true;
        return Promise.reject(userNotExistError);
    }
    const businessError = new Error(res.message || 'Request failed') as Error & {
        code?: number;
        data?: unknown;
    };
    businessError.code = res.code;
    businessError.data = res.data;
    return Promise.reject(businessError);
}, (error: AxiosError<ApiResponse>) => {
    const requestId = error.config?.metadata?.requestId;
    if (requestId) {
        abortControllers.delete(requestId);
    }
    const isCancelled = error.name === 'CanceledError' || error.code === 'ERR_CANCELED';
    const isFailed = !isCancelled && (error.code === 'ECONNABORTED' || !!error.response);
    trackRequestEnd(error.config?.url || 'unknown', isCancelled, isFailed);
    if (isCancelled) {
        return Promise.reject({ canceled: true, message: '请求已取消' });
    }
    if (error.response?.status === 401) {
        const responseCode = Number(error.response.data?.code || error.response.status);
        showAuthFeedback(handleAuthError(responseCode));
        const authError = new Error('AUTH_ERROR') as any;
        authError.isAuthError = true;
        authError.code = responseCode;
        return Promise.reject(authError);
    }
    frontendLogger.capture('error', `[Request] 失败 ${error.config?.url}, 错误:`, error.message);
    return Promise.reject(error);
});
export function request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return service(config) as any;
}
export default service;
