from pathlib import Path
import re

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Repair compact generic return declarations produced by the earlier minified source.
s = re.sub(r"\):([A-Za-z0-9_]+(?:<[^\n>]+>)?)>=", r"): \1 =", s)

# Keep the suspend callback declaration unambiguous to Kotlin's parser.
s = s.replace("private suspend inline fun<T>Result<T>.onSuccessSuspend(crossinline action:suspend(T)->Unit):Result<T>", "private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T>")

path.write_text(s, encoding="utf-8")
