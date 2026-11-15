package com.ouroboros.metadata.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.vavr.control.Try;

import com.ouroboros.util.Json;

public final class TypedMetadataSpec<T> {
  private final Class<T> metadataType;
  private final String typedSuffix;
  private final SourcePlan sourcePlan;
  private final String legacyDirectory;
  private final Pattern legacyFilePattern;
  private final List<MetadataResourceParser<? extends T>> parsers;
  private final Predicate<String> resourceNamePredicate;
  private final Function<T, String> keyExtractor;
  private final MergeStrategy mergeStrategy;
  private final ParseFailurePolicy parseFailurePolicy;
  private final BlankKeyPolicy blankKeyPolicy;

  private TypedMetadataSpec(Builder<T> builder) {
    metadataType = builder.metadataType;
    typedSuffix = requireNonBlank(builder.typedSuffix, "typedSuffix");
    sourcePlan = Objects.requireNonNull(builder.sourcePlan, "sourcePlan");
    legacyDirectory = builder.legacyDirectory;
    legacyFilePattern = builder.legacyFilePattern;
    parsers = List.copyOf(builder.parsers);
    resourceNamePredicate = Objects.requireNonNull(builder.resourceNamePredicate, "resourceNamePredicate");
    if (parsers.isEmpty()) {
      throw new IllegalArgumentException("At least one metadata parser is required");
    }
    keyExtractor = Objects.requireNonNull(builder.keyExtractor, "keyExtractor");
    mergeStrategy = Objects.requireNonNull(builder.mergeStrategy, "mergeStrategy");
    parseFailurePolicy = Objects.requireNonNull(builder.parseFailurePolicy, "parseFailurePolicy");
    blankKeyPolicy = Objects.requireNonNull(builder.blankKeyPolicy, "blankKeyPolicy");
  }

  public static <T> Builder<T> builder(Class<T> metadataType) {
    return new Builder<>(metadataType);
  }

  public Class<T> metadataType() {
    return metadataType;
  }

  public String typedSuffix() {
    return typedSuffix;
  }

  public SourcePlan sourcePlan() {
    return sourcePlan;
  }

  public Optional<String> legacyDirectory() {
    return Optional.ofNullable(legacyDirectory);
  }

  public Optional<Pattern> legacyFilePattern() {
    return Optional.ofNullable(legacyFilePattern);
  }

  public List<MetadataResourceParser<? extends T>> parsers() {
    return parsers;
  }

  public Predicate<String> resourceNamePredicate() {
    return resourceNamePredicate;
  }

  public Function<T, String> keyExtractor() {
    return keyExtractor;
  }

  public MergeStrategy mergeStrategy() {
    return mergeStrategy;
  }

  public ParseFailurePolicy parseFailurePolicy() {
    return parseFailurePolicy;
  }

  public BlankKeyPolicy blankKeyPolicy() {
    return blankKeyPolicy;
  }

  private static String requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  @FunctionalInterface
  public interface MetadataParser<T> {
    Try<? extends T> parse(String content);
  }

  @FunctionalInterface
  public interface MetadataResourceParser<T> {
    Try<? extends T> parse(MetadataResource resource);
  }

  public enum SourcePlan {
    TYPED_SUFFIX_ONLY,
    LEGACY_DIRECTORY_THEN_TYPED_SUFFIX
  }

  public enum MergeStrategy {
    LAST_WINS,
    FIRST_WINS,
    FAIL_ON_DUPLICATE
  }

  public enum ParseFailurePolicy {
    WARN_AND_SKIP,
    ERROR
  }

  public enum BlankKeyPolicy {
    WARN_AND_SKIP,
    ERROR
  }

  public static final class Builder<T> {
    private final Class<T> metadataType;
    private String typedSuffix;
    private SourcePlan sourcePlan = SourcePlan.TYPED_SUFFIX_ONLY;
    private String legacyDirectory;
    private Pattern legacyFilePattern;
    private final List<MetadataResourceParser<? extends T>> parsers = new ArrayList<>();
    private Predicate<String> resourceNamePredicate = resourceName -> true;
    private Function<T, String> keyExtractor;
    private MergeStrategy mergeStrategy = MergeStrategy.LAST_WINS;
    private ParseFailurePolicy parseFailurePolicy = ParseFailurePolicy.WARN_AND_SKIP;
    private BlankKeyPolicy blankKeyPolicy = BlankKeyPolicy.WARN_AND_SKIP;

    private Builder(Class<T> metadataType) {
      this.metadataType = Objects.requireNonNull(metadataType, "metadataType");
    }

    public Builder<T> typedSuffix(String typedSuffix) {
      this.typedSuffix = typedSuffix;
      return this;
    }

    public Builder<T> sourcePlan(SourcePlan sourcePlan) {
      this.sourcePlan = sourcePlan;
      return this;
    }

    public Builder<T> legacyDirectory(String legacyDirectory) {
      this.legacyDirectory = legacyDirectory;
      return this;
    }

    public Builder<T> legacyFilePattern(Pattern legacyFilePattern) {
      this.legacyFilePattern = legacyFilePattern;
      return this;
    }

    public Builder<T> jsonParser(Class<? extends T> metadataType) {
      parsers.add(resource -> Json.tryToBean(metadataType, resource.content()));
      return this;
    }

    public Builder<T> parser(MetadataParser<? extends T> parser) {
      Objects.requireNonNull(parser, "parser");
      parsers.add(resource -> parser.parse(resource.content()));
      return this;
    }

    public Builder<T> resourceParser(MetadataResourceParser<? extends T> parser) {
      parsers.add(Objects.requireNonNull(parser, "parser"));
      return this;
    }

    public Builder<T> resourceNamePredicate(Predicate<String> resourceNamePredicate) {
      this.resourceNamePredicate = resourceNamePredicate;
      return this;
    }

    public Builder<T> keyExtractor(Function<T, String> keyExtractor) {
      this.keyExtractor = keyExtractor;
      return this;
    }

    public Builder<T> mergeStrategy(MergeStrategy mergeStrategy) {
      this.mergeStrategy = mergeStrategy;
      return this;
    }

    public Builder<T> parseFailurePolicy(ParseFailurePolicy parseFailurePolicy) {
      this.parseFailurePolicy = parseFailurePolicy;
      return this;
    }

    public Builder<T> blankKeyPolicy(BlankKeyPolicy blankKeyPolicy) {
      this.blankKeyPolicy = blankKeyPolicy;
      return this;
    }

    public TypedMetadataSpec<T> build() {
      return new TypedMetadataSpec<>(this);
    }
  }
}
