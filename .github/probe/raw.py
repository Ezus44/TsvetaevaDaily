# Временная разведка: что есть у Пастернака на Викитеке.
import json, urllib.parse, urllib.request, re
API = "https://ru.wikisource.org/w/api.php"
UA = "StikhiDnya/1.4 (https://github.com/Ezus44/TsvetaevaDaily; probe)"
def q(**p):
    p.update(format="json", formatversion="2")
    return json.load(urllib.request.urlopen(urllib.request.Request(API, data=urllib.parse.urlencode(p).encode(), headers={"User-Agent": UA})))
for t in ["Автор:Борис Леонидович Пастернак"]:
    p = q(action="query", prop="revisions|categories", rvprop="content", rvslots="main", cllimit="max", redirects="1", titles=t)["query"]["pages"][0]
    print("######## RAW", p["title"], "[MISSING]" if p.get("missing") else "")
    if not p.get("missing"):
        print("\n".join(p["revisions"][0]["slots"]["main"]["content"].splitlines()[:90]))
        print("CATS:", [c["title"] for c in p.get("categories", []) if "Статьи" not in c["title"]])
j = q(action="query", list="search", srsearch='intitle:"Пастернак"', srnamespace="0", srlimit="100", srprop="")
print("######## SEARCH total", j["query"]["searchinfo"]["totalhits"]); print("\n".join(s["title"] for s in j["query"]["search"]))
for cat in ["Категория:Поэзия Бориса Леонидовича Пастернака", "Категория:Борис Леонидович Пастернак"]:
    j = q(action="query", list="categorymembers", cmtitle=cat, cmlimit="200")
    print("######## MEMBERS", cat, [m["title"] for m in j.get("query", {}).get("categorymembers", [])])
