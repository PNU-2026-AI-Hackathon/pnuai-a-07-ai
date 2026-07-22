import { useState } from "react";
import { useNavigate } from "react-router";
import { useSafety } from "../contexts/SafetyContext";
import { industries, calculateRisk, getAccidentCases, getChecklist } from "../utils/mockData";
import { Button } from "./ui/button";
import { Label } from "./ui/label";
import { Slider } from "./ui/slider";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { Factory, Users, ArrowRight, Shield, AlertCircle } from "lucide-react";
import { toast } from "sonner";

const regions = [
  { value: "\uBD80\uC0B0", label: "\uBD80\uC0B0" },
  { value: "\uACBD\uB0A8", label: "\uACBD\uB0A8" },
  { value: "\uC6B8\uC0B0", label: "\uC6B8\uC0B0" },
  { value: "\uB300\uAD6C", label: "\uB300\uAD6C" },
  { value: "\uACBD\uBD81", label: "\uACBD\uBD81" },
  { value: "\uC11C\uC6B8", label: "\uC11C\uC6B8" },
  { value: "\uACBD\uAE30", label: "\uACBD\uAE30" },
  { value: "\uC778\uCC9C", label: "\uC778\uCC9C" },
  { value: "\uAE30\uD0C0", label: "\uAE30\uD0C0" },
];

