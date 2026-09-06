import { request } from '@/utils/request';
import { createUploadProgressHandler, reportUploadProgress, type UploadProgressHandler } from '@/utils/uploadProgress';
export type { UploadProgressHandler } from '@/utils/uploadProgress';
type WrappedResponse<T> = {
    data: T;
};
function unwrap<T>(promise: Promise<WrappedResponse<T>>): Promise<T> {
    return promise.then(response => response.data);
}
export type FileType = 'audio' | 'video' | 'lyric' | 'image' | 'other';
export type UploadType = 1 | 2 | 3;
export interface UploadQualityInfo {
    qualityLevel: number;
    qualityName: string;
    fileSize: number;
    duration: number;
    bitrate: number;
    sampleRate: number;
    format: string;
}
export interface UploadedFile {
    originalName: string;
    url: string;
    type: FileType;
    size: number;
    qualityInfo?: UploadQualityInfo;
}
export interface BatchUploadResult {
    success: boolean;
    totalFiles: number;
    successCount: number;
    files: UploadedFile[];
    errors: string[];
    uploadTime: string;
    summary: {
        typeCount: Record<FileType, number>;
        totalSize: number;
        totalFiles: number;
    };
}
export interface ZipExtractResult {
    success: boolean;
    totalFiles: number;
    files: UploadedFile[];
    categorized: Record<FileType, UploadedFile[]>;
    summary: {
        typeCount: Record<string, number>;
        totalSize: number;
        totalFiles: number;
    };
    uploadTime: string;
}
export function requireSingleUploadedFile(result: BatchUploadResult, label: string): UploadedFile {
    const uploaded = result.files?.[0];
    if (!result.success || result.totalFiles !== 1 || result.successCount !== 1 || !uploaded) {
        throw new Error(result.errors?.join('；') || `${label}上传失败`);
    }
    return uploaded;
}
export interface AudioQualityInfo {
    url: string;
    qualityLevel?: number;
    qualityName?: string;
    fileSize?: number;
    duration?: number;
    bitrate?: number;
    sampleRate?: number;
    format?: string;
    error?: string;
}
export interface VideoDetectionInfo {
    url: string;
    status: 'success' | 'error';
    error?: string;
    [key: string]: unknown;
}
export function uploadMultipleFiles(files: File[], onProgress?: UploadProgressHandler): Promise<BatchUploadResult> {
    const formData = new FormData();
    files.forEach(file => {
        formData.append('files', file);
    });
    const onUploadProgress = createUploadProgressHandler(onProgress);
    return unwrap(request<BatchUploadResult>({
        url: '/upload/multi-file/batch',
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        },
        ...(onUploadProgress ? { onUploadProgress } : {})
    })).then(result => {
        reportUploadProgress(onProgress, 100);
        return result;
    });
}
export function uploadAndExtractZip(zipFile: File, onProgress?: UploadProgressHandler): Promise<ZipExtractResult> {
    const formData = new FormData();
    formData.append('file', zipFile);
    const onUploadProgress = createUploadProgressHandler(onProgress);
    return unwrap(request<ZipExtractResult>({
        url: '/upload/multi-file/extract-zip',
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        },
        ...(onUploadProgress ? { onUploadProgress } : {})
    })).then(result => {
        reportUploadProgress(onProgress, 100);
        return result;
    });
}
export function identifyFileType(fileName: string): Promise<FileType> {
    return unwrap(request<FileType>({
        url: '/upload/multi-file/identify-type',
        method: 'GET',
        params: { fileName }
    }));
}
export function batchDetectAudioQuality(audioUrls: string[]): Promise<AudioQualityInfo[]> {
    return unwrap(request<AudioQualityInfo[]>({
        url: '/upload/multi-file/detect-audio-batch',
        method: 'POST',
        data: { audioUrls }
    }));
}
export function batchDetectVideoInfo(videoUrls: string[]): Promise<VideoDetectionInfo[]> {
    return unwrap(request<VideoDetectionInfo[]>({
        url: '/upload/multi-file/detect-video-batch',
        method: 'POST',
        data: { videoUrls }
    }));
}
export function getSupportedFormats(): Promise<Record<string, string[]>> {
    return unwrap(request<Record<string, string[]>>({
        url: '/upload/multi-file/supported-formats',
        method: 'GET'
    }));
}
export function getUploadLimits(): Promise<{
    maxFiles: number;
    maxTotalSize: string;
    maxSingleFileSize: string;
    maxZipSize: string;
}> {
    return unwrap(request<{
        maxFiles: number;
        maxTotalSize: string;
        maxSingleFileSize: string;
        maxZipSize: string;
    }>({
        url: '/upload/multi-file/limits',
        method: 'GET'
    }));
}
export const multiFileUploadApi = {
    uploadMultipleFiles,
    uploadAndExtractZip,
    identifyFileType,
    batchDetectAudioQuality,
    batchDetectVideoInfo,
    getSupportedFormats,
    getUploadLimits
};
export default multiFileUploadApi;
