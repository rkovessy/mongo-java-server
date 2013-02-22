package de.bwaldvogel.mongo.backend;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;

public class ValueComparatorTest {

    private ValueComparator comparator;

    @Before
    public void setUp() {
        comparator = new ValueComparator();
    }

    @Test
    public void testCompareStringValues() {
        assertThat(comparator.compare("abc", "abc")).isEqualTo(0);
        assertThat(comparator.compare("abc", "zzz")).isLessThan(0);
        assertThat(comparator.compare("zzz", "abc")).isGreaterThan(0);
        assertThat(comparator.compare(null, "abc")).isLessThan(0);
        assertThat(comparator.compare("abc", null)).isGreaterThan(0);
    }

    @Test
    public void testCompareNumberValues() {
        assertThat(comparator.compare(123, 123.0)).isEqualTo(0);
        assertThat(comparator.compare(17l, 17.3)).isLessThan(0);
        assertThat(comparator.compare(59, 58.9999)).isGreaterThan(0);
        assertThat(comparator.compare(null, 27)).isLessThan(0);
        assertThat(comparator.compare(27, null)).isGreaterThan(0);
    }

    @Test
    public void testCompareDateValues() {
        assertThat(comparator.compare(new Date(17), new Date(17))).isEqualTo(0);
        assertThat(comparator.compare(new Date(28), new Date(29))).isLessThan(0);
        assertThat(comparator.compare(null, new Date())).isLessThan(0);
        assertThat(comparator.compare(new Date(), null)).isGreaterThan(0);
    }

}
