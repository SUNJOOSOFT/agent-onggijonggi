package com.onggijonggi.api.chat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Class Name : ThreadDumpOnStallExtension.java
 * Description : 테스트가 실행되는 동안 1초 간격으로 전체 스레드 덤프를 메모리에 모아뒀다가,
 *               그 테스트가 실패했을 때만 파일로 남긴다. 통과하면 버린다 — 평소엔 디스크에
 *               아무것도 쓰지 않아 오버헤드가 없다(이슈 #107).
 *
 *               `.block(Duration)` 타임아웃은 실패가 로그에 찍히는 시점엔 이미 그 호출이
 *               예외를 던지고 넘어간 뒤라, 실패 후에 한 번 찍는 덤프는 "방금 포기한 상태"만
 *               보여준다. 기다리는 동안 다른 스레드가 뭘 하고 있었는지 보려면 실행 내내
 *               주기적으로 찍어둬야 한다 — 그래서 실패 시점이 아니라 실행 내내 샘플링한다.
 *
 *               외부 `jstack` 프로세스를 실행하지 않는다 — JVM 내장 {@link ThreadMXBean}만
 *               쓰므로 OS 의존성이 없다.
 *
 *               스냅샷은 최근 {@link #MAX_SNAPSHOTS}개까지만 링버퍼로 유지한다. 이 확장이 붙는
 *               테스트는 전부 {@code WsTestTimeouts.BLOCK}(5초) 안에서 끝나 정상적인 실패는
 *               5~6개 스냅샷이면 충분하지만, 그 예산을 넘어 영원히 끝나지 않는 극단적 데드락에서도
 *               메모리가 무한히 자라지 않게 하는 안전장치다.
 */
final class ThreadDumpOnStallExtension implements BeforeEachCallback, AfterEachCallback, TestWatcher {

	private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(1);

	private static final int MAX_SNAPSHOTS = 60;

	private static final Path DUMP_DIR = Path.of("build", "test-results", "thread-dumps");

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private static final ExtensionContext.Namespace NAMESPACE =
			ExtensionContext.Namespace.create(ThreadDumpOnStallExtension.class);

	@Override
	public void beforeEach(ExtensionContext context) {
		Sampler sampler = new Sampler();
		context.getStore(NAMESPACE).put(Sampler.class, sampler);
		sampler.start();
	}

	@Override
	public void afterEach(ExtensionContext context) {
		sampler(context).stop();
	}

	@Override
	public void testFailed(ExtensionContext context, Throwable cause) {
		writeToFile(context, sampler(context).snapshots());
	}

	@Override
	public void testSuccessful(ExtensionContext context) {
		// 통과했으니 모아둔 스냅샷은 그냥 버린다 — 디스크에 쓰지 않는다.
	}

	private Sampler sampler(ExtensionContext context) {
		return context.getStore(NAMESPACE).get(Sampler.class, Sampler.class);
	}

	private void writeToFile(ExtensionContext context, List<String> snapshots) {
		try {
			Files.createDirectories(DUMP_DIR);
			String fileName = "%s.%s.%s.txt".formatted(
					context.getRequiredTestClass().getSimpleName(),
					context.getRequiredTestMethod().getName(),
					TIMESTAMP_FORMAT.format(LocalDateTime.now()));
			Files.writeString(DUMP_DIR.resolve(fileName), String.join("\n\n", snapshots));
		} catch (IOException error) {
			throw new UncheckedIOException(error);
		}
	}

	/** 1초 간격으로 전체 스레드 덤프를 최근 {@link #MAX_SNAPSHOTS}개까지 링버퍼로 쌓는 데몬 스레드. */
	private static final class Sampler {

		private final Deque<String> snapshots = new ArrayDeque<>();

		private final Thread thread = new Thread(this::run, "thread-dump-sampler");

		private volatile boolean stopped;

		Sampler() {
			thread.setDaemon(true);
		}

		void start() {
			thread.start();
		}

		/** interrupt 후 join까지 해서, 이 메서드가 끝나면 다른 스레드가 snapshots()를 안전하게 읽을 수 있다. */
		void stop() {
			stopped = true;
			thread.interrupt();
			try {
				thread.join(Duration.ofSeconds(2).toMillis());
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
		}

		List<String> snapshots() {
			return new ArrayList<>(snapshots);
		}

		private void run() {
			ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
			while (!stopped) {
				snapshots.addLast(dump(threadMxBean));
				if (snapshots.size() > MAX_SNAPSHOTS) {
					snapshots.removeFirst();
				}
				try {
					Thread.sleep(SAMPLE_INTERVAL.toMillis());
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}

		private String dump(ThreadMXBean threadMxBean) {
			StringBuilder text = new StringBuilder("=== ").append(Instant.now()).append(" ===\n");
			for (ThreadInfo info : threadMxBean.dumpAllThreads(true, true)) {
				text.append(info);
			}
			return text.toString();
		}
	}

}
