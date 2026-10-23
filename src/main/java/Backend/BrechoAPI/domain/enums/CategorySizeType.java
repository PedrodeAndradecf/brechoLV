package Backend.BrechoAPI.domain.enums;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;













public enum CategorySizeType {

    ALPHA(new LinkedHashSet<>(Set.of("PP", "P", "M", "G", "GG"))),
    NUMERIC(numericRange(33, 46));

    private final Set<String> allowed;

    CategorySizeType(Set<String> allowed) {
        this.allowed = allowed;
    }

    public boolean allows(String size) {
        return size != null && allowed.contains(size);
    }

    public Set<String> allowedSizes() {
        return Set.copyOf(allowed);
    }

    private static Set<String> numericRange(int fromInclusive, int toInclusive) {
        return IntStream.rangeClosed(fromInclusive, toInclusive)
                .mapToObj(Integer::toString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
