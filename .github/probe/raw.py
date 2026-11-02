# Временная разведка: как Викитека обосновывает общественное достояние.
import json, urllib.parse, urllib.request, re
API = "https://ru.wikisource.org/w/api.php"
UA = "StikhiDnya/1.4 (https://github.com/Ezus44/TsvetaevaDaily; probe)"
def q(**p):
    p.update(format="json", formatversion="2")
    return json.load(urllib.request.urlopen(urllib.request.Request(API, data=urllib.parse.urlencode(p).encode(), headers={"User-Agent": UA})))
def text(t):
    j = q(action="parse", page=t, prop="text", redirects="1")
    h = j.get("parse", {}).get("text", "")
    h = re.sub(r"<style[\s\S]*?</style>|<script[\s\S]*?</script>", "", h)
    h = re.sub(r"<br\s*/?>|</p>|</div>|</li>|</tr>", "\n", h)
    h = re.sub(r"<[^>]+>", "", h)
    import html as H
    return re.sub(r"\n\s*\n+", "\n", H.unescape(h)).strip()
for t in ["Шаблон:PD-RusEmpire", "Шаблон:PD-Russia", "Шаблон:PD-old-70", "Шаблон:АП"]:
    print("\n######## ", t); print(text(t)[:2500])
# Какие лицензии стоят на страницах Ахматовой и Пастернака
for t in ["Сероглазый король (Ахматова)", "Лесное (Пастернак)"]:
    j = q(action="query", prop="templates", tllimit="max", titles=t)
    print("\n######## TEMPLATES", t, [x["title"] for x in j["query"]["pages"][0].get("templates", []) if "PD" in x["title"] or "АП" in x["title"] or "Лиценз" in x["title"]])
