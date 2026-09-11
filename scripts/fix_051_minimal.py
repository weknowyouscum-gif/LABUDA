from pathlib import Path

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Normalize compact Kotlin declarations where `Type>=expression` was tokenized as >=.
s = s.replace(">=", " >= ")
s = s.replace("> >=", "> =")

# Keep the suspend helper valid Kotlin.
s = s.replace(
    "private suspend inline fun<T>Result<T>.onSuccessSuspend(crossinline action:suspend(T)->Unit):Result<T>",
    "private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T>"
)

path.write_text(s, encoding="utf-8")
