# 기초 덤프 위치

사고 64만건 · SIF 6,032건 · 법령 원문 2,547건은 용량이 커서 git 에 올리지 않습니다.
DB 담당(강주호)에게 받은 `ai_safework_full.sql` 을 **이 폴더에 두면**
`docker compose up` 시 자동으로 적재됩니다.

```
database/dump/ai_safework_full.sql
```

덤프 없이 띄우면 스키마와 체크리스트 문항까지만 만들어지고,
예방 가이드·위험도 진단은 빈 결과를 반환합니다.
