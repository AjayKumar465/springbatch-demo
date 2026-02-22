package com.example.springbatchdemo;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class OrderETLJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job orderETLJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCompleteJobAndPopulateAllTables() throws Exception {
        jobLauncherTestUtils.setJob(orderETLJob);

        JobExecution execution = jobLauncherTestUtils.launchJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Integer customerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer", Integer.class);
        assertThat(customerCount).isEqualTo(3);

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_order", Integer.class);
        assertThat(orderCount).isEqualTo(4);

        Integer txnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_transaction", Integer.class);
        assertThat(txnCount).isEqualTo(5);
    }

    @Test
    void shouldHandleDuplicateCustomerGracefully() throws Exception {
        jobLauncherTestUtils.setJob(orderETLJob);

        jobLauncherTestUtils.launchJob();
        Integer countAfterFirst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer", Integer.class);

        JobExecution secondRun = jobLauncherTestUtils.launchJob();
        assertThat(secondRun.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Integer countAfterSecond = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer", Integer.class);
        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }
}
