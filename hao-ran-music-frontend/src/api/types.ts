export interface ApiResponse<T = any> {
    code: number;
    message: string;
    data: T;
    timestamp?: number;
}
export interface PageParams {
    page: number;
    size: number;
}
export interface PageResult<T> {
    records: T[];
    total: number;
    page: number;
    size: number;
}
export interface PageResponse<T> {
    list: T[];
    total: number;
    current: number;
    pageSize: number;
}
