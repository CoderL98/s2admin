import { USE_MOCK } from '$lib/config';
import * as mock from './mock/notice';
import * as real from './real/notice';

export const getNoticeList = USE_MOCK ? mock.getNoticeList : real.getNoticeList;
export const getPublishedNotices = USE_MOCK ? mock.getPublishedNotices : real.getPublishedNotices;
export const createNotice = USE_MOCK ? mock.createNotice : real.createNotice;
export const updateNotice = USE_MOCK ? mock.updateNotice : real.updateNotice;
export const publishNotice = USE_MOCK ? mock.publishNotice : real.publishNotice;
export const removeNotice = USE_MOCK ? mock.removeNotice : real.removeNotice;
