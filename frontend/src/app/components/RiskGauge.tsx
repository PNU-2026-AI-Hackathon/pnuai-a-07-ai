import { motion } from "motion/react";

interface RiskGaugeProps {
  riskScore: number; // 0-100
  riskLevel: "safe" | "caution" | "danger";
}

export function RiskGauge({ riskScore, riskLevel }: RiskGaugeProps) {
  // Convert risk score to angle (-90 to 90 degrees)
  const angle = (riskScore / 100) * 180 - 90;
  
  const levelConfig = {
    safe: {
      color: "#22c55e",
      text: "안전",
      bgColor: "bg-green-100",
      textColor: "text-green-700",
    },
    caution: {
      color: "#eab308",
      text: "주의",
      bgColor: "bg-yellow-100",
      textColor: "text-yellow-700",
    },
    danger: {
      color: "#ef4444",
      text: "위험",
      bgColor: "bg-red-100",
      textColor: "text-red-700",
    },
  };
  
  const config = levelConfig[riskLevel];
  
  // Helper function to calculate arc path points
  const polarToCartesian = (centerX: number, centerY: number, radius: number, angleInDegrees: number) => {
    const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180.0;
    return {
      x: centerX + radius * Math.cos(angleInRadians),
      y: centerY + radius * Math.sin(angleInRadians),
    };
  };

  const describeArc = (x: number, y: number, radius: number, startAngle: number, endAngle: number) => {
    const start = polarToCartesian(x, y, radius, endAngle);
    const end = polarToCartesian(x, y, radius, startAngle);
    const largeArcFlag = endAngle - startAngle <= 180 ? "0" : "1";
    return [
      "M", start.x, start.y,
      "A", radius, radius, 0, largeArcFlag, 0, end.x, end.y
    ].join(" ");
  };

  const centerX = 100;
  const centerY = 100;
  const radius = 70;
  
  return (
    <div className="relative w-full max-w-md mx-auto">
      {/* Gauge Background */}
      <svg viewBox="0 0 200 120" className="w-full">
        {/* Background arc */}
        <path
          d={describeArc(centerX, centerY, radius, -90, 90)}
          fill="none"
          stroke="#e5e7eb"
          strokeWidth="20"
          strokeLinecap="round"
        />
        
        {/* Colored sections */}
        {/* Safe zone: -90 to -18 degrees (0-40 points, green) */}
        <path
          d={describeArc(centerX, centerY, radius, -90, -18)}
          fill="none"
          stroke="#22c55e"
          strokeWidth="20"
          strokeLinecap="round"
          opacity="0.7"
        />
        {/* Caution zone: -18 to 36 degrees (40-70 points, yellow) */}
        <path
          d={describeArc(centerX, centerY, radius, -18, 36)}
          fill="none"
          stroke="#eab308"
          strokeWidth="20"
          strokeLinecap="round"
          opacity="0.7"
        />
        {/* Danger zone: 36 to 90 degrees (70-100 points, red) */}
        <path
          d={describeArc(centerX, centerY, radius, 36, 90)}
          fill="none"
          stroke="#ef4444"
          strokeWidth="20"
          strokeLinecap="round"
          opacity="0.7"
        />
        
        {/* Needle */}
        <g transform={`rotate(${angle} 100 100)`}>
          <motion.line
            x1="100"
            y1="100"
            x2="100"
            y2="30"
            stroke={config.color}
            strokeWidth="3"
            strokeLinecap="round"
            initial={{ y2: 100 }}
            animate={{ y2: 30 }}
            transition={{ duration: 1.5, ease: "easeOut" }}
          />
          <circle cx="100" cy="100" r="6" fill={config.color} />
        </g>
        
        {/* Center labels */}
        <text x="18" y="110" className="text-xs fill-gray-500">0</text>
        <text x="172" y="110" className="text-xs fill-gray-500">100</text>
      </svg>
      
      {/* Score Display */}
      <div className="text-center -mt-8">
        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ delay: 0.5, type: "spring" }}
          className="inline-block"
        >
          <div className={`${config.bgColor} ${config.textColor} px-6 py-3 rounded-full font-bold text-2xl`}>
            {riskScore}점
          </div>
        </motion.div>
        <div className={`mt-2 text-xl font-semibold ${config.textColor}`}>
          {config.text} 단계
        </div>
      </div>
    </div>
  );
}