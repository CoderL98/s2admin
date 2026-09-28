import { USE_MOCK } from '$lib/config';
import * as mock from './mock/message';
import * as real from './real/message';

export const sendMessage = USE_MOCK ? mock.sendMessage : real.sendMessage;
export const getMyMessages = USE_MOCK ? mock.getMyMessages : real.getMyMessages;
export const getUnreadCount = USE_MOCK ? mock.getUnreadCount : real.getUnreadCount;
export const markMessageRead = USE_MOCK ? mock.markMessageRead : real.markMessageRead;
export const markAllMessagesRead = USE_MOCK ? mock.markAllMessagesRead : real.markAllMessagesRead;
export const deleteMessage = USE_MOCK ? mock.deleteMessage : real.deleteMessage;
