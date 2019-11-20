package com.findinpath.github.api;


import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class used for filtering {@link Timer} meters based on the specified criteria.
 */
public final class MeterUtils {

  private MeterUtils() {
  }

  public static <T extends Meter> T getExactlyOneMeter(
      List<Meter> meters, String metricId,
      Class<T> clazz, Tag... tags) {
    var result = getMeters(meters, metricId, clazz, tags);
    if (result.size() == 0) {
      throw new IllegalArgumentException("No meters found for the specified parameters");
    }
    if (result.size() != 1) {
      throw new IllegalArgumentException(
          "More than one meter retrieved for the specified parameters");
    }

    return result.get(0);
  }

  public static <T extends Meter> List<T> getMeters(List<Meter> meters, String metricId,
      Class<T> clazz, Tag... tags) {
    return meters
        .stream()
        .filter(meter -> matches(meter, metricId, tags))
        .filter(clazz::isInstance)
        .map(meter -> convertInstanceOfObject(meter, clazz))
        .collect(Collectors.toList());
  }

  private static <T> T convertInstanceOfObject(Object o, Class<T> clazz) {
    try {
      return clazz.cast(o);
    } catch (ClassCastException e) {
      return null;
    }
  }

  private static boolean matches(Meter meter, String metricId, Tag... tags) {
    return Optional.of(meter)
        .filter(m -> m.getId().getName().equals(metricId))
        .filter(m -> matches(m, tags))
        .isPresent();

  }

  private static boolean matches(Meter meter, Tag... tags) {
    return Optional.ofNullable(tags).stream().flatMap(Arrays::stream)
        .filter(tag -> !tag.getValue().equals(meter.getId().getTag(tag.getKey())))
        .findAny()
        .isEmpty();
  }
}
