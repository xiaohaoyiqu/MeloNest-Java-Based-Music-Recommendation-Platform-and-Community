type LogValue = string | number | boolean | null | undefined;
type LogContext = Record<string, LogValue>;
type ConsoleLevel = 'log' | 'debug' | 'info' | 'warn' | 'error';
type LogLevel = Exclude<ConsoleLevel, 'log'>;
const isEnabled = import.meta.env.DEV && import.meta.env.VITE_FRONTEND_DEBUG_LOGS === 'true';
const EVENT_PATTERN = /^[a-z][a-z0-9_]{2,63}$/;
function normalizeEvent(event: string) {
    return EVENT_PATTERN.test(event) ? event : 'invalid_frontend_event';
}
function compactContext(context: LogContext = {}) {
    return Object.fromEntries(Object.entries(context).filter(([, value]) => value !== undefined));
}
function errorType(error: unknown) {
    if (error instanceof Error && error.name)
        return error.name;
    return typeof error === 'object' && error !== null ? 'UnknownError' : typeof error;
}
function write(level: LogLevel, event: string, context?: LogContext) {
    if (!isEnabled)
        return;
    console[level](`event=${normalizeEvent(event)}`, compactContext(context));
}
function getSafeMessage(value: unknown) {
    if (typeof value !== 'string')
        return undefined;
    return value.replace(/\s+/g, ' ').trim().slice(0, 160) || undefined;
}
function getFirstError(values: unknown[]) {
    return values.find(value => value instanceof Error);
}
export const logger = {
    debug(event: string, context?: LogContext) {
        write('debug', event, context);
    },
    info(event: string, context?: LogContext) {
        write('info', event, context);
    },
    warn(event: string, error?: unknown, context?: LogContext) {
        write('warn', event, { ...context, errorType: errorType(error) });
    },
    error(event: string, error?: unknown, context?: LogContext) {
        write('error', event, { ...context, errorType: errorType(error) });
    },
    capture(level: ConsoleLevel, ...values: unknown[]) {
        const message = getSafeMessage(values[0]);
        const error = getFirstError(values);
        write(level === 'log' ? 'info' : level, 'frontend_console', {
            message,
            errorType: error ? errorType(error) : undefined
        });
    }
};
export default logger;
