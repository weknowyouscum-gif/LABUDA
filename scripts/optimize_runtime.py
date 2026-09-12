from pathlib import Path

p = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = p.read_text()

# Never block the Compose/main coroutine with runBlocking during subscription refresh.
# The refresh itself runs in a suspend scope, so rebuild the subscription list with a
# regular loop and perform the blocking ping work inside withContext(IO). Calling
# withContext from the non-suspend List.map lambda was the source of the recurring
# MainActivity compile failure.
old = '''subs=subs.map{s->importSubscription(activity,s.url).getOrNull()?.let{p->val title=normalizeSubscriptionTitle(p.title).ifBlank{s.title};s.copy(title=title,comment=p.comment.ifBlank{s.comment},totalBytes=p.totalBytes?:s.totalBytes,usedBytes=p.usedBytes,expireAt=p.expireAt?:s.expireAt,profiles=runBlocking(Dispatchers.IO){p.profiles.map{it.copy(name=cleanServerName(it.name,title),latencyMs=ping(it.host,it.port))}})}?:s}'''
new = '''subs=buildList{for(s in subs){val refreshed=importSubscription(activity,s.url).getOrNull();if(refreshed==null){add(s)}else{val title="Подписка";val pinged=withContext(Dispatchers.IO){refreshed.profiles.map{it.copy(name=cleanServerName(it.name,title),latencyMs=ping(it.host,it.port))}};add(s.copy(title=title,comment=refreshed.comment.ifBlank{s.comment},totalBytes=refreshed.totalBytes?:s.totalBytes,usedBytes=refreshed.usedBytes,expireAt=refreshed.expireAt?:s.expireAt,profiles=pinged))}}}'''
if old not in s:
    raise SystemExit('subscription refresh pattern not found')
s = s.replace(old, new, 1)
s = s.replace('import kotlinx.coroutines.runBlocking\n', '')
p.write_text(s)

print("LABUDA: removed main-thread blocking and invalid suspend call from subscription refresh")
