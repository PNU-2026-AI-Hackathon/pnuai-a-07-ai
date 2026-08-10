import { FormEvent, useEffect, useState } from "react";
import { BookOpen, Bot, History, Info, Loader2, Search, Send, Sparkles } from "lucide-react";
import { useSafety } from "../contexts/SafetyContext";
import type { AskResponse, ChatMessage, ChatSession, LawSearchResponse } from "../types/safety";
import { safetyApi } from "../utils/api";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./ui/tabs";
import { Textarea } from "./ui/textarea";

export default function LawAssistantPage() {
  const { workplace } = useSafety();
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResult, setSearchResult] = useState<LawSearchResponse | null>(null);
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<AskResponse | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [history, setHistory] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState<"search" | "chat" | "history" | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    safetyApi.listChatSessions().then(setSessions).catch(() => undefined);
  }, []);

  const searchLaws = async (event: FormEvent) => {
    event.preventDefault();
    if (!searchQuery.trim()) { setError("찾고 싶은 상황이나 법령 키워드를 입력해 주세요."); return; }
    setLoading("search"); setError("");
    try { setSearchResult(await safetyApi.searchLaws(searchQuery.trim(), 8)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "법령을 검색하지 못했습니다."); }
    finally { setLoading(null); }
  };

  const askLaw = async (event: FormEvent) => {
    event.preventDefault();
    if (!question.trim()) { setError("상담할 질문을 입력해 주세요."); return; }
    setLoading("chat"); setError("");
    try {
      let currentSession = sessionId;
      if (!currentSession) {
        const created = await safetyApi.createChatSession(workplace?.id);
        currentSession = created.sessionId;
        setSessionId(created.sessionId);
        setSessions((current) => [created, ...current]);
      }
      const response = await safetyApi.askChat(currentSession, question.trim());
      setAnswer(response);
      setQuestion("");
    } catch (caught) { setError(caught instanceof Error ? caught.message : "법령 상담을 처리하지 못했습니다."); }
    finally { setLoading(null); }
  };

  const openSession = async (session: ChatSession) => {
    setSessionId(session.sessionId); setAnswer(null); setLoading("history"); setError("");
    try { setHistory(await safetyApi.getChatMessages(session.sessionId)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "대화 이력을 불러오지 못했습니다."); }
    finally { setLoading(null); }
  };

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <header className="mb-8">
        <div className="mb-3 inline-flex items-center gap-2 rounded-full bg-blue-100 px-4 py-2 text-sm font-medium text-blue-800"><BookOpen className="h-4 w-4" />산업안전 법령 도구</div>
        <h1 className="text-3xl font-bold text-gray-950 md:text-4xl">관련 법령을 찾고 근거 있는 답변을 받으세요</h1>
        <p className="mt-3 text-gray-600">일상적인 표현으로 검색하거나, 관련 조문만을 근거로 AI에 질문할 수 있습니다.</p>
      </header>

      <Tabs defaultValue="chat">
        <TabsList className="mb-6 grid h-11 w-full max-w-md grid-cols-2"><TabsTrigger value="chat">법령 상담</TabsTrigger><TabsTrigger value="search">관련 법령 찾기</TabsTrigger></TabsList>

        <TabsContent value="chat" className="m-0">
          <div className="grid gap-6 lg:grid-cols-[240px_1fr]">
            <Card className="h-fit border-2"><CardHeader><CardTitle className="flex items-center gap-2 text-base"><History className="h-4 w-4" />최근 상담</CardTitle></CardHeader><CardContent className="space-y-2">{sessions.length ? sessions.slice(0, 8).map((session) => <Button key={session.sessionId} variant={sessionId === session.sessionId ? "secondary" : "ghost"} onClick={() => openSession(session)} className="h-auto w-full justify-start whitespace-normal py-2 text-left text-sm">{session.title || "새 법령 상담"}</Button>) : <p className="text-sm text-gray-500">아직 상담 이력이 없습니다.</p>}<Button variant="outline" onClick={() => { setSessionId(null); setHistory([]); setAnswer(null); }} className="mt-3 w-full">새 상담 시작</Button></CardContent></Card>

            <div className="space-y-5">
              <Card className="border-2"><CardHeader><CardTitle className="flex items-center gap-2 text-xl"><Bot className="h-5 w-5 text-blue-600" />산업안전 법령 상담</CardTitle><CardDescription>답변 생성 모델이 없어도 관련 조문은 항상 확인할 수 있습니다.</CardDescription></CardHeader><CardContent><form onSubmit={askLaw} className="space-y-3"><Label htmlFor="law-question">질문</Label><Textarea id="law-question" value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="예: 사다리에서 떨어질 위험이 있는데 사업주는 무엇을 해야 하나요?" className="min-h-28" maxLength={1000} /><Button type="submit" disabled={loading === "chat"} className="h-11 w-full bg-blue-600 hover:bg-blue-700">{loading === "chat" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}근거 조문으로 답변 받기</Button></form></CardContent></Card>

              {loading === "history" && <p className="flex items-center gap-2 text-sm text-gray-600"><Loader2 className="h-4 w-4 animate-spin" />대화 이력을 불러오는 중입니다.</p>}
              {history.length > 0 && <Card><CardHeader><CardTitle className="text-lg">대화 이력</CardTitle></CardHeader><CardContent className="space-y-3">{history.map((message) => <div key={message.messageId} className={`rounded-md px-4 py-3 text-sm leading-6 ${message.role === "USER" ? "ml-8 bg-blue-600 text-white" : "mr-8 bg-gray-100 text-gray-800"}`}><p className="mb-1 text-xs font-semibold opacity-75">{message.role === "USER" ? "나" : "SafeWork AI"}</p>{message.content}</div>)}</CardContent></Card>}
              {answer && <ChatAnswer response={answer} />}
            </div>
          </div>
        </TabsContent>

        <TabsContent value="search" className="m-0 space-y-6">
          <Card className="border-2"><CardHeader><CardTitle className="flex items-center gap-2 text-xl"><Search className="h-5 w-5 text-blue-600" />관련 법령 찾기</CardTitle><CardDescription>법률용어를 몰라도 상황을 그대로 입력할 수 있습니다.</CardDescription></CardHeader><CardContent><form onSubmit={searchLaws} className="flex flex-col gap-3 sm:flex-row"><div className="flex-1"><Label htmlFor="law-search" className="sr-only">검색어</Label><Input id="law-search" value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder="예: 안전관리자를 꼭 둬야 하나요?" className="h-11" /></div><Button type="submit" disabled={loading === "search"} className="h-11 bg-blue-600 hover:bg-blue-700">{loading === "search" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}검색</Button></form></CardContent></Card>
          {searchResult && <div className="space-y-4"><div className="flex flex-wrap items-center gap-2 text-sm text-gray-600"><Badge>{searchResult.mode === "HYBRID" ? "키워드+의미 검색" : "키워드 검색"}</Badge><span>{searchResult.totalCount}개 조문</span>{searchResult.searchTerms.map((term) => <Badge key={term} variant="outline">{term}</Badge>)}</div>{searchResult.results.length ? searchResult.results.map((article) => <LawArticleCard key={article.articleId} article={article} />) : <Card><CardContent className="flex items-center gap-3 py-8 text-gray-600"><Info className="h-5 w-5" />검색 결과가 없습니다. 다른 표현으로 검색해 보세요.</CardContent></Card>}</div>}
        </TabsContent>
      </Tabs>

      {error && <div role="alert" className="mt-6 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">{error}</div>}
    </div>
  );
}

