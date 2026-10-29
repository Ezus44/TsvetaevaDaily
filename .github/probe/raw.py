# Временная разведка: разметка страниц Блока, где не нашлось стихов.
import json, urllib.parse, urllib.request, re
API = "https://ru.wikisource.org/w/api.php"
UA = "TsvetaevaDaily/1.3 (https://github.com/Ezus44/TsvetaevaDaily; probe)"
titles = ["Ночь, улица, фонарь, аптека (Блок)", "Ночь, улица, фонарь, аптека (Блок)/1923 (СО)",
          "Пустая улица. Один огонь в окне (Блок)/1923 (СО)", "Родина (Блок)", "Пляски смерти (Блок)", "Кармен (Блок)"]
data = urllib.parse.urlencode(dict(action="query", prop="revisions", rvprop="content", rvslots="main", redirects="1",
    titles="|".join(titles), format="json", formatversion="2")).encode()
j = json.load(urllib.request.urlopen(urllib.request.Request(API, data=data, headers={"User-Agent": UA})))
for p in j["query"]["pages"]:
    w = p.get("revisions", [{}])[0].get("slots", {}).get("main", {}).get("content", "MISSING")
    w = re.sub(r"\{\{\s*Отексте[\s\S]*?\n\}\}", "{{Отексте…}}", w, count=1)
    print("\n######## " + p["title"]); print("\n".join(w.splitlines()[:28]))
