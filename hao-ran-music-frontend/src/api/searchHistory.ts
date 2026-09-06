import { request } from '@/utils/request';
export interface SearchHistoryItem {
    id: string;
    userId: string;
    keyword: string;
    searchType: number;
    resultCount: number;
    createTime: string;
}
export function getRecentSearch(limit: number = 10) {
    return request<string[]>({
        url: '/search/history',
        method: 'GET',
        params: { limit }
    });
}
export function saveSearchHistory(keyword: string) {
    return request({
        url: '/search/history',
        method: 'POST',
        params: { keyword }
    });
}
export function clearSearchHistory() {
    return request({
        url: '/search/history',
        method: 'DELETE'
    });
}
export function deleteSearchHistoryItem(keyword: string) {
    return request({
        url: '/search/history/item',
        method: 'DELETE',
        params: { keyword }
    });
}
export function getHotKeywords(limit: number = 10) {
    return request<string[]>({
        url: '/search/hot-keywords',
        method: 'GET',
        params: { limit }
    });
}
export const searchHistoryApi = {
    getRecentSearch,
    saveSearchHistory,
    clearSearchHistory,
    deleteSearchHistoryItem,
    getHotKeywords
};
