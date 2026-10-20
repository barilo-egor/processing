package net.rcetech.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class DetailsSearchExecutorConfig {

    public static final String DETAILS_SEARCH_EXECUTOR = "detailsSearchExecutor";

    @Bean(name = DETAILS_SEARCH_EXECUTOR)
    public SimpleAsyncTaskExecutor detailsSearchExecutor(
            @Value("${details-search.concurrency-limit:200}") int concurrencyLimit) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("Details-Search-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(concurrencyLimit);
        executor.setRejectTasksWhenLimitReached(true);
        return executor;
    }
}
