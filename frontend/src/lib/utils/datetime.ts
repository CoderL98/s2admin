/** 将日期输入框的 yyyy-MM-dd 转成后端 LogQuery 使用的时间范围 */
export function dayRange(beginDate: string, endDate: string): { beginTime?: string; endTime?: string } {
	return {
		beginTime: beginDate ? `${beginDate} 00:00:00` : undefined,
		endTime: endDate ? `${endDate} 23:59:59` : undefined
	};
}
