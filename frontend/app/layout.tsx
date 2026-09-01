/********************************************************
 파일명 : layout.tsx (app)
 설 명 : 앱 루트 레이아웃. ThemeProvider·Toaster를 감싸고, 하이드레이션 전에도 theme-color <meta>가
 깜빡이지 않도록 인라인 스크립트로 즉시 반영한다.
 *********************************************************/

import type { Metadata } from 'next';
import { Toaster } from 'sonner';

import { ThemeProvider } from '@/components/theme-provider';

import './globals.css';

export const metadata: Metadata = {
  // 셀프 호스팅이라 고정 도메인이 없어 NEXTAUTH_URL을 쓰고, 없으면 로컬 기본 포트로 폴백한다.
  metadataBase: new URL(process.env.NEXTAUTH_URL ?? 'http://localhost:3000'),
  title: 'onggijonggi-chat',
  description: 'LiteLLM 게이트웨이 기반 AI 챗봇.',
};

const LIGHT_THEME_COLOR = 'hsl(0 0% 100%)';
const DARK_THEME_COLOR = 'hsl(240deg 10% 3.92%)';
const THEME_COLOR_SCRIPT = `\
(function() {
  var html = document.documentElement;
  var meta = document.querySelector('meta[name="theme-color"]');
  if (!meta) {
    meta = document.createElement('meta');
    meta.setAttribute('name', 'theme-color');
    document.head.appendChild(meta);
  }
  function updateThemeColor() {
    var isDark = html.classList.contains('dark');
    meta.setAttribute('content', isDark ? '${DARK_THEME_COLOR}' : '${LIGHT_THEME_COLOR}');
  }
  var observer = new MutationObserver(updateThemeColor);
  observer.observe(html, { attributes: true, attributeFilter: ['class'] });
  updateThemeColor();
})();`;

/** <head>의 인라인 스크립트가 MutationObserver로 <html class="dark"> 변화를 감지해
 * theme-color <meta>를 즉시 맞춰, 첫 페인트에 상태바 색이 어긋나지 않게 한다. */
export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
    >
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: THEME_COLOR_SCRIPT,
          }}
        />
      </head>
      <body className="antialiased">
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          <Toaster position="top-center" />
          {children}
        </ThemeProvider>
      </body>
    </html>
  );
}
