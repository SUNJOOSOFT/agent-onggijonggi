'use client';

/********************************************************
 파일명 : multimodal-input.tsx
 설 명 : 채팅 입력창(Textarea)과 전송/중지 버튼. 입력 중 텍스트를 localStorage에도 보관해 새로고침해도 잃지 않게 한다.
 *********************************************************/

import type { ChatRequestOptions, CreateMessage, Message } from 'ai';
import cx from 'classnames';
import type React from 'react';
import {
  type Dispatch,
  type SetStateAction,
  memo,
  useCallback,
  useEffect,
  useRef,
} from 'react';
import { toast } from 'sonner';
import { useLocalStorage, useWindowSize } from 'usehooks-ts';

import { sanitizeUIMessages } from '@/lib/utils';
import { ArrowUpIcon, StopIcon } from './icons';
import { Button } from './ui/button';
import { Textarea } from './ui/textarea';

/** 입력 내용에 맞춰 textarea 높이를 늘린다(스크롤 없이 전체 내용이 보이도록). */
const adjustHeight = (ref: React.RefObject<HTMLTextAreaElement>) => {
  if (ref.current) {
    ref.current.style.height = 'auto';
    ref.current.style.height = `${ref.current.scrollHeight + 2}px`;
  }
};

/** 전송 후 textarea 높이를 기본값으로 되돌린다. */
const resetHeight = (ref: React.RefObject<HTMLTextAreaElement>) => {
  if (ref.current) {
    ref.current.style.height = 'auto';
    ref.current.style.height = '98px';
  }
};

/** Enter(Shift 없이)로 submitForm을 트리거한다. 스트리밍 중 Enter는 전송을 막고 토스트만 띄운다.
 * 전송 시 URL을 `/chat/{chatId}`로 바꿔(history.replaceState) 새로고침해도 같은 세션을 유지한다. */
function PureMultimodalInput({
  chatId,
  input,
  setInput,
  isLoading,
  stop,
  messages,
  setMessages,
  append,
  handleSubmit,
  className,
}: {
  chatId: string;
  input: string;
  setInput: (value: string) => void;
  isLoading: boolean;
  stop: () => void;
  messages: Array<Message>;
  setMessages: Dispatch<SetStateAction<Array<Message>>>;
  append: (
    message: Message | CreateMessage,
    chatRequestOptions?: ChatRequestOptions,
  ) => Promise<string | null | undefined>;
  handleSubmit: (
    event?: {
      preventDefault?: () => void;
    },
    chatRequestOptions?: ChatRequestOptions,
  ) => void;
  className?: string;
}) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const { width } = useWindowSize();

  useEffect(() => {
    if (textareaRef.current) {
      adjustHeight(textareaRef);
    }
  }, []);

  const [localStorageInput, setLocalStorageInput] = useLocalStorage(
    `input-${chatId}`,
    '',
  );

  useEffect(() => {
    if (textareaRef.current) {
      const domValue = textareaRef.current.value;
      // 하이드레이션 처리를 위해 localStorage보다 DOM 값을 우선한다.
      const finalValue = domValue || localStorageInput || '';
      setInput(finalValue);
      adjustHeight(textareaRef);
    }
  }, [setInput, localStorageInput]);

  useEffect(() => {
    setLocalStorageInput(input);
  }, [input, setLocalStorageInput]);

  const handleInput = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(event.target.value);
    adjustHeight(textareaRef);
  };

  const submitForm = useCallback(() => {
    window.history.replaceState({}, '', `/chat/${chatId}`);

    handleSubmit(undefined);

    setLocalStorageInput('');
    resetHeight(textareaRef);

    if (width && width > 768) {
      textareaRef.current?.focus();
    }
  }, [handleSubmit, setLocalStorageInput, width, chatId]);

  return (
    <div className="relative w-full flex flex-col gap-4">
      <Textarea
        ref={textareaRef}
        aria-label="메시지 입력"
        placeholder="Send a message..."
        value={input}
        onChange={handleInput}
        className={cx(
          'min-h-[24px] max-h-[calc(75dvh)] overflow-hidden resize-none rounded-2xl !text-base bg-muted pb-10 dark:border-zinc-700',
          className,
        )}
        rows={2}
        autoFocus
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
            event.preventDefault();

            if (isLoading) {
              toast.error('Please wait for the model to finish its response!');
            } else {
              submitForm();
            }
          }
        }}
      />

      <div className="absolute bottom-0 right-0 p-2 w-fit flex flex-row justify-end">
        {isLoading ? (
          <StopButton stop={stop} setMessages={setMessages} />
        ) : (
          <SendButton input={input} submitForm={submitForm} />
        )}
      </div>
    </div>
  );
}

export const MultimodalInput = memo(
  PureMultimodalInput,
  (prevProps, nextProps) => {
    if (prevProps.input !== nextProps.input) return false;
    if (prevProps.isLoading !== nextProps.isLoading) return false;

    return true;
  },
);

function PureStopButton({
  stop,
  setMessages,
}: {
  stop: () => void;
  setMessages: Dispatch<SetStateAction<Array<Message>>>;
}) {
  return (
    <Button
      className="rounded-full p-1.5 h-fit border dark:border-zinc-600"
      aria-label="응답 생성 중지"
      onClick={(event) => {
        event.preventDefault();
        stop();
        setMessages((messages) => sanitizeUIMessages(messages));
      }}
    >
      <StopIcon size={14} />
    </Button>
  );
}

const StopButton = memo(PureStopButton);

function PureSendButton({
  submitForm,
  input,
}: {
  submitForm: () => void;
  input: string;
}) {
  return (
    <Button
      className="rounded-full p-1.5 h-fit border dark:border-zinc-600"
      aria-label="메시지 보내기"
      onClick={(event) => {
        event.preventDefault();
        submitForm();
      }}
      disabled={input.length === 0}
    >
      <ArrowUpIcon size={14} />
    </Button>
  );
}

const SendButton = memo(PureSendButton, (prevProps, nextProps) => {
  if (prevProps.input !== nextProps.input) return false;
  return true;
});
