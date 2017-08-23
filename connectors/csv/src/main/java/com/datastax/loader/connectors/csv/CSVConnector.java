/*
 * Copyright (C) 2017 DataStax Inc.
 *
 * This software can be used solely with DataStax Enterprise. Please consult the license at
 * http://www.datastax.com/terms/datastax-dse-driver-license-terms
 */
package com.datastax.loader.connectors.csv;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.datastax.loader.commons.config.LoaderConfig;
import com.datastax.loader.connectors.api.Connector;
import com.datastax.loader.connectors.api.Record;
import com.datastax.loader.connectors.api.internal.DefaultRecord;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.WorkQueueProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * A connector for CSV files.
 *
 * <p>It is capable of reading from any URL, provided that there is a {@link
 * java.net.URLStreamHandler handler} installed for it. For file URLs, it is also capable of reading
 * several files at once from a given root directory.
 *
 * <p>This connector is highly configurable; see its {@code reference.conf} file, bundled within its
 * jar archive, for detailed information.
 */
public class CSVConnector implements Connector {

  private static final Logger LOGGER = LoggerFactory.getLogger(CSVConnector.class);

  private boolean read;
  private URL url;
  private Path root;
  private String pattern;
  private Charset encoding;
  private char delimiter;
  private char quote;
  private char escape;
  private char comment;
  private long linesToSkip;
  private long maxLines;
  private int maxThreads;
  private boolean recursive;
  private boolean header;
  private String fileNameFormat;
  private CsvParserSettings parserSettings;
  private CsvWriterSettings writerSettings;
  private AtomicInteger counter;
  private ExecutorService threadPool;

  @Override
  public void configure(LoaderConfig settings, boolean read) throws MalformedURLException {
    this.read = read;
    url = settings.getURL("url");
    pattern = settings.getString("pattern");
    encoding = settings.getCharset("encoding");
    delimiter = settings.getChar("delimiter");
    quote = settings.getChar("quote");
    escape = settings.getChar("escape");
    comment = settings.getChar("comment");
    linesToSkip = settings.getLong("linesToSkip");
    maxLines = settings.getLong("maxLines");
    maxThreads = settings.getThreads("maxThreads");
    recursive = settings.getBoolean("recursive");
    header = settings.getBoolean("header");
    fileNameFormat = settings.getString("fileNameFormat");
  }

  @Override
  public void init() throws URISyntaxException, IOException {
    if (read) {
      tryReadFromDirectory();
    } else {
      tryWriteToDirectory();
    }
    CsvFormat format = new CsvFormat();
    format.setDelimiter(delimiter);
    format.setQuote(quote);
    format.setQuoteEscape(escape);
    format.setComment(comment);
    if (read) {
      parserSettings = new CsvParserSettings();
      parserSettings.setFormat(format);
      parserSettings.setNumberOfRowsToSkip(linesToSkip);
      parserSettings.setHeaderExtractionEnabled(header);
      parserSettings.setLineSeparatorDetectionEnabled(true);
    } else {
      writerSettings = new CsvWriterSettings();
      writerSettings.setFormat(format);
      writerSettings.setQuoteEscapingEnabled(true);
      counter = new AtomicInteger(0);
    }
    if (maxThreads > 1) {
      threadPool =
          Executors.newFixedThreadPool(
              maxThreads, new ThreadFactoryBuilder().setNameFormat("csv-connector-%d").build());
    }
  }

  @Override
  public void close() throws Exception {
    if (threadPool != null) {
      threadPool.shutdown();
      threadPool.awaitTermination(1, MINUTES);
      threadPool.shutdownNow();
    }
  }

  @Override
  public Publisher<Record> read() {
    assert read;
    if (root != null) {
      return readMultipleFiles();
    } else {
      return readSingleFile(url);
    }
  }

  @Override
  public Subscriber<Record> write() {
    assert !read;
    if (root != null && maxThreads > 1) {
      return writeMultipleThreads();
    } else {
      return writeSingleThread();
    }
  }

  private void tryReadFromDirectory() throws URISyntaxException {
    try {
      Path root = Paths.get(url.toURI());
      if (Files.isDirectory(root)) {
        if (!Files.isReadable(root)) {
          throw new IllegalArgumentException("Directory is not readable: " + root);
        }
        this.root = root;
      }
    } catch (FileSystemNotFoundException ignored) {
      // not a path on a known filesystem, fall back to reading from URL directly
    }
  }

  private void tryWriteToDirectory() throws URISyntaxException, IOException {
    try {
      Path root = Paths.get(url.toURI());
      if (!Files.exists(root)) {
        root = Files.createDirectories(root);
      }
      if (Files.isDirectory(root)) {
        if (!Files.isWritable(root)) {
          throw new IllegalArgumentException("Directory is not writable: " + root);
        }
        this.root = root;
      }
    } catch (FileSystemNotFoundException ignored) {
      // not a path on a known filesystem, fall back to writing to URL directly
    }
  }

