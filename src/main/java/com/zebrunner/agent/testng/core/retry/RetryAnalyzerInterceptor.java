package com.zebrunner.agent.testng.core.retry;

import com.zebrunner.agent.testng.listener.RetryService;
import org.testng.IRetryAnalyzer;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.internal.InstanceCreator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retry analyzer interceptor that keeps track of invocation index and checks if all test method retries has
 * been executed. Method with retries is only registered once to Zebrunner
 */
public class RetryAnalyzerInterceptor implements IRetryAnalyzer {

    private static final Map<String, AtomicInteger> RETRY_ANALYZER_KEY_TO_RETRY_INDEX = new ConcurrentHashMap<>();
    private static final Map<String, IRetryAnalyzer> RETRY_ANALYZER_KEY_TO_IDENTITY = new ConcurrentHashMap<>();

    @Override
    public boolean retry(ITestResult result) {
        ITestNGMethod method = result.getMethod();
        ITestContext context = result.getTestContext();

        IRetryAnalyzer retryAnalyzer = getOriginalRetryAnalyzer(result);
        boolean needRetry = retryAnalyzer.retry(result);
        if (needRetry) {
            RetryService.setRetryStarted(method, context);

            String message = getPrintedStackTrace(result.getThrowable());

            RetryService.setRetryFailureReason(getRetryIndex(result), message, method, context);
        } else {
            RetryService.setRetryFinished(method, context);
        }
        return needRetry;
    }

    private String getPrintedStackTrace(Throwable throwable) {
        if (throwable != null) {
            StringWriter errorMessageStringWriter = new StringWriter();
            throwable.printStackTrace(new PrintWriter(errorMessageStringWriter));
            return errorMessageStringWriter.toString();
        } else {
            return "";
        }
    }

    private IRetryAnalyzer getOriginalRetryAnalyzer(ITestResult result) {
        return RETRY_ANALYZER_KEY_TO_IDENTITY.computeIfAbsent(
                RetryService.buildRetryAnalyzerClassKey(result),
                $ -> RetryService.getRetryAnalyzerClass(result.getTestContext(), result.getMethod())
                                 .map(InstanceCreator::newInstance)
                                 .orElseThrow(() -> new RuntimeException("There are no retry analyzer to apply."))
        );
    }

    private int getRetryIndex(ITestResult result) {
        return RETRY_ANALYZER_KEY_TO_RETRY_INDEX.computeIfAbsent(
                RetryService.buildRetryAnalyzerClassKey(result),
                $ -> new AtomicInteger(0)
        ).incrementAndGet();
    }

}
