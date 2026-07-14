# 🚀 SafeWork AI 팀원을 위한 기초 Git & GitHub 생존 가이드

이 문서는 깃(Git)과 깃허브(GitHub)가 처음이거나 익숙하지 않은 팀원들을 위한 생존 가이드입니다. 
복잡한 명령어는 잊고, **이 문서에 있는 순서대로만 따라 하시면 됩니다!**

---

## 1. 🛠️ 시작하기 전 필수 세팅 (최초 1회만)

Git에게 내가 누구인지 알려주는 명찰을 다는 작업입니다. 터미널(VS Code 하단 등)을 열고 아래 명령어를 입력하세요.

```bash
git config --global user.email "내 이메일 주소"
git config --global user.name "내 이름 (예: 이승원)"


----------2. 📥 프로젝트 내 컴퓨터로 가져오기 (Clone)----------------------
최초 1회, 팀 레포지토리를 내 컴퓨터로 다운받는 과정입니다.

Bash
# 1. 원하는 폴더(예: D:\workspace)로 이동
cd D:\workspace

# 2. 깃허브 주소 복사해서 클론
git clone https://github.com/PNU-2026-AI-Hackathon/pnuai-a-07-ai.git

# 3. 다운받은 폴더 안으로 쏙 들어가기
cd pnuai-a-07-ai 


----------3. 🌱 나만의 작업공간 만들기 (Branch)---------------------
[경고] main 브랜치에서 직접 작업하지 마세요! 작업 전 반드시 내 브랜치를 만들어야 합니다.

Bash
# 내가 무슨 기능을 만드는지 이름 붙여서 브랜치 생성 후 이동
git checkout -b feature/내이름-기능명

# 예시: 프론트엔드 로그인 UI 작업 시
# git checkout -b feature/dongheon-login

----------4. 💾 내 작업물 저장 후 깃허브 올리기 (Add, Commit, Push)--------------
작업을 완료했거나 중간 저장할 때 사용하는 3단계입니다.

Bash
# 1. 변경된 모든 파일 담기 (띄어쓰기 후 점 주의)
git add . 

# 2. 작업 내용 메모 남기며 저장
git commit -m "feat: 프론트엔드 뼈대 만들었습니다~"

# 3. 깃허브의 '내 브랜치'로 쏘아 올리기
git push origin feature/내이름-기능명
🚨 주의: git push origin main 명령어는 절대 사용 금지! 메인 브랜치 반영은 아래 5번의 PR(Pull Request) 방식을 통해서만 진행합니다.

------------5. 🤝 내 코드를 메인(main)에 합치기 (Pull Request)-----------------
내 브랜치에 올린 코드를 팀의 진짜 프로젝트(main 브랜치)에 합치는 과정입니다. (명령어가 아닌 깃허브 웹사이트에서 진행합니다!)

팀 깃허브 프로젝트 페이지 접속

상단에 뜨는 초록색 [Compare & pull request] 버튼 클릭

"저 로그인 페이지 다 만들었어요, 메인에 합쳐주세요!" 라고 내용 작성 후 [Create pull request] 클릭

팀장이 코드 확인 후 문제가 없으면 [Merge pull request] 버튼을 눌러서 합침 (초록색 불이 들어오면 충돌 없는 안전한 상태입니다!)

-----------6. 🔄 다른 사람이 짠 최신 코드 받아오기 (Pull)---------
팀원이 main 브랜치에 새로운 코드를 합쳤을 때, 내 컴퓨터 코드도 최신화하는 과정입니다.

Bash
# 1. 메인 브랜치로 나가기
git checkout main 

# 2. 깃허브에 있는 최신 메인 코드 받아오기
git pull origin main 

# 3. 다시 내가 작업하던 브랜치로 돌아가서 하던 일 계속하기
git checkout feature/내이름-기능명