function ChatAnswer({ response }: { response: AskResponse }) {
  return <Card className="border-2 border-blue-200"><CardHeader><div className="flex flex-wrap items-center gap-2"><CardTitle className="flex items-center gap-2 text-lg"><Sparkles className="h-5 w-5 text-blue-600" />상담 결과</CardTitle><Badge variant="outline">{response.mode === "GENERATED" ? "AI 답변" : "조문 검색"}</Badge></div></CardHeader><CardContent className="space-y-5">{response.answer && <div className="rounded-md bg-blue-50 px-4 py-4 leading-7 text-blue-950">{response.answer}</div>}{response.note && <div className="flex gap-2 rounded-md border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-900"><Info className="h-5 w-5 shrink-0" />{response.note}</div>}<div className="space-y-3"><h3 className="font-semibold text-gray-950">답변 근거 조문</h3>{response.citedArticles.map((article) => <LawArticleCard key={article.articleId} article={article} />)}</div></CardContent></Card>;
}

function LawArticleCard({ article }: { article: LawSearchResponse["results"][number] }) {
  return <Card><CardContent className="p-5"><div className="flex flex-wrap items-center gap-2"><Badge variant="outline">{article.source}</Badge><h3 className="font-semibold text-gray-950">{article.lawName} {article.articleNo}{article.clauseNo ? ` ${article.clauseNo}` : ""}</h3></div><p className="mt-1 text-sm font-medium text-blue-800">{article.title}</p><p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-gray-700">{article.content}</p></CardContent></Card>;
}
