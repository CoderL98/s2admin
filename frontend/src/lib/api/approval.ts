import { USE_MOCK } from '$lib/config';
import * as mock from './mock/approval';
import * as real from './real/approval';

export const getMyApprovals = USE_MOCK ? mock.getMyApprovals : real.getMyApprovals;
export const getPendingApprovals = USE_MOCK ? mock.getPendingApprovals : real.getPendingApprovals;
export const getApproval = USE_MOCK ? mock.getApproval : real.getApproval;
export const submitApproval = USE_MOCK ? mock.submitApproval : real.submitApproval;
export const approveApproval = USE_MOCK ? mock.approveApproval : real.approveApproval;
export const rejectApproval = USE_MOCK ? mock.rejectApproval : real.rejectApproval;
export const cancelApproval = USE_MOCK ? mock.cancelApproval : real.cancelApproval;
