'use client';

/********************************************************
 파일명 : model-selector.tsx
 설 명 : 채팅 헤더의 모델 선택 드롭다운. 목록은 게이트웨이가 실제로 서빙하는 것만 서버 컴포넌트에서
 받아 내려온다(lib/api/server-history.ts). 선택 값은 들고 있지 않고 고른 id를 onModelChange로 올려
 보내기만 한다 — 정본은 chat.tsx가 쥐고, 쿠키 저장도 거기서 한다.
 *********************************************************/

import { useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { cn } from '@/lib/utils';

import { CheckCircleFillIcon, ChevronDownIcon } from './icons';

/** 고른 id를 onModelChange로 부모에 올린다. 부모가 상태를 바꾸면 selectedModelId가 곧바로
 * 다시 내려와 라벨이 갱신되므로, 표시용 상태를 따로 두지 않는다. */
export function ModelSelector({
  availableModels,
  selectedModelId,
  onModelChange,
  className,
}: {
  availableModels: string[];
  selectedModelId: string;
  onModelChange: (modelId: string) => void;
} & React.ComponentProps<typeof Button>) {
  const [open, setOpen] = useState(false);

  // 게이트웨이 응답을 못 받았을 때. 고를 것도 알릴 것도 없어 자리만 비운다.
  if (availableModels.length === 0) return null;

  // 게이트웨이에 모델이 하나뿐이면 고를 게 없다 — 드롭다운 대신 어떤 모델이 답하는지만 알린다
  // (기본 배포 구성이 이 경우다. 모델을 늘리는 방법은 infra/config/litellm_config.yaml 참조).
  if (availableModels.length === 1) {
    return (
      <div
        className={cn(
          'w-fit px-2 h-[34px] flex items-center text-sm text-muted-foreground',
          className,
        )}
      >
        {availableModels[0]}
      </div>
    );
  }

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger
        asChild
        className={cn(
          'w-fit data-[state=open]:bg-accent data-[state=open]:text-accent-foreground',
          className,
        )}
      >
        <Button variant="outline" className="md:px-2 md:h-[34px]">
          {selectedModelId}
          <ChevronDownIcon />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-[300px]">
        {availableModels.map((modelId) => (
          <DropdownMenuItem
            key={modelId}
            onSelect={() => {
              setOpen(false);
              onModelChange(modelId);
            }}
            className="gap-4 group/item flex flex-row justify-between items-center"
            data-active={modelId === selectedModelId}
          >
            <div className="flex flex-col gap-1 items-start">{modelId}</div>
            <div className="text-foreground dark:text-foreground opacity-0 group-data-[active=true]/item:opacity-100">
              <CheckCircleFillIcon />
            </div>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
