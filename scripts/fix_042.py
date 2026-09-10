from pathlib import Path

main = Path("app/src/main/java/com/labuda/app/MainActivity.kt")
text = main.read_text(encoding="utf-8")

# Do not add the same subscription twice. Check before downloading it.
needle = '''    fun subscription(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n        .getString(KEY_SUB_URL, "").orEmpty()\n'''
insert = needle + '''\n    fun hasSubscription(context: Context, value: String): Boolean {\n        val candidate = value.trim()\n        if (candidate.isBlank()) return false\n        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n        val urls = prefs.getStringSet("subscription_urls", emptySet()).orEmpty()\n        return urls.any { it.trim() == candidate } || prefs.getString(KEY_SUB_URL, "").orEmpty().trim() == candidate\n    }\n'''
if "fun hasSubscription(context: Context" not in text:
    if needle not in text:
        raise SystemExit("ProfileStore.subscription block not found")
    text = text.replace(needle, insert, 1)

old_import_start = '''                    onImport = {\n                        busy = true\n                        message = ""\n                        scope.launch {\n                            val result = importSubscription(activity, subscriptionUrl)'''
new_import_start = '''                    onImport = {\n                        message = ""\n                        if (ProfileStore.hasSubscription(activity, subscriptionUrl)) {\n                            message = "Подписка уже добавлена"\n                        } else {\n                            busy = true\n                            scope.launch {\n                                val result = importSubscription(activity, subscriptionUrl)'''
if old_import_start in text:
    text = text.replace(old_import_start, new_import_start, 1)
    old_end = '''                            }.onFailure { message = it.message ?: "Не удалось импортировать подписку" }\n                        }\n                    }'''
    new_end = '''                                }.onFailure { message = it.message ?: "Не удалось импортировать подписку" }\n                            }\n                        }\n                    }'''
    if old_end not in text:
        raise SystemExit("Import handler end block not found")
    text = text.replace(old_end, new_end, 1)
else:
    raise SystemExit("Import handler start block not found")

# Visible UI wording: VPN -> LBD. Internal technical identifiers remain unchanged.
for old, new in [
    ("Ошибка VPN", "Ошибка LBD"),
    ("Комментарий из VPN", "Комментарий из LBD"),
    ("Статистика VPN", "Статистика"),
]:
    text = text.replace(old, new)

main.write_text(text, encoding="utf-8")

# User-visible VPN wording in the foreground notification/errors. Android VpnService
# class names, permissions, preference keys and technical logs are intentionally untouched.
service = Path("app/src/main/java/com/labuda/app/LabudaVpnService.kt")
s = service.read_text(encoding="utf-8")
for old, new in [
    ("Запуск VPN…", "Запуск LBD…"),
    ("foreground VPN", "foreground LBD"),
    ("LABUDA VPN", "LABUDA LBD"),
    ("Ошибка создания Android VPN", "Ошибка создания Android LBD"),
    ("VPN подключена", "LBD подключена"),
]:
    s = s.replace(old, new)
service.write_text(s, encoding="utf-8")

print("LABUDA 0.42 fixes applied")
