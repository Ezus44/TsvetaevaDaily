# Временная разведка: страницы изданий Лермонтова.
import json, urllib.parse, urllib.request, os
API = "https://ru.wikisource.org/w/api.php"
UA = "StikhiDnya/1.4 (https://github.com/Ezus44/TsvetaevaDaily; probe)"
def q(**p):
    p.update(format="json", formatversion="2")
    return json.load(urllib.request.urlopen(urllib.request.Request(API, data=urllib.parse.urlencode(p).encode(), headers={"User-Agent": UA})))
for t in ["Выхожу один я на дорогу (Лермонтов)/ПСС 1989 (СО)", "Выхожу один я на дорогу (Лермонтов)/ОЗ 1843 (ВТ)"]:
    p = q(action="query", prop="revisions", rvprop="content", rvslots="main", redirects="1", titles=t)["query"]["pages"][0]
    print("\n######## RAW", p["title"], "[MISSING]" if p.get("missing") else "")
    if not p.get("missing"): print(p["revisions"][0]["slots"]["main"]["content"][:3000])
    h = q(action="parse", page=t, prop="text", redirects="1").get("parse", {}).get("text", "")
    print("\n######## HTML", t, len(h)); print(h[:4000])
