import { useNavigate } from "react-router";
import { useSafety } from "../contexts/SafetyContext";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "./ui/card";
import { Progress } from "./ui/progress";
import { Checkbox } from "./ui/checkbox";
import { Badge } from "./ui/badge";
import { CheckCircle2, Download, ExternalLink, Home, FileText } from "lucide-react";
import { motion } from "motion/react";
import { useEffect } from "react";

export default function Step4Checklist() {
  const navigate = useNavigate();
  const { businessData, checklist, toggleChecklistItem } = useSafety();
  
  useEffect(() => {
    if (!businessData || checklist.length === 0) {
      navigate("/");
    }
  }, [businessData, checklist, navigate]);
  
  if (!businessData || checklist.length === 0) {
    return null;
  }
  
  const checkedCount = checklist.filter(item => item.checked).length;
  const totalCount = checklist.length;
  const completionRate = Math.round((checkedCount / totalCount) * 100);
  
  const handleDownloadPDF = () => {
    // Mock PDF download
    alert("PDF 다운로드 기능은 실제 서비스에서 구현됩니다.\n\n체크리스트가 PDF로 저장되어 현장에서 활용하실 수 있습니다.");
  };
  
  return (
    <div className="container max-w-4xl mx-auto px-4 py-8">
      <div className="text-center mb-8">
        <div className="inline-flex items-center gap-2 bg-red-100 text-red-700 px-4 py-2 rounded-full mb-4">
          <CheckCircle2 className="w-5 h-5" />
          <span className="text-sm font-medium">STEP 4 / 4</span>
        </div>
        <h1 className="text-3xl md:text-4xl font-bold mb-2 text-gray-900">
          🔴 법적 체크리스트
        </h1>
        <p className="text-gray-600">
          {businessData.industryMajor} · {businessData.industryMid} · {businessData.region} 필수 안전 조치사항
        </p>
      </div>
      
      {/* Completion Progress */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <Card className="border-2 shadow-lg mb-8 bg-gradient-to-r from-green-50 to-emerald-50">
          <CardContent className="p-6">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-xl font-bold text-gray-900">안전 이행률</h3>
                <p className="text-sm text-gray-600 mt-1">
                  {checkedCount} / {totalCount} 항목 완료
                </p>
              </div>
              <div className="text-4xl font-bold text-green-600">
                {completionRate}%
              </div>
            </div>
            <Progress value={completionRate} className="h-3" />
            {completionRate === 100 && (
              <motion.div
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                className="mt-4 bg-green-100 border border-green-300 rounded-lg p-3 text-center"
              >
                <p className="text-green-800 font-semibold">
                  🎉 모든 필수 항목을 완료하셨습니다!
                </p>
              </motion.div>
            )}
          </CardContent>
        </Card>
      </motion.div>
      
      {/* Checklist Items */}
      <div className="space-y-4 mb-8">
        {checklist.map((item, index) => (
          <motion.div
            key={item.id}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.3, delay: index * 0.05 }}
          >
            <Card className={`border-2 transition-all ${
              item.checked 
                ? "bg-green-50 border-green-300" 
                : "hover:shadow-md border-gray-200"
            }`}>
              <CardContent className="p-5">
                <div className="flex items-start gap-4">
                  <Checkbox
                    id={item.id}
                    checked={item.checked}
                    onCheckedChange={() => toggleChecklistItem(item.id)}
                    className="mt-1 h-6 w-6"
                  />
                  <div className="flex-1">
                    <label
                      htmlFor={item.id}
                      className="cursor-pointer"
                    >
                      <div className="flex items-start justify-between gap-4 mb-2">
                        <h4 className={`font-semibold text-base ${
                          item.checked ? "line-through text-gray-500" : "text-gray-900"
                        }`}>
                          {item.title}
                        </h4>
                        {item.checked && (
                          <Badge variant="default" className="bg-green-600 flex-shrink-0">
                            완료
                          </Badge>
                        )}
                      </div>
                      <p className={`text-sm ${
                        item.checked ? "text-gray-400" : "text-gray-600"
                      }`}>
                        {item.description}
                      </p>
                    </label>
                    
                    {item.actionLink && !item.checked && (
                      <Button
                        variant="link"
                        size="sm"
                        className="h-auto p-0 mt-2 text-blue-600"
                        onClick={() => window.open(item.actionLink, "_blank")}
                      >
                        <ExternalLink className="w-3 h-3 mr-1" />
                        지금 바로 조치 방법 보기
                      </Button>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>
      
      {/* Important Notice */}
      <Card className="border-2 border-orange-200 bg-orange-50 mb-8">
        <CardHeader>
          <CardTitle className="text-lg flex items-center gap-2">
            <FileText className="w-5 h-5 text-orange-600" />
            산업안전보건법 안내
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-gray-700 leading-relaxed">
            위 체크리스트는 <strong>산업안전보건법에 따른 필수 이행사항</strong>입니다.
            미이행 시 과태료 부과 또는 사업주 처벌 대상이 될 수 있습니다.
            정기적으로 점검하시고, 문제 발견 시 즉시 조치하시기 바랍니다.
          </p>
        </CardContent>
      </Card>
      
      {/* Action Buttons */}
      <div className="flex flex-col sm:flex-row gap-4">
        <Button
          variant="outline"
          size="lg"
          onClick={() => navigate("/cases")}
          className="h-12 flex-1"
        >
          이전 단계
        </Button>
        <Button
          size="lg"
          onClick={handleDownloadPDF}
          className="h-12 flex-1 bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-700 hover:to-cyan-700"
        >
          <Download className="w-5 h-5 mr-2" />
          오늘의 안전 점검표 출력하기
        </Button>
        <Button
          size="lg"
          variant="secondary"
          onClick={() => navigate("/")}
          className="h-12 flex-1"
        >
          <Home className="w-5 h-5 mr-2" />
          처음으로
        </Button>
      </div>
      
      {/* Summary Card */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.5 }}
        className="mt-8"
      >
        <Card className="border-2 border-blue-200 bg-gradient-to-r from-blue-50 to-indigo-50">
          <CardContent className="p-6 text-center">
            <h3 className="text-lg font-bold text-gray-900 mb-2">
              💡 안전은 선택이 아닌 필수입니다
            </h3>
            <p className="text-sm text-gray-700">
              이 체크리스트를 정기적으로 활용하여 안전한 작업환경을 만들어 주세요.
              <br />
              궁금한 사항은 고용노동부 또는 안전보건공단에 문의하시기 바랍니다.
            </p>
            <div className="flex flex-wrap justify-center gap-4 mt-4 text-xs text-gray-600">
              <span>📞 고용노동부 민원센터: 1350</span>
              <span>📞 안전보건공단: 1644-4544</span>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </div>
  );
}
