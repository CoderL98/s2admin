/**
 * 模拟网络延迟
 */
export function delay(ms = 300): Promise<void> {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 分页切片工具
 */
export function paginate<T>(rows: T[], pageNum: number, pageSize: number) {
	const total = rows.length;
	const start = (pageNum - 1) * pageSize;
	return {
		records: rows.slice(start, start + pageSize),
		total,
		size: pageSize,
		current: pageNum,
		pages: Math.max(1, Math.ceil(total / pageSize))
	};
}

/**
 * 在内存中为列表生成新 id
 */
let _idCounter = 1000;
export function nextId(): number {
	return ++_idCounter;
}
