import { ElMessage, ElMessageBox, type MessageParams } from 'element-plus';
import { isVNode } from 'vue';
let installed = false;
export function installFeedbackGuards() {
    if (installed)
        return;
    installed = true;
    const messageTypes = ['success', 'warning', 'info', 'error'] as const;
    messageTypes.forEach(type => {
        const original = ElMessage[type];
        ElMessage[type] = ((options: MessageParams) => {
            if (typeof options === 'string') {
                return original({ message: options, grouping: true });
            }
            if (typeof options === 'function' || isVNode(options)) {
                return original(options);
            }
            const normalized = options as Exclude<MessageParams, string>;
            const grouping = (normalized as {
                grouping?: boolean;
            }).grouping ?? true;
            return original({ ...normalized, grouping });
        }) as typeof original;
    });
    let activeMessageBox = false;
    const boxMethods = ['confirm', 'prompt'] as const;
    boxMethods.forEach(method => {
        const original = ElMessageBox[method];
        ElMessageBox[method] = ((...args: Parameters<typeof original>) => {
            if (activeMessageBox) {
                return Promise.reject('cancel');
            }
            activeMessageBox = true;
            return original(...args).finally(() => {
                activeMessageBox = false;
            });
        }) as typeof original;
    });
}
