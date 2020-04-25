/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.oss.dsbulk.codecs.text;

import static java.math.RoundingMode.UNNECESSARY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.datastax.oss.driver.shaded.guava.common.collect.Lists;
import com.datastax.oss.dsbulk.codecs.ConversionContext;
import com.datastax.oss.dsbulk.codecs.text.json.JsonCodecUtils;
import com.datastax.oss.dsbulk.codecs.util.CodecUtils;
import com.datastax.oss.dsbulk.codecs.util.OverflowStrategy;
import com.datastax.oss.dsbulk.codecs.util.TemporalFormat;
import com.datastax.oss.dsbulk.codecs.util.TimeUUIDGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.netty.util.concurrent.FastThreadLocal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class TextConversionContext extends ConversionContext {

  private static final ArrayList<String> DEFAULT_BOOLEAN_STRINGS =
      Lists.newArrayList("1:0", "Y:N", "T:F", "YES:NO", "TRUE:FALSE");

  public static final String LOCALE = "LOCALE";
  public static final String FORMAT_NUMBERS = "FORMAT_NUMBERS";
  public static final String NULL_STRINGS = "NULL_STRINGS";
  public static final String NUMERIC_PATTERN = "NUMERIC_PATTERN";
  public static final String NUMBER_FORMAT = "NUMBER_FORMAT";
  public static final String OVERFLOW_STRATEGY = "OVERFLOW_STRATEGY";
  public static final String ROUNDING_MODE = "ROUNDING_MODE";
  public static final String TIMESTAMP_PATTERN = "TIMESTAMP_PATTERN";
  public static final String DATE_PATTERN = "DATE_PATTERN";
  public static final String TIME_PATTERN = "TIME_PATTERN";
  public static final String TIMESTAMP_FORMAT = "TIMESTAMP_FORMAT";
  public static final String LOCAL_DATE_FORMAT = "LOCAL_DATE_FORMAT";
  public static final String LOCAL_TIME_FORMAT = "LOCAL_TIME_FORMAT";
  public static final String TIME_ZONE = "TIME_ZONE";
  public static final String TIME_UNIT = "TIME_UNIT";
  public static final String EPOCH = "EPOCH";
  public static final String BOOLEAN_INPUT_WORDS = "BOOLEAN_INPUT_WORDS";
  public static final String BOOLEAN_OUTPUT_WORDS = "BOOLEAN_OUTPUT_WORDS";
  public static final String BOOLEAN_NUMBERS = "BOOLEAN_NUMBERS";
  public static final String TIME_UUID_GENERATOR = "TIME_UUID_GENERATOR";
  public static final String ALLOW_EXTRA_FIELDS = "ALLOW_EXTRA_FIELDS";
  public static final String ALLOW_MISSING_FIELDS = "ALLOW_MISSING_FIELDS";
  public static final String OBJECT_MAPPER = "OBJECT_MAPPER";

  public static final GenericType<JsonNode> JSON_NODE_TYPE = GenericType.of(JsonNode.class);

  public TextConversionContext() {
    addAttribute(LOCALE, Locale.US);
    addAttribute(TIME_ZONE, ZoneOffset.UTC);
    addAttribute(FORMAT_NUMBERS, false);
    addAttribute(ROUNDING_MODE, UNNECESSARY);
    addAttribute(OVERFLOW_STRATEGY, OverflowStrategy.REJECT);
    addAttribute(TIME_UNIT, MILLISECONDS);
    addAttribute(EPOCH, Instant.EPOCH.atZone(ZoneOffset.UTC));
    addAttribute(TIME_UUID_GENERATOR, TimeUUIDGenerator.RANDOM);
    addAttribute(NUMERIC_PATTERN, "#,###.##");
    addAttribute(TIMESTAMP_PATTERN, "CQL_TIMESTAMP");
    addAttribute(DATE_PATTERN, "ISO_LOCAL_DATE");
    addAttribute(TIME_PATTERN, "ISO_LOCAL_TIME");
    addAttribute(NULL_STRINGS, new ArrayList<>());
    List<String> list = Objects.requireNonNull((List<String>) DEFAULT_BOOLEAN_STRINGS);
    addAttribute(BOOLEAN_INPUT_WORDS, CodecUtils.getBooleanInputWords(list));
    addAttribute(BOOLEAN_OUTPUT_WORDS, CodecUtils.getBooleanOutputWords(list));
    addAttribute(BOOLEAN_NUMBERS, Lists.newArrayList(BigDecimal.ONE, BigDecimal.ZERO));
    addAttribute(ALLOW_EXTRA_FIELDS, false);
    addAttribute(ALLOW_MISSING_FIELDS, false);
    addAttribute(OBJECT_MAPPER, JsonCodecUtils.getObjectMapper());
    rebuildFormats();
  }

  /**
   * Sets the locale to use for locale-sensitive conversions. The default is {@link Locale#US}.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setLocale(@NonNull Locale locale) {
    addAttribute(LOCALE, Objects.requireNonNull(locale));
    rebuildFormats();
    return this;
  }

  /**
   * Sets the time zone to use for temporal conversions. The default is {@link ZoneOffset#UTC}.
   *
   * <p>When loading, the time zone will be used to obtain a timestamp from inputs that do not
   * convey any explicit time zone information. When unloading, the time zone will be used to format
   * all timestamps.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setTimeZone(@NonNull ZoneId timeZone) {
    addAttribute(TIME_ZONE, Objects.requireNonNull(timeZone));
    rebuildFormats();
    return this;
  }

  /**
   * Whether or not to use the {@linkplain #setNumberFormat(String) numeric pattern} to format
   * numeric output. The default is {@code false}.
   *
   * <p>When set to {@code true}, {@linkplain #setNumberFormat(String) numeric pattern} will be
   * applied when formatting. This allows for nicely-formatted output, but may result in {@linkplain
   * #setRoundingMode(RoundingMode) rounding} or alteration of the original decimal's scale. When
   * set to {@code false}, numbers will be stringified using the {@code toString()} method, and will
   * never result in rounding or scale alteration. Only applicable when unloading, and only if the
   * connector in use requires stringification, because the connector, such as the CSV connector,
   * does not handle raw numeric data; ignored otherwise.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setFormatNumbers(boolean formatNumbers) {
    addAttribute(FORMAT_NUMBERS, formatNumbers);
    rebuildFormats();
    return this;
  }

  /**
   * The rounding strategy to use for conversions from CQL numeric types to {@code String}. The
   * default is {@link RoundingMode#UNNECESSARY}.
   *
   * <p>Only applicable when unloading, if {@link #setFormatNumbers(boolean)} is true and if the
   * connector in use requires stringification, because the connector, such as the CSV connector,
   * does not handle raw numeric data; ignored otherwise.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setRoundingMode(@NonNull RoundingMode roundingMode) {
    addAttribute(ROUNDING_MODE, roundingMode);
    rebuildFormats();
    return this;
  }

  /**
   * The overflow strategy to apply. See {@link OverflowStrategy} javadocs for overflow definitions.
   * The default is {@link OverflowStrategy#REJECT}.
   *
   * <p>Only applicable for loading, when parsing numeric inputs; it does not apply for unloading,
   * since formatting never results in overflow.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setOverflowStrategy(@NonNull OverflowStrategy overflowStrategy) {
    addAttribute(OVERFLOW_STRATEGY, overflowStrategy);
    rebuildFormats();
    return this;
  }

  /**
   * This setting applies only to CQL {@code timestamp} columns, and {@code USING TIMESTAMP} clauses
   * in queries. If the input is a string containing only digits that cannot be parsed using the
   * {@linkplain #setTimestampFormat(String) timestamp format}, the specified time unit is applied
   * to the parsed value.
   *
   * <p>The default is {@link TimeUnit#MILLISECONDS}.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setTimeUnit(@NonNull TimeUnit unit) {
    addAttribute(TIME_UNIT, unit);
    rebuildFormats();
    return this;
  }

  /**
   * This setting applies only to CQL {@code timestamp} columns, and {@code USING TIMESTAMP} clauses
   * in queries. If the input is a string containing only digits that cannot be parsed using the
   * {@linkplain #setTimestampFormat(String) timestamp format}, the specified epoch determines the
   * relative point in time used with the parsed value.
   *
   * <p>The default is {@link Instant#EPOCH} at {@link ZoneOffset#UTC}.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setEpoch(@NonNull ZonedDateTime epoch) {
    addAttribute(EPOCH, epoch);
    rebuildFormats();
    return this;
  }

  /**
   * Strategy to use when generating time-based (version 1) UUIDs from timestamps. The default is
   * {@link TimeUUIDGenerator#RANDOM}.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setTimeUUIDGenerator(@NonNull TimeUUIDGenerator uuidGenerator) {
    addAttribute(TIME_UUID_GENERATOR, uuidGenerator);
    return this;
  }

  /**
   * The numeric pattern to use for conversions between {@code String} and CQL numeric types. The
   * default is {@code #,###.##}.
   *
   * <p>See {@link java.text.DecimalFormat} javadocs for details about the pattern syntax to use.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setNumberFormat(@NonNull String numberFormat) {
    addAttribute(NUMERIC_PATTERN, numberFormat);
    rebuildFormats();
    return this;
  }

  /**
   * The temporal pattern to use for {@code String} to CQL {@code timestamp} conversion. The default
   * is {@code CQL_TIMESTAMP}.
   *
   * <p>Valid choices:
   *
   * <ul>
   *   <li>A date-time pattern such as {@code yyyy-MM-dd HH:mm:ss}
   *   <li>A pre-defined formatter such as {@code ISO_ZONED_DATE_TIME} or {@code ISO_INSTANT}. Any
   *       public static field in {@link java.time.format.DateTimeFormatter} can be used.
   *   <li>The special formatter {@code CQL_TIMESTAMP}, which is a special parser that accepts all
   *       valid CQL literal formats for the {@code timestamp} type.When parsing, this format
   *       recognizes all CQL temporal literals; if the input is a local date or date/time, the
   *       timestamp is resolved using the time zone specified under {@code timeZone}. When
   *       formatting, this format uses the {@code ISO_OFFSET_DATE_TIME} pattern, which is compliant
   *       with both CQL and ISO-8601.
   * </ul>
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setTimestampFormat(@NonNull String timestampFormat) {
    addAttribute(TIMESTAMP_PATTERN, timestampFormat);
    rebuildFormats();
    return this;
  }

  /**
   * The temporal pattern to use for {@code String} to CQL {@code date} conversion. The default is
   * {@code yyyy-MM-dd}.
   *
   * <p>Valid choices:
   *
   * <ul>
   *   <li>A date-time pattern such as {@code yyyy-MM-dd}.
   *   <li>A pre-defined formatter such as {@code ISO_LOCAL_DATE}. Any public static field in {@link
   *       java.time.format.DateTimeFormatter} can be used.
   * </ul>
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setDateFormat(@NonNull String dateFormat) {
    addAttribute(DATE_PATTERN, dateFormat);
    rebuildFormats();
    return this;
  }

  /**
   * The temporal pattern to use for {@code String} to CQL {@code time} conversion.The default is
   * {@code HH:mm:ss}.
   *
   * <p>Valid choices:
   *
   * <ul>
   *   <li>A date-time pattern, such as {@code HH:mm:ss}.
   *   <li>A pre-defined formatter, such as {@code ISO_LOCAL_TIME}. Any public static field in
   *       {@link java.time.format.DateTimeFormatter} can be used.
   * </ul>
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setTimeFormat(@NonNull String timeFormat) {
    addAttribute(TIME_PATTERN, timeFormat);
    rebuildFormats();
    return this;
  }

  /**
   * Specify how {@code true} and {@code false} representations can be used by dsbulk.
   *
   * <p>Each element of the list must be of the form {@code true_value:false_value},
   * case-insensitive. The default list is {@code "1:0", "Y:N", "T:F", "YES:NO", "TRUE:FALSE"}.
   *
   * <p>For loading, all representations are honored: when a record field value exactly matches one
   * of the specified strings, the value is replaced with {@code true} or {@code false} before
   * writing to DSE.
   *
   * <p>For unloading, this setting is only applicable for string-based connectors, such as the CSV
   * connector: the first representation will be used to format booleans before they are written
   * out, and all others are ignored.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setBooleanStrings(@NonNull List<String> booleanStrings) {
    List<String> list = Objects.requireNonNull(booleanStrings);
    addAttribute(BOOLEAN_INPUT_WORDS, CodecUtils.getBooleanInputWords(list));
    addAttribute(BOOLEAN_OUTPUT_WORDS, CodecUtils.getBooleanOutputWords(list));
    return this;
  }

  /**
   * Specify how {@code true} and {@code false} representations can be used by dsbulk.
   *
   * <p>Each element of the list must be of the form {@code true_value:false_value},
   * case-insensitive. The default list is {@code "1:0", "Y:N", "T:F", "YES:NO", "TRUE:FALSE"}.
   *
   * <p>For loading, all representations are honored: when a record field value exactly matches one
   * of the specified strings, the value is replaced with {@code true} or {@code false} before
   * writing to DSE.
   *
   * <p>For unloading, this setting is only applicable for string-based connectors, such as the CSV
   * connector: the first representation will be used to format booleans before they are written
   * out, and all others are ignored.
   *
   * @return this builder (for method chaining).
   */
  @SuppressWarnings("unused")
  public TextConversionContext setBooleanStrings(@NonNull String... booleanStrings) {
    return setBooleanStrings(Arrays.asList(booleanStrings));
  }

  /**
   * Comma-separated list of case-sensitive strings that should be mapped to {@code null}.
   *
   * <p>For loading, when a record field value exactly matches one of the specified strings, the
   * value is replaced with {@code null} before writing to DSE.
   *
   * <p>For unloading, this setting is only applicable for string-based connectors, such as the CSV
   * connector: the first string specified will be used to change a row cell containing {@code null}
   * to the specified string when written out.
   *
   * <p>For example, setting this to {@code ["NULL"]} will cause a field containing the word {@code
   * NULL} to be mapped to {@code null} while loading, and a column containing {@code null} to be
   * converted to the word {@code NULL} while unloading.
   *
   * <p>The default value is {@code []} (no strings are mapped to {@code null}). In the default
   * mode, DSBulk behaves as follows: when loading, if the target CQL type is textual (i.e. text,
   * varchar or ascii), the original field value is left untouched; for other types, if the value is
   * an empty string, it is converted to {@code null}; when unloading, all {@code null} values are
   * converted to an empty string.
   *
   * <p>Note that, regardless of this setting, DSBulk will always convert empty strings to {@code
   * null} if the target CQL type is not textual (i.e. not text, varchar or ascii).
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setNullStrings(@NonNull List<String> nullStrings) {
    addAttribute(NULL_STRINGS, nullStrings);
    rebuildFormats();
    return this;
  }

  /**
   * Comma-separated list of case-sensitive strings that should be mapped to {@code null}.
   *
   * <p>For loading, when a record field value exactly matches one of the specified strings, the
   * value is replaced with {@code null} before writing to DSE.
   *
   * <p>For unloading, this setting is only applicable for string-based connectors, such as the CSV
   * connector: the first string specified will be used to change a row cell containing {@code null}
   * to the specified string when written out.
   *
   * <p>For example, setting this to {@code ["NULL"]} will cause a field containing the word {@code
   * NULL} to be mapped to {@code null} while loading, and a column containing {@code null} to be
   * converted to the word {@code NULL} while unloading.
   *
   * <p>The default value is {@code []} (no strings are mapped to {@code null}). In the default
   * mode, DSBulk behaves as follows: when loading, if the target CQL type is textual (i.e. text,
   * varchar or ascii), the original field value is left untouched; for other types, if the value is
   * an empty string, it is converted to {@code null}; when unloading, all {@code null} values are
   * converted to an empty string.
   *
   * <p>Note that, regardless of this setting, DSBulk will always convert empty strings to {@code
   * null} if the target CQL type is not textual (i.e. not text, varchar or ascii).
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setNullStrings(@NonNull String... nullStrings) {
    return setNullStrings(Arrays.asList(Objects.requireNonNull(nullStrings)));
  }

  /**
   * Sets how numbers are mapped to boolean values. The default is {@link BigDecimal#ONE} for {@code
   * true} and{@link BigDecimal#ZERO} for {@code false}.
   *
   * @return this builder (for method chaining).
   */
  public TextConversionContext setBooleanNumbers(
      @NonNull BigDecimal trueNumber, BigDecimal falseNumber) {
    addAttribute(BOOLEAN_NUMBERS, Lists.newArrayList(trueNumber, falseNumber));
    return this;
  }

  public TextConversionContext setObjectMapper(@NonNull ObjectMapper objectMapper) {
    addAttribute(OBJECT_MAPPER, Objects.requireNonNull(objectMapper));
    return this;
  }

  public TextConversionContext setAllowExtraFields(boolean allowExtraFields) {
    addAttribute(ALLOW_EXTRA_FIELDS, allowExtraFields);
    return this;
  }

  public TextConversionContext setAllowMissingFields(boolean allowMissingFields) {
    addAttribute(ALLOW_MISSING_FIELDS, allowMissingFields);
    return this;
  }

  private void rebuildFormats() {
    String numericPattern = getAttribute(NUMERIC_PATTERN);
    String timestampPattern = getAttribute(TIMESTAMP_PATTERN);
    String datePattern = getAttribute(DATE_PATTERN);
    String timePattern = getAttribute(TIME_PATTERN);
    Locale locale = getAttribute(LOCALE);
    ZoneId timeZone = getAttribute(TIME_ZONE);
    TimeUnit timeUnit = getAttribute(TIME_UNIT);
    ZonedDateTime epoch = getAttribute(EPOCH);
    RoundingMode roundingMode = getAttribute(ROUNDING_MODE);
    boolean formatNumbers = getAttribute(FORMAT_NUMBERS);
    FastThreadLocal<NumberFormat> numberFormat =
        CodecUtils.getNumberFormatThreadLocal(numericPattern, locale, roundingMode, formatNumbers);
    TemporalFormat dateFormat =
        CodecUtils.getTemporalFormat(
            datePattern, timeZone, locale, timeUnit, epoch, numberFormat, false);
    TemporalFormat timeFormat =
        CodecUtils.getTemporalFormat(
            timePattern, timeZone, locale, timeUnit, epoch, numberFormat, false);
    TemporalFormat timestampFormat =
        CodecUtils.getTemporalFormat(
            timestampPattern, timeZone, locale, timeUnit, epoch, numberFormat, true);
    addAttribute(NUMBER_FORMAT, numberFormat);
    addAttribute(LOCAL_DATE_FORMAT, dateFormat);
    addAttribute(LOCAL_TIME_FORMAT, timeFormat);
    addAttribute(TIMESTAMP_FORMAT, timestampFormat);
  }
}