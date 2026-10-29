# Временная разведка: как устроены страницы Блока на Викитеке.
import json, urllib.parse, urllib.request, re
API = "https://ru.wikisource.org/w/api.php"
UA = "TsvetaevaDaily/1.3 (https://github.com/Ezus44/TsvetaevaDaily; probe)"
def q(**p):
    p.update(format="json", formatversion="2")
    return json.load(urllib.request.urlopen(urllib.request.Request(API, data=urllib.parse.urlencode(p).encode(), headers={"User-Agent": UA})))
def raw(t, n):
    j = q(action="query", prop="revisions", rvprop="content", rvslots="main", redirects="1", titles=t)
    p = j["query"]["pages"][0]
    print(f"\n######## RAW {p['title']}" + (" [MISSING]" if p.get("missing") else ""))
    if not p.get("missing"):
        w = p["revisions"][0]["slots"]["main"]["content"]
        print("\n".join(w.splitlines()[:n]))
raw("Автор:Александр Александрович Блок", 160)
j = q(action="query", prop="categories", cllimit="max", titles="Автор:Александр Александрович Блок")
print("\n######## CATS", [c["title"] for c in j["query"]["pages"][0].get("categories", []) if "Статьи" not in c["title"]])
j = q(action="query", list="search", srsearch='intitle:"Блок"', srnamespace="0", srlimit="80", srprop="")
print("\n######## SEARCH total", j["query"]["searchinfo"]["totalhits"]); print("\n".join(s["title"] for s in j["query"]["search"]))
for cat in ["Категория:Поэзия Александра Александровича Блока", "Категория:Александр Александрович Блок"]:
    j = q(action="query", list="categorymembers", cmtitle=cat, cmlimit="60")
    print(f"\n######## MEMBERS {cat}:", [m["title"] for m in j.get("query", {}).get("categorymembers", [])])
