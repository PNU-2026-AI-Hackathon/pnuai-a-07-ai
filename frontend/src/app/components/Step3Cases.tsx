import { useState } from "react";
import { useNavigate } from "react-router";
import { useSafety } from "../contexts/SafetyContext";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "./ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "./ui/dialog";
import { Badge } from "./ui/badge";
import { AlertCircle, Calendar, MapPin, Info, ArrowRight, Bot } from "lucide-react";
import { motion } from "motion/react";
import { useEffect } from "react";

export default function Step3Cases() {
  const navigate = useNavigate();
  const { businessData, accidentCases } = useSafety();
  const [selectedCase, setSelectedCase] = useState<string | null>(null);
  
  useEffect(() => {
    if (!businessData || accidentCases.length === 0) {
      navigate("/");
    }
  }, [businessData, accidentCases, navigate]);
  
  if (!businessData || accidentCases.length === 0) {
    return null;
  }
  
  const selectedCaseData = accidentCases.find(c => c.id === selectedCase);
  
  return (
    <div className="container max-w-6xl mx-auto px-4 py-8">
      <div className="text-center mb-8">
        <div className="inline-flex items-center gap-2 bg-orange-100 text-orange-700 px-4 py-2 rounded-full mb-4">
          <AlertCircle className="w-5 h-5" />
          <span className="text-sm font-medium">STEP 3 / 4</span>
        </div>
        <h1 className="text-3xl md:text-4xl font-bold mb-2 text-gray-900">
          🟠 맞춤형 재해 사례
        </h1>
        <p className="text-gray-600">
          {businessData.industry} · {businessData.subIndustry}와 유사한 실제 사고 사례
        </p>
      </div>
      
      <div className="mb-8 bg-blue-50 border-2 border-blue-200 rounded-lg p-4">
        <p className="text-sm text-blue-800">
          <Info className="w-4 h-4 inline mr-2" />
          아래 사례들은 사장님의 사업장과 유사한 조건에서 발생한 실제 산업재해입니다.
          <strong className="ml-1">남의 일이 아닙니다.</strong>
        </p>
      </div>
      
      {/* Accident Case Cards */}
      <div className="grid md:grid-cols-2 gap-6 mb-8">
        {accidentCases.map((accidentCase, index) => (
          <motion.div
            key={accidentCase.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: index * 0.1 }}
          >
            <Card className="border-2 shadow-lg hover:shadow-xl transition-shadow cursor-pointer h-full"
                  onClick={() => setSelectedCase(accidentCase.id)}>
              <CardHeader>
                <div className="flex items-start justify-between mb-2">
                  <Badge variant="destructive" className="text-xs">
                    실제 사례
                  </Badge>
                  <Badge variant="outline" className="text-xs">
                    사례 #{accidentCase.id}
                  </Badge>
                </div>
                <CardTitle className="text-lg line-clamp-2">
                  {accidentCase.cause}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-2 text-sm">
                  <div className="flex items-start gap-2 text-gray-600">
                    <Calendar className="w-4 h-4 mt-0.5 flex-shrink-0" />
                    <span>{accidentCase.date}</span>
                  </div>
                  <div className="flex items-start gap-2 text-gray-600">
                    <MapPin className="w-4 h-4 mt-0.5 flex-shrink-0" />
                    <span>{accidentCase.location}</span>
                  </div>
                  <div className="flex items-start gap-2 text-red-600 font-semibold">
                    <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                    <span>{accidentCase.result}</span>
                  </div>
                </div>
                
                {/* AI Advice */}
                <div className="bg-gradient-to-r from-blue-50 to-cyan-50 border-l-4 border-blue-500 rounded-r-lg p-3 mt-4">
                  <div className="flex items-start gap-2">
                    <Bot className="w-5 h-5 text-blue-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <p className="text-xs font-semibold text-blue-800 mb-1">AI 한 줄 평</p>
                      <p className="text-sm text-gray-700">{accidentCase.aiAdvice}</p>
                    </div>
                  </div>
                </div>
                
                <Button 
                  variant="outline" 
                  size="sm" 
                  className="w-full mt-2"
                  onClick={(e) => {
                    e.stopPropagation();
                    setSelectedCase(accidentCase.id);
                  }}
                >
                  상세보기
                </Button>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>
      
      {/* Action Buttons */}
      <div className="flex flex-col sm:flex-row gap-4 justify-center">
        <Button
          variant="outline"
          size="lg"
          onClick={() => navigate("/report")}
          className="h-12"
        >
          이전 단계
        </Button>
        <Button
          size="lg"
          onClick={() => navigate("/checklist")}
          className="h-12 bg-gradient-to-r from-red-600 to-orange-600 hover:from-red-700 hover:to-orange-700"
        >
          안전 체크리스트 확인하기
          <ArrowRight className="w-5 h-5 ml-2" />
        </Button>
      </div>
      
      {/* Detail Modal */}
      <Dialog open={!!selectedCase} onOpenChange={() => setSelectedCase(null)}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          {selectedCaseData && (
            <>
              <DialogHeader>
                <DialogTitle className="text-xl">
                  {selectedCaseData.cause}
                </DialogTitle>
                <DialogDescription className="space-y-3 pt-4">
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="font-semibold text-gray-700">발생일:</span>
                      <p className="text-gray-600">{selectedCaseData.date}</p>
                    </div>
                    <div>
                      <span className="font-semibold text-gray-700">발생장소:</span>
                      <p className="text-gray-600">{selectedCaseData.location}</p>
                    </div>
                  </div>
                  
                  <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                    <span className="font-semibold text-red-800">피해 결과:</span>
                    <p className="text-red-700 mt-1">{selectedCaseData.result}</p>
                  </div>
                </DialogDescription>
              </DialogHeader>
              
              <div className="space-y-4 pt-4">
                <div>
                  <h4 className="font-semibold text-gray-900 mb-2">사고 경위</h4>
                  <p className="text-gray-700 leading-relaxed">
                    {selectedCaseData.fullDescription}
                  </p>
                </div>
                
                <div className="bg-gradient-to-r from-blue-50 to-cyan-50 border-l-4 border-blue-500 rounded-r-lg p-4">
                  <div className="flex items-start gap-3">
                    <Bot className="w-6 h-6 text-blue-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-blue-800 mb-2">AI 조언</p>
                      <p className="text-gray-700">{selectedCaseData.aiAdvice}</p>
                    </div>
                  </div>
                </div>
              </div>
              
              <div className="pt-4 flex justify-end">
                <Button onClick={() => setSelectedCase(null)}>
                  닫기
                </Button>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
