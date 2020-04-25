/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.oss.dsbulk.runner;

import com.datastax.oss.dsbulk.commons.url.LoaderURLStreamHandlerFactory;
import com.datastax.oss.dsbulk.commons.utils.ConsoleUtils;
import com.datastax.oss.dsbulk.commons.utils.ThrowableUtils;
import com.datastax.oss.dsbulk.runner.cli.AnsiConfigurator;
import com.datastax.oss.dsbulk.runner.cli.CommandLineParser;
import com.datastax.oss.dsbulk.runner.cli.GlobalHelpRequestException;
import com.datastax.oss.dsbulk.runner.cli.ParsedCommandLine;
import com.datastax.oss.dsbulk.runner.cli.SectionHelpRequestException;
import com.datastax.oss.dsbulk.runner.cli.VersionRequestException;
import com.datastax.oss.dsbulk.runner.help.HelpEmitter;
import com.datastax.oss.dsbulk.workflow.api.Workflow;
import com.datastax.oss.dsbulk.workflow.api.error.TooManyErrorsException;
import com.typesafe.config.Config;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataStaxBulkLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataStaxBulkLoader.class);

  public static final int STATUS_OK = 0;
  public static final int STATUS_COMPLETED_WITH_ERRORS = 1;
  public static final int STATUS_ABORTED_TOO_MANY_ERRORS = 2;
  public static final int STATUS_ABORTED_FATAL_ERROR = 3;
  public static final int STATUS_INTERRUPTED = 4;
  public static final int STATUS_CRASHED = 5;

  /** A filter to exclude some errors from the sanitized message printed to the console. */
  private static final Predicate<Throwable> NO_REACTOR_ERRORS =
      t ->
          // filter out reactor exceptions as they are usually not relevant to users and
          // other more meaningful errors are generally present.
          !t.getClass().getName().startsWith("reactor.")
              && (t.getMessage() == null || !t.getMessage().startsWith("#block"));

  private final String[] args;

  public static void main(String[] args) {
    URL.setURLStreamHandlerFactory(new LoaderURLStreamHandlerFactory());
    int status = new DataStaxBulkLoader(args).run();
    System.exit(status);
  }

  public DataStaxBulkLoader(String... args) {
    this.args = args;
  }

  public int run() {

    Workflow workflow = null;
    try {

      AnsiConfigurator.configureAnsi(args);

      CommandLineParser parser = new CommandLineParser(args);
      ParsedCommandLine result = parser.parse();
      Config config = result.getConfig();
      workflow = result.getWorkflowProvider().newWorkflow(config);

      WorkflowThread workflowThread = new WorkflowThread(workflow);
      Runtime.getRuntime().addShutdownHook(new CleanupThread(workflow, workflowThread));

      // start the workflow and wait for its completion
      workflowThread.start();
      workflowThread.join();
      return workflowThread.status;

    } catch (GlobalHelpRequestException e) {
      HelpEmitter.emitGlobalHelp(e.getConnectorName());
      return STATUS_OK;

    } catch (SectionHelpRequestException e) {
      try {
        HelpEmitter.emitSectionHelp(e.getSectionName(), e.getConnectorName());
        return STATUS_OK;
      } catch (Exception e2) {
        LOGGER.error(e2.getMessage(), e2);
        return STATUS_CRASHED;
      }

    } catch (VersionRequestException e) {
      // Use the OS charset
      PrintWriter pw =
          new PrintWriter(
              new BufferedWriter(new OutputStreamWriter(System.out, Charset.defaultCharset())));
      pw.println(ConsoleUtils.getBulkLoaderNameAndVersion());
      pw.flush();
      return STATUS_OK;

    } catch (Throwable t) {
      return handleUnexpectedError(workflow, t);
    }
  }

  /**
   * A thread responsible for running the workflow.
   *
   * <p>We run the workflow on a dedicated thread to be able to interrupt it when the JVM receives a
   * SIGINT signal (CTRL + C).
   *
   * @see CleanupThread
   */
  private static class WorkflowThread extends Thread {

    private final Workflow workflow;
    private volatile int status = -1;

    private WorkflowThread(Workflow workflow) {
      super("workflow-runner");
      this.workflow = workflow;
    }

    @Override
    public void run() {
      try {
        workflow.init();
        status = workflow.execute() ? STATUS_OK : STATUS_COMPLETED_WITH_ERRORS;
      } catch (TooManyErrorsException e) {
        LOGGER.error(workflow + " aborted: " + e.getMessage(), e);
        status = STATUS_ABORTED_TOO_MANY_ERRORS;
      } catch (Throwable error) {
        status = handleUnexpectedError(workflow, error);
      } finally {
        try {
          // make sure System.err is flushed before the closing sequence is printed to System.out
          System.out.flush();
          System.err.flush();
          workflow.close();
        } catch (Exception e) {
          LOGGER.error(String.format("%s could not be closed.", workflow), e);
        }
      }
    }
  }

  /**
   * A shutdown hook responsible for interrupting the workflow thread in the event of a user
   * interruption via SIGINT (CTLR + C), and for closing the workflow.
   */
  private static class CleanupThread extends Thread {

    private static final Duration SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(10);

    private final Workflow workflow;
    private final WorkflowThread workflowThread;

    private CleanupThread(Workflow workflow, WorkflowThread workflowThread) {
      super("cleanup-thread");
      this.workflow = workflow;
      this.workflowThread = workflowThread;
    }

    @Override
    public void run() {
      try {
        if (workflowThread.isAlive()) {
          LOGGER.error(workflow + " interrupted, waiting for termination.");
          workflowThread.interrupt();
          workflowThread.join(SHUTDOWN_GRACE_PERIOD.toMillis());
          if (workflowThread.isAlive()) {
            workflowThread.status = STATUS_CRASHED;
            LOGGER.error(
                String.format(
                    "%s did not terminate within %d seconds, forcing termination.",
                    workflow, SHUTDOWN_GRACE_PERIOD.getSeconds()));
          }
        }
        // make sure System.err is flushed before the closing sequence is printed to System.out
        System.out.flush();
        System.err.flush();
        workflow.close();
      } catch (Exception e) {
        LOGGER.error(String.format("%s could not be closed.", workflow), e);
      }
    }
  }

  private static int handleUnexpectedError(Workflow workflow, Throwable error) {
    // Reactor framework often wraps InterruptedException.
    if (ThrowableUtils.isInterrupted(error)) {
      return STATUS_INTERRUPTED;
    } else {
      String errorMessage = ThrowableUtils.getSanitizedErrorMessage(error, NO_REACTOR_ERRORS, 2);
      String operationName = workflow == null ? "Operation" : workflow.toString();
      if (error instanceof Exception) {
        LOGGER.error(operationName + " failed: " + errorMessage, error);
        return STATUS_ABORTED_FATAL_ERROR;
      } else {
        LOGGER.error(operationName + " failed unexpectedly: " + errorMessage, error);
        return STATUS_CRASHED;
      }
    }
  }
}