  private Flux<Record> readSingleFile(URL url) {
    Flux<Record> records =
        Flux.create(
            e -> {
              CsvParser parser = new CsvParser(parserSettings);
              LOGGER.debug("Reading {}", url);
              try (InputStream is = openInputStream(url)) {
                parser.beginParsing(is, encoding);
                while (true) {
                  com.univocity.parsers.common.record.Record row = parser.parseNextRecord();
                  ParsingContext context = parser.getContext();
                  String source = context.currentParsedContent();
                  if (row == null) {
                    break;
                  }
                  if (e.isCancelled()) {
                    break;
                  }
                  Record record;
                  if (header) {
                    record =
                        new DefaultRecord(
                            source,
                            Suppliers.memoize(() -> getCurrentLocation(url, context)),
                            context.parsedHeaders(),
                            (Object[]) row.getValues());
                  } else {
                    record =
                        new DefaultRecord(
                            source,
                            Suppliers.memoize(() -> getCurrentLocation(url, context)),
                            (Object[]) row.getValues());
                  }
                  LOGGER.trace("Emitting record {}", record);
                  e.next(record);
                }
                LOGGER.debug("Done reading {}", url);
                e.complete();
                parser.stopParsing();
              } catch (IOException e1) {
                LOGGER.error("Error writing to " + url, e1);
                e.error(e1);
                parser.stopParsing();
              }
            },
            FluxSink.OverflowStrategy.BUFFER);
    if (maxLines != -1) {
      records = records.take(maxLines);
    }
    return records;
  }

  private Publisher<Record> readMultipleFiles() {
    Scheduler scheduler =
        maxThreads > 1 ? Schedulers.fromExecutor(threadPool) : Schedulers.immediate();
    return Flux.merge(
        scanRootDirectory()
            .map(
                p -> {
                  try {
                    return readSingleFile(p.toUri().toURL()).subscribeOn(scheduler);
                  } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                  }
                }),
        maxThreads);
  }

  private Flux<Path> scanRootDirectory() {
    return Flux.create(
        e -> {
          PathMatcher matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
          try {
            Files.walkFileTree(
                root,
                Collections.emptySet(),
                recursive ? Integer.MAX_VALUE : 1,
                new SimpleFileVisitor<Path>() {

                  @Override
                  public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                      throws IOException {
                    return e.isCancelled() ? TERMINATE : CONTINUE;
                  }

                  @Override
                  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                      throws IOException {
                    if (Files.isReadable(file)
                        && Files.isRegularFile(file)
                        && matcher.matches(file)
                        && !e.isCancelled()) {
                      e.next(file);
                    }
                    return e.isCancelled() ? TERMINATE : CONTINUE;
                  }

                  @Override
                  public FileVisitResult visitFileFailed(Path file, IOException ex)
                      throws IOException {
                    LOGGER.warn("Could not read " + file.toAbsolutePath().toUri().toURL(), e);
                    return e.isCancelled() ? TERMINATE : CONTINUE;
                  }
                });
            e.complete();
          } catch (IOException e1) {
            e.error(e1);
          }
        },
        FluxSink.OverflowStrategy.BUFFER);
  }

  @NotNull
  private Subscriber<Record> writeSingleThread() {

    return new BaseSubscriber<Record>() {

      private URL url;
      private CsvWriter writer;

      private void start() {
        url = getOrCreateDestinationURL();
        writer = createCSVWriter(url);
        LOGGER.debug("Writing " + url);
      }

      @Override
      protected void hookOnSubscribe(Subscription subscription) {
        start();
        subscription.request(Long.MAX_VALUE);
      }

      @Override
      protected void hookOnNext(Record record) {
        if (root != null && writer.getRecordCount() == maxLines) {
          end();
          start();
        }
        if (header && writer.getRecordCount() == 0) {
          writer.writeHeaders(record.fields());
        }
        LOGGER.trace("Writing record {}", record);
        writer.writeRow(record.values());
      }

      @Override
      protected void hookOnError(Throwable t) {
        LOGGER.error("Error writing to " + url, t);
      }

      @Override
      protected void hookFinally(SignalType type) {
        end();
      }

      private void end() {
        LOGGER.debug("Done writing {}", url);
        if (writer != null) {
          writer.close();
        }
      }
    };
  }

  private Subscriber<Record> writeMultipleThreads() {
    WorkQueueProcessor<Record> dispatcher = WorkQueueProcessor.create(threadPool, 1024);
    for (int i = 0; i < maxThreads; i++) {
      dispatcher.subscribe(writeSingleThread());
    }
    return dispatcher;
  }

  private CsvWriter createCSVWriter(URL url) {
    try {
      return new CsvWriter(openOutputStream(url), writerSettings);
    } catch (Exception e) {
      LOGGER.error("Could not create CSV writer for " + url, e);
      throw new RuntimeException(e);
    }
  }

  private URL getOrCreateDestinationURL() {
    if (root != null) {
      try {
        String next = String.format(fileNameFormat, counter.incrementAndGet());
        return root.resolve(next).toUri().toURL();
      } catch (Exception e) {
        LOGGER.error("Could not create file URL with format " + fileNameFormat, e);
        throw new RuntimeException(e);
      }
    }
    // assume we are writing to a single URL and ignore fileNameFormat
    return url;
  }

  private static URI getCurrentLocation(URL url, ParsingContext context) {
    long line = context.currentLine();
    int column = context.currentColumn();
    return URI.create(
        url.toExternalForm()
            + (url.getQuery() == null ? '?' : '&')
            + "?line="
            + line
            + "&column="
            + column);
  }

  private static InputStream openInputStream(URL url) throws IOException {
    InputStream in = url.openStream();
    return in instanceof BufferedInputStream ? in : new BufferedInputStream(in);
  }

  private static OutputStream openOutputStream(URL url) throws IOException, URISyntaxException {
    OutputStream out;
    // file URLs do not support writing, only reading,
    // so we need to special-case them here
    if (url.getProtocol().equals("file")) {
      out = new FileOutputStream(new File(url.toURI()));
    } else {
      URLConnection connection = url.openConnection();
      connection.setDoOutput(true);
      out = connection.getOutputStream();
    }
    return out instanceof BufferedOutputStream ? out : new BufferedOutputStream(out);
  }
}
