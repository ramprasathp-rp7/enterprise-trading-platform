package com.tradingplatform.api.analytics;

import com.tradingplatform.api.analytics.model.AnalyticsLoadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Terminal trigger for the analytics ETL.
 *
 * Runs only when the app is started with --run-analytics-etl, so it never
 * fires on a normal boot (e.g. when the app starts as the usual API server).
 *
 * Usage (after `mvn clean package`):
 *   java -jar target/<your-jar>.jar --run-analytics-etl
 *
 * Or via Maven directly, without packaging first:
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--run-analytics-etl
 */
@Component
public class AnalyticsEtlRunner implements CommandLineRunner {

    @Autowired
    private AnalyticsETLService analyticsETLService;

    @Override
    public void run(String... args) throws Exception {
        boolean shouldRun = false;
        for (String arg : args) {
            if (arg.equals("--run-analytics-etl")) {
                shouldRun = true;
                break;
            }
        }

        if (!shouldRun) {
            return; // normal boot — do nothing
        }

        System.out.println("=== Starting analytics ETL run ===");
        try {
            AnalyticsLoadResult result = analyticsETLService.runLoad();
            System.out.println("=== Analytics ETL run complete ===");
            System.out.println("Batch ID:         " + result.batchId());
            System.out.println("Started:           " + result.startTime());
            System.out.println("Finished:          " + result.endTime());
            System.out.println("dim_date loaded:   " + result.dimDateRowsLoaded());
            System.out.println("dim_instrument:    " + result.dimInstrumentRowsLoaded());
            System.out.println("dim_account:       " + result.dimAccountRowsLoaded());
            System.out.println("fact_trades loaded:" + result.factTradesRowsLoaded());
            System.out.println("Dead-lettered:     " + result.deadLetteredCount());
        } catch (Exception e) {
            System.err.println("Analytics ETL run FAILED: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            // Exit once the ETL finishes so the run doesn't leave a full web
            // server sitting up in the terminal. Remove this if you'd rather
            // keep the app running afterward.
            System.exit(0);
        }
    }
}