/********************************************************
 파일명 : overview.tsx
 설 명 : 세션에 메시지가 하나도 없을 때(새 대화 시작 직후) 화면 중앙에 보여주는 소개문.
 *********************************************************/

import { motion } from 'framer-motion';

import { BotIcon, MessageIcon } from './icons';

export const Overview = () => {
  return (
    <motion.div
      key="overview"
      className="max-w-3xl mx-auto md:mt-20"
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.98 }}
      transition={{ delay: 0.5 }}
    >
      <div className="rounded-xl p-6 flex flex-col gap-8 leading-relaxed text-center max-w-xl">
        <p className="flex flex-row justify-center gap-4 items-center">
          <BotIcon />
          <span>+</span>
          <MessageIcon size={32} />
        </p>
        <p>옹기종기는 LiteLLM 게이트웨이로 여러 모델에 붙는 AI 챗봇입니다.</p>
        <p>
          로그인 후 질문을 입력하면 답변과 함께 근거 문서를 근거 패널로
          보여드립니다.
        </p>
        <p>
          여러 대화를 동시에 진행하려면 왼쪽 사이드바에서 새 대화를 만들어 오갈
          수 있습니다.
        </p>
      </div>
    </motion.div>
  );
};
