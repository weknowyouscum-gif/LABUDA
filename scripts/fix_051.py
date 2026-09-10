from pathlib import Path

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# The 0.47 transformation introduced a suspend callback helper. Normalize all
# generated variants so the callback is consistently suspend and every ping
# calculation inside it is coroutine-safe.
s = s.replace(".onSuccess { p ->", ".onSuccessSuspend { p ->")
s = s.replace(".onSuccessSuspend{p->", ".onSuccessSuspend { p ->")
s = s.replace(".onSuccessSuspend {p->", ".onSuccessSuspend { p ->")
s = s.replace("withContext(Dispatchers.IO){p.profiles.map{it.copy(latencyMs=ping(it.host,it.port))}}", "runBlocking(Dispatchers.IO) { p.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) } }")
s = s.replace("withContext(Dispatchers.IO){p.profiles.map{it.copy(latencyMs=ping(it.host,it.port))}}", "runBlocking(Dispatchers.IO) { p.profiles.map { it.copy(latencyMs = ping(it.host, it.port)) } }")

# Ensure the helper has one valid Kotlin declaration.
start = s.find("private suspend inline fun <T> Result<T>.onSuccessSuspend")
if start >= 0:
    end = s.find("@Composable private fun ImportScreen", start)
    if end >= 0:
        helper = "private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T> { if (isSuccess) action(getOrThrow()); return this }\n"
        s = s[:start] + helper + s[end:]

if "import kotlinx.coroutines.runBlocking" not in s:
    s = s.replace("import kotlinx.coroutines.launch\n", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.runBlocking\n")

path.write_text(s, encoding="utf-8")
