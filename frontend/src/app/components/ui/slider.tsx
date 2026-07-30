"use client";

import * as React from "react";
import * as SliderPrimitive from "@radix-ui/react-slider";

import { cn } from "./utils";

function Slider({
  className,
  defaultValue,
  value,
  min = 0,
  max = 100,
  onValueChange,
  orientation = "horizontal",
  ...props
}: React.ComponentProps<typeof SliderPrimitive.Root>) {
  const initialValues = React.useMemo(
    () =>
      Array.isArray(value)
        ? value
        : Array.isArray(defaultValue)
          ? defaultValue
          : [min, max],
    [value, defaultValue, min, max],
  );
  const [internalValues, setInternalValues] = React.useState(initialValues);
  const _values = Array.isArray(value) ? value : internalValues;

  const rangeStyle = React.useMemo<React.CSSProperties>(() => {
    const valueSpan = Math.max(max - min, 1);
    const percentages = _values
      .map((item) => Math.min(1, Math.max(0, (item - min) / valueSpan)))
      .sort((a, b) => a - b);
    const start = percentages.length > 1 ? percentages[0] : 0;
    const end = percentages.at(-1) ?? 0;
    const thumbSize = 16;

    if (percentages.length > 1) {
      const distance = end - start;

      return {
        left: `calc(${start * 100}% + ${(0.5 - start) * thumbSize}px)`,
        width: `calc(${distance * 100}% - ${distance * thumbSize}px)`,
      };
    }

    return {
      left: 0,
      width: `calc(${end * 100}% + ${(0.5 - end) * thumbSize}px)`,
    };
  }, [_values, min, max]);

  const handleValueChange = (nextValues: number[]) => {
    if (!Array.isArray(value)) {
      setInternalValues(nextValues);
    }
    onValueChange?.(nextValues);
  };

  return (
    <SliderPrimitive.Root
      data-slot="slider"
      defaultValue={defaultValue}
      value={value}
      min={min}
      max={max}
      orientation={orientation}
      onValueChange={handleValueChange}
      className={cn(
        "relative flex w-full touch-none items-center select-none data-[disabled]:opacity-50 data-[orientation=vertical]:h-full data-[orientation=vertical]:min-h-44 data-[orientation=vertical]:w-auto data-[orientation=vertical]:flex-col",
        className,
      )}
      {...props}
    >
      <SliderPrimitive.Track
        data-slot="slider-track"
        className={cn(
          "bg-muted relative grow overflow-hidden rounded-full data-[orientation=horizontal]:h-4 data-[orientation=horizontal]:w-full data-[orientation=vertical]:h-full data-[orientation=vertical]:w-1.5",
        )}
      >
        {orientation === "horizontal" ? (
          <div
            aria-hidden="true"
            data-slot="slider-range"
            className="bg-primary absolute h-full"
            style={rangeStyle}
          />
        ) : (
          <SliderPrimitive.Range
            data-slot="slider-range"
            className="bg-primary absolute w-full"
          />
        )}
      </SliderPrimitive.Track>
      {Array.from({ length: _values.length }, (_, index) => (
        <SliderPrimitive.Thumb
          data-slot="slider-thumb"
          key={index}
          className="border-primary bg-background ring-ring/50 block size-4 shrink-0 rounded-full border shadow-sm transition-[color,box-shadow] hover:ring-4 focus-visible:ring-4 focus-visible:outline-hidden disabled:pointer-events-none disabled:opacity-50"
        />
      ))}
    </SliderPrimitive.Root>
  );
}

export { Slider };
