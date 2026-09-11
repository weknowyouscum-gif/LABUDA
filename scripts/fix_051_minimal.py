from pathlib import Path

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Fix compact generic return declarations such as List<Foo>=expr.
for type_name in ("List<VlessProfile>", "List<SubscriptionInfo>", "Result<SubscriptionPayload>"):
    s = s.replace(type_name + ">=", type_name + " =")

# Keep the suspend helper valid Kotlin.
s = s.replace(
    "private suspend inline fun<T>Result<T>.onSuccessSuspend(crossinline action:suspend(T)->Unit):Result<T>",
    "private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T>"
)

path.write_text(s, encoding="utf-8")
