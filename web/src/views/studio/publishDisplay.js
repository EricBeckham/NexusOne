export function showText(value) {
  if (value == null || value === "") {
    return "暂无";
  }
  return String(value);
}

export function logSummary(log) {
  if (!log || typeof log !== "object") {
    return "暂无";
  }
  const outcome = showText(log.outcome);
  const message = log.message == null || log.message === "" ? "" : ` ${log.message}`;
  return `${outcome}${message}`;
}
