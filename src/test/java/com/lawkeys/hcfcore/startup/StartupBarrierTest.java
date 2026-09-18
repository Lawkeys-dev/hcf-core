package com.lawkeys.hcfcore.startup;

import com.lawkeys.hcfcore.startup.StartupBarrier.Load;
import com.lawkeys.hcfcore.startup.StartupBarrier.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rule-level tests for the barrier that keeps the server closed until its data is loaded. */
class StartupBarrierTest {

    private Recorder recorder;
    private StartupBarrier barrier;

    @BeforeEach
    void setUp() {
        recorder = new Recorder();
        barrier = new StartupBarrier(recorder);
    }

    // ------------------------------------------------------------------

    @Nested
    class Opening {

        @Test
        void aModuleThatLoadsFastCannotOpenItBeforeTheNextModuleHasDeclaredItsLoad() {
            // The reason seal() exists: teams can finish loading before the claim
            // module has even started. Counting only the loads declared so far
            // would open the server here, with claims still on their way.
            Load teams = barrier.expect("teams");
            teams.succeeded();
            assertEquals(State.LOADING, barrier.state());

            Load claims = barrier.expect("claims");
            barrier.seal();
            assertEquals(State.LOADING, barrier.state(), "claims are still loading");

            claims.succeeded();
            assertEquals(State.READY, barrier.state());
            assertEquals(1, recorder.ready.get());
        }

        @Test
        void itOpensOnTheLastLoadWhenSealedFirst() {
            Load teams = barrier.expect("teams");
            Load balances = barrier.expect("balances");
            barrier.seal();

            teams.succeeded();
            assertFalse(barrier.isReady());
            balances.succeeded();
            assertTrue(barrier.isReady());
            assertEquals(1, recorder.ready.get());
        }

        @Test
        void itOpensAtTheSealWhenEveryLoadHasAlreadySucceeded() {
            barrier.expect("teams").succeeded();
            barrier.expect("claims").succeeded();
            assertEquals(0, recorder.ready.get());

            barrier.seal();
            assertTrue(barrier.isReady());
            assertEquals(1, recorder.ready.get());
        }

        @Test
        void withNothingToWaitForItOpensWhenSealed() {
            barrier.seal();
            assertTrue(barrier.isReady());
        }

        @Test
        void sealingTwiceDoesNotOpenItTwice() {
            barrier.seal();
            barrier.seal();
            assertEquals(1, recorder.ready.get());
        }
    }

    @Nested
    class Failing {

        @Test
        void oneFailedLoadKeepsItClosedEvenWhenEveryOtherLoadSucceeds() {
            Load teams = barrier.expect("teams");
            Load claims = barrier.expect("claims");
            Load balances = barrier.expect("balances");
            barrier.seal();

            teams.succeeded();
            claims.failed();
            assertEquals(State.FAILED, barrier.state(), "closed at the first failure, not after the others");
            balances.succeeded();

            assertEquals(State.FAILED, barrier.state());
            assertEquals(0, recorder.ready.get(), "a failed startup never reports ready");
            assertEquals(List.of("claims"), recorder.failures);
        }

        @Test
        void aFailureBeforeTheSealStillKeepsItClosed() {
            barrier.expect("database").failed();
            barrier.seal();

            assertEquals(State.FAILED, barrier.state());
            assertEquals(0, recorder.ready.get());
        }

        @Test
        void everyFailedLoadIsReported() {
            // The console should name every load that needs fixing, not only the
            // first one, or the operator restarts once per broken module.
            Load teams = barrier.expect("teams");
            Load claims = barrier.expect("claims");
            barrier.seal();

            teams.failed();
            claims.failed();

            assertEquals(List.of("teams", "claims"), recorder.failures);
        }
    }

    @Nested
    class Reporting {

        @Test
        void aLoadThatAlreadySucceededCannotCloseAnOpenBarrier() {
            Load teams = barrier.expect("teams");
            barrier.seal();
            assertTrue(teams.succeeded());

            assertFalse(teams.failed(), "a second report is refused");
            assertEquals(State.READY, barrier.state(), "players already let in are not locked out by a stray report");
            assertTrue(recorder.failures.isEmpty());
        }

        @Test
        void aLoadThatFailedCannotLaterCountAsASuccess() {
            Load teams = barrier.expect("teams");
            barrier.seal();
            assertTrue(teams.failed());

            assertFalse(teams.succeeded());
            assertEquals(State.FAILED, barrier.state());
            assertEquals(0, recorder.ready.get());
        }

        @Test
        void noLoadCanBeDeclaredOnceSealed() {
            barrier.seal();
            assertTrue(barrier.isReady());

            // Accepting it would move an open barrier back to "loading" - or, had
            // it been accepted before the barrier opened, risk never being waited for.
            assertThrows(IllegalStateException.class, () -> barrier.expect("late"));
            assertEquals(State.READY, barrier.state());
        }

        @Test
        void aLoadCannotBeDeclaredTwice() {
            barrier.expect("teams");
            assertThrows(IllegalArgumentException.class, () -> barrier.expect("teams"));
        }
    }

    /**
     * Loads report from the scheduler's async threads, and the last of them may
     * race the seal on the main thread. Whatever the interleaving, the barrier must
     * open exactly once.
     */
    @Nested
    class Concurrency {

        @Test
        void manyLoadsFinishingAtOnceOpenItExactlyOnce() throws Exception {
            int loads = 32;
            List<Load> handles = new ArrayList<>();
            for (int i = 0; i < loads; i++) {
                handles.add(barrier.expect("load-" + i));
            }
            barrier.seal();

            runConcurrently(handles.stream().<Runnable>map(load -> load::succeeded).toList());

            assertTrue(barrier.isReady());
            assertEquals(1, recorder.ready.get());
        }

        @Test
        void theLastLoadRacingTheSealOpensItExactlyOnce() throws Exception {
            for (int round = 0; round < 500; round++) {
                Recorder roundRecorder = new Recorder();
                StartupBarrier roundBarrier = new StartupBarrier(roundRecorder);
                Load only = roundBarrier.expect("teams");

                runConcurrently(List.of(roundBarrier::seal, only::succeeded));

                assertTrue(roundBarrier.isReady(), "round " + round);
                assertEquals(1, roundRecorder.ready.get(), "round " + round);
            }
        }

        /** Starts every action at the same moment on its own thread, and waits for all of them. */
        private void runConcurrently(List<Runnable> actions) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(actions.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (Runnable action : actions) {
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        action.run();
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    // ------------------------------------------------------------------

    private static final class Recorder implements StartupBarrier.Observer {

        final AtomicInteger ready = new AtomicInteger();
        final List<String> failures = new CopyOnWriteArrayList<>();

        @Override
        public void ready() {
            ready.incrementAndGet();
        }

        @Override
        public void failed(String load) {
            failures.add(load);
        }
    }
}
