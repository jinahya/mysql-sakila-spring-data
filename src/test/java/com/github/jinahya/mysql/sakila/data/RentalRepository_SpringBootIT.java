package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.data.RentalRepository.DayRentalCount;
import com.github.jinahya.mysql.sakila.data.RentalRepository.MonthRentalCount;
import com.github.jinahya.mysql.sakila.data.RentalRepository.RentalWait;
import com.github.jinahya.mysql.sakila.data.RentalRepository.StoreRentalCount;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link RentalRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few rentals of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, against
 * counts of the rentals taken another way, or against the output upstream's {@code rental_exercises_*.sql} records
 * for the installed data. The base's own tests, such as its {@code findById} round trips, run here too.
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

    /**
     * A sort by the {@code rental_id} column, for the native {@link RentalRepository#listReturnedLate(Pageable)},
     * whose sort is applied to its SQL as it is.
     */
    private static final Sort BY_RENTAL_ID_COLUMN = Sort.by(Sort.Direction.ASC, Rental.COLUMN_NAME_RENTAL_ID);

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
     * Counts the rentals made at or after the specified instant and before the other, through a
     * {@link Specification} on the rental date, and optionally only those not returned yet.
     *
     * @param start       the first instant.
     * @param end         the instant after the last.
     * @param notReturned whether to count the rentals not returned yet only.
     * @return the number of rentals.
     */
    private long countRentedBetween(final LocalDateTime start, final LocalDateTime end, final boolean notReturned) {
        final Specification<Rental> specification = (r, q, b) -> b.and(
                b.greaterThanOrEqualTo(r.get(Rental_.rentalDate), start),
                b.lessThan(r.get(Rental_.rentalDate), end),
                notReturned ? b.isNull(r.get(Rental_.returnDate)) : b.conjunction()
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

        private static Stream<Rental> rentals() {
            return RentalRepository_SpringBootIT.rentals();
        }

        /**
         * Asserts that the count is the exercise's recorded output, 183, and the number of all rentals minus those
         * returned, counted by a {@link Specification} of the opposite predicate, {@code isNotNull} on the return
         * date.
         */
        @Test
        void __() {
            final var count = repositoryInstance().countNotReturned();
            assertThat(count).isEqualTo(183L);
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
    }

    /**
     * Tests {@link RentalRepository#selectMonthlyCounts()} ({@code SAKILA-RENTAL-B-02}).
     */
    @Nested
    class SelectMonthlyCounts_Test {

        private static Stream<Rental> rentals() {
            return RentalRepository_SpringBootIT.rentals();
        }

        /**
         * Asserts that the list is the exercise's recorded output, five months, with their numbers of rentals.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectMonthlyCounts();
            assertThat(list)
                    .extracting(c -> "%d-%02d".formatted(c.getYear(), c.getMonth()), MonthRentalCount::getRentals)
                    .containsExactly(tuple("2005-05", 1156L),
                                     tuple("2005-06", 2311L),
                                     tuple("2005-07", 6709L),
                                     tuple("2005-08", 5686L),
                                     tuple("2006-02", 182L));
        }

        /**
         * Asserts that the list is ordered by the year and then by the month, that each count is the number of
         * rentals whose rental date falls in that month, counted again by a {@link Specification} on the dates, and
         * that the counts add up to all rentals.
         */
        @Test
        void __Ordered() {
            final var list = repositoryInstance().selectMonthlyCounts();
            assertThat(list)
                    .isSortedAccordingTo(Comparator.comparing(MonthRentalCount::getYear)
                                                 .thenComparing(MonthRentalCount::getMonth))
                    .allSatisfy(c -> {
                        final var start = LocalDateTime.of(c.getYear(), c.getMonth(), 1, 0, 0);
                        assertThat(c.getRentals()).as("rentals in %d-%02d", c.getYear(), c.getMonth())
                                .isEqualTo(countRentedBetween(start, start.plusMonths(1L), false));
                    });
            assertThat(list.stream().mapToLong(MonthRentalCount::getRentals).sum())
                    .isEqualTo(repositoryInstance().count());
        }

        /**
         * Asserts that the month of a selected rental's rental date is among the months selected.
         *
         * @param rental one of the rentals selected in {@link RentalRepository_SpringBootIT#__()}.
         */
        @MethodSource("rentals")
        @ParameterizedTest
        void __Selected(final Rental rental) {
            final var list = repositoryInstance().selectMonthlyCounts();
            final var rentalDate = rental.getRentalDate();
            assertThat(list)
                    .anySatisfy(c -> {
                        assertThat(c.getYear()).isEqualTo(rentalDate.getYear());
                        assertThat(c.getMonth()).isEqualTo(rentalDate.getMonthValue());
                    });
        }
    }

    /**
     * Tests {@link RentalRepository#selectNotReturnedCountsByStore(Pageable)} ({@code SAKILA-RENTAL-I-01}).
     */
    @Nested
    class SelectNotReturnedCountsByStore_Test {

        private static final Sort BY_STORE_ID = Sort.by("storeId");

        /**
         * Asserts that the stores, by store id as the exercise sorts, are the exercise's recorded output, 92 and 91
         * rentals not returned yet.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectNotReturnedCountsByStore(PageRequest.of(0, 5, BY_STORE_ID));
            assertThat(page.getContent())
                    .extracting(StoreRentalCount::getStoreId, StoreRentalCount::getRentals)
                    .containsExactly(tuple(1, 92L), tuple(2, 91L));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        /**
         * Asserts that the page is ordered by store id, that each count is the number of rentals not returned yet of
         * the store's copies, counted by a {@link Specification}, and that the counts add up to
         * {@link RentalRepository#countNotReturned()}.
         */
        @Test
        void __Ordered() {
            final var page = repositoryInstance().selectNotReturnedCountsByStore(Pageable.unpaged(BY_STORE_ID));
            assertThat(page.getContent())
                    .isSortedAccordingTo(Comparator.comparing(StoreRentalCount::getStoreId))
                    .allSatisfy(c -> {
                        final Specification<Rental> specification = (r, q, b) -> b.and(
                                b.isNull(r.get(Rental_.returnDate)),
                                b.equal(r.get(Rental_.inventory).get(Inventory_.storeId), c.getStoreId()));
                        assertThat(c.getRentals()).isEqualTo(repositoryInstance().count(specification));
                    });
            assertThat(page.getContent().stream().mapToLong(StoreRentalCount::getRentals).sum())
                    .isEqualTo(repositoryInstance().countNotReturned());
        }

        /**
         * Asserts that pages of one, by rentals descending, hold the store with the most first, and then the other.
         */
        @Test
        void __Paged() {
            final var sort = Sort.by(Sort.Direction.DESC, "rentals");
            final var first = repositoryInstance().selectNotReturnedCountsByStore(PageRequest.of(0, 1, sort));
            assertThat(first.getContent()).extracting(StoreRentalCount::getStoreId).containsExactly(1);
            assertThat(first.getTotalPages()).isEqualTo(2);
            final var second = repositoryInstance().selectNotReturnedCountsByStore(first.nextPageable());
            assertThat(second.getContent()).extracting(StoreRentalCount::getStoreId).containsExactly(2);
            assertThat(second.hasNext()).isFalse();
        }
    }

    /**
     * Tests {@link RentalRepository#selectNotReturnedCountsByDay(Pageable)} ({@code SAKILA-RENTAL-I-02}).
     */
    @Nested
    class SelectNotReturnedCountsByDay_Test {

        private static final Sort BY_DAY = Sort.by("day");

        /**
         * Asserts that the days, by day as the exercise sorts, are the exercise's recorded output.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectNotReturnedCountsByDay(PageRequest.of(0, 5, BY_DAY));
            assertThat(page.getContent())
                    .extracting(DayRentalCount::getDay, DayRentalCount::getRentals)
                    .containsExactly(tuple(LocalDate.of(2005, 8, 21), 1L),
                                     tuple(LocalDate.of(2006, 2, 14), 182L));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        /**
         * Asserts that the page is ordered by day, that each count is the number of rentals made on the day and not
         * returned yet, counted by a {@link Specification}, and that the counts add up to
         * {@link RentalRepository#countNotReturned()}.
         */
        @Test
        void __Ordered() {
            final var page = repositoryInstance().selectNotReturnedCountsByDay(Pageable.unpaged(BY_DAY));
            assertThat(page.getContent())
                    .isSortedAccordingTo(Comparator.comparing(DayRentalCount::getDay))
                    .allSatisfy(c -> {
                        final var start = c.getDay().atStartOfDay();
                        assertThat(c.getRentals()).isEqualTo(countRentedBetween(start, start.plusDays(1L), true));
                    });
            assertThat(page.getContent().stream().mapToLong(DayRentalCount::getRentals).sum())
                    .isEqualTo(repositoryInstance().countNotReturned());
        }

        /**
         * Asserts that pages of one, by day descending, hold the later day first.
         */
        @Test
        void __Paged() {
            final var sort = Sort.by(Sort.Direction.DESC, "day");
            final var first = repositoryInstance().selectNotReturnedCountsByDay(PageRequest.of(0, 1, sort));
            assertThat(first.getContent()).extracting(DayRentalCount::getDay)
                    .containsExactly(LocalDate.of(2006, 2, 14));
            final var second = repositoryInstance().selectNotReturnedCountsByDay(first.nextPageable());
            assertThat(second.getContent()).extracting(DayRentalCount::getDay)
                    .containsExactly(LocalDate.of(2005, 8, 21));
        }
    }

    /**
     * Tests {@link RentalRepository#listReturnedLate(Pageable)} ({@code SAKILA-RENTAL-A-01}).
     */
    @Nested
    class ListReturnedLate_Test {

        /**
         * Asserts that the rentals returned late are as many as the exercise's recorded output, 8121, and as many as
         * computed in memory over every returned rental, read by a {@link Specification} that fetches the copy and
         * its film along, so that the duration is at hand outside any transaction.
         * <p>
         * MySQL's {@code DATETIME} has no fraction of a second, and neither does the {@code LocalDateTime} loaded from
         * it, and {@code INTERVAL n DAY} adds what {@code plusDays(n)} adds, so the two counts have to be the same.
         */
        @Test
        void __() {
            final var page = repositoryInstance().listReturnedLate(PageRequest.of(0, 5, BY_RENTAL_ID_COLUMN));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(8121L);
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
            assertThat(page.getTotalElements()).isEqualTo(expected);
        }

        /**
         * Asserts that a page of the given size, by the {@code rental_id} column, is ordered by id, and that each
         * rental of it was returned after its rental date plus its film's rental duration.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 10})
        @ParameterizedTest
        void __Ordered(final int size) {
            final var page = repositoryInstance().listReturnedLate(PageRequest.of(0, size, BY_RENTAL_ID_COLUMN));
            assertPage(page);
            final var ids = page.getContent().stream().map(Rental::getRentalId).toList();
            assertThat(ids).hasSize(size).isSorted();
            final Specification<Rental> ofThePage = (r, q, b) -> {
                r.fetch(Rental_.inventory).fetch(Inventory_.film);
                return r.get(Rental_.rentalId).in(ids);
            };
            assertThat(repositoryInstance().findAll(ofThePage))
                    .hasSize(size)
                    .allSatisfy(r -> assertThat(r.getReturnDate()).isAfter(
                            r.getRentalDate().plusDays(r.getInventory().getFilm().getRentalDuration())));
        }

        /**
         * Asserts that the second page of five continues the first: its rentals are the sixth to the tenth of a page
         * of ten.
         */
        @Test
        void __Paged() {
            final var first = repositoryInstance().listReturnedLate(PageRequest.of(0, 5, BY_RENTAL_ID_COLUMN));
            final var second = repositoryInstance().listReturnedLate(first.nextPageable());
            final var ten = repositoryInstance().listReturnedLate(PageRequest.of(0, 10, BY_RENTAL_ID_COLUMN));
            final var walked = new ArrayList<>(first.getContent());
            walked.addAll(second.getContent());
            assertThat(walked).containsExactlyElementsOf(ten.getContent());
            assertThat(second.getTotalElements()).isEqualTo(first.getTotalElements());
        }
    }

    /**
     * Tests {@link RentalRepository#selectLongestWaitsBetweenRentals()} ({@code SAKILA-RENTAL-A-02}).
     */
    @Nested
    class SelectLongestWaitsBetweenRentals_Test {

        /**
         * Asserts that the native query selects the exercise's recorded output: five waits, by days descending, then
         * by customer id.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectLongestWaitsBetweenRentals();
            assertThat(list)
                    .extracting(RentalWait::getCustomerId, RentalWait::getDays)
                    .containsExactly(tuple(61, 57L),
                                     tuple(101, 43L),
                                     tuple(4, 39L),
                                     tuple(401, 39L),
                                     tuple(433, 39L));
        }

        /**
         * Asserts that the waits are ordered by days descending and then by customer id, and that each is the days
         * between its two dates, the earlier the previous rental, as {@code DATEDIFF} counts them, by calendar dates.
         */
        @Test
        void __Ordered() {
            final var list = repositoryInstance().selectLongestWaitsBetweenRentals();
            assertThat(list)
                    .isSortedAccordingTo(Comparator.comparingLong(RentalWait::getDays).reversed()
                                                 .thenComparing(RentalWait::getCustomerId))
                    .allSatisfy(w -> {
                        assertThat(w.getPreviousRental()).isBefore(w.getRentalDate());
                        assertThat(w.getDays()).isEqualTo(ChronoUnit.DAYS.between(
                                w.getPreviousRental().toLocalDate(), w.getRentalDate().toLocalDate()));
                    });
        }
    }

    /**
     * Tests {@link RentalRepository#listOverlapping(Pageable)} ({@code SAKILA-RENTAL-A-03}).
     */
    @Nested
    class ListOverlapping_Test {

        /**
         * Asserts that no rental is overlapped by another of the same copy, which is the exercise's recorded output,
         * zero.
         */
        @Test
        void __() {
            final var page = repositoryInstance().listOverlapping(PageRequest.of(0, 5, BY_RENTAL_ID));
            assertPage(page);
            assertThat(page).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        /**
         * Asserts that a page past the first is empty too, with no next one.
         */
        @Test
        void __Paged() {
            final var page = repositoryInstance().listOverlapping(PageRequest.of(1, 5, BY_RENTAL_ID));
            assertThat(page).isEmpty();
            assertThat(page.hasNext()).isFalse();
        }
    }
}
