import { request } from '@/utils/request';
export interface QuickPhrase {
    id: string;
    userId: string;
    phrase: string;
    sortOrder: number;
    createTime: string;
    updateTime: string;
}
export function getQuickPhrases() {
    return request<QuickPhrase[]>({
        url: '/user/quick-phrase/list',
        method: 'GET'
    });
}
export function addQuickPhrase(phrase: string) {
    return request<QuickPhrase>({
        url: '/user/quick-phrase/add',
        method: 'POST',
        data: { phrase }
    });
}
export function updateQuickPhrase(phraseId: string, phrase: string) {
    return request<boolean>({
        url: `/user/quick-phrase/${phraseId}`,
        method: 'PUT',
        data: { phrase }
    });
}
export function deleteQuickPhrase(phraseId: string) {
    return request<boolean>({
        url: `/user/quick-phrase/${phraseId}`,
        method: 'DELETE'
    });
}
export function updateQuickPhraseSort(phraseIds: string[]) {
    return request<boolean>({
        url: '/user/quick-phrase/sort',
        method: 'POST',
        data: { phraseIds }
    });
}
export function batchSaveQuickPhrases(phrases: string[]) {
    return request<boolean>({
        url: '/user/quick-phrase/batch-save',
        method: 'POST',
        data: { phrases }
    });
}
export function clearQuickPhrases() {
    return request<number>({
        url: '/user/quick-phrase/clear',
        method: 'DELETE'
    });
}
export const quickPhraseApi = {
    getQuickPhrases,
    addQuickPhrase,
    updateQuickPhrase,
    deleteQuickPhrase,
    updateQuickPhraseSort,
    batchSaveQuickPhrases,
    clearQuickPhrases
};
