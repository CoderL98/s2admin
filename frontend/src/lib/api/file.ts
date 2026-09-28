import { USE_MOCK } from '$lib/config';
import * as mock from './mock/file';
import * as real from './real/file';

export const getFileList = USE_MOCK ? mock.getFileList : real.getFileList;
export const uploadFile = USE_MOCK ? mock.uploadFile : real.uploadFile;
export const removeFile = USE_MOCK ? mock.removeFile : real.removeFile;
export const downloadStoredFile = USE_MOCK ? mock.downloadStoredFile : real.downloadStoredFile;
