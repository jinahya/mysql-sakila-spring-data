package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.RentalRepository.MonthRentalCount;
import com.github.jinahya.mysql.sakila.persistence.Inventory_;
import com.github.jinahya.mysql.sakila.persistence.Rental;
import com.github.jinahya.mysql.sakila.persistence.Rental_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link RentalRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few rentals of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, or against
 * counts taken again by a {@link Specification}, or in memory over the rows. The base's own tests, such as its
 * {@code findById} round trips, run here too. {@link RentalRepository#countReturnedLate()} is MySQL's own, so this is
 * its only test.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see RentalRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RentalRepository_SpringBootIT
        extends _Repository_SpringBootIT<RentalRepository, Rental, Integer> {

    private static List<Rental> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Rental_.rentalId} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_RENTAL_ID = Sort.by(Sort.Direction.ASC, Rental_.RENTAL_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    RentalRepository_SpringBootIT() {
        super(RentalRepository.class, Rental.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five rentals, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The rentals are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_RENTAL_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Rental> rentals() {
        return entities.stream();
    }

    /**
     * Counts the rentals made in the specified month, through a {@link Specification} on the rental date: at or
     * after the first instant of the month, and before the first instant of the next.
     *
     * @param year  the year of the month.
     * @param month the month of the year.
     * @return the number of rentals made in the month.
     */
    private long countRentedIn(final int year, final int month) {
        final var start = LocalDateTime.of(year, month, 1, 0, 0);
        final var end = start.plusMonths(1L);
        final Specification<Rental> specification = (r, q, b) -> b.and(
                b.greaterThanOrEqualTo(r.get(Rental_.rentalDate), start),
                b.lessThan(r.get(Rental_.rentalDate), end)
        );
        return repositoryInstance().count(specification);
    }

    /**
     * Asserts that the specified page is consistent with itself: no more content than its size, and at least as many
     * elements in all as in its content.
     *
     * @param page the page.
     * @param <T>  the type of the elements.
     */
    private static <T> void assertPage(final Page<T> page) {
        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(page.getSize());
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(page.getNumberOfElements());
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link RentalRepository#countNotReturned()} ({@code SAKILA-RENTAL-B-01}).
     */
    @Nested
    class CountNotReturned_Test {

        /**
         * Asserts that the count is the number of all rentals minus those returned, counted by a
         * {@link Specification} of the opposite predicate, {@code isNotNull} on the return date.
         */
        @Test
        void __() {
            final var count = repositoryInstance().countNotReturned();
            final Specification<Rental> returned = (r, q, b) -> b.isNotNull(r.get(Rental_.returnDate));
            assertThat(count).isEqualTo(repositoryInstance().count() - repositoryInstance().count(returned));
        }

        /**
         * Asserts that the count is consistent with a selected rental: at least one if that rental is not returned,
         * and fewer than all rentals if it is.
         *
         * @param rental one of the rentals selected in {@link RentalRepository_SpringBootIT#__()}.
         */
        @MethodSource("rentals")
        @ParameterizedTest
        void __Selected(final Rental rental) {
            final var count = repositoryInstance().countNotReturned();
            if (rental.getReturnDate() == null) {
                assertThat(count).isPositive();
            } else {
                assertThat(count).isLessThan(repositoryInstance().count());
            }
        }

        private static Stream<Rental> rentals() {
            return RentalRepository_SpringBootIT.rentals();
        }
    }

    /**
     * Tests {@link RentalRepository#selectMonthlyCounts(Pageable)} ({@code SAKILA-RENTAL-B-02}).
     */
    @Nested
    class SelectMonthlyCounts_Test {

        private static final Sort BY_YEAR_THEN_MONTH = Sort.by("year", "month");

        /**
         * Asserts that a page of the given size, by the year and then by the month as the exercise sorts, is ordered
         * so, that each month is a month, and that each count is the number of rentals whose rental date falls in
         * that month, counted again by a {@link Specification} on the dates.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 3, 12})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().selectMonthlyCounts(PageRequest.of(0, size, BY_YEAR_THEN_MONTH));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(MonthRentalCount::getYear)
                                                 .thenComparing(MonthRentalCount::getMonth))
                    .allSatisfy(c -> {
                        assertThat(c.getMonth()).isBetween(1, 12);
                        assertThat(c.getRentals()).as("rentals in %d-%02d", c.getYear(), c.getMonth())
                                .isEqualTo(countRentedIn(c.getYear(), c.getMonth()));
                    });
        }

        /**
         * Asserts that the counts of all months add up to the number of all rentals, and that the months are as many
         * in all as the count query says: every rental has a rental date, so every rental is in exactly one month.
         */
        @Test
        void __AddUp() {
            final var all = repositoryInstance().selectMonthlyCounts(Pageable.unpaged(BY_YEAR_THEN_MONTH));
            assertThat(all.getContent()).hasSize((int) all.getTotalElements());
            final var sum = all.getContent().stream().mapToLong(MonthRentalCount::getRentals).sum();
            assertThat(sum).isEqualTo(repositoryInstance().count());
        }

        /**
         * Asserts that the month of a selected rental's rental date is among the months selected.
         *
         * @param rental one of the rentals selected in {@link RentalRepository_SpringBootIT#__()}.
         */
        @MethodSource("rentals")
        @ParameterizedTest
        void __Selected(final Rental rental) {
            final var all = repositoryInstance().selectMonthlyCounts(Pageable.unpaged(BY_YEAR_THEN_MONTH));
            final var rentalDate = rental.getRentalDate();
            assertThat(all.getContent())
                    .anySatisfy(c -> {
                        assertThat(c.getYear()).isEqualTo(rentalDate.getYear());
                        assertThat(c.getMonth()).isEqualTo(rentalDate.getMonthValue());
                    });
        }

        private static Stream<Rental> rentals() {
            return RentalRepository_SpringBootIT.rentals();
        }
    }

    /**
     * Tests {@link RentalRepository#countReturnedLate()} ({@code SAKILA-RENTAL-A-01}).
     */
    @Nested
    class CountReturnedLate_Test {

        /**
         * Asserts that the native count is the number of returned rentals whose return date is after the rental date
         * plus the film's rental duration in days, computed in memory over every returned rental, read by a
         * {@link Specification} that fetches the copy and its film along, so that the duration is at hand outside
         * any transaction.
         * <p>
         * MySQL's {@code DATETIME} has no fraction of a second, and neither does the {@code LocalDateTime} loaded from
         * it, and {@code INTERVAL n DAY} adds what {@code plusDays(n)} adds, so the two counts have to be the same.
         */
        @Test
        void __() {
            final var count = repositoryInstance().countReturnedLate();
            final Specification<Rental> returned = (r, q, b) -> {
                r.fetch(Rental_.inventory).fetch(Inventory_.film);
                return b.isNotNull(r.get(Rental_.returnDate));
            };
            final var expected = repositoryInstance().findAll(returned).stream()
                    .filter(r -> {
                        final var allowed = r.getRentalDate().plusDays(r.getInventory().getFilm().getRentalDuration());
                        return r.getReturnDate().isAfter(allowed);
                    })
                    .count();
            assertThat(count).isEqualTo(expected);
        }
    }
}
