from pathlib import Path

p = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
s = p.read_text()

# Never block the Compose/main coroutine with runBlocking during subscription refresh.
s = s.replace(
    'profiles=runBlocking(Dispatchers.IO){p.profiles.map{it.copy(name=cleanServerName(it.name,title),latencyMs=ping(it.host,it.port))}}',
    'profiles=withContext(Dispatchers.IO){p.profiles.map{it.copy(name=cleanServerName(it.name,title),latencyMs=ping(it.host,it.port))}}'
)
s = s.replace('import kotlinx.coroutines.runBlocking\n', '')
p.write_text(s)

print("LABUDA: removed main-thread blocking from subscription refresh")
