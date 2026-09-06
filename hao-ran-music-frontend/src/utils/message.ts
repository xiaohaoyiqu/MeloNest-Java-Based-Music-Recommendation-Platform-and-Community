let errorMessageTimer: ReturnType<typeof setTimeout> | null = null;
let lastErrorMessage = '';
export function showError(message: string): void {
    if (lastErrorMessage === message) {
        return;
    }
    lastErrorMessage = message;
    if (errorMessageTimer) {
        clearTimeout(errorMessageTimer);
    }
    errorMessageTimer = setTimeout(() => {
        import('element-plus').then(({ ElMessage }) => {
            ElMessage({ type: 'error', message, duration: 5000, grouping: true, showClose: true });
            setTimeout(() => {
                lastErrorMessage = '';
            }, 3000);
        });
    }, 100);
}
export function showWarning(message: string): void {
    if (errorMessageTimer) {
        clearTimeout(errorMessageTimer);
    }
    import('element-plus').then(({ ElMessage }) => {
        ElMessage({ type: 'warning', message, duration: 4500, grouping: true, showClose: true });
    });
}
export function showSuccess(message: string): void {
    if (errorMessageTimer) {
        clearTimeout(errorMessageTimer);
    }
    import('element-plus').then(({ ElMessage }) => {
        ElMessage({ type: 'success', message, duration: 3500, grouping: true });
    });
}
