import { useNavigate } from "react-router";
import { useSafety } from "../contexts/SafetyContext";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";
import { RiskGauge } from "./RiskGauge";
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from "recharts";
import { AlertTriangle, ArrowRight, TrendingUp } from "lucide-react";
import { motion } from "motion/react";
import { useEffect } from "react";

const COLORS = ["#ef4444", "#f97316", "#eab308", "#94a3b8"];

export default function Step2Report() {
  const navigate = useNavigate();
  const { businessData, riskData } = useSafety();
  
  useEffect(() => {
    if (!businessData || !riskData) {
      navigate("/");
    }
  }, [businessData, riskData, navigate]);
  
  if (!businessData || !riskData) {
    return null;
  }
  
  const chartData = riskData.topAccidents.map(acc => ({
    name: acc.type,
    value: acc.percentage,
  }));
  
  return (
    <div className="container max-w-6xl mx-auto px-4 py-8">
      <div className="text-center mb-8">
        <div className="inline-flex items-center gap-2 bg-yellow-100 text-yellow-700 px-4 py-2 rounded-full mb-4">
          <AlertTriangle className="w-5 h-5" />
          <span className="text-sm font-medium">STEP 2 / 4</span>
        </div>
        <h1 className="text-3xl md:text-4xl font-bold mb-2 text-gray-900">
          🟡 AI 위험 리포트
        </h1>
        <p className="text-gray-600">
          {businessData.industry} · {businessData.subIndustry} · 직원 {businessData.employeeCount}명
        </p>
      </div>
      
      <div className="grid md:grid-cols-2 gap-6 mb-8">
        {/* Risk Gauge Card */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          <Card className="border-2 shadow-lg">
            <CardHeader>
              <CardTitle className="text-xl">종합 위험도</CardTitle>
            </CardHeader>
            <CardContent className="pb-8">
              <RiskGauge riskScore={riskData.overallRisk} riskLevel={riskData.riskLevel} />
            </CardContent>
          </Card>
        </motion.div>
        
        {/* Accident Types Chart */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
        >
          <Card className="border-2 shadow-lg">
            <CardHeader>
              <CardTitle className="text-xl">사고 유형 TOP 3</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie
                    data={chartData}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ name, value }) => `${name} ${value}%`}
                    outerRadius={80}
                    fill="#8884d8"
                    dataKey="value"
                    animationDuration={1000}
                  >
                    {chartData.map((entry, index) => (
                      <Cell key={`accident-${entry.name}-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </motion.div>
      </div>
      
      {/* Comparison Alert */}
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5, delay: 0.4 }}
      >
        <Card className="border-2 border-orange-200 bg-gradient-to-r from-orange-50 to-red-50 shadow-lg mb-8">
          <CardContent className="p-6">
            <div className="flex items-start gap-4">
              <div className="bg-orange-500 text-white p-3 rounded-full">
                <TrendingUp className="w-6 h-6" />
              </div>
              <div className="flex-1">
                <h3 className="text-xl font-bold text-gray-900 mb-2">
                  업계 비교 분석
                </h3>
                <p className="text-lg text-gray-700 mb-3">
                  {riskData.comparisonText}
                </p>
                <div className="bg-white/80 rounded-lg p-4 space-y-2">
                  <p className="text-sm font-semibold text-gray-800">⚠️ 주요 위험 요인</p>
                  <ul className="text-sm text-gray-700 space-y-1 ml-4">
                    {riskData.topAccidents.slice(0, 3).map((acc, idx) => (
                      <li key={idx}>• {acc.type} 사고가 {acc.percentage}%로 높은 비중</li>
                    ))}
                    {businessData.employeeCount < 10 && (
                      <li>• 소규모 사업장으로 안전관리 체계 취약</li>
                    )}
                  </ul>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </motion.div>
      
      {/* Action Buttons */}
      <div className="flex flex-col sm:flex-row gap-4 justify-center">
        <Button
          variant="outline"
          size="lg"
          onClick={() => navigate("/")}
          className="h-12"
        >
          처음으로
        </Button>
        <Button
          size="lg"
          onClick={() => navigate("/cases")}
          className="h-12 bg-gradient-to-r from-orange-600 to-red-600 hover:from-orange-700 hover:to-red-700"
        >
          실제 사고 사례 보기
          <ArrowRight className="w-5 h-5 ml-2" />
        </Button>
      </div>
    </div>
  );
}
