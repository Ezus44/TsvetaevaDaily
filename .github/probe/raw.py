# Временная разведка: Лермонтов на Викитеке.
import json, urllib.parse, urllib.request, re
API = "https://ru.wikisource.org/w/api.php"
UA = "StikhiDnya/1.4 (https://github.com/Ezus44/TsvetaevaDaily; probe)"
def q(**p):
    p.update(format="json", formatversion="2")
    return json.load(urllib.request.urlopen(urllib.request.Request(API, data=urllib.parse.urlencode(p).encode(), headers={"User-Agent": UA})))
def raw(t, n):
    p = q(action="query", prop="revisions", rvprop="content", rvslots="main", redirects="1", titles=t)["query"]["pages"][0]
    print("\n######## RAW", p["title"], "[MISSING]" if p.get("missing") else "")
    if not p.get("missing"): print("\n".join(p["revisions"][0]["slots"]["main"]["content"].splitlines()[:n]))
raw("Выхожу один я на дорогу (Лермонтов)", 40)
raw("Автор:Михаил Юрьевич Лермонтов", 70)
j = q(action="query", list="search", srsearch='intitle:"Выхожу один я на дорогу"', srnamespace="0", srlimit="20", srprop="")
print("\n######## SEARCH выхожу:", [s["title"] for s in j["query"]["search"]])
j = q(action="query", list="search", srsearch='intitle:"Стихотворения Лермонтова"', srnamespace="0", srlimit="20", srprop="")
print("\n######## SEARCH index:", [s["title"] for s in j["query"]["search"]])
