const DB_NAME = 'HaoRanMusic';
const DB_VERSION = 1;
const STORE_NAME = 'localFiles';
export interface StoredFile {
    name: string;
    blob: Blob;
    addedAt: number;
}
class LocalFileDB {
    private db: IDBDatabase | null = null;
    async init(): Promise<void> {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(DB_NAME, DB_VERSION);
            request.onerror = () => reject(request.error);
            request.onsuccess = () => {
                this.db = request.result;
                resolve();
            };
            request.onupgradeneeded = (event) => {
                const db = (event.target as IDBOpenDBRequest).result;
                if (!db.objectStoreNames.contains(STORE_NAME)) {
                    db.createObjectStore(STORE_NAME, { keyPath: 'name' });
                }
            };
        });
    }
    async saveFile(file: File): Promise<void> {
        if (!this.db)
            await this.init();
        return new Promise((resolve, reject) => {
            const transaction = this.db!.transaction([STORE_NAME], 'readwrite');
            const store = transaction.objectStore(STORE_NAME);
            const storedFile: StoredFile = {
                name: file.name,
                blob: file,
                addedAt: Date.now()
            };
            const request = store.put(storedFile);
            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
    }
    async getFile(name: string): Promise<File | null> {
        if (!this.db)
            await this.init();
        return new Promise((resolve, reject) => {
            const transaction = this.db!.transaction([STORE_NAME], 'readonly');
            const store = transaction.objectStore(STORE_NAME);
            const request = store.get(name);
            request.onsuccess = () => {
                const storedFile = request.result as StoredFile | undefined;
                if (storedFile) {
                    const file = new File([storedFile.blob], storedFile.name, {
                        type: storedFile.blob.type,
                        lastModified: storedFile.addedAt
                    });
                    resolve(file);
                }
                else {
                    resolve(null);
                }
            };
            request.onerror = () => reject(request.error);
        });
    }
    async deleteFile(name: string): Promise<void> {
        if (!this.db)
            await this.init();
        return new Promise((resolve, reject) => {
            const transaction = this.db!.transaction([STORE_NAME], 'readwrite');
            const store = transaction.objectStore(STORE_NAME);
            const request = store.delete(name);
            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
    }
    async clearOldFiles(maxAge: number = 7 * 24 * 60 * 60 * 1000): Promise<void> {
        if (!this.db)
            await this.init();
        return new Promise((resolve, reject) => {
            const transaction = this.db!.transaction([STORE_NAME], 'readwrite');
            const store = transaction.objectStore(STORE_NAME);
            const request = store.openCursor();
            request.onsuccess = (event) => {
                const cursor = (event.target as IDBRequest).result;
                if (cursor) {
                    const storedFile = cursor.value as StoredFile;
                    if (Date.now() - storedFile.addedAt > maxAge) {
                        cursor.delete();
                    }
                    cursor.continue();
                }
                else {
                    resolve();
                }
            };
            request.onerror = () => reject(request.error);
        });
    }
}
export const localFileDB = new LocalFileDB();
