import { useEffect, useRef, useCallback } from "react";

const IDLE_MS = 15 * 60 * 1000; // 15 minutes

export function useIdleTimeout(onIdle: () => void): void {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const callbackRef = useRef(onIdle);
  callbackRef.current = onIdle;

  const resetTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
    }
    timerRef.current = setTimeout(() => {
      callbackRef.current();
    }, IDLE_MS);
  }, []);

  useEffect(() => {
    const events: (keyof WindowEventMap)[] = [
      "mousemove",
      "mousedown",
      "keydown",
      "scroll",
      "touchstart",
      "click",
      "focus",
    ];

    const handler = () => {
      resetTimer();
    };

    for (const ev of events) {
      window.addEventListener(ev, handler, { passive: true });
    }

    resetTimer();

    return () => {
      for (const ev of events) {
        window.removeEventListener(ev, handler);
      }
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
      }
    };
  }, [resetTimer]);
}
