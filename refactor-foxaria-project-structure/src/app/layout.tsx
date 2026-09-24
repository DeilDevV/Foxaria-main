import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Foxaria — реструктуризация репозитория",
  description:
    "Модули → modules/, серверы → servers/, сайт → web/site. История git сохранена, сборка и деплой работают как раньше.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ru">
      <body className="antialiased">{children}</body>
    </html>
  );
}
