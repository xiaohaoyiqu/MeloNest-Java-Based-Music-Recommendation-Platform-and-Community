import { getCurrentInstance, onUnmounted, ref } from 'vue';
import { logger as frontendLogger } from '@/utils/logger';
export interface PollingOptions {
    interval?: number;
    immediate?: boolean;
    condition?: () => boolean;
}
export function useDataPolling(fetchFn: () => Promise<void>, options: PollingOptions = {}) {
    const { interval = 60000, immediate = true, condition = () => true } = options;
    const timerRef = ref<number | null>(null);
    const isPolling = ref(false);
    const startPolling = () => {
        if (timerRef.value)
            return;
        isPolling.value = true;
        const poll = async () => {
            try {
                await fetchFn();
                if (condition()) {
                    timerRef.value = window.setTimeout(poll, interval);
                }
                else {
                    stopPolling();
                }
            }
            catch (error) {
                frontendLogger.capture('error', '[useDataPolling] 轮询请求失败:', error);
                timerRef.value = window.setTimeout(poll, interval * 2);
            }
        };
        if (immediate) {
            void poll();
        }
        else {
            timerRef.value = window.setTimeout(poll, interval);
        }
    };
    const stopPolling = () => {
        if (timerRef.value) {
            clearTimeout(timerRef.value);
            timerRef.value = null;
        }
        isPolling.value = false;
    };
    if (getCurrentInstance()) {
        onUnmounted(() => {
            stopPolling();
        });
    }
    return {
        isPolling,
        startPolling,
        stopPolling
    };
}
