import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getBlacklistList, addToBlacklist, removeFromBlacklist } from '@/api/blacklist';
import { ElMessage } from 'element-plus';
import { logger as frontendLogger } from '@/utils/logger';
export const useBlacklistStore = defineStore('blacklist', () => {
    const blacklistedUserIds = ref<Array<string | number>>([]);
    const lastUpdateTime = ref(0);
    const CACHE_DURATION = 5 * 60 * 1000;
    async function loadBlacklist() {
        const now = Date.now();
        if (now - lastUpdateTime.value < CACHE_DURATION && blacklistedUserIds.value.length > 0) {
            frontendLogger.capture('log', '[BlacklistStore] 使用缓存的黑名单数据');
            return;
        }
        try {
            frontendLogger.capture('log', '[BlacklistStore] 正在加载黑名单...');
            const res = await getBlacklistList();
            if (res.data && Array.isArray(res.data)) {
                blacklistedUserIds.value = res.data.map((u: any) => u.id || u.blacklistedUserId || u.userId);
                lastUpdateTime.value = now;
                frontendLogger.capture('log', '[BlacklistStore] 黑名单加载完成，数量:', blacklistedUserIds.value.length);
            }
        }
        catch (error) {
            frontendLogger.capture('error', '[BlacklistStore] 加载黑名单失败:', error);
        }
    }
    function isBlacklisted(userId: string | number): boolean {
        return blacklistedUserIds.value.some(id => String(id) === String(userId));
    }
    function filterBlacklisted(userIds: Array<string | number>): Array<string | number> {
        return userIds.filter(id => isBlacklisted(id));
    }
    async function addBlacklist(userId: string | number, reason?: string) {
        try {
            await addToBlacklist(String(userId), reason);
            if (!blacklistedUserIds.value.some(id => String(id) === String(userId))) {
                blacklistedUserIds.value.push(userId);
            }
            ElMessage.success('已加入黑名单');
            frontendLogger.capture('log', '[BlacklistStore] 用户已加入黑名单:', userId);
            return true;
        }
        catch (error: any) {
            frontendLogger.capture('error', '[BlacklistStore] 添加黑名单失败:', error);
            ElMessage.error(error?.message || '添加失败');
            return false;
        }
    }
    async function removeBlacklist(userId: string | number) {
        try {
            await removeFromBlacklist(String(userId));
            const index = blacklistedUserIds.value.findIndex(id => String(id) === String(userId));
            if (index > -1) {
                blacklistedUserIds.value.splice(index, 1);
            }
            ElMessage.success('已移出黑名单');
            frontendLogger.capture('log', '[BlacklistStore] 用户已移出黑名单:', userId);
            return true;
        }
        catch (error: any) {
            frontendLogger.capture('error', '[BlacklistStore] 移除黑名单失败:', error);
            ElMessage.error(error?.message || '移除失败');
            return false;
        }
    }
    async function refresh() {
        lastUpdateTime.value = 0;
        await loadBlacklist();
    }
    function clear() {
        blacklistedUserIds.value = [];
        lastUpdateTime.value = 0;
    }
    function filterItems<T extends {
        userId?: string | number;
        id?: string | number;
    }>(items: T[]): T[] {
        return items.filter(item => {
            const userId = item.userId || item.id;
            return userId ? !isBlacklisted(userId) : true;
        });
    }
    return {
        blacklistedUserIds,
        lastUpdateTime,
        loadBlacklist,
        isBlacklisted,
        filterBlacklisted,
        addBlacklist,
        removeBlacklist,
        refresh,
        clear,
        filterItems
    };
});