export default function Step1Input() {
  const navigate = useNavigate();
  const { setBusinessData, setRiskData, setAccidentCases, setChecklist } = useSafety();
  
  const [industryMajor, setIndustryMajor] = useState("");
  const [industryMid, setIndustryMid] = useState("");
  const [region, setRegion] = useState("\uBD80\uC0B0");
  const [workerCount, setWorkerCount] = useState([20]);
  const [validationError, setValidationError] = useState("");
  
  const selectedIndustryData = industries.find(i => i.name === industryMajor);
  
  const handleStart = () => {
    if (!industryMajor || !industryMid || !region) {
      const message = "업종과 세부 업종을 모두 선택해주세요.";
      setValidationError(message);
      toast.warning("업종 선택이 필요해요", {
        description: message,
      });
      return;
    }

    setValidationError("");
    
    const businessData = {
      industryMajor,
      industryMid,
      region,
      workerCount: workerCount[0],
    };
    
    setBusinessData(businessData);
    setRiskData(calculateRisk(businessData));
    setAccidentCases(getAccidentCases(businessData));
    setChecklist(getChecklist(businessData));
    
    navigate("/report");
  };
  
  return (
    <div className="container max-w-4xl mx-auto px-4 py-8 md:py-16">
      <div className="text-center mb-8 md:mb-12">
        <div className="inline-flex items-center gap-2 bg-blue-100 text-blue-700 px-4 py-2 rounded-full mb-4">
          <Shield className="w-5 h-5" />
          <span className="text-sm font-medium">AI 기반 산업안전 진단 시스템</span>
        </div>
        <h1 className="text-3xl md:text-5xl font-bold mb-4 text-gray-900">
          우리 사업장의<br />산업재해 위험도는?
        </h1>
        <p className="text-lg text-gray-600">
          간단한 정보 입력만으로 3초 만에 확인하세요
        </p>
      </div>
      
      <Card className="border-2 shadow-xl">
        <CardHeader>
          <CardTitle className="text-2xl">🟢 STEP 1. 사업장 진단</CardTitle>
          <CardDescription className="text-base">
            몇 가지 기본 정보만 입력해주세요
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-8">
          {/* Industry Selection */}
          <div className="space-y-4">
            <Label htmlFor="industry" className="text-base font-semibold flex items-center gap-2">
              <Factory className="w-5 h-5 text-blue-600" />
              업종 선택
            </Label>
            <div className="grid md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="industry-main" className="text-sm text-gray-600">
                  주업종
                </Label>
                <Select value={industryMajor} onValueChange={(value) => {
                  setIndustryMajor(value);
                  setIndustryMid("");
                  setValidationError("");
                }}>
                  <SelectTrigger
                    id="industry-main"
                    className={`h-12 ${validationError && !industryMajor ? "border-red-500 ring-2 ring-red-100" : ""}`}
                    aria-invalid={validationError && !industryMajor ? "true" : "false"}
                  >
                    <SelectValue placeholder="업종을 선택하세요" />
                  </SelectTrigger>
                  <SelectContent>
                    {industries.map((ind) => (
                      <SelectItem key={ind.name} value={ind.name}>
                        {ind.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="industry-sub" className="text-sm text-gray-600">
                  세부 업종
                </Label>
                <Select 
                  value={industryMid} 
                  onValueChange={(value) => {
                    setIndustryMid(value);
                    setValidationError("");
                  }}
                  disabled={!industryMajor}
                >
                  <SelectTrigger
                    id="industry-sub"
                    className={`h-12 ${validationError && !industryMid ? "border-red-500 ring-2 ring-red-100" : ""}`}
                    aria-invalid={validationError && !industryMid ? "true" : "false"}
                  >
                    <SelectValue placeholder={industryMajor ? "세부 업종 선택" : "먼저 주업종을 선택하세요"} />
                  </SelectTrigger>
                  <SelectContent>
                    {selectedIndustryData?.subIndustries.map((sub) => (
                      <SelectItem key={sub} value={sub}>
                        {sub}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>

          {/* Region Selection */}
          <div className="space-y-2">
            <Label htmlFor="region" className="text-base font-semibold">
              {"\uC9C0\uC5ED"}
            </Label>
            <Select value={region} onValueChange={setRegion}>
              <SelectTrigger id="region" className="h-12">
                <SelectValue placeholder={"\uC9C0\uC5ED\uC744 \uC120\uD0DD\uD558\uC138\uC694"} />
              </SelectTrigger>
              <SelectContent>
                {regions.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          
          {/* Employee Count Slider */}
          <div className="space-y-4">
            <Label className="text-base font-semibold flex items-center gap-2">
              <Users className="w-5 h-5 text-green-600" />
              직원 수
            </Label>
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-3xl font-bold text-blue-600">
                  {workerCount[0]}명
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setWorkerCount([Math.max(1, workerCount[0] - 5)])}
                  >
                    -5
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setWorkerCount([Math.min(200, workerCount[0] + 5)])}
                  >
                    +5
                  </Button>
                </div>
              </div>
              <Slider
                value={workerCount}
                onValueChange={setWorkerCount}
                min={1}
                max={200}
                step={1}
                className="w-full"
              />
              <div className="flex justify-between text-sm text-gray-500">
                <span>1명</span>
                <span>200명</span>
              </div>
            </div>
          </div>

          {validationError && (
            <div
              role="alert"
              className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-red-900"
            >
              <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-red-600" />
              <div>
                <p className="font-semibold">필수 정보가 비어 있어요</p>
                <p className="text-sm text-red-700">{validationError}</p>
              </div>
            </div>
          )}
          
          {/* Start Button */}
          <Button 
            onClick={handleStart}
            size="lg"
            className="w-full h-14 text-lg font-semibold bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 shadow-lg"
          >
            <span>3초 만에 우리 공장 위험도 확인하기</span>
            <ArrowRight className="w-5 h-5 ml-2" />
          </Button>
        </CardContent>
      </Card>
      
      {/* Trust Indicators */}
      <div className="mt-8 flex flex-wrap justify-center gap-6 text-sm text-gray-600">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 bg-green-500 rounded-full" />
          <span>고용노동부 산재 통계 기반</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 bg-blue-500 rounded-full" />
          <span>AI 분석 리포트 제공</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 bg-orange-500 rounded-full" />
          <span>무료 체크리스트 다운로드</span>
        </div>
      </div>
    </div>
  );
}
