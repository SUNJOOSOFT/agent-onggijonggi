'use client';

/********************************************************
 파일명 : model-selector.tsx
 설 명 : 채팅 헤더의 모델 선택 드롭다운. 목록은 게이트웨이가 실제로 서빙하는 것만 서버 컴포넌트에서
 받아 내려온다(lib/api/server-history.ts). useOptimistic으로 클릭 즉시 UI를 갱신하고, actions.ts의
 서버 액션으로 쿠키에 선택을 저장해 다음 방문에도 유지되게 한다.
 *********************************************************/

import { startTransition, useOptimistic, useState } from 'react';

import { saveModelId } from '@/app/(chat)/actions';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { cn } from '@/lib/utils';

import { CheckCircleFillIcon, ChevronDownIcon } from './icons';

/** 선택 즉시 useOptimistic으로 라벨을 먼저 바꾸고, startTransition 안에서 saveModelId
 * 서버 액션으로 쿠키에 기록한다(네트워크 왕복을 기다리지 않고 즉각 반응). */
export function ModelSelector({
  availableModels,
  selectedModelId,
  className,
}: {
  availableModels: string[];
  selectedModelId: string;
} & React.ComponentProps<typeof Button>) {
  const [open, setOpen] = useState(false);
  const [optimisticModelId, setOptimisticModelId] =
    useOptimistic(selectedModelId);

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
          {optimisticModelId}
          <ChevronDownIcon />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-[300px]">
        {availableModels.map((modelId) => (
          <DropdownMenuItem
            key={modelId}
            onSelect={() => {
              setOpen(false);

              startTransition(() => {
                setOptimisticModelId(modelId);
                saveModelId(modelId);
              });
            }}
            className="gap-4 group/item flex flex-row justify-between items-center"
            data-active={modelId === optimisticModelId}
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
