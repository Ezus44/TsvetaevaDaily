import json, sys, urllib.parse, urllib.request, re
API = "https://ru.wikisource.org/w/api.php"
UA = "TsvetaevaDaily/1.2 (https://github.com/Ezus44/TsvetaevaDaily; probe)"
titles = [
 "Стихи о Москве (Цветаева)/1", "Отцам (Цветаева)/1", "Ручьи (Цветаева)/2",
 "Кем быть? (Маяковский)", "Разговор с товарищем Лениным (Маяковский)", "Урожайный марш (Маяковский)",
 "Телега жизни (Пушкин)", "Разлука (Пушкин)", "Пока в нас сердце замирает (Пушкин)", "Желал я душу освежить (Пушкин)",
]
data = urllib.parse.urlencode(dict(action="query", prop="revisions", rvprop="content", rvslots="main", redirects="1",
    titles="|".join(titles), format="json", formatversion="2")).encode()
j = json.load(urllib.request.urlopen(urllib.request.Request(API, data=data, headers={"User-Agent": UA})))
for p in j["query"]["pages"]:
    w = p.get("revisions", [{}])[0].get("slots", {}).get("main", {}).get("content", "MISSING")
    w = re.sub(r"\{\{\s*Отексте[\s\S]*?\n\}\}", "{{Отексте…}}", w, count=1)
    print("\n######## " + p["title"]); print("\n".join(w.splitlines()[:30]))
for t, a, b in [("150 000 000 (Маяковский)", 0, 400), ("Феникс (Цветаева)/Картина третья", 0, 400)]:
    data = urllib.parse.urlencode(dict(action="query", prop="revisions", rvprop="content", rvslots="main", titles=t, format="json", formatversion="2")).encode()
    w = json.load(urllib.request.urlopen(urllib.request.Request(API, data=data, headers={"User-Agent": UA})))["query"]["pages"][0]["revisions"][0]["slots"]["main"]["content"]
    print("\n######## STRUCTURE " + t)
    for i, l in enumerate(w.splitlines()):
        if re.search(r"<poem|</poem|\{\{\s*(poem|f1|poemx|v|f)\b|^=|<ref|\{\{примечания|^'''|\{\{poem-off", l, re.I): print(i, l[:140])
