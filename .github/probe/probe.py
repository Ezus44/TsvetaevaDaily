# Временная диагностика: какая разметка у страниц со стихами на Викитеке.
import json, re, sys, time, urllib.parse, urllib.request, collections

API = "https://ru.wikisource.org/w/api.php"
UA = "TsvetaevaDaily/1.2 (https://github.com/Ezus44/TsvetaevaDaily; probe)"

def get(**p):
    p.update(format="json", formatversion="2")
    data = urllib.parse.urlencode(p).encode()
    for i in range(5):
        try:
            req = urllib.request.Request(API, data=data, headers={"User-Agent": UA})
            return json.load(urllib.request.urlopen(req, timeout=60))
        except Exception as e:
            print("retry", e); time.sleep(5 * (i + 1))
    return {}

def raw(titles):
    out = {}
    for i in range(0, len(titles), 50):
        j = get(action="query", prop="revisions", rvprop="content", rvslots="main", redirects="1", titles="|".join(titles[i:i+50]))
        for p in j.get("query", {}).get("pages", []):
            r = p.get("revisions")
            if r: out[p["title"]] = r[0]["slots"]["main"]["content"]
        time.sleep(0.3)
    return out

def members(cat):
    res, cont = [], {}
    while True:
        j = get(action="query", list="categorymembers", cmtitle=cat, cmnamespace="0", cmlimit="max", **cont)
        res += [m["title"] for m in j.get("query", {}).get("categorymembers", [])]
        if "continue" not in j: return res
        cont = {k: v for k, v in j["continue"].items()}

def show(title, a=0, b=60):
    t = raw([title]).get(title)
    print(f"\n######## RAW {title} [{a}:{b}]")
    print("MISSING" if t is None else "\n".join(t.splitlines()[a:b]))

PATTERNS = {
    "<poem": r"<poem", "{{poemx": r"\{\{\s*poemx", "{{f1": r"\{\{\s*f1\s*\|", "{{poem-on": r"\{\{\s*poem-on",
    "{{poem|": r"\{\{\s*poem\s*\|", "<pages": r"<pages\s", "<pre>": r"<pre>", "{{стихи": r"\{\{\s*стихи",
    "{{block center": r"\{\{\s*block center", "<div class=poem": r"class=\"?poem",
}

def stats(name, cats):
    titles = []
    for c in cats: titles += members(c)
    titles = list(dict.fromkeys(titles))
    print(f"\n######## STATS {name}: {len(titles)} pages in {cats}")
    sample = titles[:400]
    texts = raw(sample)
    cnt = collections.Counter(); none = []
    tmpl = collections.Counter()
    for t, w in texts.items():
        hit = [k for k, r in PATTERNS.items() if re.search(r, w, re.I)]
        for k in hit: cnt[k] += 1
        if not hit: none.append(t)
        body = re.sub(r"\{\{\s*Отексте[\s\S]*?\n\}\}", "", w, count=1)
        for m in re.finditer(r"\{\{\s*([^|{}\n]+?)\s*[|}]", body): tmpl[m.group(1).lower()] += 1
    print("fetched", len(texts), dict(cnt))
    print("top templates:", tmpl.most_common(25))
    print("no known markup:", len(none), none[:15])
    for t in none[:3]:
        print(f"\n---- {t}\n" + "\n".join(texts[t].splitlines()[:40]))
    for k in ["{{poemx", "{{f1"]:
        ex = [t for t, w in texts.items() if re.search(PATTERNS[k], w, re.I)][:2]
        for t in ex:
            w = texts[t]; i = re.search(PATTERNS[k], w, re.I).start()
            print(f"\n---- {k} example: {t}\n{w[i:i+900]}")

show("Шаблон:Poemx", 0, 40)
show("Шаблон:F1", 0, 40)
show("Шаблон:Poemx/doc", 0, 50)
show("Шаблон:F1/doc", 0, 50)
show("Стихотворения (1912-1915) (Парнок)", 120, 200)
show("Стихотворения Пушкина 1809—1825", 0, 50)
show("Стихотворения Маяковского", 0, 50)
show("Послушайте! (Маяковский)", 0, 50)
show("Облако в штанах (Маяковский)", 0, 40)
show("Евгений Онегин (Пушкин)", 0, 40)
stats("Парнок", ["Категория:Поэзия Софии Яковлевны Парнок", "Категория:София Яковлевна Парнок"])
stats("Маяковский", ["Категория:Поэзия Владимира Владимировича Маяковского"])
stats("Пушкин", ["Категория:Поэзия Александра Сергеевича Пушкина"])
stats("Цветаева", ["Категория:Поэзия Марины Ивановны Цветаевой"])
