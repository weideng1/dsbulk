/*
 * Copyright DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.dsbulk.engine.internal.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.Configuration;
import com.datastax.driver.core.ProtocolOptions;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.dsbulk.commons.config.LoaderConfig;
import com.datastax.dsbulk.commons.internal.config.ConfigUtils;
import com.datastax.dsbulk.commons.internal.config.DefaultLoaderConfig;
import com.datastax.dsbulk.engine.WorkflowType;
import com.datastax.dsbulk.engine.internal.log.LogManager;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** */
class LogSettingsTest {

  private Cluster cluster;

  @SuppressWarnings("Duplicates")
  @BeforeEach
  void setUp() throws Exception {
    cluster = mock(Cluster.class);
    Configuration configuration = mock(Configuration.class);
    ProtocolOptions protocolOptions = mock(ProtocolOptions.class);
    when(cluster.getConfiguration()).thenReturn(configuration);
    when(configuration.getProtocolOptions()).thenReturn(protocolOptions);
    when(protocolOptions.getProtocolVersion()).thenReturn(ProtocolVersion.V4);
    when(configuration.getCodecRegistry()).thenReturn(CodecRegistry.DEFAULT_INSTANCE);
  }

  @Test
  void should_create_log_manager_with_default_output_directory() throws Exception {
    LoaderConfig config = new DefaultLoaderConfig(ConfigFactory.load().getConfig("dsbulk.log"));
    LogSettings settings = new LogSettings(config, "test");
    LogManager logManager = settings.newLogManager(WorkflowType.LOAD, cluster);
    try {
      logManager.init();
      assertThat(logManager).isNotNull();
      assertThat(logManager.getExecutionDirectory().toFile().getAbsolutePath())
          .isEqualTo(Paths.get("./logs/test").normalize().toFile().getAbsolutePath());
    } finally {
      //noinspection ResultOfMethodCallIgnored
      Files.walk(logManager.getExecutionDirectory().getParent())
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    }
  }

  @Test
  void should_create_log_manager_when_output_directory_url_provided() throws Exception {
    Path dir = Files.createTempDirectory("test");
    String dirToload = ConfigUtils.maybeEscapeBackslash(dir.toString());
    LoaderConfig config =
        new DefaultLoaderConfig(
            ConfigFactory.parseString("directory = \"" + dirToload + "\"")
                .withFallback(ConfigFactory.load().getConfig("dsbulk.log")));
    LogSettings settings = new LogSettings(config, "test");
    LogManager logManager = settings.newLogManager(WorkflowType.LOAD, cluster);
    logManager.init();
    assertThat(logManager).isNotNull();
    assertThat(logManager.getExecutionDirectory().toFile()).isEqualTo(dir.resolve("test").toFile());
  }

  @Test
  void should_create_log_manager_when_output_directory_path_provided() throws Exception {
    Path dir = Files.createTempDirectory("test");
    String dirToload = ConfigUtils.maybeEscapeBackslash(dir.toString());
    LoaderConfig config =
        new DefaultLoaderConfig(
            ConfigFactory.parseString("directory = \"" + dirToload + "\"")
                .withFallback(ConfigFactory.load().getConfig("dsbulk.log")));
    LogSettings settings = new LogSettings(config, "test");
    LogManager logManager = settings.newLogManager(WorkflowType.LOAD, cluster);
    logManager.init();
    assertThat(logManager).isNotNull();
    assertThat(logManager.getExecutionDirectory().toFile()).isEqualTo(dir.resolve("test").toFile());
  }
}
