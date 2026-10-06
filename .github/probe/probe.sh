#!/usr/bin/env bash
# Временная диагностика: как устроены страницы авторов на Викитеке.
API="https://ru.wikisource.org/w/api.php"
UA="TsvetaevaDaily/1.2 (https://github.com/Ezus44/TsvetaevaDaily; probe)"
q() { curl -sS -A "$UA" --data-urlencode "format=json" --data-urlencode "formatversion=2" "$@" "$API"; }
raw() {
  echo; echo "################ RAW: $1 (первые $2 строк)"
  q --data-urlencode action=query --data-urlencode prop=revisions --data-urlencode rvprop=content \
    --data-urlencode rvslots=main --data-urlencode redirects=1 --data-urlencode "titles=$1" \
    | jq -r '.query.pages[0] | (.title + (if .missing then " [MISSING]" else "" end)), (.revisions[0].slots.main.content // "")' | head -n "$2"
}
cats() {
  echo; echo "################ CATEGORIES of $1"
  q --data-urlencode action=query --data-urlencode prop=categories --data-urlencode cllimit=max --data-urlencode "titles=$1" \
    | jq -r '.query.pages[0].categories[]?.title'
}
members() {
  echo; echo "################ MEMBERS of $1"
  q --data-urlencode action=query --data-urlencode list=categorymembers --data-urlencode cmlimit=max --data-urlencode "cmtitle=$1" \
    | jq -r '.query.categorymembers[]? | "\(.ns) \(.title)"' | head -n 60
}
search() {
  echo; echo "################ SEARCH $1"
  q --data-urlencode action=query --data-urlencode list=search --data-urlencode srlimit=100 --data-urlencode srprop= \
    --data-urlencode srnamespace=0 --data-urlencode "srsearch=$1" \
    | jq -r '"total: \(.query.searchinfo.totalhits)", (.query.search[]?.title)' | head -n 80
}

raw "Автор:София Яковлевна Парнок" 200
cats "Автор:София Яковлевна Парнок"
search 'intitle:"Парнок"'
raw "Стихотворения (Парнок)" 120
raw "Стихотворения (1912-1915) (Парнок)" 80
raw "Лоза (Парнок)" 60
raw "Снова знак к отплытию нам дан! (София Парнок)" 40

raw "Автор:Владимир Владимирович Маяковский" 80
cats "Автор:Владимир Владимирович Маяковский"
search 'intitle:"Маяковский"'

raw "Автор:Александр Сергеевич Пушкин" 80
cats "Автор:Александр Сергеевич Пушкин"
search 'intitle:"Пушкин"'
raw "Пророк (Пушкин)" 50
