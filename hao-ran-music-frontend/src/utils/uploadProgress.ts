import type { AxiosProgressEvent } from 'axios';
export type UploadProgressHandler = (percentage: number) => void;
export function createUploadProgressHandler(onProgress?: UploadProgressHandler) {
    if (typeof onProgress !== 'function')
        return undefined;
    return (event: AxiosProgressEvent) => {
        if (!event.total || event.total <= 0)
            return;
        reportUploadProgress(onProgress, Math.min(100, Math.max(0, Math.round((event.loaded / event.total) * 100))));
    };
}
export function reportUploadProgress(onProgress: unknown, percentage: number) {
    if (typeof onProgress === 'function')
        onProgress(percentage);
}
