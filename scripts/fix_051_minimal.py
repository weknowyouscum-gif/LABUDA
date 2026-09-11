from pathlib import Path

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Fix Kotlin declarations that were compacted into the ambiguous `>=` token.
for old, new in [
    ("):List<VlessProfile>=", "): List<VlessProfile> ="),
    ("):List<SubscriptionInfo>=", "): List<SubscriptionInfo> ="),
    ("):Result<SubscriptionPayload>=", "): Result<SubscriptionPayload> ="),
    ("):List<SubscriptionInfo> =", "): List<SubscriptionInfo> ="),
]:
    s = s.replace(old, new)

# Keep the suspend helper valid Kotlin.
s = s.replace(
    "private suspend inline fun<T>Result<T>.onSuccessSuspend(crossinline action:suspend(T)->Unit):Result<T>",
    "private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T>"
)

path.write_text(s, encoding="utf-8")
