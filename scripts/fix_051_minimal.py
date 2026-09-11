from pathlib import Path

path = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Fix compact Kotlin return declarations where the source contains `>=`.
for type_name in ("List<VlessProfile>", "List<SubscriptionInfo>", "Result<SubscriptionPayload>"):
    s = s.replace(type_name + ">=", type_name + " =")

# Exact declarations present in the minified source.
s = s.replace("fun profiles(context:Context):List<VlessProfile>=", "fun profiles(context:Context):List<VlessProfile> =")
s = s.replace("private fun decode(arr:JSONArray):List<SubscriptionInfo>=", "private fun decode(arr:JSONArray):List<SubscriptionInfo> =")
s = s.replace("private suspend inline fun<T>Result<T>.onSuccessSuspend(crossinline action:suspend(T)->Unit):Result<T>{", "private suspend inline fun <T> Result<T>.onSuccessSuspend(crossinline action: suspend (T) -> Unit): Result<T> {")
s = s.replace("private suspend fun importSubscription(activity:Context,input:String):Result<SubscriptionPayload>=", "private suspend fun importSubscription(activity:Context,input:String):Result<SubscriptionPayload> =")

path.write_text(s, encoding="utf-8")
